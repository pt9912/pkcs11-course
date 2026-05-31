package dev.course.pkcs11

import java.nio.file.Files
import java.nio.file.Path
import java.security.Provider
import java.security.ProviderException
import java.security.SecureRandom
import java.security.Security
import java.util.HexFormat
import kotlin.math.ln
import kotlin.system.exitProcess

// HSM-RNG via SunPKCS11 — vgl. Pkcs11RandomDemo.java fuer Details.

private const val CHUNK_SIZE = 8 * 1024
private const val TOTAL_BYTES = 1 * 1024 * 1024

fun main() {
    val configPath = System.getenv("PKCS11_JAVA_CONFIG").nullIfBlank() ?: "src/main/resources/softhsm.cfg"
    val slotOverride = System.getenv("PKCS11_SLOT_ID").nullIfBlank()
    val libraryOverride = System.getenv("PKCS11_LIBRARY").nullIfBlank()
    val pin = (System.getenv("PKCS11_USER_PIN").nullIfBlank() ?: "987654").toCharArray()

    try {
        val base = Security.getProvider("SunPKCS11")
            ?: error("SunPKCS11 Provider ist in diesem JDK nicht verfuegbar.")
        val provider = base.configure(buildConfigArgument(configPath, slotOverride, libraryOverride))
        println("Provider: ${provider.name}")

        // PIN-Wipe in finally — wenn jemand die Demo auf ein HSM mit
        // RNG-Login-Pflicht portiert und Login einbaut, ist der Pattern schon da.
        // Explizites \u0000-Escape statt rohem NUL-Byte (Konvention seit v0.4.0).
        val hsmRandom = try {
            SecureRandom.getInstance("PKCS11", provider)
        } finally {
            pin.fill('\u0000')
        }

        // 1) Proof-of-Life.
        println("\n=== 1) Proof-of-Life: 32 Byte aus dem HSM ===")
        val sample = ByteArray(32).also(hsmRandom::nextBytes)
        println("  Hex: ${HexFormat.of().formatHex(sample)}")

        // 2) Durchsatz.
        println("\n=== 2) Durchsatz HSM-SecureRandom vs Default-SecureRandom ===")
        val osRandom = SecureRandom()
        val hsm = timeGenerate(TOTAL_BYTES, CHUNK_SIZE, hsmRandom)
        val os = timeGenerate(TOTAL_BYTES, CHUNK_SIZE, osRandom)
        report("HSM (SecureRandom PKCS11)", hsm)
        report("Default-SecureRandom (${osRandom.algorithm})", os)
        val ratio = os.nanos.toDouble() / hsm.nanos
        println(if (ratio < 1)
            "  HSM ist Faktor ${"%.1f".format(1.0 / ratio)}x langsamer als Default-SecureRandom"
        else
            "  HSM ist Faktor ${"%.1f".format(ratio)}x schneller als Default-SecureRandom (SoftHSM-Spezialfall)"
        )

        // 3) Entropie.
        println("\n=== 3) Verteilungs-Check ueber 64 KB HSM-Bytes ===")
        val bucket = if (hsm.data.size > 64 * 1024) hsm.data.copyOfRange(0, 64 * 1024) else hsm.data
        val entropy = shannonEntropy(bucket)
        println("  Shannon-Entropie: ${"%.4f".format(entropy)} bit/byte (Idealwert: 8.0)")
        if (entropy < 7.5) {
            System.err.println("Entropie ${"%.4f".format(entropy)} bit/byte zu niedrig — RNG-Output sieht nicht uniform aus.")
            exitProcess(3)
        }
        println("\nFertig — der HSM-RNG funktioniert wie erwartet.")
    } catch (e: Exception) {
        reportFailure(e)
        exitProcess(1)
    }
}

private data class TimedBuffer(val data: ByteArray, val nanos: Long)

private fun timeGenerate(total: Int, chunk: Int, rnd: SecureRandom): TimedBuffer {
    val out = ByteArray(total)
    val start = System.nanoTime()
    var offset = 0
    val buf = ByteArray(chunk)
    while (offset < total) {
        val need = minOf(chunk, total - offset)
        val target = if (need == chunk) buf else ByteArray(need)
        rnd.nextBytes(target)
        System.arraycopy(target, 0, out, offset, need)
        offset += need
    }
    return TimedBuffer(out, System.nanoTime() - start)
}

private fun report(label: String, buf: TimedBuffer) {
    val secs = buf.nanos / 1e9
    val mbps = (buf.data.size / 1024.0 / 1024.0) / secs
    println("  %-50s %7.3fs  %8.2f MB/s".format(label, secs, mbps))
}

private fun shannonEntropy(data: ByteArray): Double {
    if (data.isEmpty()) return 0.0
    val counts = IntArray(256)
    for (b in data) counts[b.toInt() and 0xff]++
    val n = data.size.toDouble()
    var h = 0.0
    for (c in counts) {
        if (c == 0) continue
        val p = c / n
        h -= p * (ln(p) / ln(2.0))
    }
    return h
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
