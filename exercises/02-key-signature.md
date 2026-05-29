# Übung 02 — Key erzeugen und signieren

## Aufgabe

Voraussetzung: Der Token `dev-token` ist initialisiert (`make init-token`).

1. Erzeuge ein RSA-Keypair mit ID `01` und Label `signing-key`.
2. Liste die Objekte.
3. Signiere `hello pkcs11`.
4. Verifiziere die Signatur mit OpenSSL.

## Bonus

Ändere die Eingabedatei nach dem Signieren. Die Verifikation muss fehlschlagen.
