package dev.course.pkcs11

import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Provider
import java.security.ProviderException
import java.security.SecureRandom
import java.security.Security
import java.security.Signature
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import kotlin.system.exitProcess

// PKCS#11 Multi-Part / Streaming — vgl. Pkcs11StreamDemo.java fuer Details.
private const val CHUNK = 64 * 1024

fun main() {
    val configPath = System.getenv("PKCS11_JAVA_CONFIG").nullIfBlank() ?: "src/main/resources/softhsm.cfg"
    val slotOverride = System.getenv("PKCS11_SLOT_ID").nullIfBlank()
    val libraryOverride = System.getenv("PKCS11_LIBRARY").nullIfBlank()
    val pin = (System.getenv("PKCS11_USER_PIN").nullIfBlank() ?: "987654").toCharArray()
    val outputDir = System.getenv("PKCS11_OUTPUT_DIR").nullIfBlank() ?: "/workspace/lab/work"
    val signAlias = System.getenv("PKCS11_KEY_ALIAS").nullIfBlank() ?: "signing-key"
    val aesAlias = System.getenv("PKCS11_AES_STREAM_LABEL").nullIfBlank() ?: "aes-stream-key"

    val inputPath = Path.of(outputDir, "large.bin")
    val sigPath = Path.of(outputDir, "kotlin-stream.sig")
    val encPath = Path.of(outputDir, "kotlin-stream.enc")
    val decPath = Path.of(outputDir, "kotlin-stream.dec")

    try {
        if (!Files.exists(inputPath)) {
            System.err.println("Testfile fehlt: $inputPath. Erst 'make stream-sign' ausfuehren.")
            exitProcess(2)
        }
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

        listAliases(keyStore)
        val signKey = (keyStore.getKey(signAlias, null) as? PrivateKey) ?: run {
            System.err.println("Alias '$signAlias' fehlt. Erst 'make import-cert' ausfuehren.")
            exitProcess(2)
        }
        val aesKey = (keyStore.getKey(aesAlias, null) as? SecretKey) ?: run {
            System.err.println("Alias '$aesAlias' fehlt. Erst 'make gen-aes-stream' ausfuehren.")
            exitProcess(2)
        }

        val inputSize = Files.size(inputPath)

        // Sign-Streaming
        val signer = Signature.getInstance("SHA256withRSA", provider)
        signer.initSign(signKey)
        Files.newInputStream(inputPath).use { input ->
            val buf = ByteArray(CHUNK)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                signer.update(buf, 0, n)
            }
        }
        val sig = signer.sign()
        Files.write(sigPath, sig)
        println("Sign:    ${inputPath.fileName} → ${sigPath.fileName} (${sig.size} Bytes Signatur ueber $inputSize Bytes Input)")

        // Encrypt-Streaming
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val encCipher = Cipher.getInstance("AES/CBC/PKCS5Padding", provider)
        encCipher.init(Cipher.ENCRYPT_MODE, aesKey, IvParameterSpec(iv))
        streamCipher(encCipher, inputPath, encPath)
        println("Encrypt: ${inputPath.fileName} → ${encPath.fileName} (${Files.size(encPath)} Bytes inkl. PKCS#7-Padding)")

        val decCipher = Cipher.getInstance("AES/CBC/PKCS5Padding", provider)
        decCipher.init(Cipher.DECRYPT_MODE, aesKey, IvParameterSpec(iv))
        streamCipher(decCipher, encPath, decPath)
        println("Decrypt: ${encPath.fileName} → ${decPath.fileName} (${Files.size(decPath)} Bytes)")

        if (!filesEqual(inputPath, decPath)) {
            System.err.println("Round-Trip FEHLGESCHLAGEN.")
            exitProcess(3)
        }
        println("Round-Trip: OK")
    } catch (e: Exception) {
        reportFailure(e)
        exitProcess(1)
    }
}

private fun streamCipher(cipher: Cipher, inPath: Path, outPath: Path) {
    Files.newInputStream(inPath).use { raw ->
        CipherInputStream(raw, cipher).use { cis ->
            Files.newOutputStream(outPath).use { out: OutputStream ->
                val buf = ByteArray(CHUNK)
                while (true) {
                    val n = cis.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                }
            }
        }
    }
}

private fun filesEqual(a: Path, b: Path): Boolean {
    if (Files.size(a) != Files.size(b)) return false
    Files.newInputStream(a).use { ia ->
        Files.newInputStream(b).use { ib ->
            val ba = ByteArray(CHUNK)
            val bb = ByteArray(CHUNK)
            while (true) {
                val na = (ia as InputStream).readNBytes(ba, 0, CHUNK)
                val nb = (ib as InputStream).readNBytes(bb, 0, CHUNK)
                if (na != nb) return false
                for (i in 0 until na) if (ba[i] != bb[i]) return false
                if (na < CHUNK) return true
            }
            @Suppress("UNREACHABLE_CODE") return true
        }
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

private fun listAliases(keyStore: KeyStore) {
    println("Aliase im PKCS#11-KeyStore:")
    val aliases = keyStore.aliases()
    while (aliases.hasMoreElements()) {
        val a = aliases.nextElement()
        println("- $a key=${keyStore.isKeyEntry(a)}")
    }
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
