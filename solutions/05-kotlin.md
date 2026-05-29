# Loesung 05 - Kotlin ueber SunPKCS11

## Lauf

```bash
make init-token
make gen-rsa
make import-cert
make kotlin-demo
```

Erwartet (gekuerzt — die Demo druckt zusaetzlich `Mechanismus: ...`, `Aliase im PKCS#11-KeyStore:` und `Signatur (Base64): ...`):

```text
Provider: SunPKCS11-SoftHSM
Alias: signing-key
Verifikation: true
```

## Kernablauf im Code

`lab/kotlin/pkcs11-demo/src/main/kotlin/dev/course/pkcs11/KotlinPkcs11Demo.kt` nutzt denselben JCA-Fluss wie Java:

1. `Security.getProvider("SunPKCS11")`.
2. Provider mit `softhsm.cfg` konfigurieren.
3. `KeyStore.getInstance("PKCS11", provider)`.
4. `load(null, pin.toCharArray())`.
5. Alias `signing-key` suchen.
6. `PrivateKey` und Zertifikat aus dem KeyStore lesen.
7. Mit `Signature.getInstance("SHA256withRSA", provider)` signieren.
8. Mit dem Public Key aus dem Zertifikat verifizieren.

## Fehler pruefen

`make kotlin-demo` haengt an `import-cert -> gen-rsa -> init-token`. Sobald du eine ENV-Variable umstellst, kann eine dieser Vorstufen scheitern, bevor Kotlin gestartet wird. Deshalb Vorstufe wie gewohnt laufen lassen und die Demo direkt mit der Manipulation aufrufen.

Falsche PIN:

```bash
make init-token gen-rsa import-cert
docker compose -f lab/docker-compose.yml run --rm \
  -e PKCS11_USER_PIN=000000 \
  pkcs11-kotlin bash -lc 'cd lab/kotlin/pkcs11-demo && ./gradlew --quiet --no-daemon run'
```

Erwartet: `reportFailure` druckt eine `ProviderException`-Kette inklusive `CKR_PIN_INCORRECT`.

Falsche Library:

```bash
docker compose -f lab/docker-compose.yml run --rm \
  -e PKCS11_LIBRARY=/nicht/da \
  pkcs11-kotlin bash -lc 'cd lab/kotlin/pkcs11-demo && ./gradlew --quiet --no-daemon run'
```

Erwartet: Provider-Load schlaegt mit `IOException`/`CKR_GENERAL_ERROR` fehl.

Wenn das Zertifikat fehlt, ist der private Key fuer den Java-KeyStore nicht als Private-Key-Alias nutzbar. `make kotlin-demo` repariert das ueber die Abhaengigkeit `import-cert` automatisch; fuer den Fehlerfall musst du die Demo direkt starten.
