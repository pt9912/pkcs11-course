package dev.course.pkcs11;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.ProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.util.Arrays;
import java.util.Enumeration;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public final class Pkcs11StreamDemo {
    // PKCS#11 Multi-Part / Streaming ueber SunPKCS11:
    // Signature.update(chunk) → C_SignUpdate
    // Signature.sign()        → C_SignFinal
    // Cipher.update(chunk)    → C_EncryptUpdate / C_DecryptUpdate
    // Cipher.doFinal()        → C_EncryptFinal  / C_DecryptFinal
    //
    // SunPKCS11-KeyStore exponiert SECRET-Keys ueber CKA_LABEL als Alias —
    // anders als bei Private-Keys ist KEIN Cert noetig.
    private static final int CHUNK = 64 * 1024;

    private Pkcs11StreamDemo() {}

    public static void main(String[] args) {
        String configPath = System.getenv().getOrDefault("PKCS11_JAVA_CONFIG", "src/main/resources/softhsm.cfg");
        String slotOverride = nullIfBlank(System.getenv("PKCS11_SLOT_ID"));
        String libraryOverride = nullIfBlank(System.getenv("PKCS11_LIBRARY"));
        char[] pin = nonEmpty(System.getenv("PKCS11_USER_PIN"), "987654").toCharArray();
        String outputDir = nonEmpty(System.getenv("PKCS11_OUTPUT_DIR"), "/workspace/lab/work");
        // Hinweis: Makefile reicht PKCS11_KEY_ALIAS/PKCS11_AES_STREAM_LABEL als
        // leeren String durch, wenn sie nicht gesetzt sind — getOrDefault wuerde
        // den Default nur bei null nehmen. nonEmpty kapselt beides.
        String signAlias = nonEmpty(System.getenv("PKCS11_KEY_ALIAS"), "signing-key");
        String aesAlias = nonEmpty(System.getenv("PKCS11_AES_STREAM_LABEL"), "aes-stream-key");

        Path inputPath = Path.of(outputDir, "large.bin");
        Path sigPath = Path.of(outputDir, "java-stream.sig");
        Path encPath = Path.of(outputDir, "java-stream.enc");
        Path decPath = Path.of(outputDir, "java-stream.dec");

        try {
            if (!Files.exists(inputPath)) {
                System.err.println("Testfile fehlt: " + inputPath + ". Erst 'make stream-sign' ausfuehren.");
                System.exit(2);
            }
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

            listAliases(keyStore);
            PrivateKey signKey = (PrivateKey) keyStore.getKey(signAlias, null);
            if (signKey == null) {
                System.err.println("Alias '" + signAlias + "' fehlt (signing-key). Erst 'make import-cert' ausfuehren.");
                System.exit(2);
            }
            SecretKey aesKey = (SecretKey) keyStore.getKey(aesAlias, null);
            if (aesKey == null) {
                System.err.println("Alias '" + aesAlias + "' fehlt. Erst 'make gen-aes-stream' ausfuehren.");
                System.exit(2);
            }

            long inputSize = Files.size(inputPath);

            // --- Sign-Streaming ---
            Signature signer = Signature.getInstance("SHA256withRSA", provider);
            signer.initSign(signKey);
            try (InputStream in = Files.newInputStream(inputPath)) {
                byte[] buf = new byte[CHUNK];
                int n;
                while ((n = in.read(buf)) > 0) {
                    signer.update(buf, 0, n);
                }
            }
            byte[] sig = signer.sign();
            Files.write(sigPath, sig);
            System.out.printf("Sign:    %s → %s (%d Bytes Signatur ueber %d Bytes Input)%n",
                    inputPath.getFileName(), sigPath.getFileName(), sig.length, inputSize);

            // --- Encrypt-Streaming ---
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);

            Cipher encCipher = Cipher.getInstance("AES/CBC/PKCS5Padding", provider);
            encCipher.init(Cipher.ENCRYPT_MODE, aesKey, new IvParameterSpec(iv));
            streamCipher(encCipher, inputPath, encPath);
            System.out.printf("Encrypt: %s → %s (%d Bytes inkl. PKCS#7-Padding)%n",
                    inputPath.getFileName(), encPath.getFileName(), Files.size(encPath));

            Cipher decCipher = Cipher.getInstance("AES/CBC/PKCS5Padding", provider);
            decCipher.init(Cipher.DECRYPT_MODE, aesKey, new IvParameterSpec(iv));
            streamCipher(decCipher, encPath, decPath);
            System.out.printf("Decrypt: %s → %s (%d Bytes)%n",
                    encPath.getFileName(), decPath.getFileName(), Files.size(decPath));

            if (!filesEqual(inputPath, decPath)) {
                System.err.println("Round-Trip FEHLGESCHLAGEN.");
                System.exit(3);
            }
            System.out.println("Round-Trip: OK");
        } catch (Exception e) {
            reportFailure(e);
            System.exit(1);
        }
    }

    // Cipher-Stream-Pattern: CipherInputStream liest Klartext, transformiert via
    // Cipher.update/doFinal in Chunks. Speicher bleibt konstant.
    private static void streamCipher(Cipher cipher, Path inPath, Path outPath) throws IOException {
        try (InputStream raw = Files.newInputStream(inPath);
             CipherInputStream cis = new CipherInputStream(raw, cipher);
             OutputStream out = Files.newOutputStream(outPath)) {
            byte[] buf = new byte[CHUNK];
            int n;
            while ((n = cis.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
    }

    private static boolean filesEqual(Path a, Path b) throws IOException {
        if (Files.size(a) != Files.size(b)) return false;
        try (InputStream ia = Files.newInputStream(a);
             InputStream ib = Files.newInputStream(b)) {
            byte[] ba = new byte[CHUNK];
            byte[] bb = new byte[CHUNK];
            while (true) {
                int na = ia.readNBytes(ba, 0, CHUNK);
                int nb = ib.readNBytes(bb, 0, CHUNK);
                if (na != nb) return false;
                for (int i = 0; i < na; i++) {
                    if (ba[i] != bb[i]) return false;
                }
                if (na < CHUNK) return true;
            }
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

    private static void listAliases(KeyStore keyStore) throws Exception {
        System.out.println("Aliase im PKCS#11-KeyStore:");
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String a = aliases.nextElement();
            System.out.println("- " + a + " key=" + keyStore.isKeyEntry(a));
        }
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
