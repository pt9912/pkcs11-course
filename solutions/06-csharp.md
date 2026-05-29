# Loesung 06 - C# ueber Pkcs11Interop

## Lauf

```bash
make init-token
make gen-rsa
make csharp-demo
```

Erwartet:

```text
Token: dev-token
Signatur: /workspace/lab/work/csharp.sig (...)
Verified OK
```

## Kernablauf im Code

`lab/csharp/Pkcs11Demo/Program.cs` macht:

1. `Pkcs11InteropFactories` erzeugen.
2. Library mit dem Pfad aus `PKCS11_MODULE` laden.
3. Slots mit Token lesen und ueber `GetTokenInfo().Label` den Token `dev-token` finden.
4. Read/Write-Session oeffnen und mit `CKU_USER` einloggen.
5. Private-Key-Objekt suchen:
   - `CKA_CLASS = CKO_PRIVATE_KEY`
   - `CKA_ID = 01`
6. Mit Mechanismus `CKM_SHA256_RSA_PKCS` signieren.
7. Signatur mit OpenSSL gegen den exportierten Public Key pruefen.

## Fehler pruefen

```bash
PKCS11_MODULE=/does/not/exist.so make csharp-demo
```

Erwartet: Library-Load-Fehler vor Login oder Signatur.

```bash
PKCS11_USER_PIN=000000 make csharp-demo
```

Erwartet: `CKR_PIN_INCORRECT`.
