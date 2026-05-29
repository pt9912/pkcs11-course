# Lösung 05 — Kotlin über SunPKCS11

## Kernablauf

Kotlin/JVM nutzt denselben Stack wie die Java-Demo:

1. `Security.getProvider("SunPKCS11")` holen.
2. Provider mit `softhsm.cfg` konfigurieren.
3. `KeyStore.getInstance("PKCS11", provider)` öffnen.
4. `load(null, pin.toCharArray())` ausführen.
5. Alias `signing-key` suchen.
6. `PrivateKey` und Zertifikat aus dem KeyStore lesen.
7. Mit `Signature.getInstance("SHA256withRSA", provider)` signieren.
8. Mit dem Public Key aus dem Zertifikat verifizieren.

## Erwarteter Output

```text
Provider: SunPKCS11-SoftHSM
Alias: signing-key
Verifikation: true
```

## Typische Fehler

| Fehler | Ursache |
|---|---|
| Kein Alias sichtbar | Zertifikat fehlt oder `CKA_ID` passt nicht zum privaten Key |
| Login-Fehler | Falsche User-PIN |
| Provider-Load scheitert | Falscher `library`-Pfad in `softhsm.cfg` |

## Minimalbeispiel

`Pkcs11Kotlin.kt`:

```kotlin
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Security
import java.security.Signature
import java.security.cert.X509Certificate

fun main() {
    val pin = (System.getenv("PKCS11_USER_PIN") ?: "987654").toCharArray()
    val configPath = System.getenv("PKCS11_JAVA_CONFIG")
        ?: "lab/java/pkcs11-demo/src/main/resources/softhsm.cfg"

    val base = Security.getProvider("SunPKCS11")
        ?: error("SunPKCS11 nicht verfuegbar")
    val provider = base.configure(configPath)
    Security.addProvider(provider)
    println("Provider: ${provider.name}")

    val ks = KeyStore.getInstance("PKCS11", provider).apply { load(null, pin) }
    val alias = "signing-key"
    val key = ks.getKey(alias, null) as PrivateKey
    val cert = ks.getCertificate(alias) as X509Certificate

    val data = "hello from kotlin pkcs11".toByteArray()
    val signer = Signature.getInstance("SHA256withRSA", provider).apply {
        initSign(key); update(data)
    }
    val sig = signer.sign()

    val verifier = Signature.getInstance("SHA256withRSA").apply {
        initVerify(cert.publicKey); update(data)
    }
    println("Alias: $alias")
    println("Verifikation: ${verifier.verify(sig)}")
}
```

Build und Lauf im Kotlin-Container (`kotlinc` ist auf dem `PATH`):

```bash
docker compose -f lab/docker-compose.yml run --rm pkcs11-kotlin bash
# im Container:
kotlinc Pkcs11Kotlin.kt -include-runtime -d pkcs11-kotlin.jar
java -jar pkcs11-kotlin.jar
```
