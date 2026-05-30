package dev.course.pkcs11

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Provider
import java.security.ProviderException
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Base64
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.ExtensionsGenerator
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import kotlin.system.exitProcess

// CSR-Demo — vgl. Pkcs11CsrDemo.java.
fun main() {
    val configPath = System.getenv("PKCS11_JAVA_CONFIG").nullIfBlank() ?: "src/main/resources/softhsm.cfg"
    val preferredAlias = System.getenv("PKCS11_KEY_ALIAS").nullIfBlank()
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

        val alias = resolveAlias(keyStore, preferredAlias) ?: run {
            System.err.println("Kein Alias mit Cert-Eintrag gefunden.")
            exitProcess(2)
        }
        val privKey = keyStore.getKey(alias, null) as PrivateKey
        val pubKey = (keyStore.getCertificate(alias) as X509Certificate).publicKey

        val subject = X500Name("CN=kotlin-app.example.org, O=PKCS11 Lab")

        val extGen = ExtensionsGenerator()
        extGen.addExtension(Extension.subjectAlternativeName, false,
            GeneralNames(GeneralName(GeneralName.dNSName, "kotlin-app.example.org")))
        extGen.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))

        val csrBuilder = JcaPKCS10CertificationRequestBuilder(subject, pubKey)
            .addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extGen.generate())

        val signer = JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(provider)
            .build(privKey)

        val csr = csrBuilder.build(signer)

        val jcaCsr = JcaPKCS10CertificationRequest(csr).setProvider("SunJCE")
        if (!jcaCsr.isSignatureValid(JcaContentVerifierProviderBuilder().build(pubKey))) {
            System.err.println("CSR-Selbstsignatur ungueltig.")
            exitProcess(3)
        }

        val out = Path.of(outputDir)
        Files.createDirectories(out)
        val csrPath = out.resolve("kotlin-app.csr")
        val pem = "-----BEGIN CERTIFICATE REQUEST-----\n" +
            Base64.getMimeEncoder(64, byteArrayOf('\n'.code.toByte())).encodeToString(csr.encoded) +
            "\n-----END CERTIFICATE REQUEST-----\n"
        Files.writeString(csrPath, pem)

        println("--- CSR-Generierung ---")
        println("Alias:        $alias")
        println("Subject:      $subject")
        println("DNS-SAN:      kotlin-app.example.org")
        println("SignAlgo:     sha256WithRSAEncryption")
        println("CSR:          $csrPath (${pem.length} Bytes PEM)")
        println("Selbstsignatur (PoP): OK")
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

private fun resolveAlias(keyStore: KeyStore, preferred: String?): String? {
    val aliases = keyStore.aliases()
    var found: String? = null
    while (aliases.hasMoreElements()) {
        val a = aliases.nextElement()
        if (!keyStore.isKeyEntry(a)) continue
        if (preferred != null && preferred == a) return a
        if (preferred == null && a == "signing-key") found = a
        if (preferred == null && found == null) found = a
    }
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
