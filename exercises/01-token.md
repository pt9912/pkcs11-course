# Uebung 01 - Token initialisieren

## Ziel

Du initialisierst den SoftHSM-Token `dev-token` und lernst, warum Anwendungen nicht fest auf Slot `0` vertrauen sollten.

## Vorbereitung

Ausserhalb des Devcontainers:

```bash
make build
```

Im Devcontainer ist kein Build-Schritt notwendig, wenn das Image bereits neu aufgebaut wurde.

## Aufgabe

1. Initialisiere den Token:
   ```bash
   make init-token
   ```
2. Liste die Slots auf:
   ```bash
   make list-slots
   ```
3. Notiere:
   - Slot-ID des initialisierten Tokens
   - Token-Label
   - ob ein weiterer uninitialisierter Slot sichtbar ist

## Erwartete Ausgabe

- Ein Token mit Label `dev-token` ist sichtbar.
- `Initialized` und `User PIN init.` sind aktiv.
- Die Slot-ID muss nicht `0` sein.

## Fehlerfall

Fuehre `make init-token` ein zweites Mal aus. Erwartet: Das Skript erkennt den bestehenden Token und bricht nicht destruktiv ab.

## Reflexionsfragen

- Warum ist `--token-label dev-token` robuster als ein fester Slot?
- Welche Information brauchst du in einer Anwendung, um den richtigen Token zu finden?

## Musterloesung

Siehe `solutions/01-token.md`.
