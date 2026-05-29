# Uebung 05 - Kotlin ueber SunPKCS11

## Ziel

Du verwendest aus Kotlin denselben Java-Sicherheitsstack wie in der Java-Demo und erkennst, welche Teile sprachunabhaengig JCA/JCE sind.

## Vorbereitung

```bash
make init-token
make gen-rsa
make import-cert
```

## Aufgabe

1. Starte die Kotlin-Demo:
   ```bash
   make kotlin-demo
   ```
2. Lies `lab/kotlin/pkcs11-demo/src/main/kotlin/dev/course/pkcs11/KotlinPkcs11Demo.kt`.
3. Vergleiche den Ablauf mit der Java-Demo:
   - Provider konfigurieren
   - `KeyStore` laden
   - Alias finden
   - Private Key holen
   - Signatur erzeugen
   - mit Public Key aus Zertifikat verifizieren

## Erwartete Ausgabe

- Der Providername ist `SunPKCS11-SoftHSM`.
- Der Alias `signing-key` ist sichtbar.
- Die Verifikation liefert `true`.

## Fehlerfall

`PKCS11_USER_PIN=000000 make kotlin-demo` wuerde schon in der Dependency-Kette `import-cert -> gen-rsa -> init-token` aussteigen. Damit der Fehler in der Kotlin-Demo selbst sichtbar wird, Vorstufe mit echter PIN laufen lassen und nur die Demo umschalten:

```bash
make init-token gen-rsa import-cert
docker compose -f lab/docker-compose.yml run --rm \
  -e PKCS11_USER_PIN=000000 \
  pkcs11-kotlin bash -lc 'cd lab/kotlin/pkcs11-demo && ./gradlew --quiet --no-daemon run'
```

(Im Devcontainer: `make init-token gen-rsa import-cert && PKCS11_USER_PIN=000000 (cd lab/kotlin/pkcs11-demo && ./gradlew --quiet --no-daemon run)`.)

Erwartet: Der `reportFailure`-Helper druckt eine `ProviderException`-Kette mit `CKR_PIN_INCORRECT` beim `KeyStore.load`.

Optional: Loesche das Zertifikat, aber lasse den privaten Key im Token. Erwartet: Der Alias ist nicht mehr als Private-Key-Alias sichtbar.

## Reflexionsfragen

- Welche Unterschiede zwischen Java und Kotlin sind fuer PKCS#11 wirklich relevant?
- Warum bleibt das Zertifikat auch bei Kotlin entscheidend?

## Musterloesung

Siehe `solutions/05-kotlin.md`.
