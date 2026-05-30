# 21 — PIN-Management und Lockout

## Lernziele

Nach diesem Kapitel kannst du:

- die zwei PIN-Rollen `CKU_USER` und `CKU_SO` und ihre Use-Cases unterscheiden.
- den PIN-Status eines Tokens anhand der `CKF_USER_PIN_*`/`CKF_SO_PIN_*`-Flags ablesen.
- `C_SetPIN` (User aendert eigene PIN) und `C_InitPIN` (SO setzt User-PIN) sicher einsetzen.
- die Unterschiede zwischen SoftHSM (kein echter Lockout), Smartcards (3 Versuche) und Cloud-HSMs (mit Konfiguration) einordnen.

## Lab-Bezug

```bash
make pin-info        # PIN-Flags des Tokens lesbar ausgeben
make pin-change      # User-PIN aendern + zurueck
make pin-recovery    # 3 Fehlversuche + SO setzt PIN per InitPIN
make go-pin-demo     # Vollstaendiger Lifecycle (Set, Init, Flag-Beobachtung)
make csharp-pin-demo # Selbes in C#
```

Jede Demo stellt den Ausgangs-PIN-State am Ende wieder her — du kannst sie beliebig oft hintereinander laufen lassen, ohne dass die anderen Kapitel kaputtgehen.

## Zwei Rollen, drei Operationen

| Rolle | Wer? | Was darf sie? |
|---|---|---|
| `CKU_USER` | normale Anwendung mit User-PIN | Crypto-Operationen, eigene PIN aendern |
| `CKU_SO` | Security Officer mit SO-PIN | Token initialisieren, User-PIN zuruecksetzen, ggf. SO-PIN aendern |

Die wichtigsten PIN-Operationen:

| Funktion | Wer ruft? | Was passiert |
|---|---|---|
| `C_Login(session, type, pin)` | beide | Login. Falsche PIN → `CKR_PIN_INCORRECT` + Retry-Counter laeuft. |
| `C_Logout(session)` | beide | Login-State abbauen (wirkt anwendungsweit; siehe Kapitel 17). |
| `C_SetPIN(session, old, new)` | aktuell eingeloggter User/SO | Eigene PIN aendern. User aendert User-PIN, SO aendert SO-PIN. |
| `C_InitPIN(session, new)` | nur SO | **User-PIN** auf neuen Wert setzen. Klassischer Recovery-Pfad nach Lockout. |
| `C_InitToken(slot, sopin, label)` | (vor jeder Session) | Token komplett initialisieren — wischt alle Objekte. SO-PIN wird neu gesetzt. |

`C_InitPIN` ist eines der wichtigsten Recovery-Werkzeuge: wer als User ausgesperrt ist, ruft den SO, der setzt die PIN neu. Funktioniert nur, wenn der **SO-PIN** noch valide ist.

## Flag-Geometrie

`C_GetTokenInfo` liefert `flags`, ein Bitmask. Die PIN-relevanten Bits:

| Flag | Hex | Bedeutung |
|---|---|---|
| `CKF_USER_PIN_INITIALIZED` | `0x00000008` | User-PIN ist gesetzt (sonst muss SO `C_InitPIN` als ersten Login machen) |
| `CKF_USER_PIN_COUNT_LOW` | `0x00010000` | mindestens ein Fehlversuch seit letzter erfolgreicher User-Anmeldung |
| `CKF_USER_PIN_FINAL_TRY` | `0x00020000` | nur noch **EIN** Versuch bis Lockout |
| `CKF_USER_PIN_LOCKED` | `0x00040000` | User-PIN gesperrt — Login via User-PIN nicht moeglich, nur SO-Recovery |
| `CKF_USER_PIN_TO_BE_CHANGED` | `0x00080000` | nach `C_InitPIN` durch SO: User muss PIN aendern, bevor er Crypto macht |
| `CKF_SO_PIN_COUNT_LOW` | `0x00100000` | wie oben fuer SO |
| `CKF_SO_PIN_FINAL_TRY` | `0x00200000` | wie oben fuer SO |
| `CKF_SO_PIN_LOCKED` | `0x00400000` | **Token effektiv gebrickt** — kein Recovery-Pfad ueber PKCS#11 |

`CKF_SO_PIN_LOCKED` ist der gefuerchtete Endzustand: wenn die SO-PIN gesperrt ist, kann niemand mehr ueber PKCS#11 die User-PIN zuruecksetzen. Bei Smartcards und USB-Tokens bedeutet das oft, dass der Token weggeworfen werden muss; bei Enterprise-HSMs gibt es Hersteller-spezifische Recovery-Pfade (M-of-N-Smartcards, Reset via Werks-Reset + Cluster-Resync).

## Anwendungs-Pflichten

Eine ordentliche Anwendung pruft **vor** jedem Login die Token-Flags:

```text
Pseudo-Code:
  flags = C_GetTokenInfo(slot).flags
  if flags & CKF_USER_PIN_LOCKED:
      → "PIN gesperrt, bitte SO kontaktieren" (kein Login-Versuch starten!)
  if flags & CKF_USER_PIN_FINAL_TRY:
      → "ACHTUNG: nur noch EIN Versuch — bei Falscher PIN wirst du gesperrt!"
  if flags & CKF_USER_PIN_COUNT_LOW:
      → "Letzter Versuch war fehlerhaft. Korrekte PIN setzt Counter zurueck."
  proceed with C_Login(...)
```

Wer das `CKF_USER_PIN_TO_BE_CHANGED`-Flag ignoriert, scheitert beim ersten Crypto-Aufruf mit `CKR_PIN_EXPIRED` — die Anwendung muss erst per `C_SetPIN` eine neue PIN waehlen.

## SoftHSM 2.6: kein echter Lockout

Wichtige Lab-Realitaet: **SoftHSM 2.6 setzt zwar `CKF_USER_PIN_COUNT_LOW` nach dem ersten Fehlversuch, lockt aber nie wirklich aus**. Auch nach 50 falschen PINs bleibt der Token nutzbar — sobald die richtige PIN kommt, geht's weiter. `CKF_USER_PIN_FINAL_TRY` und `CKF_USER_PIN_LOCKED` werden in SoftHSM nicht erreicht.

Reale HSMs verhalten sich anders:

| Plattform | Default-Verhalten |
|---|---|
| YubiKey PIV | 3 Fehlversuche → User-PIN locked, PUK-Recovery erforderlich |
| Smartcards (CardOS, IDPrime) | 3 oder 5 Fehlversuche → PIN locked, PUK |
| Thales Luna | konfigurierbar (default 10), nach Lockout SO-Reset |
| Utimaco | konfigurierbar, Default 5 |
| AWS CloudHSM | konfigurierbar, Default 5; nach Lockout API-Call zum Reset |
| Azure Dedicated HSM | konfigurierbar |

Das Lab-Skript `61-pin-recovery-by-so.sh` zeigt deshalb nur die Flag-Transition (Count-Low) und den **SO-Reset-Workflow** — der echte Lockout-Test wuerde auf SoftHSM nichts ausloesen.

## Constant-Time-PIN-Vergleich

Wer einen **eigenen** PIN-Pruefer baut (z.B. CLI-Tool, das die PIN gegen einen Vorhalt prueft, bevor es PKCS#11 anspricht): unbedingt constant-time vergleichen. `if (input == storedPin)` oder `strcmp` ist Timing-anfaellig. JCAs `MessageDigest.isEqual`, Gos `subtle.ConstantTimeCompare`, .NETs `CryptographicOperations.FixedTimeEquals`.

In der Praxis macht das aber **das Token**: `C_Login` mit falscher PIN nimmt auf realen HSMs eine kuenstliche Verzoegerung (typisch 1-3 Sekunden) — Brute-Force ist damit selbst bei 4-stelliger PIN nach Tagen sinnlos.

## JCA-Limitierung

JCA hat `KeyStore.LoadStoreParameter` und `KeyStore.PasswordProtection`, aber `C_SetPIN` und `C_InitPIN` sind ueber den `Provider`-API **nicht** sauber erreichbar. SunPKCS11 hat interne Klassen (`sun.security.pkcs11.SunPKCS11.changePassword` etc.), aber das ist nicht-portabler JDK-Internalcode.

Praktischer Workaround: ein Hilfs-Tool in Bash, Go oder C# rufen, das via PKCS#11 die PIN-Verwaltung uebernimmt. Java-Anwendung wartet einfach, bis der User-PIN gueltig ist.

Konsequenz fuers Lab: **keine Java/Kotlin-PIN-Demo**. Die Modul-Demos decken Bash, Go und C# ab.

## Eigenexperiment

- Aendere im `60-pin-change.sh` die zwischenliegende PIN `555444` auf eine ungueltige (z.B. `12`, kuerzer als `pin min=4`). Beobachte den Fehler `CKR_PIN_LEN_RANGE`.
- Schalte im Go-Demo den Cleanup-Block ab (Step 5). Nach dem Run ist die User-PIN auf `222333`. Mach `make pin-info` — und stell die PIN dann per `make pin-recovery` (oder manuell) zurueck.
- Versuche eine SO-PIN-Aenderung: `pkcs11-tool --change-pin --login --login-type so --so-pin 1234 --new-pin 9999` — und wieder zurueck. Niemals (!) die SO-PIN absichtlich falsch eingeben, ohne den Recovery-Pfad zu kennen.

Strukturierte Aufgaben in [`exercises/15-pin-management.md`](../exercises/15-pin-management.md).
