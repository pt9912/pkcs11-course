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
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Enumeration;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

public final class Pkcs11EncryptDemo {
    // Hybrid-Verschluesselung — Sender ohne HSM, Empfaenger mit HSM:
    // 1) AES-Session-Key + IV als Host-Material erzeugen.
    // 2) Sender wrappt den AES-Key per RSA-OAEP (SunJCE, mit aus dem Cert extrahiertem
    //    Public Key — analog zu real-world S/MIME wo der Sender nur das Empfaenger-Cert hat).
    // 3) Sender verschluesselt das Dokument mit AES-256-GCM.
    // 4) Empfaenger entschluesselt per HSM. Doppelt knifflig:
    //    - SunPKCS11 registriert KEINEN OAEP-Cipher (nur RSA/ECB/PKCS1Padding und NoPadding).
    //    - SoftHSM 2.6.x meldet bei CKM_RSA_PKCS_OAEP mit SHA-256 ein CKR_ARGUMENTS_BAD.
    //    Loesung wie in der OpenSSL pkcs11-Engine: rohe RSA-Operation (CKM_RSA_X_509)
    //    auf dem HSM, OAEP-Unpadding in Software. Der private Key bleibt im HSM, nur
    //    der gepaddete Klartext wandert kurz durch den Anwendungsspeicher.
    private static final String OAEP_HASH = "SHA-1"; // SoftHSM-kompatibel; real-world: SHA-256
    private static final int GCM_TAG_BITS = 128;

    private Pkcs11EncryptDemo() {}

    public static void main(String[] args) {
        // nonEmpty: leere ENV-Strings als "use default" behandeln (seit 0.15.1,
        // siehe Makefile-DOCKER_ENV).
        String configPath = nonEmpty(System.getenv("PKCS11_JAVA_CONFIG"), "src/main/resources/softhsm.cfg");
        String alias = nullIfBlank(System.getenv("PKCS11_WRAP_KEY_ALIAS"));
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

            String resolved = resolveWrapKeyAlias(keyStore, alias);
            if (resolved == null) {
                System.err.println("Kein Alias mit Cert-Eintrag fuer den Wrap-Key gefunden.");
                System.err.println("Erst 'make gen-rsa-wrap issue-wrap-cert' ausfuehren oder PKCS11_WRAP_KEY_ALIAS setzen.");
                System.exit(2);
            }
            PrivateKey privKey = (PrivateKey) keyStore.getKey(resolved, null);
            Certificate cert = keyStore.getCertificate(resolved);
            if (cert == null) {
                System.err.println("Alias '" + resolved + "' hat keine Zertifikatskette.");
                System.exit(2);
            }
            RSAPublicKey pubKey = (RSAPublicKey) cert.getPublicKey();
            int keyBytes = (pubKey.getModulus().bitLength() + 7) / 8;

            byte[] aesKey = new byte[32];
            byte[] iv = new byte[12];
            SecureRandom rng = new SecureRandom();
            rng.nextBytes(aesKey);
            rng.nextBytes(iv);

            byte[] data = "Vertrauliches Dokument aus Java.\nZeile zwei.\n".getBytes(StandardCharsets.UTF_8);

            // Sender: OAEP-Encrypt mit SunJCE (extrahierter Pubkey, kein HSM noetig).
            byte[] wrapped = rsaOAEPEncryptSoftware(pubKey, aesKey);
            byte[] ciphertext = aesGCMEncrypt(aesKey, iv, data);
            Arrays.fill(aesKey, (byte) 0);

            // Empfaenger: Roh-RSA via HSM, OAEP-Unpadding in Software.
            byte[] paddedKey = rsaRawDecryptViaHsm(provider, privKey, wrapped, keyBytes);
            byte[] recoveredKey = oaepUnpad(paddedKey, keyBytes, MessageDigest.getInstance(OAEP_HASH));
            Arrays.fill(paddedKey, (byte) 0);
            byte[] recovered = aesGCMDecrypt(recoveredKey, iv, ciphertext);
            Arrays.fill(recoveredKey, (byte) 0);

            if (!Arrays.equals(data, recovered)) {
                System.err.println("Round-Trip fehlgeschlagen.");
                System.exit(3);
            }

            Path out = Path.of(outputDir);
            Files.createDirectories(out);
            Files.write(out.resolve("java-document.txt"), data);
            Files.write(out.resolve("java-wrapped-key.bin"), wrapped);
            Files.write(out.resolve("java-iv.bin"), iv);
            Files.write(out.resolve("java-document.enc"), ciphertext);

            System.out.println("Alias:        " + resolved);
            System.out.println("Wrapped Key:  " + out.resolve("java-wrapped-key.bin") + " (" + wrapped.length + " Bytes)");
            System.out.println("Ciphertext:   " + out.resolve("java-document.enc") + " (" + ciphertext.length + " Bytes inkl. GCM-Tag)");
            System.out.println("OAEP-Hash:    " + OAEP_HASH + " (SoftHSM-Quirk + SunPKCS11-Limitation, siehe Demo-Kommentar)");
            System.out.println("Wrapped Key (Base64): " + Base64.getEncoder().encodeToString(wrapped));
            System.out.println("Round-Trip:   OK");
        } catch (Exception e) {
            reportFailure(e);
            System.exit(1);
        }
    }

    private static byte[] rsaOAEPEncryptSoftware(PublicKey key, byte[] plaintext) throws Exception {
        // Bewusst KEIN Provider angegeben — JCA waehlt SunJCE, der OAEP unterstuetzt.
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        OAEPParameterSpec spec = new OAEPParameterSpec(OAEP_HASH, "MGF1",
                "SHA-1".equals(OAEP_HASH) ? MGF1ParameterSpec.SHA1 : MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        return cipher.doFinal(plaintext);
    }

    private static byte[] rsaRawDecryptViaHsm(Provider provider, PrivateKey key, byte[] ciphertext, int keyBytes) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding", provider);
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] padded = cipher.doFinal(ciphertext);
        // Roh-RSA kann fuehrende Nullbytes verlieren — wir kennen die Modulus-Laenge
        // aus dem zugehoerigen Public Key (HSM-Private-Keys sind opaque und
        // implementieren java.security.interfaces.RSAKey nicht zwingend).
        if (padded.length < keyBytes) {
            byte[] full = new byte[keyBytes];
            System.arraycopy(padded, 0, full, keyBytes - padded.length, padded.length);
            return full;
        }
        return padded;
    }

    // OAEP-Unpadding nach RFC 8017 (PKCS#1 v2.2), Label = leer.
    // Reine Hash-/XOR-Logik, kein Geheimnis verlaesst diese Funktion ausser dem
    // recovered AES-Key, der direkt nach Verbrauch genullt wird.
    private static byte[] oaepUnpad(byte[] em, int k, MessageDigest hash) throws BadPaddingException {
        int hLen = hash.getDigestLength();
        if (em.length != k || k < 2 * hLen + 2) {
            throw new BadPaddingException("OAEP-Format ungueltig (Laenge)");
        }
        if (em[0] != 0) {
            throw new BadPaddingException("OAEP-Format ungueltig (Y != 0)");
        }
        byte[] maskedSeed = Arrays.copyOfRange(em, 1, 1 + hLen);
        byte[] maskedDB = Arrays.copyOfRange(em, 1 + hLen, k);
        byte[] seedMask = mgf1(maskedDB, hLen, hash);
        byte[] seed = xor(maskedSeed, seedMask);
        byte[] dbMask = mgf1(seed, k - hLen - 1, hash);
        byte[] db = xor(maskedDB, dbMask);

        byte[] lHash = hash.digest(new byte[0]);
        for (int i = 0; i < hLen; i++) {
            if (db[i] != lHash[i]) {
                throw new BadPaddingException("OAEP-Format ungueltig (lHash-Mismatch)");
            }
        }
        int idx = hLen;
        while (idx < db.length && db[idx] == 0) idx++;
        if (idx >= db.length || db[idx] != 0x01) {
            throw new BadPaddingException("OAEP-Format ungueltig (kein 0x01-Trenner)");
        }
        return Arrays.copyOfRange(db, idx + 1, db.length);
    }

    private static byte[] mgf1(byte[] seed, int length, MessageDigest hash) {
        int hLen = hash.getDigestLength();
        byte[] out = new byte[length];
        int offset = 0;
        int counter = 0;
        while (offset < length) {
            hash.reset();
            hash.update(seed);
            hash.update(new byte[]{
                    (byte) ((counter >>> 24) & 0xff),
                    (byte) ((counter >>> 16) & 0xff),
                    (byte) ((counter >>> 8) & 0xff),
                    (byte) (counter & 0xff)
            });
            byte[] block = hash.digest();
            int take = Math.min(hLen, length - offset);
            System.arraycopy(block, 0, out, offset, take);
            offset += take;
            counter++;
        }
        return out;
    }

    private static byte[] xor(byte[] a, byte[] b) {
        byte[] out = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            out[i] = (byte) (a[i] ^ b[i]);
        }
        return out;
    }

    private static byte[] aesGCMEncrypt(byte[] key, byte[] iv, byte[] plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher.doFinal(plaintext);
    }

    private static byte[] aesGCMDecrypt(byte[] key, byte[] iv, byte[] ciphertext) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher.doFinal(ciphertext);
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

    private static String resolveWrapKeyAlias(KeyStore keyStore, String preferred) throws Exception {
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
            if (preferred == null && "wrap-key".equals(a)) found = a;
        }
        if (!any) System.out.println("- keine Aliase sichtbar");
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
