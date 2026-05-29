# PKCS#11-Kurs für Entwickler

Ein kompletter, praxisnaher Kurs für Linux/Docker-Entwickler. Ziel: Du lernst PKCS#11 nicht nur theoretisch, sondern arbeitest mit SoftHSM, OpenSC, OpenSSL, Java/JCA und einem kleinen Signatur-Service.

## Voraussetzungen

- Linux
- Docker und Docker Compose
- Optional: VS Code Dev Containers

## Schnellstart

Wenn du das Repo bereits ausgecheckt hast, direkt:

```bash
make build
make shell
```

`apply.sh` ist nur für den Bootstrap aus einem Tarball gedacht, der Kurs ohne Repo entpacken will. Im normalen Git-Workflow ignorieren.

Im Container:

```bash
make init-token
make list-slots
make gen-rsa
make list-objects
make sign
make verify
make import-cert
make java-demo
make go-demo
make kotlin-demo
make csharp-demo

# Optional: moderne Mechanismen
make gen-ec
make sign-ec
make verify-ec
make sign-pss
```

## Kursstruktur

- `course/00-kursuebersicht.md` — Aufbau, Ziele, Lernpfad
- `course/01-grundlagen.md` — Slots, Tokens, Sessions, Objects, Mechanisms
- `course/02-lab-setup.md` — Docker, SoftHSM, OpenSC
- `course/03-token-und-objekte.md` — Token initialisieren, Keys erzeugen, Objekte lesen
- `course/04-signieren-und-verifizieren.md` — Signaturen mit pkcs11-tool und OpenSSL
- `course/05-zertifikate.md` — Zertifikate erzeugen, importieren und mit Keys koppeln
- `course/06-java-sunpkcs11.md` — Java JCA/JCE über SunPKCS11
- `course/07-service-integration.md` — Signatur-Service als Architekturübung
- `course/08-debugging.md` — CKR-Fehler, Mechanism-Mapping, Slots, PINs
- `course/09-production-checkliste.md` — Was bei echten HSMs anders wird
- `course/10-abschlussprojekt.md` — vollständiges Abschlussprojekt
- `course/11-ec-und-pss.md` — ECDSA und RSA-PSS in der Praxis
- `exercises/` — Aufgaben
- `solutions/` — Musterlösungen
- `lab/` — ausführbares Docker-Lab
- `docs/cheatsheet.md` — schneller Spickzettel
- `docs/api.md` — Leitfaden zur PKCS#11-API (native C-API, pkcs11-tool, OpenSSL, Java)

## Referenzen

- OASIS PKCS#11 Base Specification v2.40 (weit verbreitet): https://docs.oasis-open.org/pkcs11/pkcs11-base/v2.40/pkcs11-base-v2.40.html
- OASIS PKCS#11 Specification v3.2 (aktuell): https://docs.oasis-open.org/pkcs11/pkcs11-spec/v3.2/pkcs11-spec-v3.2.html
- SoftHSM v2 Dokumentation: https://opendnssec.readthedocs.io/en/latest/softhsm2/
- OpenSC pkcs11-tool: https://github.com/OpenSC/OpenSC/wiki/Using-pkcs11-tool-and-OpenSSL
- Oracle PKCS#11 Reference Guide: https://docs.oracle.com/en/java/javase/17/security/pkcs11-reference-guide1.html
- RFC 7512 PKCS#11 URI: https://www.rfc-editor.org/rfc/rfc7512

## Harte Wahrheit

PKCS#11 ist kein Thema, das man durch Lesen wirklich versteht. Die Spezifikation ist wichtig, aber das Verständnis kommt durch Slots, falsche PINs, nicht sichtbare Objekte, unpassende Mechanisms und kaputte Signaturen. Dieser Kurs zwingt dich genau durch diese Fälle.
