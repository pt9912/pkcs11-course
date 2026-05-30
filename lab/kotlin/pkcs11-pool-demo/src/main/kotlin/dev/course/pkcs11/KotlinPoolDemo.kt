package dev.course.pkcs11

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.Provider
import java.security.ProviderException
import java.security.Security
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Mac
import javax.crypto.SecretKey
import kotlin.system.exitProcess

// Session-Pooling in Kotlin — vgl. Pkcs11PoolDemo.java fuer Details.
private const val POOL_SIZE = 8
private const val TOTAL_OPS = 10_000
private const val MESSAGE_SIZE = 64

fun main() {
    val configPath = System.getenv("PKCS11_JAVA_CONFIG").nullIfBlank() ?: "src/main/resources/softhsm.cfg"
    val slotOverride = System.getenv("PKCS11_SLOT_ID").nullIfBlank()
    val libraryOverride = System.getenv("PKCS11_LIBRARY").nullIfBlank()
    val pin = (System.getenv("PKCS11_USER_PIN").nullIfBlank() ?: "987654").toCharArray()
    val hmacAlias = System.getenv("PKCS11_HMAC_LABEL").nullIfBlank() ?: "hmac-key"

    try {
        val base = Security.getProvider("SunPKCS11")
            ?: error("SunPKCS11 Provider ist in diesem JDK nicht verfuegbar.")
        val provider = base.configure(buildConfigArgument(configPath, slotOverride, libraryOverride))
        println("Provider: ${provider.name}")

        val keyStore = KeyStore.getInstance("PKCS11", provider)
        try {
            keyStore.load(null, pin)
        } finally {
            pin.fill(' ')
        }
        val hmacKey = (keyStore.getKey(hmacAlias, null) as? SecretKey) ?: run {
            System.err.println("HMAC-Key-Alias '$hmacAlias' fehlt. Erst 'make gen-hmac' ausfuehren.")
            exitProcess(2)
        }

        val data = ByteArray(MESSAGE_SIZE) { it.toByte() }

        // Sequenziell
        val seqMac = Mac.getInstance("HmacSHA256", provider).apply { init(hmacKey) }
        val seqStart = System.nanoTime()
        repeat(TOTAL_OPS) { seqMac.doFinal(data) }
        val seqElapsedMs = (System.nanoTime() - seqStart) / 1_000_000L

        // Parallel mit Mac-Pool
        val pool = LinkedBlockingQueue<Mac>(POOL_SIZE)
        repeat(POOL_SIZE) {
            val m = Mac.getInstance("HmacSHA256", provider).apply { init(hmacKey) }
            pool.add(m)
        }

        val counter = AtomicInteger()
        val exec = Executors.newFixedThreadPool(POOL_SIZE)
        val parStart = System.nanoTime()
        repeat(POOL_SIZE) {
            exec.submit {
                try {
                    while (counter.incrementAndGet() <= TOTAL_OPS) {
                        val m = pool.take()
                        try {
                            m.doFinal(data)
                        } finally {
                            pool.put(m)
                        }
                    }
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
        exec.shutdown()
        if (!exec.awaitTermination(60, TimeUnit.SECONDS)) {
            error("Workers timeout")
        }
        val parElapsedMs = (System.nanoTime() - parStart) / 1_000_000L

        val speedup = seqElapsedMs.toDouble() / parElapsedMs.coerceAtLeast(1)
        println("Operationen:    $TOTAL_OPS × HMAC-SHA256($MESSAGE_SIZE Bytes)")
        println("Pool-Groesse:   $POOL_SIZE Mac-Instanzen")
        println("Sequenziell:    $seqElapsedMs ms (${"%.0f".format(TOTAL_OPS * 1000.0 / seqElapsedMs.coerceAtLeast(1))} ops/s)")
        println("Parallel (×$POOL_SIZE): $parElapsedMs ms (${"%.0f".format(TOTAL_OPS * 1000.0 / parElapsedMs.coerceAtLeast(1))} ops/s)")
        println("Speedup:        ${"%.2f".format(speedup)}x")
    } catch (e: Exception) {
        reportFailure(e)
        exitProcess(1)
    }
}

private fun buildConfigArgument(configPath: String, slotOverride: String?, libraryOverride: String?): String {
    if (slotOverride == null && libraryOverride == null) return configPath
    val base = Files.readString(Path.of(configPath))
    val sb = StringBuilder("--").append(base)
    if (!base.endsWith("\n")) sb.append('\n')
    libraryOverride?.let { sb.append("library = ").append(it).append('\n') }
    slotOverride?.let { sb.append("slot = ").append(it).append('\n') }
    return sb.toString()
}

private fun String?.nullIfBlank(): String? = if (this.isNullOrEmpty()) null else this

private fun reportFailure(t: Throwable) {
    System.err.println("Fehler beim PKCS#11-Lauf:")
    var cur: Throwable? = t
    var depth = 0
    while (cur != null && depth < 8) {
        val label = if (cur is ProviderException) "ProviderException" else cur.javaClass.simpleName
        System.err.println("  $label: ${cur.message}")
        cur = cur.cause
        depth++
    }
}
