package dev.course.pkcs11

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Provider
import java.security.ProviderException
import java.security.Security
import java.security.cert.X509Certificate
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import kotlin.system.exitProcess

// CMS/PKCS#7 — vgl. Pkcs11CmsDemo.java fuer die Architektur.
// SunPKCS11 liefert den PrivateKey-Handle, JcaContentSignerBuilder bindet ihn,
// BC CMSSignedDataGenerator assembliert die SignedData-Struktur.

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
            System.err.println("Kein Alias mit Cert-Eintrag gefunden — erst 'make import-cert' ausfuehren.")
            exitProcess(2)
        }
        val privKey = keyStore.getKey(alias, null) as PrivateKey
        val cert = keyStore.getCertificate(alias) as X509Certificate?
            ?: run {
                System.err.println("Alias '$alias' hat keine Zertifikatskette.")
                exitProcess(2)
            }

        val content = "Vertrag XYZ vom 30.05.2026\nUnterzeichnender: Kotlin-CMS-Demo\n".toByteArray(StandardCharsets.UTF_8)

        val contentSigner = JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(provider)
            .build(privKey)

        val gen = CMSSignedDataGenerator()
        gen.addSignerInfoGenerator(
            JcaSignerInfoGeneratorBuilder(JcaDigestCalculatorProviderBuilder().build())
                .build(contentSigner, cert)
        )
        gen.addCertificates(JcaCertStore(listOf(cert)))

        val signed: CMSSignedData = gen.generate(CMSProcessableByteArray(content), false)
        val der = signed.encoded

        // Self-Verify
        val reparsed = CMSSignedData(CMSProcessableByteArray(content), der)
        for (si in reparsed.signerInfos.signers) {
            if (!si.verify(JcaSimpleSignerInfoVerifierBuilder().build(cert))) {
                System.err.println("Self-Verify fehlgeschlagen.")
                exitProcess(3)
            }
        }

        val out = Path.of(outputDir)
        Files.createDirectories(out)
        val contentPath = out.resolve("kotlin-cms-document.txt")
        val sigPath = out.resolve("kotlin-cms-document.p7s")
        Files.write(contentPath, content)
        Files.write(sigPath, der)

        println("Alias:          $alias")
        println("Signer-Subject: ${cert.subjectX500Principal}")
        println("Klartext:       $contentPath (${content.size} Bytes)")
        println("CMS-Signatur:   $sigPath (${der.size} Bytes, detached)")
        println("Self-Verify:    OK")
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
        if (preferred == null && a == "signing-key") found = a
        if (preferred == null && found == null) found = a
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
