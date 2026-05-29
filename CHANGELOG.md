# Changelog

## 0.3.0 - 2026-05-29

### Hinzugefügt
- Kapitel 09: Session-Pooling und Threading-Modelle pro Stack (SunPKCS11, miekg/pkcs11, Pkcs11Interop) mit Java- und Go-Skizzen.
- Kapitel 08: `pkcs11-spy`-Beispiel-Log mit Mini-Trace einer Signatur, plus Lehrnotizen zu `CKA_ID`-Encoding und PIN-Leak.
- Kapitel 11: EC-Kurven-Vergleichstabelle (P-256/384/521, secp256k1, Brainpool, Ed25519/448) inkl. HSM-Verfuegbarkeit.
- Kapitel 10: Audit-Log-Schema (JSON Lines) inkl. Felder, Regeln und Beispielen fuer Erfolgs- und Fehler-Events.
- Kapitel 07: Konkretes Java/Micronaut-Skelett (Configuration/Factory/Service/Controller) statt nur Architektur-Bullets.
- Kapitel 05: CSR-Workflow als Code-Block fuer die Produktionsalternative.
- README: Hinweis auf `apply.sh` und auf die Reihenfolgen-Diskrepanz zwischen Dateinummern und Lernpfad.

### Geändert
- PSS-Mechanismen vereinheitlicht: `08-debugging.md` und `11-ec-und-pss.md` listen jetzt `CKM_RSA_PKCS_PSS` (Anwendung hasht) **und** `CKM_SHA256_RSA_PKCS_PSS` (Token hasht); Cheatsheet zeigt die im Lab tatsaechlich benutzte Variante.
- ECDSA: `11-ec-und-pss.md` erklaert, warum das Lab `CKM_ECDSA` mit applikationsseitigem SHA-256 benutzt (SoftHSM v2 listet nur `CKM_ECDSA`), und zeigt die `ECDSA-SHA256`-Variante fuer produktive HSMs.
- `05-zertifikate.md`: Schritt 1 explizit als "Self-Signed-Erzeugung" formuliert (Skript ist self-signed, nicht CSR).
- CHANGELOG-Eintrag der 0.2.0 von "JDK-17-Referenz" auf "JDK-21-Referenz" korrigiert (Drift gegen Dockerfile und README).
- `lab/csharp/Pkcs11Demo/Program.cs`: PIN-Bytes werden nach `Login` per `Array.Clear` getilgt, analog zu Java/Kotlin.
- `lab/Dockerfile`: `chmod 0777` als Lab-only annotiert.
- `lab/scripts/06-sign.sh`: Hinweis, dass Pubkey-Read ohne Login auf produktiven HSMs nicht garantiert ist.
- `course/06-java-sunpkcs11.md`: Verweist explizit auf den Lab-Disclaimer in `softhsm.cfg`.

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
- README: `apply.sh`-Hinweis geklärt, v2.40-Spec-Link ergänzt, JDK-21-Referenz.
- Cheatsheet komplett überarbeitet: PSS, EC, Cert-Import, häufige Stolperer.
- Übung 03 mit konkreten Erfolgskriterien.

## 0.1.0 - 2026-05-28

- Initialer PKCS#11-Kurs.
- Docker-Lab mit SoftHSM, OpenSC, OpenSSL und JDK.
- Java-Signaturdemo über SunPKCS11.
- Übungen und Musterlösungen.
- Debugging- und Produktionscheckliste.
