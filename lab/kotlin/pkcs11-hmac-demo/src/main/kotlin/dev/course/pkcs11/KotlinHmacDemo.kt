package dev.course.pkcs11

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Provider
import java.security.ProviderException
import java.security.Security
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.SecretKey
import kotlin.system.exitProcess

// HMAC-SHA256 ueber SunPKCS11/JCA — vgl. Pkcs11HmacDemo.java fuer Details.

fun main() {
    val configPath = System.getenv("PKCS11_JAVA_CONFIG").nullIfBlank() ?: "src/main/resources/softhsm.cfg"
    val slotOverride = System.getenv("PKCS11_SLOT_ID").nullIfBlank()
    val libraryOverride = System.getenv("PKCS11_LIBRARY").nullIfBlank()
    val pin = (System.getenv("PKCS11_USER_PIN").nullIfBlank() ?: "987654").toCharArray()
    val outputDir = System.getenv("PKCS11_OUTPUT_DIR").nullIfBlank() ?: "/workspace/lab/work"
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

        // 1) Raw HMAC
        val data = "API-Token-Anfrage von client-42 am 30.05.2026T12:00:00Z\n".toByteArray(StandardCharsets.UTF_8)
        val mac = hmacSign(provider, hmacKey, data)
        if (!hmacVerify(provider, hmacKey, data, mac)) {
            System.err.println("Verify (Original) FEHLGESCHLAGEN.")
            exitProcess(3)
        }
        val tampered = data.clone().also { it[it.size - 2] = (it[it.size - 2].toInt() xor 0x01).toByte() }
        if (hmacVerify(provider, hmacKey, tampered, mac)) {
            System.err.println("Tampered-Verify haette fehlschlagen muessen.")
            exitProcess(3)
        }

        val out = Path.of(outputDir)
        Files.createDirectories(out)
        Files.write(out.resolve("kotlin-hmac-data.txt"), data)
        Files.write(out.resolve("kotlin-hmac-data.mac"), mac)

        // 2) JWT (HS256)
        val now = Instant.now().epochSecond
        val claims = linkedMapOf<String, Any>(
            "sub" to "user-42",
            "iss" to "pkcs11-lab",
            "iat" to now,
            "exp" to now + 3600,
        )
        val jwt = jwtSign(provider, hmacKey, claims)
        if (!jwtVerify(provider, hmacKey, jwt)) {
            System.err.println("JWT-Verify fehlgeschlagen.")
            exitProcess(3)
        }
        Files.writeString(out.resolve("kotlin-hmac.jwt"), jwt)

        println("Raw HMAC:")
        println("  Data:  ${out.resolve("kotlin-hmac-data.txt")} (${data.size} Bytes)")
        println("  MAC:   ${out.resolve("kotlin-hmac-data.mac")} (${mac.size} Bytes)")
        println("  Verify (Original):   OK")
        println("  Verify (Tampered):   abgelehnt (erwartet)")
        println("JWT (HS256):")
        println("  Token: ${out.resolve("kotlin-hmac.jwt")}")
        println("  Wert:  $jwt")
        println("  Verify: OK")
    } catch (e: Exception) {
        reportFailure(e)
        exitProcess(1)
    }
}

private fun hmacSign(provider: Provider, key: SecretKey, data: ByteArray): ByteArray =
    Mac.getInstance("HmacSHA256", provider).apply { init(key) }.doFinal(data)

private fun hmacVerify(provider: Provider, key: SecretKey, data: ByteArray, expected: ByteArray): Boolean {
    val computed = hmacSign(provider, key, data)
    return MessageDigest.isEqual(computed, expected)
}

private fun simpleJson(map: Map<String, Any>): String =
    map.entries.joinToString(",", "{", "}") { (k, v) ->
        val key = "\"${escape(k)}\""
        val value = if (v is Number) v.toString() else "\"${escape(v.toString())}\""
        "$key:$value"
    }

private fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

private fun jwtSign(provider: Provider, key: SecretKey, claims: Map<String, Any>): String {
    val headerB64 = b64url(simpleJson(linkedMapOf("alg" to "HS256", "typ" to "JWT")).toByteArray(StandardCharsets.UTF_8))
    val payloadB64 = b64url(simpleJson(claims).toByteArray(StandardCharsets.UTF_8))
    val signingInput = "$headerB64.$payloadB64"
    val mac = hmacSign(provider, key, signingInput.toByteArray(StandardCharsets.UTF_8))
    return "$signingInput.${b64url(mac)}"
}

private fun jwtVerify(provider: Provider, key: SecretKey, token: String): Boolean {
    val parts = token.split(".")
    if (parts.size != 3) return false
    val mac = try {
        Base64.getUrlDecoder().decode(parts[2])
    } catch (_: IllegalArgumentException) {
        return false
    }
    val signingInput = "${parts[0]}.${parts[1]}".toByteArray(StandardCharsets.UTF_8)
    return hmacVerify(provider, key, signingInput, mac)
}

private fun b64url(data: ByteArray): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(data)

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
