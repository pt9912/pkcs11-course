package dev.course.pkcs11;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.Provider;
import java.security.ProviderException;
import java.security.Security;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

public final class Pkcs11HmacDemo {
    // HMAC-SHA256 ueber den HSM via SunPKCS11/JCA.
    //
    // Unterschied zu Go/C#: JCA-Mac hat keine eingebaute verify()-Methode.
    // Wir berechnen den MAC neu via HSM und vergleichen constant-time mit
    // MessageDigest.isEqual. Der private HMAC-Key bleibt im HSM, der Vergleich
    // passiert auf dem Host — bei HMAC-Output (32 Byte) ist das unkritisch,
    // solange wir kein "==" oder Arrays.equals nutzen (Timing-Leak).
    private Pkcs11HmacDemo() {}

    public static void main(String[] args) {
        String configPath = nonEmpty(System.getenv("PKCS11_JAVA_CONFIG"), "src/main/resources/softhsm.cfg");
        String slotOverride = nullIfBlank(System.getenv("PKCS11_SLOT_ID"));
        String libraryOverride = nullIfBlank(System.getenv("PKCS11_LIBRARY"));
        char[] pin = nonEmpty(System.getenv("PKCS11_USER_PIN"), "987654").toCharArray();
        String outputDir = nonEmpty(System.getenv("PKCS11_OUTPUT_DIR"), "/workspace/lab/work");
        String hmacAlias = nonEmpty(System.getenv("PKCS11_HMAC_LABEL"), "hmac-key");

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

            SecretKey hmacKey = (SecretKey) keyStore.getKey(hmacAlias, null);
            if (hmacKey == null) {
                System.err.println("HMAC-Key-Alias '" + hmacAlias + "' fehlt. Erst 'make gen-hmac' ausfuehren.");
                System.exit(2);
            }

            // --- 1) Raw HMAC ---
            byte[] data = "API-Token-Anfrage von client-42 am 30.05.2026T12:00:00Z\n".getBytes(StandardCharsets.UTF_8);
            byte[] mac = hmacSign(provider, hmacKey, data);
            boolean ok = hmacVerify(provider, hmacKey, data, mac);
            if (!ok) {
                System.err.println("Verify (Original) FEHLGESCHLAGEN.");
                System.exit(3);
            }

            byte[] tampered = data.clone();
            tampered[tampered.length - 2] ^= 0x01;
            boolean tamperedOk = hmacVerify(provider, hmacKey, tampered, mac);
            if (tamperedOk) {
                System.err.println("Tampered-Verify haette fehlschlagen muessen.");
                System.exit(3);
            }

            Path out = Path.of(outputDir);
            Files.createDirectories(out);
            Files.write(out.resolve("java-hmac-data.txt"), data);
            Files.write(out.resolve("java-hmac-data.mac"), mac);

            // --- 2) JWT (HS256) ---
            long now = Instant.now().getEpochSecond();
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("sub", "user-42");
            claims.put("iss", "pkcs11-lab");
            claims.put("iat", now);
            claims.put("exp", now + 3600);

            String jwt = jwtSign(provider, hmacKey, claims);
            if (!jwtVerify(provider, hmacKey, jwt)) {
                System.err.println("JWT-Verify fehlgeschlagen.");
                System.exit(3);
            }
            Files.writeString(out.resolve("java-hmac.jwt"), jwt);

            System.out.println("Raw HMAC:");
            System.out.println("  Data:  " + out.resolve("java-hmac-data.txt") + " (" + data.length + " Bytes)");
            System.out.println("  MAC:   " + out.resolve("java-hmac-data.mac") + " (" + mac.length + " Bytes)");
            System.out.println("  Verify (Original):   OK");
            System.out.println("  Verify (Tampered):   abgelehnt (erwartet)");
            System.out.println("JWT (HS256):");
            System.out.println("  Token: " + out.resolve("java-hmac.jwt"));
            System.out.println("  Wert:  " + jwt);
            System.out.println("  Verify: OK");
        } catch (Exception e) {
            reportFailure(e);
            System.exit(1);
        }
    }

    private static byte[] hmacSign(Provider provider, SecretKey key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256", provider);
        mac.init(key);
        return mac.doFinal(data);
    }

    // JCA-Mac kann nicht direkt verifizieren — wir rechnen neu und vergleichen
    // mit MessageDigest.isEqual (constant-time).
    private static boolean hmacVerify(Provider provider, SecretKey key, byte[] data, byte[] expected) throws Exception {
        byte[] computed = hmacSign(provider, key, data);
        return MessageDigest.isEqual(computed, expected);
    }

    // Minimaler JSON-Encoder fuer Header und einfache Claim-Werte (String/Long).
    // Verlaesslicher als das Einschleppen einer JSON-Lib, fuer eine Demo genug.
    private static String simpleJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v instanceof Number n) {
                sb.append(n);
            } else {
                sb.append('"').append(escape(String.valueOf(v))).append('"');
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String jwtSign(Provider provider, SecretKey key, Map<String, Object> claims) throws Exception {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");
        String headerB64 = b64url(simpleJson(header).getBytes(StandardCharsets.UTF_8));
        String payloadB64 = b64url(simpleJson(claims).getBytes(StandardCharsets.UTF_8));
        String signingInput = headerB64 + "." + payloadB64;
        byte[] mac = hmacSign(provider, key, signingInput.getBytes(StandardCharsets.UTF_8));
        return signingInput + "." + b64url(mac);
    }

    private static boolean jwtVerify(Provider provider, SecretKey key, String token) throws Exception {
        String[] parts = token.split("\\.");
        if (parts.length != 3) return false;
        byte[] mac;
        try {
            mac = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            return false;
        }
        byte[] signingInput = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8);
        return hmacVerify(provider, key, signingInput, mac);
    }

    private static String b64url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
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
