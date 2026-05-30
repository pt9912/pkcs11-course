package dev.course.pkcs11;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.ProviderException;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

public final class Pkcs11PoolDemo {
    // Session-Pooling in Java / SunPKCS11:
    // KeyStore und SecretKey sind thread-safe (Lookup, Cache).
    // Mac/Signature/Cipher-INSTANZEN sind nicht thread-safe — eine Instanz pro
    // Worker, oder Pool von Instanzen. SunPKCS11 holt sich intern Sessions aus
    // einem eigenen Pool ("Session Pool of SunPKCS11"), unsere Lib-Ebene
    // muss nur den App-Pool von Mac-Instanzen managen.
    private static final int POOL_SIZE = 8;
    private static final int TOTAL_OPS = 10_000;
    private static final int MESSAGE_SIZE = 64;

    private Pkcs11PoolDemo() {}

    public static void main(String[] args) {
        String configPath = nonEmpty(System.getenv("PKCS11_JAVA_CONFIG"), "src/main/resources/softhsm.cfg");
        String slotOverride = nullIfBlank(System.getenv("PKCS11_SLOT_ID"));
        String libraryOverride = nullIfBlank(System.getenv("PKCS11_LIBRARY"));
        char[] pin = nonEmpty(System.getenv("PKCS11_USER_PIN"), "987654").toCharArray();
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

            byte[] data = new byte[MESSAGE_SIZE];
            for (int i = 0; i < data.length; i++) data[i] = (byte) i;

            // --- Sequenziell ---
            Mac seqMac = Mac.getInstance("HmacSHA256", provider);
            seqMac.init(hmacKey);
            long seqStart = System.nanoTime();
            for (int i = 0; i < TOTAL_OPS; i++) {
                seqMac.doFinal(data);
            }
            long seqElapsedMs = (System.nanoTime() - seqStart) / 1_000_000L;

            // --- Parallel mit Mac-Pool ---
            BlockingQueue<Mac> pool = new LinkedBlockingQueue<>(POOL_SIZE);
            List<Mac> allMacs = new ArrayList<>(POOL_SIZE);
            for (int i = 0; i < POOL_SIZE; i++) {
                Mac m = Mac.getInstance("HmacSHA256", provider);
                m.init(hmacKey);
                allMacs.add(m);
                pool.add(m);
            }

            AtomicInteger counter = new AtomicInteger();
            ExecutorService exec = Executors.newFixedThreadPool(POOL_SIZE);
            long parStart = System.nanoTime();
            for (int w = 0; w < POOL_SIZE; w++) {
                exec.submit(() -> {
                    try {
                        while (counter.incrementAndGet() <= TOTAL_OPS) {
                            Mac m = pool.take();
                            try {
                                m.doFinal(data);
                            } finally {
                                pool.put(m);
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            exec.shutdown();
            if (!exec.awaitTermination(60, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Workers timeout");
            }
            long parElapsedMs = (System.nanoTime() - parStart) / 1_000_000L;

            double speedup = (double) seqElapsedMs / Math.max(1, parElapsedMs);
            System.out.printf("Operationen:    %d × HMAC-SHA256(%d Bytes)%n", TOTAL_OPS, MESSAGE_SIZE);
            System.out.printf("Pool-Groesse:   %d Mac-Instanzen%n", POOL_SIZE);
            System.out.printf("Sequenziell:    %d ms (%.0f ops/s)%n", seqElapsedMs, TOTAL_OPS * 1000.0 / Math.max(1, seqElapsedMs));
            System.out.printf("Parallel (×%d): %d ms (%.0f ops/s)%n", POOL_SIZE, parElapsedMs, TOTAL_OPS * 1000.0 / Math.max(1, parElapsedMs));
            System.out.printf("Speedup:        %.2fx%n", speedup);
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
