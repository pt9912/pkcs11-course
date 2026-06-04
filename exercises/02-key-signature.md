# Uebung 02 - Key erzeugen und signieren

## Ziel

Du erzeugst ein RSA-Keypair im Token, signierst Daten ueber PKCS#11 und verifizierst die Signatur ausserhalb des Tokens mit OpenSSL.

## Vorbereitung

```bash
make init-token
```

## Aufgabe

1. Erzeuge das RSA-Keypair `signing-key` mit `CKA_ID=01`:
   ```bash
   make gen-rsa
   ```
2. Liste die Token-Objekte:
   ```bash
   make list-objects
   ```
3. Signiere Testdaten:
   ```bash
   make sign
   ```
4. Verifiziere die Signatur:
   ```bash
   make verify
   ```

## Erwartete Ausgabe

- `make list-objects` zeigt ein Public-Key-Objekt und ein Private-Key-Objekt.
- `make verify` endet mit `Verified OK`.
- `lab/work/data.sig` und `lab/work/public.der` wurden erzeugt.

## Fehlerfall

Aendere nach `make sign` die Datei `lab/work/data.txt` und starte danach:

```bash
make verify
```

Erwartet: Die Verifikation schlaegt fehl, weil die Signatur zu den urspruenglichen Daten gehoert.

## Reflexionsfragen

Drei Stufen — eine Recall-, eine Analyse-, eine Evaluate-Frage:

1. **(Recall)** Warum kann OpenSSL die Signatur ohne privaten Key verifizieren?
2. **(Analyse)** Schau in `lab/scripts/06-sign.sh`, welcher `--mechanism` aufgerufen wird, und vergleiche mit `lab/scripts/07-verify.sh`. Wo passiert das Hashing — im Token, in OpenSSL, oder gar nicht? Begruende anhand der Mechanism-Familien-Tabelle aus [Kap. 04](../course/04-signieren-und-verifizieren.md).
3. **(Evaluate)** Ein Teamkollege schlaegt vor, `make sign` umzustellen, sodass `pkcs11-tool --mechanism RSA-PKCS` (ohne SHA-Praefix) genutzt wird, damit der Token weniger Arbeit hat. Welche eine Frage stellst du, bevor du zustimmst — und welcher Lab-Fehler waere die wahrscheinlichste Konsequenz?

## Musterloesung

Siehe `solutions/02-key-signature.md`.
