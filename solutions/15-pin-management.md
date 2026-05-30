# Loesung 15 - PIN-Management und Lockout

## PIN-Info bei frischen Tokens

`make pin-info` zeigt alle vier User-Flags auf "nein" und alle zwei SO-Flags auf "nein". Das ist der Erwartungs-Zustand nach `make init-token`.

## PIN-Change

```text
=== 2) PIN aendern 987654 -> 555444 ===
PIN successfully changed
  Login-Test mit neuer PIN...
  Login mit 555444 OK, 16 Objekte.
=== 3) PIN zurueck 555444 -> 987654 ===
PIN successfully changed
  Login-Test mit Ausgangs-PIN...
  Login mit 987654 OK, 16 Objekte. Ausgangs-State wiederhergestellt.
```

## Flag-Transition

Nach 1 Fehlversuch:

```text
  CKF_USER_PIN_COUNT_LOW:    JA   (mindestens 1 Fehlversuch seit letzter erfolgreicher Login)
```

Nach erfolgreicher Anmeldung (z.B. `make sign`):

```text
  CKF_USER_PIN_COUNT_LOW:    nein
```

Reset-Verhalten: einmal richtig anmelden, alle COUNT_LOW-Counter sind wieder bei null.

## SO-Recovery

`make pin-recovery` lauft sauber durch:
- 3x `CKR_PIN_INCORRECT`
- Flag `user PIN count low` gesetzt
- SO-Login + `C_InitPIN` auf `111111`
- Login mit `111111` OK
- Cleanup: SO-`C_InitPIN` zurueck auf `987654`

## Sprach-Demos

Identische Storyline in Go und C#. Schluesselzeilen:

```text
=== 3) Drei Fehlversuche mit falschem PIN ===
  Versuch 1: pkcs11: 0xA0: CKR_PIN_INCORRECT
  ...
  PIN-Flags gesetzt: CKF_USER_PIN_COUNT_LOW
=== 4) SO setzt User-PIN per C_InitPIN ===
  SO-Init OK, User-PIN ist jetzt 222333
  Login mit 222333 funktioniert — Recovery erfolgreich.
```

## Bonus: PIN-Laenge zu kurz

```text
PIN length is out of range
error: PKCS11 function C_SetPIN failed: rv = CKR_PIN_LEN_RANGE (0xa2)
```

Mit `slot.token_info.pin_min=4` werden 3-stellige PINs abgelehnt. In Produktion: Min/Max aus `C_GetTokenInfo` lesen, Eingabe vorab validieren — sonst kommt der Fehler erst beim `C_SetPIN`-Call, was haesslicher UX ist.

## Antworten zu den Reflexionsfragen

**`C_SetPIN` vs `C_InitPIN`:**
- `C_SetPIN(session, old, new)` braucht die **alte PIN** als Argument und aendert die PIN der **gerade eingeloggten** Rolle. User aendert User-PIN, SO aendert SO-PIN.
- `C_InitPIN(session, new)` kann nur ein **SO** ausfuehren (in SO-Session). Es setzt die **User-PIN** ohne deren alten Wert zu kennen. Genau der Recovery-Hebel — der User hat seine PIN vergessen oder ausgesperrt, der SO setzt ihm einen neuen Initialwert.

**`CKF_USER_PIN_FINAL_TRY` ignorieren:**  
Wenn die Anwendung dem User einen Login-Dialog zeigt, ohne erst die Token-Flags zu pruefen, sieht der User nicht, dass er nur noch EINEN Versuch hat. Tippt er die PIN einmal falsch (Tippfehler), ist die PIN gelockt — `CKR_PIN_LOCKED`, Recovery ueber SO noetig. Eine UX-saubere Anwendung warnt explizit: "Letzter Versuch — bei Falscher PIN wirst du gesperrt!"

**SO-PIN verloren:**  
Drei realistische Optionen, je nach HSM:
1. **Smartcard/USB-Token (YubiKey, etc.)**: Werks-Reset via PUK (wenn man den noch hat) oder das Geraet wegwerfen. Inhalte verloren.
2. **Enterprise-HSM (Thales/Utimaco)**: M-of-N Smartcards, die beim HSM-Setup ausgegeben wurden. Mit dem Quorum laesst sich der SO-PIN zuruecksetzen. Wer kein Quorum hat, fuehrt einen Werks-Reset durch — alle Keys weg.
3. **Cloud-HSM (AWS CloudHSM, Azure Dedicated HSM)**: Cloud-Provider API kann SO-PIN zuruecksetzen, abhaengig vom Account-Owner. Bei "der CTO ist im Urlaub und keine andere Person hat die Berechtigung", muessen Backup-Recovery-Pfade ueber Cloud-IAM gegangen werden.

Lesson: wer einen produktiven HSM hat, MUSS einen dokumentierten SO-PIN-Recovery-Plan haben.

**Brute-Force trotz "billig":**  
Selbst ohne Lockout: eine kuenstliche Verzoegerung von 1 Sekunde pro Fehlversuch macht eine 6-stellige PIN (10^6 Kombinationen) zu einem Brute-Force-Aufwand von 10^6 Sekunden = **11.6 Tagen** — und das ohne Lockout. Mit Lockout nach 3 Versuchen ist Brute-Force gegen ein 4-stelliges PIN-Pad effektiv unmoeglich, weil der Token nach drei Versuchen tot ist. Wer eine Smartcard mit 4-stelliger PIN benutzt, vertraut **nicht** auf PIN-Entropie, sondern auf den Lockout-Mechanismus.
