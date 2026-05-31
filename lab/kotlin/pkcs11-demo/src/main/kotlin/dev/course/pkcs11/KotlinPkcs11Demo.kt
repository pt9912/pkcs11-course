package dev.course.pkcs11

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.Key
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Provider
import java.security.ProviderException
import java.security.Security
import java.security.Signature
import java.security.cert.Certificate
import java.security.interfaces.ECKey
import java.security.interfaces.RSAKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import java.util.Base64
import java.util.Locale
import kotlin.system.exitProcess

fun main() {
    val configPath = System.getenv("PKCS11_JAVA_CONFIG").nullIfBlank() ?: "src/main/resources/softhsm.cfg"
    val mechanism = System.getenv("PKCS11_MECHANISM").nullIfBlank() ?: "SHA256withRSA"
    // Default auf signing-key: ohne Default zog die Alias-Suche den ersten
    // KeyEntry, was nach Modulen mit weiteren Privkeys (wrap-key aus 13,
    // hmac-key aus 16, ca-key aus 22) der falsche war. Explizit signing-key
    // entspricht dem, was Kapitel und Uebung erwarten.
    val preferredAlias = System.getenv("PKCS11_KEY_ALIAS").nullIfBlank() ?: "signing-key"
    val slotOverride = System.getenv("PKCS11_SLOT_ID").nullIfBlank()
    val libraryOverride = System.getenv("PKCS11_LIBRARY").nullIfBlank()
    val pin = (System.getenv("PKCS11_USER_PIN").nullIfBlank() ?: "987654").toCharArray()

    try {
        val base = Security.getProvider("SunPKCS11")
            ?: error("SunPKCS11 Provider ist in diesem JDK nicht verfuegbar.")
        val provider = base.configure(buildConfigArgument(configPath, slotOverride, libraryOverride))

        println("Provider: ${provider.name}")
        println("Mechanismus: $mechanism")

        val keyStore = KeyStore.getInstance("PKCS11", provider)
        try {
            keyStore.load(null, pin)
        } finally {
            // Nullbyte, analog zu Arrays.fill(pin, '\0') in der Java-Demo
            // und Array.Clear in der C#-Demo. Hinterlaesst keine sichtbaren
            // ASCII-Reste in einem Heap-Dump.
            pin.fill('\u0000')
        }

        val alias = findPrivateKeyAlias(keyStore, mechanism, preferredAlias)
        if (alias == null) {
            System.err.println("Kein passender Private-Key-Alias im PKCS#11-KeyStore sichtbar.")
            System.err.println("Moegliche Ursachen:")
            System.err.println("- Kein Zertifikat mit derselben CKA_ID wie der Private Key (make import-cert)")
            System.err.println("- Key-Typ passt nicht zum Mechanismus '$mechanism'")
            System.err.println("- PKCS11_KEY_ALIAS zeigt auf einen nicht existierenden Alias")
            exitProcess(2)
        }

        val privateKey = keyStore.getKey(alias, null) as PrivateKey
        val certificate: Certificate = keyStore.getCertificate(alias)
            ?: run {
                System.err.println("Alias '$alias' hat keine Zertifikatskette.")
                exitProcess(2)
            }
        val publicKey = certificate.publicKey
        val data = "hello from kotlin pkcs11".toByteArray(StandardCharsets.UTF_8)

        val signature = sign(provider, privateKey, mechanism, data)
        val ok = verify(provider, publicKey, mechanism, data, signature)

        println("Alias: $alias")
        println("Signatur (Base64): ${Base64.getEncoder().encodeToString(signature)}")
        println("Verifikation: $ok")

        if (!ok) {
            exitProcess(3)
        }
    } catch (e: Exception) {
        reportFailure(e)
        exitProcess(1)
    }
}

// SunPKCS11 akzeptiert den Argument-String entweder als Pfad zu einer Config-Datei
// oder, wenn er mit "--" beginnt, als Inline-Inhalt. Inline-Modus erlaubt slot-/library-Override
// per ENV, ohne die getrackte softhsm.cfg zu veraendern.
private fun buildConfigArgument(configPath: String, slotOverride: String?, libraryOverride: String?): String {
    if (slotOverride == null && libraryOverride == null) {
        return configPath
    }
    val base = Files.readString(Path.of(configPath))
    val sb = StringBuilder("--")
    sb.append(base)
    if (!base.endsWith("\n")) sb.append('\n')
    libraryOverride?.let { sb.append("library = ").append(it).append('\n') }
    slotOverride?.let { sb.append("slot = ").append(it).append('\n') }
    return sb.toString()
}

private fun findPrivateKeyAlias(keyStore: KeyStore, mechanism: String, preferred: String?): String? {
    println("Aliase im PKCS#11-KeyStore:")
    val aliases = keyStore.aliases()
    var found: String? = null
    var any = false
    while (aliases.hasMoreElements()) {
        any = true
        val alias = aliases.nextElement()
        val isKey = keyStore.isKeyEntry(alias)
        val isCert = keyStore.isCertificateEntry(alias)
        println("- $alias key=$isKey cert=$isCert")
        if (!isKey) continue
        if (preferred != null && preferred == alias) return alias
        if (preferred == null && found == null && matchesMechanism(keyStore.getKey(alias, null), mechanism)) {
            found = alias
        }
    }
    if (!any) println("- keine Aliase sichtbar")
    return found
}

private fun matchesMechanism(key: Key, mechanism: String): Boolean {
    val algorithm = key.algorithm.uppercase(Locale.ROOT)
    val m = mechanism.uppercase(Locale.ROOT)
    if (m.contains("RSA")) return key is RSAKey || algorithm.contains("RSA") || algorithm == "PRIVATE"
    if (m.contains("ECDSA") || m.contains("WITHECDSA")) return key is ECKey || algorithm.contains("EC") || algorithm == "PRIVATE"
    return true
}

private fun sign(provider: Provider, privateKey: PrivateKey, mechanism: String, data: ByteArray): ByteArray {
    val signer = Signature.getInstance(mechanism, provider)
    if (isPss(mechanism)) signer.setParameter(defaultPssParameters())
    signer.initSign(privateKey)
    signer.update(data)
    return signer.sign()
}

private fun verify(provider: Provider, publicKey: java.security.PublicKey, mechanism: String, data: ByteArray, signature: ByteArray): Boolean {
    val verifier = Signature.getInstance(mechanism, provider)
    if (isPss(mechanism)) verifier.setParameter(defaultPssParameters())
    verifier.initVerify(publicKey)
    verifier.update(data)
    return verifier.verify(signature)
}

private fun isPss(mechanism: String): Boolean {
    val m = mechanism.uppercase(Locale.ROOT)
    return m == "RSASSA-PSS" || m.endsWith("WITHRSAANDMGF1") || m.contains("PSS")
}

private fun defaultPssParameters(): PSSParameterSpec =
    PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1)

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
