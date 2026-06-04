# 21 — PIN-Management und Lockout

## Bevor du anfaengst — was vermutest du?

> Du bekommst die Aufgabe, in einem Backend-Service "die Lockout-Logik fuer falsche PINs einzubauen". Wie sieht die Loesung aus?

Wahrscheinliche Vermutung: ein Zaehler (`failed_attempts++`) in Redis oder einer DB-Tabelle, nach drei falschen Versuchen ein 15-Minuten-Block, dann Reset. So baut man Passwort-Brute-Force-Schutz fuer normale Login-Endpunkte.

Diese Vermutung ist falsch — und gefaehrlich, wenn man sie auf PKCS#11 anwendet. Bei einem HSM ist der Counter **im Token**, nicht in deinem Service. Wer in der Anwendung einen separaten Counter pflegt und die echte PIN dabei mehrfach ausprobiert, sperrt den Token aus und merkt es erst beim naechsten echten Sign-Versuch. Beim YubiKey nach 3 Versuchen, bei Smartcards nach 3-5, bei Cloud-HSMs nach konfiguriertem Wert — und der Recovery braucht den Security-Officer. Eine PIN-Logik *neben* dem Token ist nicht nur redundant, sondern aktiv schaedlich.

PIN-Management heisst hier: das Token-State **lesen** (Flags), Operations gegen **C_Login**/**C_SetPIN**/**C_InitPIN** disziplinieren, und niemals einen Zaehler in der Anwendung neben dem Token-Counter pflegen.

## Lernziele

Nach diesem Kapitel kannst du:

- die zwei PIN-Rollen `CKU_USER` und `CKU_SO` und ihre Use-Cases unterscheiden.
- den PIN-Status eines Tokens anhand der `CKF_USER_PIN_*`/`CKF_SO_PIN_*`-Flags ablesen.
- `C_SetPIN` (User aendert eigene PIN) und `C_InitPIN` (SO setzt User-PIN) sicher einsetzen.
- die Unterschiede zwischen SoftHSM (kein echter Lockout), Smartcards (3 Versuche) und Cloud-HSMs (mit Konfiguration) einordnen.
- **(Bloom 5 — evaluate)** entscheiden, welcher Recovery-Pfad (SO-Reset, Vendor-Werks-Reset, Operator-Eingriff bei BouncyHsm) fuer eine konkrete Token-Klasse angemessen ist — und warum eine Anwendung **niemals** einen eigenen Retry-Counter neben dem Token-Counter pflegen darf.

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

### BouncyHsm als Alternative

[BouncyHsm](https://github.com/harrison314/BouncyHsm) ist ein in C# geschriebener PKCS#11-Software-HSM, der explizit als Entwickler-freundlicher SoftHSM-Ersatz positioniert ist (PKCS#11 2.40/3.1/3.2, Web-UI, REST-API). Fuer das PIN-Thema interessant: BouncyHsm **hat** das Datenmodell fuer `CKF_USER_PIN_LOCKED` (`Token.IsUserPinLocked`-Property, korrekt propagiert in `GetTokenInfoHandler`), erhoeht aber im `LoginHandler` ebenfalls **keinen Retry-Counter**. Das Locked-Flag wird stattdessen **operator-driven** ueber die Web-UI bzw. REST-API gesetzt — gedacht zum deterministischen Testen, wie sich eine Anwendung gegenueber einem gelockten Token verhaelt.

Praktischer Unterschied:

| | SoftHSM 2.6 | BouncyHsm | Reale HSMs |
|---|---|---|---|
| `CKF_USER_PIN_COUNT_LOW` automatisch | ja | nein (im Login-Pfad) | ja |
| `CKF_USER_PIN_LOCKED` Datenmodell | fehlt | vorhanden, manuell setzbar | vorhanden, counter-driven |
| Auto-Lockout nach N Fehlversuchen | nein | nein | ja (konfigurierbar) |

Wer eine Anwendung gegen einen **gelockten** Token-Status testen will (ohne ein echtes HSM zu brauchen), ist mit BouncyHsm besser bedient: einmal in der Web-UI "PIN lock" anklicken, und der Token meldet ab sofort `CKF_USER_PIN_LOCKED` und `CKR_PIN_LOCKED`. Wer das **automatische** Hochzaehlen beim Login testen will, braucht reale HSM-Hardware oder ein Cloud-HSM.

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

## Selbsttest

<details>
<summary>1. Was bedeutet <code>CKF_USER_PIN_FINAL_TRY</code>, und welche Aktion ist <em>verboten</em>, sobald die Anwendung dieses Flag sieht?</summary>

Nur noch ein Versuch bis Lockout. Verboten: automatisierter Retry-Versuch ("vielleicht klappt es ja"). Die Anwendung muss den Anwender warnen und auf manuelle PIN-Eingabe warten. Ein automatischer Retry mit falscher PIN sperrt den Token, und der Recovery braucht den SO.
</details>

<details>
<summary>2. Welcher Recovery-Pfad ist verfuegbar, wenn <code>CKF_USER_PIN_LOCKED</code> gesetzt ist? Welcher, wenn <code>CKF_SO_PIN_LOCKED</code>?</summary>

`CKF_USER_PIN_LOCKED`: SO meldet sich an und ruft `C_InitPIN(session, neue_pin)` — die User-PIN wird neu gesetzt, der Counter zurueckgesetzt. `CKF_SO_PIN_LOCKED`: ueber PKCS#11 kein Recovery moeglich. Bei Smartcards typisch wegwerfen; bei Enterprise-HSMs Hersteller-spezifische Recovery-Pfade (M-of-N-Quorum, Cluster-Resync, Werks-Reset).
</details>

<details>
<summary>3. Warum darf eine Anwendung niemals einen eigenen <code>failed_attempts</code>-Counter neben dem Token-Counter pflegen?</summary>

Doppelte Counter sind nicht synchronisierbar. Die Anwendung weiss nicht, ob das Token gerade einen Versuch gezaehlt hat (etwa weil ein anderer Prozess die PIN probiert hat). Wer pro Anwendung einen Counter pflegt und die PIN testweise validiert, schickt echte PIN-Versuche an das Token und verbraucht dessen Counter. Ergebnis: der Token sperrt sich, ohne dass die Anwendung etwas davon bemerkt — bis der naechste echte Sign-Versuch scheitert.
</details>

<details>
<summary>4. <strong>(evaluate)</strong> Du sollst PIN-Rotation alle 90 Tage fuer einen Signing-Service einrichten, der 24/7 laeuft. Zwei Vorschlaege: (A) Cronjob ruft <code>C_SetPIN(alt, neu)</code> auf das laufende Token; Service liest die neue PIN beim naechsten Restart. (B) Schwesterprozess macht <code>C_SetPIN</code> + <code>kill -HUP</code> an den Service; Service hat einen SIGHUP-Handler, der die PIN aus dem Vault neu liest und den KeyStore reinitialisiert. Welcher Vorschlag gewinnt — und welcher der drei Faktoren (Downtime, Lockout-Risiko, Audit-Sichtbarkeit) ist im Kontext "24/7 Signing" der entscheidende?</summary>

Vorschlag **B** gewinnt. Entscheidender Faktor ist das **Lockout-Risiko**, nicht die Downtime. Bei (A) hat das Token zwischen "Cronjob hat PIN geaendert" und "Service-Restart" eine alte PIN im Memory — jeder Sign-Versuch schickt die alte PIN, das Token reagiert mit `CKR_PIN_INCORRECT` und erhoeht den Failure-Counter. Bei 24/7-Last und ein paar tausend Requests pro Minute ist `CKF_USER_PIN_LOCKED` in Minuten erreicht — das ist exakt das Szenario, das Kap. 21 als "Anwendung schickt echte PIN-Versuche, ohne es zu wissen" beschreibt. (B) macht den Wechsel atomar aus Token-Sicht: PIN-Change und Service-Reinitialisierung sind eine zusammenhaengende Operation, der Service hat keine Phase, in der er die alte PIN gegen das Token wirft. Downtime ist bei beiden Patterns minimal (Sekundenbereich), Audit-Sichtbarkeit ist bei (B) sogar besser, weil der SIGHUP-Pfad in den Service-Logs als `pin.rotate`-Event auftaucht.
</details>
