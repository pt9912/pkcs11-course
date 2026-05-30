# Uebung 15 - PIN-Management und Lockout

## Ziel

Du lernst, den PIN-Status eines Tokens zu lesen, die User-PIN sicher zu aendern, das SO-Recovery-Verfahren auszufuehren — und du verstehst, warum SoftHSM bei "Lockout-Tests" nichts wirklich locked.

## Vorbereitung

```bash
make init-token
```

## Aufgabe 1 — PIN-Status lesen

```bash
make pin-info
```

Erwartete Ausgabe (frischer Token ohne Fehlversuche):

```text
Token:        dev-token
Roh-Flags:    login required, rng, token initialized, PIN initialized, other flags=0x20

--- PIN-Status (User) ---
  CKF_USER_PIN_COUNT_LOW:    nein
  CKF_USER_PIN_FINAL_TRY:    nein
  CKF_USER_PIN_LOCKED:       nein
  CKF_USER_PIN_TO_BE_CHANGED: nein
...
```

## Aufgabe 2 — User-PIN aendern und zurueck

```bash
make pin-change
```

Erwartet: zwei "PIN successfully changed"-Zeilen, Login-Tests jeweils mit 16 Objekten. Am Ende ist die PIN wieder `987654`.

## Aufgabe 3 — Flag-Transition beobachten

```bash
make pin-info
docker compose -f lab/docker-compose.yml run --rm pkcs11-lab \
  pkcs11-tool --module /usr/lib/softhsm/libsofthsm2.so \
  --token-label dev-token --login --pin 000000 --list-objects
make pin-info
```

Erwartet: zwischen den beiden `pin-info`-Aufrufen wechselt `CKF_USER_PIN_COUNT_LOW` von "nein" auf "JA". Nach einer **erfolgreichen** Anmeldung (z.B. `make sign`) ist das Flag wieder weg — der Counter wird beim korrekten Login zurueckgesetzt.

## Aufgabe 4 — SO-Recovery durchspielen

```bash
make pin-recovery
```

Erwartet:
- 3x `CKR_PIN_INCORRECT`
- `Flags: ... user PIN count low ...`
- Hinweis auf SoftHSM-Realitaet
- `User PIN successfully initialized` (zwei Mal — einmal als Recovery, einmal als Cleanup)

## Aufgabe 5 — Sprach-Demo

```bash
make go-pin-demo
make csharp-pin-demo
```

Beide zeigen denselben Lifecycle wie der Bash-Pfad, plus dekodierte Token-Flags als String-Liste.

## Aufgabe 6 — Bonus: PIN-Laengen-Validation

In `60-pin-change.sh` die TMP_PIN auf `12` setzen (kuerzer als `pin min=4`). Erwartet: `CKR_PIN_LEN_RANGE`. Lesson: realistische Anwendungen pruefen Min/Max aus `slot.token_info.pin_min/max` **bevor** sie `C_SetPIN` aufrufen.

## Reflexionsfragen

- Was unterscheidet `C_SetPIN` von `C_InitPIN` semantisch und in der Berechtigung?
- Warum kann eine Anwendung das `CKF_USER_PIN_FINAL_TRY`-Flag nicht ignorieren, ohne den User in den Lockout zu schicken?
- Wenn die SO-PIN deines produktiven HSMs verloren geht — was sind realistische Recovery-Optionen?
- Wieso ist es selten ein Problem, wenn ein Brute-Force-Angreifer alle 4-stelligen PINs durchprobieren will, sobald das Token "kuenstliche Verzoegerung" implementiert?

## Musterloesung

Siehe `solutions/15-pin-management.md`.
