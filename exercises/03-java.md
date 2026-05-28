# Übung 03 — Java über SunPKCS11

## Aufgabe

1. Token initialisieren, RSA-Key erzeugen, Zertifikat importieren:
   ```bash
   make init-token
   make gen-rsa
   make import-cert
   ```
2. Java-Demo starten:
   ```bash
   make java-demo
   ```
3. Prüfe:
   - Der Output enthält genau einen Alias mit `key=true cert=true`.
   - `Verifikation: true` steht am Ende.
   - Exit-Code ist `0` (`echo $?` direkt nach dem Lauf).

## Fehler erzwingen

- Setze in `softhsm.cfg` einen falschen `library`-Pfad. Erwartet: klare Fehlermeldung beim Provider-Load, nicht erst beim Signieren.
- Lösche das Zertifikat (`pkcs11-tool ... --delete-object --type cert --id 01`) und starte erneut. Erwartet: Exit-Code `2` mit Hinweis auf `make import-cert`.
- Ändere die PIN in der Umgebungsvariable. Erwartet: `CKR_PIN_INCORRECT` beim KeyStore-Load.

## Bonus

- Lass die Java-Demo zusätzlich mit dem EC-Key signieren. Welche Änderungen am Java-Code sind nötig?
