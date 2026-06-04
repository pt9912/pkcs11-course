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

Drei Stufen — eine Recall-, eine Analyse-, eine Evaluate-Frage:

1. **(Recall)** Warum ist `--token-label dev-token` robuster als ein fester Slot?
2. **(Analyse)** Welche Information brauchst du in einer Anwendung, um den richtigen Token zu finden — und welche zwei API-Calls (`C_*`) waeren noetig, um sie aus einem PKCS#11-Modul zu bekommen?
3. **(Evaluate)** Du sollst die Slot-Auswahl in einer produktiven Multi-HSM-Umgebung (5 HSMs, je 2 Tokens) entwerfen: Token-Label, PKCS#11-URI (RFC 7512) oder Konfigurations-Mapping ueber Slot-Serien? Welcher Faktor (Inventur-Drift, Recovery-Lesbarkeit, Cluster-Failover) bricht eine Slot-Index-Loesung als erstes?

## Musterloesung

Siehe `solutions/01-token.md`.
