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
   - Der Output enthält mindestens einen Alias mit `key=true cert=true` (genau einen, solange du keinen zweiten Key + Cert importierst).
   - `Verifikation: true` steht am Ende.
   - Exit-Code ist `0` (`echo $?` direkt nach dem Lauf).

## Fehler erzwingen

- Setze in `softhsm.cfg` einen falschen `library`-Pfad. Erwartet: klare Fehlermeldung beim Provider-Load, nicht erst beim Signieren.
- Lösche das Zertifikat und starte die Java-Demo danach direkt, ohne `make java-demo`, weil dieses Target das Zertifikat automatisch neu importiert:
  ```bash
  docker compose -f lab/docker-compose.yml run --rm pkcs11-lab \
    bash -lc 'pkcs11-tool --module "$PKCS11_MODULE" --login --pin "$PKCS11_USER_PIN" --token-label "$PKCS11_TOKEN_LABEL" --delete-object --type cert --id 01'

  docker compose -f lab/docker-compose.yml run --rm pkcs11-lab \
    bash -lc 'cd lab/java/pkcs11-demo && gradle --quiet run'
  ```
  Erwartet: Exit-Code `2` mit Hinweis auf `make import-cert`.
- Starte die Java-Demo direkt mit falscher PIN, damit vorher kein `import-cert`-Schritt scheitert:
  ```bash
  docker compose -f lab/docker-compose.yml run --rm -e PKCS11_USER_PIN=000000 pkcs11-lab \
    bash -lc 'cd lab/java/pkcs11-demo && gradle --quiet run'
  ```
  Erwartet: `CKR_PIN_INCORRECT` beim KeyStore-Load.

## Bonus

- Lass die Java-Demo zusätzlich mit dem EC-Key signieren. Welche Änderungen am Java-Code sind nötig?
