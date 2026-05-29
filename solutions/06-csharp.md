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

`make csharp-demo` haengt an `gen-rsa -> init-token`. Wenn ENV-Werte umgestellt werden, kann eine Vorstufe stoppen, bevor .NET ueberhaupt laeuft. Daher Vorstufe sauber rendern und nur die Demo direkt starten:

```bash
make init-token gen-rsa
docker compose -f lab/docker-compose.yml run --rm \
  -e PKCS11_MODULE=/does/not/exist.so \
  pkcs11-csharp bash -lc 'cd lab/csharp/Pkcs11Demo && dotnet run --configuration Release'
```

Erwartet: Library-Load-Fehler vor Login oder Signatur.

```bash
make init-token gen-rsa
docker compose -f lab/docker-compose.yml run --rm \
  -e PKCS11_USER_PIN=000000 \
  pkcs11-csharp bash -lc 'cd lab/csharp/Pkcs11Demo && dotnet run --configuration Release'
```

Erwartet: `CKR_PIN_INCORRECT` aus `Session.Login`.
