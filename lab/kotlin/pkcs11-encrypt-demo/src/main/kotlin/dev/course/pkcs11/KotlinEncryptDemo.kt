package dev.course.pkcs11

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Provider
import java.security.ProviderException
import java.security.SecureRandom
import java.security.Security
import java.security.interfaces.RSAPublicKey
import java.security.spec.MGF1ParameterSpec
import java.util.Base64
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec
import kotlin.system.exitProcess

// Hybrid-Verschluesselung — vgl. Pkcs11EncryptDemo.java fuer die ausfuehrliche
// Erklaerung der zwei Workarounds:
// 1) SunPKCS11 registriert keinen OAEP-Cipher → roh-RSA via HSM + Software-OAEP.
// 2) SoftHSM 2.6.x mag SHA-256 OAEP nicht via direkte PKCS#11-Aufrufe → SHA-1.
private const val OAEP_HASH = "SHA-1"
private const val GCM_TAG_BITS = 128

fun main() {
    val configPath = System.getenv("PKCS11_JAVA_CONFIG").nullIfBlank() ?: "src/main/resources/softhsm.cfg"
    val preferredAlias = System.getenv("PKCS11_WRAP_KEY_ALIAS").nullIfBlank()
    val slotOverride = System.getenv("PKCS11_SLOT_ID").nullIfBlank()
    val libraryOverride = System.getenv("PKCS11_LIBRARY").nullIfBlank()
    val pin = (System.getenv("PKCS11_USER_PIN").nullIfBlank() ?: "987654").toCharArray()
    val outputDir = System.getenv("PKCS11_OUTPUT_DIR").nullIfBlank() ?: "/workspace/lab/work"

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

        val alias = resolveWrapKeyAlias(keyStore, preferredAlias) ?: run {
            System.err.println("Kein Alias mit Cert-Eintrag fuer den Wrap-Key gefunden.")
            System.err.println("Erst 'make gen-rsa-wrap issue-wrap-cert' ausfuehren.")
            exitProcess(2)
        }
        val privKey = keyStore.getKey(alias, null) as PrivateKey
        val cert = keyStore.getCertificate(alias) ?: run {
            System.err.println("Alias '$alias' hat keine Zertifikatskette.")
            exitProcess(2)
        }
        val pubKey = cert.publicKey as RSAPublicKey
        val keyBytes = (pubKey.modulus.bitLength() + 7) / 8

        val aesKey = ByteArray(32)
        val iv = ByteArray(12)
        SecureRandom().apply {
            nextBytes(aesKey)
            nextBytes(iv)
        }
        val data = "Vertrauliches Dokument aus Kotlin.\nZeile zwei.\n".toByteArray(StandardCharsets.UTF_8)

        // Sender: OAEP-Encrypt mit SunJCE (Pubkey aus Cert), kein HSM noetig.
        val wrapped = rsaOAEPEncryptSoftware(pubKey, aesKey)
        val ciphertext = aesGCMEncrypt(aesKey, iv, data)
        aesKey.fill(0)

        // Empfaenger: rohe RSA-Operation via HSM, OAEP-Unpadding in Software.
        val padded = rsaRawDecryptViaHsm(provider, privKey, wrapped, keyBytes)
        val recoveredKey = oaepUnpad(padded, keyBytes, MessageDigest.getInstance(OAEP_HASH))
        padded.fill(0)
        val recovered = aesGCMDecrypt(recoveredKey, iv, ciphertext)
        recoveredKey.fill(0)

        if (!data.contentEquals(recovered)) {
            System.err.println("Round-Trip fehlgeschlagen.")
            exitProcess(3)
        }

        val out = Path.of(outputDir)
        Files.createDirectories(out)
        Files.write(out.resolve("kotlin-document.txt"), data)
        Files.write(out.resolve("kotlin-wrapped-key.bin"), wrapped)
        Files.write(out.resolve("kotlin-iv.bin"), iv)
        Files.write(out.resolve("kotlin-document.enc"), ciphertext)

        println("Alias:        $alias")
        println("Wrapped Key:  ${out.resolve("kotlin-wrapped-key.bin")} (${wrapped.size} Bytes)")
        println("Ciphertext:   ${out.resolve("kotlin-document.enc")} (${ciphertext.size} Bytes inkl. GCM-Tag)")
        println("OAEP-Hash:    $OAEP_HASH (SoftHSM-Quirk + SunPKCS11-Limitation)")
        println("Wrapped Key (Base64): ${Base64.getEncoder().encodeToString(wrapped)}")
        println("Round-Trip:   OK")
    } catch (e: Exception) {
        reportFailure(e)
        exitProcess(1)
    }
}

private fun rsaOAEPEncryptSoftware(key: RSAPublicKey, plaintext: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
    val mgf = if (OAEP_HASH == "SHA-1") MGF1ParameterSpec.SHA1 else MGF1ParameterSpec.SHA256
    cipher.init(Cipher.ENCRYPT_MODE, key, OAEPParameterSpec(OAEP_HASH, "MGF1", mgf, PSource.PSpecified.DEFAULT))
    return cipher.doFinal(plaintext)
}

private fun rsaRawDecryptViaHsm(provider: Provider, key: PrivateKey, ciphertext: ByteArray, keyBytes: Int): ByteArray {
    val cipher = Cipher.getInstance("RSA/ECB/NoPadding", provider)
    cipher.init(Cipher.DECRYPT_MODE, key)
    val padded = cipher.doFinal(ciphertext)
    return if (padded.size < keyBytes) {
        ByteArray(keyBytes).also { System.arraycopy(padded, 0, it, keyBytes - padded.size, padded.size) }
    } else padded
}

// OAEP-Unpadding nach RFC 8017 (PKCS#1 v2.2), Label = leer.
private fun oaepUnpad(em: ByteArray, k: Int, hash: MessageDigest): ByteArray {
    val hLen = hash.digestLength
    if (em.size != k || k < 2 * hLen + 2) throw BadPaddingException("OAEP-Format ungueltig (Laenge)")
    if (em[0].toInt() != 0) throw BadPaddingException("OAEP-Format ungueltig (Y != 0)")
    val maskedSeed = em.copyOfRange(1, 1 + hLen)
    val maskedDB = em.copyOfRange(1 + hLen, k)
    val seedMask = mgf1(maskedDB, hLen, hash)
    val seed = xor(maskedSeed, seedMask)
    val dbMask = mgf1(seed, k - hLen - 1, hash)
    val db = xor(maskedDB, dbMask)
    val lHash = hash.digest(ByteArray(0))
    for (i in 0 until hLen) {
        if (db[i] != lHash[i]) throw BadPaddingException("OAEP-Format ungueltig (lHash-Mismatch)")
    }
    var idx = hLen
    while (idx < db.size && db[idx].toInt() == 0) idx++
    if (idx >= db.size || db[idx].toInt() != 0x01) throw BadPaddingException("OAEP-Format ungueltig (kein 0x01-Trenner)")
    return db.copyOfRange(idx + 1, db.size)
}

private fun mgf1(seed: ByteArray, length: Int, hash: MessageDigest): ByteArray {
    val hLen = hash.digestLength
    val out = ByteArray(length)
    var offset = 0
    var counter = 0
    while (offset < length) {
        hash.reset()
        hash.update(seed)
        hash.update(byteArrayOf(
            (counter ushr 24).toByte(),
            (counter ushr 16).toByte(),
            (counter ushr 8).toByte(),
            counter.toByte()
        ))
        val block = hash.digest()
        val take = minOf(hLen, length - offset)
        System.arraycopy(block, 0, out, offset, take)
        offset += take
        counter++
    }
    return out
}

private fun xor(a: ByteArray, b: ByteArray): ByteArray = ByteArray(a.size) { i -> (a[i].toInt() xor b[i].toInt()).toByte() }

private fun aesGCMEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray): ByteArray =
    Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
    }.doFinal(plaintext)

private fun aesGCMDecrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray =
    Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
    }.doFinal(ciphertext)

private fun buildConfigArgument(configPath: String, slotOverride: String?, libraryOverride: String?): String {
    if (slotOverride == null && libraryOverride == null) return configPath
    val base = Files.readString(Path.of(configPath))
    val sb = StringBuilder("--").append(base)
    if (!base.endsWith("\n")) sb.append('\n')
    libraryOverride?.let { sb.append("library = ").append(it).append('\n') }
    slotOverride?.let { sb.append("slot = ").append(it).append('\n') }
    return sb.toString()
}

private fun resolveWrapKeyAlias(keyStore: KeyStore, preferred: String?): String? {
    println("Aliase im PKCS#11-KeyStore:")
    val aliases = keyStore.aliases()
    var found: String? = null
    var any = false
    while (aliases.hasMoreElements()) {
        any = true
        val a = aliases.nextElement()
        val isKey = keyStore.isKeyEntry(a)
        println("- $a key=$isKey")
        if (!isKey) continue
        if (preferred != null && preferred == a) return a
        if (preferred == null && a == "wrap-key") found = a
    }
    if (!any) println("- keine Aliase sichtbar")
    return found
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
