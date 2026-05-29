# Übung 05 — Kotlin über SunPKCS11

## Aufgabe

Voraussetzung: Der Token `dev-token`, der RSA-Key `signing-key` und ein Zertifikat mit derselben `CKA_ID=01` existieren (`make init-token`, `make gen-rsa`, `make import-cert`).

Kotlin-Container starten (enthält `kotlinc` zusätzlich zu JDK/Maven):

```bash
docker compose -f lab/docker-compose.yml run --rm pkcs11-kotlin bash
# alternativ pkcs11-dev mit allen Sprachen
```

1. Erstelle ein kleines Kotlin/JVM-Programm, das den Java-Provider `SunPKCS11` nutzt.
2. Lade die bestehende Config `lab/java/pkcs11-demo/src/main/resources/softhsm.cfg`.
3. Registriere den Provider über `Security.getProvider("SunPKCS11").configure(...)`.
4. Öffne `KeyStore.getInstance("PKCS11", provider)` und lade ihn mit `PKCS11_USER_PIN`.
5. Finde den Alias `signing-key`, lies den privaten Key und signiere `hello from kotlin pkcs11` mit `SHA256withRSA`.
6. Verifiziere die Signatur mit dem Public Key aus dem Zertifikat.

## Erwartung

- Der Providername ist `SunPKCS11-SoftHSM`.
- Der Alias `signing-key` ist als Key-Entry sichtbar.
- Die Verifikation liefert `true`.

## Fehler erzwingen

- Lösche das Zertifikat, aber lasse den privaten Key im Token. Erwartet: Der Alias ist nicht mehr als Private-Key-Alias sichtbar.
- Nutze eine falsche PIN. Erwartet: Login-Fehler beim `KeyStore.load`.
