package dev.course.pkcs11;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Provider;
import java.security.ProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.HexFormat;

public final class Pkcs11RandomDemo {
    // HSM-RNG via JCA: SunPKCS11 registriert SecureRandom unter dem Provider-Namen.
    // SecureRandom.getInstance("PKCS11", provider) gibt eine Instanz zurueck,
    // deren nextBytes() intern C_GenerateRandom auf dem Token aufruft.
    //
    //  1) 32 Byte Proof-of-Life.
    //  2) Durchsatz HSM-SecureRandom vs Default-SecureRandom (Linux: NativePRNG → /dev/urandom).
    //  3) Shannon-Entropie ueber 64 KB HSM-Output.
    //
    // Anmerkung: JCA bietet kein SeedRandom direkt — Anwendungen muessen sich
    // auf den HSM-internen Seed verlassen. Das passt zur PKCS#11-Semantik:
    // C_SeedRandom ist optional und auf vielen HSMs nicht implementiert.

    private static final int CHUNK_SIZE = 8 * 1024;
    private static final int TOTAL_BYTES = 1 * 1024 * 1024;

    private Pkcs11RandomDemo() {}

    public static void main(String[] args) {
        String configPath = nonEmpty(System.getenv("PKCS11_JAVA_CONFIG"), "src/main/resources/softhsm.cfg");
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

            // SecureRandom ist der schmale Pfad zum RNG — kein KeyStore-Load,
            // kein Login. PKCS#11-Spec: C_GenerateRandom verlangt keine
            // CKU_USER-Anmeldung; SunPKCS11 macht intern was noetig ist.
            // PIN wird beim reinen RNG-Pfad zwar nicht gebraucht, wir wischen
            // sie trotzdem in finally — wenn jemand die Demo auf ein HSM mit
            // RNG-Login-Pflicht portiert und Login einbaut, ist der Pattern schon da.
            SecureRandom hsmRandom;
            try {
                hsmRandom = SecureRandom.getInstance("PKCS11", provider);
            } finally {
                java.util.Arrays.fill(pin, '\0');
            }

            // 1) Proof-of-Life.
            System.out.println("\n=== 1) Proof-of-Life: 32 Byte aus dem HSM ===");
            byte[] sample = new byte[32];
            hsmRandom.nextBytes(sample);
            System.out.println("  Hex: " + HexFormat.of().formatHex(sample));

            // 2) Durchsatz.
            System.out.println("\n=== 2) Durchsatz HSM-SecureRandom vs Default-SecureRandom ===");
            SecureRandom osRandom = new SecureRandom(); // SHA1PRNG/NativePRNG, Linux: getrandom-Pfad
            TimedBuffer hsm = timeGenerate(TOTAL_BYTES, CHUNK_SIZE, hsmRandom);
            TimedBuffer os = timeGenerate(TOTAL_BYTES, CHUNK_SIZE, osRandom);
            report("HSM (SecureRandom PKCS11)", hsm);
            report("Default-SecureRandom (" + osRandom.getAlgorithm() + ")", os);
            double ratio = (double) os.nanos / hsm.nanos;
            System.out.println(ratio < 1
                ? String.format("  HSM ist Faktor %.1fx langsamer als Default-SecureRandom", 1.0 / ratio)
                : String.format("  HSM ist Faktor %.1fx schneller als Default-SecureRandom (SoftHSM-Spezialfall)", ratio));

            // 3) Entropie.
            System.out.println("\n=== 3) Verteilungs-Check ueber 64 KB HSM-Bytes ===");
            byte[] bucket = hsm.data.length > 64 * 1024 ? java.util.Arrays.copyOf(hsm.data, 64 * 1024) : hsm.data;
            double entropy = shannonEntropy(bucket);
            System.out.printf("  Shannon-Entropie: %.4f bit/byte (Idealwert: 8.0)%n", entropy);
            if (entropy < 7.5) {
                System.err.printf("Entropie %.4f bit/byte zu niedrig — RNG-Output sieht nicht uniform aus.%n", entropy);
                System.exit(3);
            }
            System.out.println("\nFertig — der HSM-RNG funktioniert wie erwartet.");
        } catch (Exception e) {
            reportFailure(e);
            System.exit(1);
        }
    }

    private record TimedBuffer(byte[] data, long nanos) {}

    private static TimedBuffer timeGenerate(int total, int chunk, SecureRandom rnd) {
        byte[] out = new byte[total];
        long start = System.nanoTime();
        int offset = 0;
        byte[] buf = new byte[chunk];
        while (offset < total) {
            int need = Math.min(chunk, total - offset);
            // Wenn need < chunk, ein eigener kurzer Buffer — sonst koennten wir
            // ueber das Ziel-Array hinaus schreiben. Fuer Demo-Lesbarkeit ok.
            byte[] target = need == chunk ? buf : new byte[need];
            rnd.nextBytes(target);
            System.arraycopy(target, 0, out, offset, need);
            offset += need;
        }
        return new TimedBuffer(out, System.nanoTime() - start);
    }

    private static void report(String label, TimedBuffer buf) {
        double secs = buf.nanos / 1e9;
        double mbps = (buf.data.length / 1024.0 / 1024.0) / secs;
        System.out.printf("  %-50s %7.3fs  %8.2f MB/s%n", label, secs, mbps);
    }

    private static double shannonEntropy(byte[] data) {
        if (data.length == 0) return 0;
        int[] counts = new int[256];
        for (byte b : data) counts[b & 0xff]++;
        double n = data.length;
        double h = 0;
        for (int c : counts) {
            if (c == 0) continue;
            double p = c / n;
            h -= p * (Math.log(p) / Math.log(2));
        }
        return h;
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
