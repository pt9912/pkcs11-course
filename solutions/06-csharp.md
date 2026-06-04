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

## Antworten zu den Reflexionsfragen

**1. (Recall) C# braucht keinen Cert, Java schon:** Pkcs11Interop ist ein duenner Wrapper ueber die native PKCS#11-API. Die `FindObjects`-Calls suchen direkt ueber `CKA_CLASS=CKO_PRIVATE_KEY` plus `CKA_ID=01` — kein Alias-Konstrukt, keine KeyStore-Abstraktion. Java/Kotlin nutzen SunPKCS11, das einen JCA-`KeyStore` aufbaut; dessen Alias-Konvention verlangt ein Cert mit derselben `CKA_ID`. Es ist also kein PKCS#11-Unterschied, sondern eine Konvention der jeweiligen Sprach-API.

**2. (Analyse) Native Cleanup + Exception-Verhalten:** Drei Stufen, alle muessen sauber laufen, sonst leakt der Anwendungsspeicher Handles oder das Token bleibt im `CKR_USER_ALREADY_LOGGED_IN`-State haengen:

1. `Session.Logout()` (gilt prozessweit fuer den Token — nicht pro Session pflegen).
2. `Session.Dispose()` schliesst die Session und gibt Handles frei.
3. `Library.Dispose()` ruft `C_Finalize`.

Pkcs11Interop nutzt `IDisposable`/`using`-Bloecke, die das in der richtigen Reihenfolge erzwingen. **Exception zwischen `OpenSession` und `Login`:** das `using`-konstruct fuer die Session ist da bereits aktiv (Pkcs11Interop oeffnet die Session in `slot.OpenSession(...)`). Wirft `Login` eine Exception (z.B. `CKR_PIN_INCORRECT`), wird der `using`-Block der Session sauber bis zum `Session.Dispose()` durchlaufen — Session wird also korrekt geschlossen. Was **nicht** stattfindet: `Session.Logout()`, weil der Login gar nicht erfolgreich war. Das ist genau richtig — `Logout()` ohne `Login()` gibt `CKR_USER_NOT_LOGGED_IN`. Bei Crash-Pfaden ohne `using` (z.B. Background-Threads, manuelles Try/Finally) muss man explizit unterscheiden: nur loggen, was eingelogt war. `pkcs11-spy` zeigt fehlende `C_CloseSession` als Smell.

**3. (Analyse) `AppType` SingleThreaded vs MultiThreaded:** Die Lab-Demo nutzt `AppType.MultiThreaded` (Standard in den `Pkcs11InteropFactories`-Aufrufen). Bedeutung: Pkcs11Interop ruft `C_Initialize` mit `CKF_OS_LOCKING_OK` — die Library soll OS-Mutexe nutzen, um interne Datenstrukturen thread-safe zu halten. SoftHSM und die meisten produktiven HSM-Libs unterstuetzen das. `AppType.SingleThreaded` ruft `C_Initialize` ohne dieses Flag und sagt der Library: "ich versichere, dass nur ein Thread zugreift". Relevant fuer aeltere oder eingeschraenkte Libs, die `CKF_OS_LOCKING_OK` nicht implementieren — dann wuerde MultiThreaded mit `CKR_NEED_TO_CREATE_THREADS` brechen. Im Lab nie ein Problem; bei exotischen Smartcard-Treibern ein realer Schalter.

**4. (Evaluate) Linux + Windows fuer .NET-PKCS#11:** Zwei plattformabhaengige Stolperfallen:

- **CMS / SignedCms-Limitation auf Linux** (siehe Kap. 14 §"Bridge-Problem 2"): `System.Security.Cryptography.Pkcs.SignedCms` funktioniert auf Linux mit HSM-Keys nicht, weil das OpenSSL-Backend `ExportParameters(true)` ruft. Auf Windows mit CNG-Backend geht es. Loesung: BouncyCastle.Cryptography auf beiden Plattformen — Single-Path, keine `#if WINDOWS`-Pfade.
- **Library-Pfad und PKCS11_MODULE**: auf Linux `/usr/lib/softhsm/libsofthsm2.so`, auf Windows ein DLL-Pfad mit anderen Konventionen (`C:\Program Files\SoftHSM2\lib\softhsm2-x64.dll`). Plus: SafeNet-/Thales-Lunaclient verteilt auf Windows ueber Installer, auf Linux ueber `.deb`/`.rpm`. Loesung: Library-Pfad **immer** aus Config (`PKCS11_MODULE`-ENV oder appsettings.json), nie hardcoded — die Lab-Demo macht das schon.

**Beide Plattformen unterstuetzen?** Ja, wenn die Entwicklung auf Windows produktiv ist (typische Enterprise-Setups). Nein, wenn Production-Linux und keine Windows-User existieren — dann ist "Linux only" eine ehrliche Markierung, die nachhaltig spart (kein doppeltes Smoke-Testing, kein Plattform-Test-Setup). Faustregel: lieber explizit "Linux only" sagen als implizit "wir testen es nicht" leben.
