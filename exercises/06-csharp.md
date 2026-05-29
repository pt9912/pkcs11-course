# Uebung 06 - C# ueber Pkcs11Interop

## Ziel

Du greifst aus .NET mit Pkcs11Interop auf das PKCS#11-Modul zu, findest den Token ueber sein Label und signierst mit dem privaten RSA-Key.

## Vorbereitung

```bash
make init-token
make gen-rsa
```

## Aufgabe

1. Starte die C#-Demo:
   ```bash
   make csharp-demo
   ```
2. Lies `lab/csharp/Pkcs11Demo/Program.cs`.
3. Markiere im Code:
   - Library-Load
   - Slot-Auswahl ueber Token-Label
   - Session-Handling
   - Login
   - Key-Suche per `CKA_CLASS` und `CKA_ID`
   - Signaturmechanismus
   - deterministisches Freigeben der Ressourcen

## Erwartete Ausgabe

- Das Programm meldet das Token-Label `dev-token`.
- Eine Signaturdatei `lab/work/csharp.sig` entsteht.
- OpenSSL meldet `Verified OK`.
- Session und Library werden ueber `using`/`Dispose` freigegeben.

## Fehlerfall

Modul- oder PIN-Manipulation ueber `make csharp-demo` wuerde an der Dependency-Kette `gen-rsa -> init-token` scheitern, bevor C# laeuft. Vorstufe daher mit echten Werten starten und nur die .NET-Demo umschalten.

Falscher Modulpfad:

```bash
make init-token gen-rsa
docker compose -f lab/docker-compose.yml run --rm \
  -e PKCS11_MODULE=/does/not/exist.so \
  pkcs11-csharp bash -lc 'cd lab/csharp/Pkcs11Demo && dotnet run --configuration Release'
```

Erwartet: Klarer Library-Load-Fehler vor Login oder Signatur.

Falsche PIN:

```bash
make init-token gen-rsa
docker compose -f lab/docker-compose.yml run --rm \
  -e PKCS11_USER_PIN=000000 \
  pkcs11-csharp bash -lc 'cd lab/csharp/Pkcs11Demo && dotnet run --configuration Release'
```

Erwartet: `CKR_PIN_INCORRECT` als Pkcs11Exception aus `Session.Login`.

## Reflexionsfragen

- Warum braucht C# hier kein Zertifikat, Java aber fuer den KeyStore-Alias schon?
- Welche Cleanup-Schritte sind bei nativen PKCS#11-Bibliotheken kritisch?

## Musterloesung

Siehe `solutions/06-csharp.md`.
