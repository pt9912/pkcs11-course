package dev.course.pkcs11;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.Security;
import java.util.Arrays;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class Pkcs11EcdhDemo {
    // ECDH + HKDF Demo via SunPKCS11 + JCA. Mapped auf C_DeriveKey
    // (CKM_ECDH1_DERIVE) intern, sichtbar via KeyAgreement("ECDH").
    //
    // SunPKCS11-Limitation: KeyStore.aliases() zeigt einen Private-Key-Alias
    // nur, wenn ein Zertifikat mit gleicher CKA_ID existiert. Deshalb laeuft
    // make java-ecdh-demo gegen issue-ecdh-certs, der self-signed Certs fuer
    // alice-ec-key und bob-ec-key importiert. Die Certs werden im Protokoll
    // nicht benutzt, sie machen nur die Aliase sichtbar.
    //
    // HKDF macht JCA in Java 21 nicht direkt. Wir implementieren RFC 5869
    // selbst (Mac-HMAC-SHA256 als PRF) — kurz und deterministisch.

    private static final String HKDF_INFO = "ECDH-Lab-V1";

    private Pkcs11EcdhDemo() {}

    public static void main(String[] args) {
        String configPath = nonEmpty(System.getenv("PKCS11_JAVA_CONFIG"), "src/main/resources/softhsm.cfg");
        String slotOverride = nullIfBlank(System.getenv("PKCS11_SLOT_ID"));
        String libraryOverride = nullIfBlank(System.getenv("PKCS11_LIBRARY"));
        char[] pin = nonEmpty(System.getenv("PKCS11_USER_PIN"), "987654").toCharArray();
        String aliceAlias = nonEmpty(System.getenv("PKCS11_ECDH_ALICE_LABEL"), "alice-ec-key");
        String bobAlias = nonEmpty(System.getenv("PKCS11_ECDH_BOB_LABEL"), "bob-ec-key");
        String kdfMode = nonEmpty(System.getenv("PKCS11_ECDH_KDF"), "hkdf");

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

            PrivateKey alicePriv = (PrivateKey) keyStore.getKey(aliceAlias, null);
            PrivateKey bobPriv = (PrivateKey) keyStore.getKey(bobAlias, null);
            if (alicePriv == null || bobPriv == null) {
                System.err.println("Alice oder Bob Private Key nicht gefunden. make issue-ecdh-certs ausfuehren?");
                System.exit(2);
            }
            PublicKey alicePub = keyStore.getCertificate(aliceAlias).getPublicKey();
            PublicKey bobPub = keyStore.getCertificate(bobAlias).getPublicKey();

            // === 1) Setup ===
            System.out.println("=== 1) Setup ===");
            System.out.println("  Alice priv: " + alicePriv.getAlgorithm() + "   pub-encoded=" + shortHex(alicePub.getEncoded(), 8) + "...");
            System.out.println("  Bob   priv: " + bobPriv.getAlgorithm() + "   pub-encoded=" + shortHex(bobPub.getEncoded(), 8) + "...");

            // === 2) ECDH via JCA KeyAgreement ===
            System.out.println("\n=== 2) ECDH via KeyAgreement(\"ECDH\") ===");
            byte[] aliceSecret = ecdhDerive(provider, alicePriv, bobPub);
            byte[] bobSecret = ecdhDerive(provider, bobPriv, alicePub);
            if (!MessageDigest.isEqual(aliceSecret, bobSecret)) {
                System.err.println("Shared Secrets unterscheiden sich.");
                System.exit(3);
            }
            System.out.println("  Alice-Secret: " + shortHex(aliceSecret, 8) + "... (" + aliceSecret.length + " Byte)");
            System.out.println("  Bob-Secret:   " + shortHex(bobSecret, 8) + "... (" + bobSecret.length + " Byte)");
            System.out.println("  Match: ja  (constant-time via MessageDigest.isEqual)");

            // === 3) KDF ===
            byte[] aliceKey, bobKey;
            if ("raw".equalsIgnoreCase(kdfMode)) {
                System.out.println("\n=== 3) KDF=raw — Shared Secret direkt als AES-256-Key ===");
                aliceKey = Arrays.copyOf(aliceSecret, 32);
                bobKey = Arrays.copyOf(bobSecret, 32);
            } else {
                System.out.println("\n=== 3) KDF=hkdf — HKDF-SHA256 host-side (RFC 5869) ===");
                System.out.println("  info=\"" + HKDF_INFO + "\"  salt=zero  CKM_HKDF_DERIVE nicht in SoftHSM 2.x");
                aliceKey = hkdfExpand(aliceSecret, HKDF_INFO.getBytes(StandardCharsets.UTF_8), 32);
                bobKey = hkdfExpand(bobSecret, HKDF_INFO.getBytes(StandardCharsets.UTF_8), 32);
            }
            if (!MessageDigest.isEqual(aliceKey, bobKey)) {
                System.err.println("KDF-Output unterscheidet sich.");
                System.exit(3);
            }
            System.out.println("  AES-Key (gekuerzt): " + shortHex(aliceKey, 8) + "...");

            // === 4) AES-GCM Roundtrip ===
            System.out.println("\n=== 4) AES-256-GCM Roundtrip ===");
            byte[] plaintext = "Hallo Bob — diese Nachricht kommt von Alice ueber ECDH+HKDF (Java).".getBytes(StandardCharsets.UTF_8);
            byte[] nonce = new byte[12];
            new java.security.SecureRandom().nextBytes(nonce);
            byte[] ciphertext = aesGcm(Cipher.ENCRYPT_MODE, aliceKey, nonce, plaintext);
            byte[] recovered = aesGcm(Cipher.DECRYPT_MODE, bobKey, nonce, ciphertext);
            System.out.println("  Alice -> " + (ciphertext.length - 16) + " Byte Ciphertext + 12 Byte Nonce + 16 Byte Tag");
            System.out.println("  Bob   <- entschluesselt: " + new String(recovered, StandardCharsets.UTF_8));
            if (!Arrays.equals(recovered, plaintext)) {
                System.err.println("Roundtrip kaputt.");
                System.exit(3);
            }
            System.out.println("\nFertig — Shared-Secret-Match-Beweis erbracht und AES-Roundtrip erfolgreich.");
        } catch (Exception e) {
            reportFailure(e);
            System.exit(1);
        }
    }

    private static byte[] ecdhDerive(Provider provider, PrivateKey myPriv, PublicKey peerPub) throws Exception {
        // KeyAgreement("ECDH") mit dem SunPKCS11-Provider routet auf
        // C_DeriveKey(CKM_ECDH1_DERIVE) intern. generateSecret() liefert
        // die rohe x-Koordinate (CKD_NULL).
        KeyAgreement ka = KeyAgreement.getInstance("ECDH", provider);
        ka.init(myPriv);
        ka.doPhase(peerPub, true);
        return ka.generateSecret();
    }

    // RFC 5869 HKDF Extract + Expand mit HMAC-SHA256. Salt=null wird per
    // Konvention als HashLen Nullbytes interpretiert (so machen es auch
    // Go's hkdf.New und .NETs HKDF.DeriveKey, damit die drei Demos byte-
    // identische Outputs liefern).
    private static byte[] hkdfExpand(byte[] secret, byte[] info, int length) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        int hashLen = hmac.getMacLength();

        // Extract: PRK = HMAC(salt, IKM=secret) mit salt=0...0 (HashLen Bytes).
        byte[] salt = new byte[hashLen];
        hmac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = hmac.doFinal(secret);

        // Expand: T(i) = HMAC(PRK, T(i-1) || info || counter), concat T(1)..T(n).
        hmac.init(new SecretKeySpec(prk, "HmacSHA256"));
        if (length > 255 * hashLen) {
            throw new IllegalArgumentException("HKDF length darf max 255*HashLen sein");
        }
        byte[] result = new byte[length];
        byte[] previous = new byte[0];
        int offset = 0;
        // Counter ist int, weil ein byte-Counter bei length > 127*HashLen ueberlaeuft
        // (Java byte ist signed). Cast auf byte erst beim hmac.update — der nimmt
        // ohnehin nur das untere Byte.
        for (int counter = 1; offset < length; counter++) {
            hmac.reset();
            hmac.update(previous);
            hmac.update(info);
            hmac.update((byte) counter);
            previous = hmac.doFinal();
            int copyLen = Math.min(hashLen, length - offset);
            System.arraycopy(previous, 0, result, offset, copyLen);
            offset += copyLen;
        }
        return result;
    }

    private static byte[] aesGcm(int mode, byte[] key, byte[] nonce, byte[] input) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        return c.doFinal(input);
    }

    private static String shortHex(byte[] data, int n) {
        int take = Math.min(n, data.length);
        return HexFormat.of().formatHex(data, 0, take);
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
