# Changelog

## 0.2.0 - 2026-05-28

### Hinzugefügt
- Kapitel 11: ECDSA und RSA-PSS mit JCA-Mapping.
- Skript `08-import-cert.sh`: Self-Signed-Cert via OpenSSL `pkcs11`-Engine, Import als Token-Objekt.
- Skripte `09-generate-ec.sh`, `10-sign-ec.sh`, `11-verify-ec.sh`, `12-sign-pss.sh`.
- Mechanism-Mapping-Tabelle in `08-debugging.md` (`CKM_*` ↔ `pkcs11-tool` ↔ OpenSSL ↔ JCA).
- FIPS- und Cloud-HSM-Abschnitt in der Produktionscheckliste.
- `.gitignore`, `.dockerignore`.

### Geändert
- `Pkcs11Demo.java` schlägt jetzt hart fehl, wenn kein Zertifikat im Token liegt. Der vorherige Wegwerf-KeyPair-Fallback umging das eigentliche Lernziel.
- `make java-demo` ruft `import-cert` als Voraussetzung auf.
- `lab/Dockerfile`: `libengine-pkcs11-openssl` ergänzt.
- `lab/docker-compose.yml`: ENV-Duplikate entfernt, Dockerfile ist Single Source of Truth.
- `04-generate-rsa.sh`: `--usage-decrypt` entfernt, Key ist jetzt sortenrein Signier-Key.
- `06-sign.sh`: kaputter mehrzeiliger `printf` korrigiert.
- `02-lab-setup.md`: JDK-Version auf 17 korrigiert (war Doku-Drift).
- README: `apply.sh`-Hinweis geklärt, v2.40-Spec-Link ergänzt, JDK-17-Referenz.
- Cheatsheet komplett überarbeitet: PSS, EC, Cert-Import, häufige Stolperer.
- Übung 03 mit konkreten Erfolgskriterien.

## 0.1.0 - 2026-05-28

- Initialer PKCS#11-Kurs.
- Docker-Lab mit SoftHSM, OpenSC, OpenSSL und JDK.
- Java-Signaturdemo über SunPKCS11.
- Übungen und Musterlösungen.
- Debugging- und Produktionscheckliste.
