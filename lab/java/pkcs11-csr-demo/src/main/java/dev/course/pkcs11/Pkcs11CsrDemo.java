package dev.course.pkcs11;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;

public final class Pkcs11CsrDemo {
    // CSR-Generierung mit HSM-Signing-Key — analog zum CMS-Demo:
    // ContentSigner wird vom JcaContentSignerBuilder mit setProvider(SunPKCS11)
    // erzeugt; BC nutzt diesen Signer fuer die CSR-TBS-Signatur. Der private
    // Key verlaesst den HSM nicht — nur die CSR-Subject-TBS-Bytes wandern hin.
    private Pkcs11CsrDemo() {}

    public static void main(String[] args) {
        String configPath = nonEmpty(System.getenv("PKCS11_JAVA_CONFIG"), "src/main/resources/softhsm.cfg");
        String preferredAlias = nullIfBlank(System.getenv("PKCS11_KEY_ALIAS"));
        String slotOverride = nullIfBlank(System.getenv("PKCS11_SLOT_ID"));
        String libraryOverride = nullIfBlank(System.getenv("PKCS11_LIBRARY"));
        char[] pin = nonEmpty(System.getenv("PKCS11_USER_PIN"), "987654").toCharArray();
        String outputDir = nonEmpty(System.getenv("PKCS11_OUTPUT_DIR"), "/workspace/lab/work");

        try {
            Provider base = Security.getProvider("SunPKCS11");
            if (base == null) {
                throw new IllegalStateException("SunPKCS11 Provider ist in diesem JDK nicht verfuegbar.");
            }
            Provider provider = base.configure(buildConfigArgument(configPath, slotOverride, libraryOverride));
            System.out.println("Provider: " + provider.getName());

            KeyStore keyStore = KeyStore.getInstance("PKCS11", provider);
            try {
                keyStore.load(null, pin);
            } finally {
                Arrays.fill(pin, '\0');
            }

            String alias = resolveAlias(keyStore, preferredAlias);
            if (alias == null) {
                System.err.println("Kein Alias mit Cert-Eintrag gefunden — erst 'make import-cert' ausfuehren.");
                System.exit(2);
            }
            PrivateKey privKey = (PrivateKey) keyStore.getKey(alias, null);
            PublicKey pubKey = ((X509Certificate) keyStore.getCertificate(alias)).getPublicKey();

            X500Name subject = new X500Name("CN=java-app.example.org, O=PKCS11 Lab");

            // SAN- und KeyUsage-Extensions im extensionRequest-Attribut.
            ExtensionsGenerator extGen = new ExtensionsGenerator();
            extGen.addExtension(Extension.subjectAlternativeName, false,
                    new GeneralNames(new GeneralName(GeneralName.dNSName, "java-app.example.org")));
            extGen.addExtension(Extension.keyUsage, true,
                    new KeyUsage(KeyUsage.digitalSignature));

            PKCS10CertificationRequestBuilder csrBuilder =
                    new JcaPKCS10CertificationRequestBuilder(subject, pubKey)
                            .addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extGen.generate());

            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                    .setProvider(provider)
                    .build(privKey);

            PKCS10CertificationRequest csr = csrBuilder.build(signer);

            // Self-verify: Signatur vs eingebetteter Pubkey.
            JcaPKCS10CertificationRequest jcaCsr = new JcaPKCS10CertificationRequest(csr).setProvider("SunJCE");
            boolean ok = jcaCsr.isSignatureValid(new JcaContentVerifierProviderBuilder().build(pubKey));
            if (!ok) {
                System.err.println("CSR-Selbstsignatur ungueltig.");
                System.exit(3);
            }

            Path out = Path.of(outputDir);
            Files.createDirectories(out);
            Path csrPath = out.resolve("java-app.csr");
            String pem = "-----BEGIN CERTIFICATE REQUEST-----\n"
                    + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(csr.getEncoded())
                    + "\n-----END CERTIFICATE REQUEST-----\n";
            Files.writeString(csrPath, pem);

            System.out.println("--- CSR-Generierung ---");
            System.out.println("Alias:        " + alias);
            System.out.println("Subject:      " + subject);
            System.out.println("DNS-SAN:      java-app.example.org");
            System.out.println("SignAlgo:     sha256WithRSAEncryption");
            System.out.println("CSR:          " + csrPath + " (" + pem.length() + " Bytes PEM)");
            System.out.println("Selbstsignatur (PoP): OK");
        } catch (Exception e) {
            reportFailure(e);
            System.exit(1);
        }
    }

    private static String buildConfigArgument(String configPath, String slotOverride, String libraryOverride) throws IOException {
        if (slotOverride == null && libraryOverride == null) {
            return configPath;
        }
        String base = Files.readString(Path.of(configPath));
        StringBuilder sb = new StringBuilder("--");
        sb.append(base);
        if (!base.endsWith("\n")) sb.append('\n');
        if (libraryOverride != null) sb.append("library = ").append(libraryOverride).append('\n');
        if (slotOverride != null) sb.append("slot = ").append(slotOverride).append('\n');
        return sb.toString();
    }

    private static String resolveAlias(KeyStore keyStore, String preferred) throws Exception {
        var aliases = keyStore.aliases();
        String found = null;
        while (aliases.hasMoreElements()) {
            String a = aliases.nextElement();
            if (!keyStore.isKeyEntry(a)) continue;
            if (preferred != null && preferred.equals(a)) return a;
            if (preferred == null && "signing-key".equals(a)) found = a;
            if (preferred == null && found == null) found = a;
        }
        return found;
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
