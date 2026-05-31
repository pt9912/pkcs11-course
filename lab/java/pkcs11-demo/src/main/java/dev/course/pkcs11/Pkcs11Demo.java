package dev.course.pkcs11;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.interfaces.ECKey;
import java.security.interfaces.RSAKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Enumeration;
import java.util.Locale;

public final class Pkcs11Demo {
    private Pkcs11Demo() {}

    public static void main(String[] args) {
        // nonEmpty statt getOrDefault: leere ENV-Werte ("") muessen wie nicht
        // gesetzte behandelt werden, sonst kippt das Makefile-export-Pass-Through
        // die Defaults auf "" (siehe 0.15.1 Makefile-DOCKER_ENV-Aenderung).
        String configPath = nonEmpty(System.getenv("PKCS11_JAVA_CONFIG"), "src/main/resources/softhsm.cfg");
        String mechanism = nonEmpty(System.getenv("PKCS11_MECHANISM"), "SHA256withRSA");
        // Default auf signing-key: ohne Default zog die Alias-Suche den ersten
        // KeyEntry, was nach Modulen mit weiteren Privkeys (wrap-key aus 13,
        // hmac-key aus 16, ca-key aus 22) der falsche war. Explizit signing-key
        // entspricht dem, was Kapitel und Uebung erwarten.
        String preferredAlias = nonEmpty(System.getenv("PKCS11_KEY_ALIAS"), "signing-key");
        String slotOverride = nullIfBlank(System.getenv("PKCS11_SLOT_ID"));
        String libraryOverride = nullIfBlank(System.getenv("PKCS11_LIBRARY"));

        char[] pin = nonEmpty(System.getenv("PKCS11_USER_PIN"), "987654").toCharArray();

        try {
            Provider base = Security.getProvider("SunPKCS11");
            if (base == null) {
                throw new IllegalStateException("SunPKCS11 Provider ist in diesem JDK nicht verfuegbar.");
            }

            Provider provider = base.configure(buildConfigArgument(configPath, slotOverride, libraryOverride));

            System.out.println("Provider: " + provider.getName());
            System.out.println("Mechanismus: " + mechanism);

            KeyStore keyStore = KeyStore.getInstance("PKCS11", provider);
            try {
                keyStore.load(null, pin);
            } finally {
                Arrays.fill(pin, '\0');
            }

            String alias = findPrivateKeyAlias(keyStore, mechanism, preferredAlias);
            if (alias == null) {
                System.err.println("Kein passender Private-Key-Alias im PKCS#11-KeyStore sichtbar.");
                System.err.println("Moegliche Ursachen:");
                System.err.println("- Kein Zertifikat mit derselben CKA_ID wie der Private Key (make import-cert)");
                System.err.println("- Key-Typ passt nicht zum Mechanismus '" + mechanism + "'");
                System.err.println("- PKCS11_KEY_ALIAS zeigt auf einen nicht existierenden Alias");
                System.exit(2);
            }

            PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, null);
            Certificate certificate = keyStore.getCertificate(alias);
            if (certificate == null) {
                System.err.println("Alias '" + alias + "' hat keine Zertifikatskette.");
                System.exit(2);
            }
            PublicKey publicKey = certificate.getPublicKey();

            byte[] data = "hello from java pkcs11".getBytes(StandardCharsets.UTF_8);

            byte[] signature = sign(provider, privateKey, mechanism, data);
            boolean ok = verify(provider, publicKey, mechanism, data, signature);

            System.out.println("Alias: " + alias);
            System.out.println("Signatur (Base64): " + Base64.getEncoder().encodeToString(signature));
            System.out.println("Verifikation: " + ok);

            if (!ok) {
                System.exit(3);
            }
        } catch (Exception e) {
            reportFailure(e);
            System.exit(1);
        }
    }

    // SunPKCS11 akzeptiert den Argument-String entweder als Pfad zu einer
    // Config-Datei oder, wenn er mit "--" beginnt, als Inline-Inhalt.
    // Inline-Modus erlaubt es, slotListIndex und library per ENV zu ueberschreiben,
    // ohne die getrackte softhsm.cfg zu veraendern.
    private static String buildConfigArgument(String configPath, String slotOverride, String libraryOverride) throws IOException {
        if (slotOverride == null && libraryOverride == null) {
            return configPath;
        }
        String base = Files.readString(Path.of(configPath));
        StringBuilder sb = new StringBuilder("--");
        sb.append(base);
        if (!base.endsWith("\n")) {
            sb.append('\n');
        }
        if (libraryOverride != null) {
            sb.append("library = ").append(libraryOverride).append('\n');
        }
        if (slotOverride != null) {
            // Slot-ID hat Vorrang vor slotListIndex.
            sb.append("slot = ").append(slotOverride).append('\n');
        }
        return sb.toString();
    }

    private static String findPrivateKeyAlias(KeyStore keyStore, String mechanism, String preferred) throws Exception {
        System.out.println("Aliase im PKCS#11-KeyStore:");
        Enumeration<String> aliases = keyStore.aliases();
        String found = null;
        boolean any = false;
        while (aliases.hasMoreElements()) {
            any = true;
            String alias = aliases.nextElement();
            boolean isKey = keyStore.isKeyEntry(alias);
            boolean isCert = keyStore.isCertificateEntry(alias);
            System.out.println("- " + alias + " key=" + isKey + " cert=" + isCert);
            if (!isKey) {
                continue;
            }
            if (preferred != null && preferred.equals(alias)) {
                return alias;
            }
            if (preferred == null && found == null && matchesMechanism(keyStore.getKey(alias, null), mechanism)) {
                found = alias;
            }
        }
        if (!any) {
            System.out.println("- keine Aliase sichtbar");
        }
        return found;
    }

    private static boolean matchesMechanism(java.security.Key key, String mechanism) {
        // SunPKCS11 meldet bei nicht-extrahierbaren Keys manchmal Algorithm "PRIVATE" —
        // in dem Fall koennen wir den Typ nicht pruefen und akzeptieren den Alias.
        String algorithm = key.getAlgorithm().toUpperCase(Locale.ROOT);
        String m = mechanism.toUpperCase(Locale.ROOT);
        if (m.contains("RSA")) {
            return key instanceof RSAKey || algorithm.contains("RSA") || algorithm.equals("PRIVATE");
        }
        if (m.contains("ECDSA") || m.contains("WITHECDSA")) {
            return key instanceof ECKey || algorithm.contains("EC") || algorithm.equals("PRIVATE");
        }
        return true;
    }

    private static byte[] sign(Provider provider, PrivateKey privateKey, String mechanism, byte[] data) throws Exception {
        Signature signature = Signature.getInstance(mechanism, provider);
        if (isPss(mechanism)) {
            signature.setParameter(defaultPssParameters());
        }
        signature.initSign(privateKey);
        signature.update(data);
        return signature.sign();
    }

    private static boolean verify(Provider provider, PublicKey publicKey, String mechanism, byte[] data, byte[] signature) throws Exception {
        // Verifikation bewusst mit demselben PKCS#11-Provider, damit nicht-extrahierbare
        // Public Keys (z.B. EC mit CKA_EXTRACTABLE=false) ebenfalls funktionieren.
        Signature verifier = Signature.getInstance(mechanism, provider);
        if (isPss(mechanism)) {
            verifier.setParameter(defaultPssParameters());
        }
        verifier.initVerify(publicKey);
        verifier.update(data);
        return verifier.verify(signature);
    }

    private static boolean isPss(String mechanism) {
        String m = mechanism.toUpperCase(Locale.ROOT);
        return m.equals("RSASSA-PSS") || m.endsWith("WITHRSAANDMGF1") || m.contains("PSS");
    }

    private static PSSParameterSpec defaultPssParameters() {
        // SHA-256 / MGF1-SHA-256 / saltLen = hashLen = 32 / trailerField = 1.
        // Muss zu den CK_RSA_PKCS_PSS_PARAMS auf der Token-Seite passen,
        // sonst CKR_MECHANISM_PARAM_INVALID.
        return new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1);
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    private static String nonEmpty(String s, String fallback) {
        return (s == null || s.isEmpty()) ? fallback : s;
    }

    private static void reportFailure(Throwable t) {
        System.err.println("Fehler beim PKCS#11-Lauf:");
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth < 8) {
            String label = cur instanceof ProviderException ? "ProviderException" : cur.getClass().getSimpleName();
            System.err.println("  " + label + ": " + cur.getMessage());
            cur = cur.getCause();
            depth++;
        }
    }
}
