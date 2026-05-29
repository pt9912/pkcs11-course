# PKCS#11-Kurs fuer Entwickler

Ein praxisnaher Kurs fuer Entwickler, die PKCS#11 wirklich benutzen muessen: lokale HSM-Simulation mit SoftHSM, Objekt- und Mechanism-Debugging mit OpenSC, Signaturen mit OpenSSL und Sprachbindings fuer Java, Go, Kotlin und C#.

Der Kurs ist absichtlich hands-on. Du liest ein kurzes Kapitel, fuehrst das passende Make-Target aus, provozierst einen Fehler und vergleichst dein Ergebnis mit der Musterloesung.

## Voraussetzungen

- Linux
- Docker und Docker Compose fuer den Betrieb ausserhalb eines Devcontainers
- Optional: VS Code Dev Containers

> `apply.sh` ist ein Hilfsskript, das den Kurs in ein Zielverzeichnis kopiert (`./apply.sh pkcs11-course`). Wer den Kurs bereits direkt eingecheckt hat, braucht es nicht.

## Schnellstart ausserhalb des Devcontainers

```bash
make build
make init-token
make gen-rsa
make sign
make verify
```

Die Make-Targets starten dann automatisch die passenden Docker-Compose-Services.

## Schnellstart im Devcontainer

Der Devcontainer nutzt den Service `pkcs11-dev`. Dort sind SoftHSM, OpenSC, OpenSSL, JDK 21, Gradle, Go und .NET bereits installiert. Docker wird im Devcontainer fuer die Kurs-Targets nicht benoetigt.

```bash
make list-slots
make init-token
make gen-rsa
make java-demo
make go-demo
make kotlin-demo
make csharp-demo
```

Technisch setzt `.devcontainer/devcontainer.json` die Variable `PKCS11_IN_DEVCONTAINER=1`. Das `Makefile` fuehrt die Lab-Skripte dann direkt aus. Ausserhalb des Devcontainers bleibt das Docker-Compose-Verhalten aktiv.

## Wichtige Make-Targets

| Target | Zweck |
|---|---|
| `make build` | Docker-Images bauen, ausserhalb des Devcontainers |
| `make shell` | Lab-Shell oeffnen |
| `make restore` | C#-NuGet-Abhaengigkeiten fuer Editor/Language Server wiederherstellen |
| `make init-token` | SoftHSM-Token `dev-token` initialisieren |
| `make list-slots` | Slots und Token anzeigen |
| `make list-mechanisms` | unterstuetzte Mechanismen anzeigen |
| `make gen-rsa` | RSA-Keypair `signing-key` mit `CKA_ID=01` erzeugen |
| `make sign` / `make verify` | RSA-PKCS#1-v1.5 signieren und mit OpenSSL pruefen |
| `make import-cert` | Zertifikat mit gleicher `CKA_ID` importieren |
| `make java-demo` | Java/SunPKCS11-Demo ausfuehren |
| `make go-demo` | Go-Demo mit `github.com/miekg/pkcs11` ausfuehren |
| `make kotlin-demo` | Kotlin/JVM-Demo mit SunPKCS11 ausfuehren |
| `make csharp-demo` | C#-Demo mit Pkcs11Interop ausfuehren |
| `make gen-ec` / `make sign-ec` / `make verify-ec` | ECDSA-Pfad |
| `make sign-pss` | RSA-PSS-Pfad |
| `make clean` | generierte Lab-Artefakte entfernen |

## Kursstruktur

Die folgende Tabelle listet die Kapitel in Dateinummern-Reihenfolge. Der **didaktisch empfohlene Lernpfad** weicht davon ab (Debugging und ECDSA/PSS werden frueher gezogen, Service-Integration und Produktionscheckliste spaeter). Siehe `course/00-kursuebersicht.md` fuer die Reihenfolge, die der Kurs zum Lesen vorgibt.

| Datei | Inhalt | Uebung |
|---|---|---|
| `course/00-kursuebersicht.md` | Aufbau, Ziele, Lernpfad | alle |
| `course/01-grundlagen.md` | Slots, Tokens, Sessions, Objects, Mechanisms | `exercises/01-token.md` |
| `course/02-lab-setup.md` | Docker, Devcontainer, SoftHSM, OpenSC | `exercises/01-token.md` |
| `course/03-token-und-objekte.md` | Token initialisieren, Keys erzeugen, Objekte lesen | `exercises/01-token.md`, `exercises/02-key-signature.md` |
| `course/04-signieren-und-verifizieren.md` | Signaturen mit `pkcs11-tool` und OpenSSL | `exercises/02-key-signature.md` |
| `course/05-zertifikate.md` | Zertifikate erzeugen, importieren und mit Keys koppeln | `exercises/03-java.md` |
| `course/06-java-sunpkcs11.md` | Java JCA/JCE ueber SunPKCS11 | `exercises/03-java.md` |
| `course/07-service-integration.md` | Signatur-Service als Architekturuebung | `course/10-abschlussprojekt.md` |
| `course/08-debugging.md` | CKR-Fehler, Mechanism-Mapping, Slots, PINs | alle |
| `course/09-production-checkliste.md` | Was bei echten HSMs anders wird | Abschlussprojekt |
| `course/10-abschlussprojekt.md` | vollstaendiges Abschlussprojekt | Abschlussprojekt |
| `course/11-ec-und-pss.md` | ECDSA und RSA-PSS in der Praxis | optionale Erweiterung |
| `course/12-sprachbindings.md` | Java, Go, Kotlin und C# im Vergleich | `exercises/03-java.md` bis `06-csharp.md` |

Weitere Materialien:

- `exercises/` - Aufgaben im einheitlichen Format
- `solutions/` - Musterloesungen und erwartete Ergebnisse
- `lab/` - ausfuehrbares Docker-/Devcontainer-Lab
- `docs/cheatsheet.md` - schneller Spickzettel
- `docs/api.md` - Leitfaden zur PKCS#11-API

## Arbeitsweise

1. Kapitel lesen.
2. Genannte Make-Targets ausfuehren.
3. Uebung bearbeiten.
4. Mindestens einen Fehlerfall absichtlich ausloesen.
5. Ergebnis mit `solutions/` vergleichen.

Wenn ein `CKR_*`-Fehler erscheint, ist das kein Nebenthema. Genau dort lernt man PKCS#11.

## Referenzen

- OASIS PKCS#11 Base Specification v2.40: https://docs.oasis-open.org/pkcs11/pkcs11-base/v2.40/pkcs11-base-v2.40.html
- OASIS PKCS#11 Specification v3.2: https://docs.oasis-open.org/pkcs11/pkcs11-spec/v3.2/pkcs11-spec-v3.2.html
- SoftHSM v2 Dokumentation: https://opendnssec.readthedocs.io/en/latest/softhsm2/
- OpenSC `pkcs11-tool`: https://github.com/OpenSC/OpenSC/wiki/Using-pkcs11-tool-and-OpenSSL
- Oracle PKCS#11 Reference Guide: https://docs.oracle.com/en/java/javase/17/security/pkcs11-reference-guide1.html
- RFC 7512 PKCS#11 URI: https://www.rfc-editor.org/rfc/rfc7512

## Harte Wahrheit

PKCS#11 versteht man nicht durch API-Namen. Man versteht es durch Slots, wandernde Token, falsche PINs, fehlende Zertifikate, unpassende Mechanisms, Provider-Mapping und Signaturen, die nur wegen Encoding oder Padding nicht verifizieren.
