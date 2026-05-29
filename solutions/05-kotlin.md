# Loesung 05 - Kotlin ueber SunPKCS11

## Lauf

```bash
make init-token
make gen-rsa
make import-cert
make kotlin-demo
```

Erwartet:

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

```bash
PKCS11_USER_PIN=000000 make kotlin-demo
```

Erwartet: Login-Fehler beim `KeyStore.load`.

Wenn das Zertifikat fehlt, ist der private Key fuer den Java-KeyStore nicht als Private-Key-Alias nutzbar. `make kotlin-demo` repariert das ueber die Abhaengigkeit `import-cert` automatisch; fuer den Fehlerfall musst du die Demo direkt starten.
