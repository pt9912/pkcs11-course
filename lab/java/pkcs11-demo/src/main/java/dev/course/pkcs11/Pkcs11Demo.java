package dev.course.pkcs11;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.cert.Certificate;
import java.util.Base64;
import java.util.Enumeration;

public final class Pkcs11Demo {
    private Pkcs11Demo() {}

    public static void main(String[] args) throws Exception {
        String pin = System.getenv().getOrDefault("PKCS11_USER_PIN", "987654");
        String configPath = System.getenv().getOrDefault(
                "PKCS11_JAVA_CONFIG",
                "src/main/resources/softhsm.cfg"
        );

        Provider base = Security.getProvider("SunPKCS11");
        if (base == null) {
            throw new IllegalStateException("SunPKCS11 Provider ist in diesem JDK nicht verfuegbar.");
        }

        Provider provider = base.configure(configPath);
        Security.addProvider(provider);

        System.out.println("Provider: " + provider.getName());

        KeyStore keyStore = KeyStore.getInstance("PKCS11", provider);
        keyStore.load(null, pin.toCharArray());

        String alias = findPrivateKeyAlias(keyStore);
        if (alias == null) {
            System.err.println("Kein Private-Key-Alias im PKCS#11-KeyStore sichtbar.");
            System.err.println("Ein Zertifikat mit derselben CKA_ID wie der Private Key fehlt im Token.");
            System.err.println("Importiere zuerst: make import-cert");
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

        byte[] signature = sign(provider, privateKey, data);
        boolean ok = verify(publicKey, data, signature);

        System.out.println("Alias: " + alias);
        System.out.println("Signatur (Base64): " + Base64.getEncoder().encodeToString(signature));
        System.out.println("Verifikation: " + ok);

        if (!ok) {
            System.exit(3);
        }
    }

    private static String findPrivateKeyAlias(KeyStore keyStore) throws Exception {
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
            if (found == null && isKey) {
                found = alias;
            }
        }
        if (!any) {
            System.out.println("- keine Aliase sichtbar");
        }
        return found;
    }

    private static byte[] sign(Provider provider, PrivateKey privateKey, byte[] data) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA", provider);
        signature.initSign(privateKey);
        signature.update(data);
        return signature.sign();
    }

    private static boolean verify(PublicKey publicKey, byte[] data, byte[] signature) throws Exception {
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(publicKey);
        verifier.update(data);
        return verifier.verify(signature);
    }
}
