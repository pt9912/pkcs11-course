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

Modul- oder PIN-Manipulation ueber `make csharp-demo` wuerde an der Dependency-Kette `gen-rsa -> init-token` scheitern, bevor C# laeuft. Vorstufe daher mit echten Werten starten und nur die .NET-Demo umschalten — Aufrufmuster nach Modus: siehe [`course/02-lab-setup.md` §Fehlerfaelle direkt ausfuehren](../course/02-lab-setup.md#fehlerfaelle-direkt-ausfuehren--devcontainer-vs-docker-compose). Compose-Service ist `pkcs11-csharp`, der Befehl `cd lab/csharp/Pkcs11Demo && dotnet run --configuration Release`. Vorstufe: `make init-token gen-rsa`.

- **Falscher Modulpfad**: ENV `PKCS11_MODULE=/does/not/exist.so`. Erwartet: Klarer Library-Load-Fehler vor Login oder Signatur.
- **Falsche PIN**: ENV `PKCS11_USER_PIN=000000`. Erwartet: `CKR_PIN_INCORRECT` als Pkcs11Exception aus `Session.Login`.

## Reflexionsfragen

Vier Stufen — eine Recall-, zwei Analyse- und eine Evaluate-Frage:

1. **(Recall)** Warum braucht C# hier kein Zertifikat, Java aber fuer den KeyStore-Alias schon?
2. **(Analyse)** Welche Cleanup-Schritte sind bei nativen PKCS#11-Bibliotheken kritisch — und was passiert konkret, wenn eine Exception zwischen `OpenSession` und `Login` fliegt? Verfolge dazu die `using`-Reihenfolge in `lab/csharp/Pkcs11Demo/Program.cs`.
3. **(Analyse)** Pkcs11Interop hat zwei `AppType`-Modi: `SingleThreaded` und `MultiThreaded`. Welcher wird in der Lab-Demo benutzt, was bedeutet das fuer die Library-internen Locks, und wann wuerde der andere Modus relevant?
4. **(Evaluate)** Du planst einen .NET-Backend-Service, der auf Linux (Container, Production) und Windows (Workstation-Entwicklung) laufen soll. Welche **zwei** Pkcs11Interop-spezifischen Stolperfallen verlangen je nach Plattform andere Loesungen — und wuerdest du beide Plattformen unterstuetzen oder die Windows-Variante als "nicht supported" markieren?

## Musterloesung

Siehe `solutions/06-csharp.md`.
