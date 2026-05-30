package dev.course.pkcs11;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.ProviderException;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

public final class Pkcs11CmsDemo {
    // CMS/PKCS#7 — detached SignedData mit HSM-Key.
    //
    // BouncyCastle ist der Standard fuer CMS in der JVM. SunPKCS11 liefert
    // den PrivateKey-Handle, JcaContentSignerBuilder bindet ihn als
    // ContentSigner. BCs CMSSignedDataGenerator orchestriert die SignedAttrs,
    // ruft den ContentSigner mit der DER-Encoding der Attrs auf, der Signer
    // routet das via SunPKCS11 zum HSM (CKM_SHA256_RSA_PKCS). Privater Key
    // verlaesst den HSM nicht — nur die Signed-Attrs wandern hin und der
    // Signaturblock zurueck.
    private Pkcs11CmsDemo() {}

    public static void main(String[] args) {
        String configPath = System.getenv().getOrDefault("PKCS11_JAVA_CONFIG", "src/main/resources/softhsm.cfg");
        String preferredAlias = nullIfBlank(System.getenv("PKCS11_KEY_ALIAS"));
        String slotOverride = nullIfBlank(System.getenv("PKCS11_SLOT_ID"));
        String libraryOverride = nullIfBlank(System.getenv("PKCS11_LIBRARY"));
        char[] pin = (System.getenv().getOrDefault("PKCS11_USER_PIN", "987654")).toCharArray();
        String outputDir = System.getenv().getOrDefault("PKCS11_OUTPUT_DIR", "/workspace/lab/work");

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
            X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias);
            if (cert == null) {
                System.err.println("Alias '" + alias + "' hat keine Zertifikatskette.");
                System.exit(2);
            }

            byte[] content = "Vertrag XYZ vom 30.05.2026\nUnterzeichnender: Java-CMS-Demo\n".getBytes(StandardCharsets.UTF_8);

            // ContentSigner: SHA256withRSA, ueber den SunPKCS11-Provider → das Token signiert.
            ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA")
                    .setProvider(provider)
                    .build(privKey);

            CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
            gen.addSignerInfoGenerator(
                    new JcaSignerInfoGeneratorBuilder(
                            new JcaDigestCalculatorProviderBuilder().build())
                            .build(contentSigner, cert));
            gen.addCertificates(new JcaCertStore(List.of(cert)));

            CMSSignedData signed = gen.generate(new CMSProcessableByteArray(content), false);
            byte[] der = signed.getEncoded();

            // Self-Verify: Cert aus der SignedData rausziehen und SignerInfo pruefen.
            CMSSignedData reparsed = new CMSSignedData(new CMSProcessableByteArray(content), der);
            for (SignerInformation si : reparsed.getSignerInfos().getSigners()) {
                if (!si.verify(new JcaSimpleSignerInfoVerifierBuilder().build(cert))) {
                    System.err.println("Self-Verify fehlgeschlagen.");
                    System.exit(3);
                }
            }

            Path out = Path.of(outputDir);
            Files.createDirectories(out);
            Path contentPath = out.resolve("java-cms-document.txt");
            Path sigPath = out.resolve("java-cms-document.p7s");
            Files.write(contentPath, content);
            Files.write(sigPath, der);

            System.out.println("Alias:          " + alias);
            System.out.println("Signer-Subject: " + cert.getSubjectX500Principal());
            System.out.println("Klartext:       " + contentPath + " (" + content.length + " Bytes)");
            System.out.println("CMS-Signatur:   " + sigPath + " (" + der.length + " Bytes, detached)");
            System.out.println("Self-Verify:    OK");
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
        System.out.println("Aliase im PKCS#11-KeyStore:");
        Enumeration<String> aliases = keyStore.aliases();
        String found = null;
        boolean any = false;
        while (aliases.hasMoreElements()) {
            any = true;
            String a = aliases.nextElement();
            boolean isKey = keyStore.isKeyEntry(a);
            System.out.println("- " + a + " key=" + isKey);
            if (!isKey) continue;
            if (preferred != null && preferred.equals(a)) return a;
            if (preferred == null && "signing-key".equals(a)) found = a;
            if (preferred == null && found == null) found = a;
        }
        if (!any) System.out.println("- keine Aliase sichtbar");
        return found;
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isEmpty()) ? null : s;
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
