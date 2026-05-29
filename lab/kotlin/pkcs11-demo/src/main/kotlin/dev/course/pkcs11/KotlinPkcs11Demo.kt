package dev.course.pkcs11

import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Security
import java.security.Signature
import java.security.cert.Certificate
import java.util.Base64
import kotlin.system.exitProcess

fun main() {
    val pin = System.getenv("PKCS11_USER_PIN") ?: "987654"
    val configPath = System.getenv("PKCS11_JAVA_CONFIG") ?: "src/main/resources/softhsm.cfg"

    val base = Security.getProvider("SunPKCS11")
        ?: error("SunPKCS11 Provider ist in diesem JDK nicht verfuegbar.")
    val provider = base.configure(configPath)
    Security.addProvider(provider)

    println("Provider: ${provider.name}")

    val keyStore = KeyStore.getInstance("PKCS11", provider)
    keyStore.load(null, pin.toCharArray())

    val alias = findPrivateKeyAlias(keyStore)
    if (alias == null) {
        System.err.println("Kein Private-Key-Alias im PKCS#11-KeyStore sichtbar.")
        System.err.println("Importiere zuerst: make import-cert")
        exitProcess(2)
    }

    val privateKey = keyStore.getKey(alias, null) as PrivateKey
    val certificate: Certificate = keyStore.getCertificate(alias)
    val publicKey = certificate.publicKey
    val data = "hello from kotlin pkcs11".toByteArray(StandardCharsets.UTF_8)

    val signer = Signature.getInstance("SHA256withRSA", provider)
    signer.initSign(privateKey)
    signer.update(data)
    val signature = signer.sign()

    val verifier = Signature.getInstance("SHA256withRSA")
    verifier.initVerify(publicKey)
    verifier.update(data)
    val ok = verifier.verify(signature)

    println("Alias: $alias")
    println("Signatur (Base64): ${Base64.getEncoder().encodeToString(signature)}")
    println("Verifikation: $ok")

    if (!ok) {
        exitProcess(3)
    }
}

private fun findPrivateKeyAlias(keyStore: KeyStore): String? {
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
        if (found == null && isKey) {
            found = alias
        }
    }
    if (!any) {
        println("- keine Aliase sichtbar")
    }
    return found
}
