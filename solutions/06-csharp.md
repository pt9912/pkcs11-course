# Lösung 06 — C# über Pkcs11Interop

## Kernablauf

1. `Pkcs11InteropFactories` erzeugen.
2. Library mit dem Pfad aus `PKCS11_MODULE` laden.
3. Slots mit Token lesen und über `GetTokenInfo().Label` den Token `dev-token` finden.
4. Read/Write-Session öffnen und mit `CKU_USER` einloggen.
5. Private-Key-Objekt suchen:
   - `CKA_CLASS = CKO_PRIVATE_KEY`
   - `CKA_ID = 01`
6. Mit Mechanismus `CKM_SHA256_RSA_PKCS` signieren.
7. Signatur mit OpenSSL gegen den exportierten Public Key prüfen.

## Verifikation

```bash
pkcs11-tool --module "$PKCS11_MODULE" --token-label "$PKCS11_TOKEN_LABEL" \
  --read-object --type pubkey --id 01 --output-file lab/work/public-csharp.der

openssl rsa -pubin -inform DER -in lab/work/public-csharp.der -out lab/work/public-csharp.pem
openssl dgst -sha256 -verify lab/work/public-csharp.pem \
  -signature lab/work/csharp.sig lab/work/csharp.txt
```

Erwartet: `Verified OK`.

## Typische Fehler

| Fehler | Ursache |
|---|---|
| Library-Load-Fehler | Falscher Modulpfad oder native Abhängigkeit fehlt |
| `CKR_PIN_INCORRECT` | Falsche User-PIN |
| Kein Key gefunden | Token initialisiert, aber RSA-Key nicht erzeugt oder falsche `CKA_ID` |
