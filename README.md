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

## Kursstruktur

Die folgende Tabelle listet die Kapitel in Dateinummern-Reihenfolge. Der **didaktisch empfohlene Lernpfad** weicht davon ab (Debugging und ECDSA/PSS werden frueher gezogen, Service-Integration und Produktionscheckliste spaeter). Siehe [`course/00-kursuebersicht.md`](course/00-kursuebersicht.md) fuer die Reihenfolge, die der Kurs zum Lesen vorgibt.

| Datei | Inhalt | Uebung |
|---|---|---|
| [`course/00-kursuebersicht.md`](course/00-kursuebersicht.md) | Aufbau, Ziele, Lernpfad | [alle Uebungen](exercises/) |
| [`course/01-grundlagen.md`](course/01-grundlagen.md) | Slots, Tokens, Sessions, Objects, Mechanisms | [`exercises/01-token.md`](exercises/01-token.md) |
| [`course/02-lab-setup.md`](course/02-lab-setup.md) | Docker, Devcontainer, SoftHSM, OpenSC | [`exercises/01-token.md`](exercises/01-token.md) |
| [`course/03-token-und-objekte.md`](course/03-token-und-objekte.md) | Token initialisieren, Keys erzeugen, Objekte lesen | [`exercises/01-token.md`](exercises/01-token.md), [`exercises/02-key-signature.md`](exercises/02-key-signature.md) |
| [`course/04-signieren-und-verifizieren.md`](course/04-signieren-und-verifizieren.md) | Signaturen mit `pkcs11-tool` und OpenSSL | [`exercises/02-key-signature.md`](exercises/02-key-signature.md) |
| [`course/05-zertifikate.md`](course/05-zertifikate.md) | Zertifikate erzeugen, importieren und mit Keys koppeln | [`exercises/03-java.md`](exercises/03-java.md) |
| [`course/06-java-sunpkcs11.md`](course/06-java-sunpkcs11.md) | Java JCA/JCE ueber SunPKCS11 | [`exercises/03-java.md`](exercises/03-java.md) |
| [`course/07-service-integration.md`](course/07-service-integration.md) | Signatur-Service als Architekturuebung | [`course/10-abschlussprojekt.md`](course/10-abschlussprojekt.md) |
| [`course/08-debugging.md`](course/08-debugging.md) | CKR-Fehler, Mechanism-Mapping, Slots, PINs | [alle Uebungen](exercises/) |
| [`course/09-production-checkliste.md`](course/09-production-checkliste.md) | Was bei echten HSMs anders wird | [`course/10-abschlussprojekt.md`](course/10-abschlussprojekt.md) |
| [`course/10-abschlussprojekt.md`](course/10-abschlussprojekt.md) | vollstaendiges Abschlussprojekt | [`course/10-abschlussprojekt.md`](course/10-abschlussprojekt.md) |
| [`course/11-ec-und-pss.md`](course/11-ec-und-pss.md) | ECDSA und RSA-PSS in der Praxis | optionale Erweiterung |
| [`course/12-sprachbindings.md`](course/12-sprachbindings.md) | Java, Go, Kotlin und C# im Vergleich | [`exercises/03-java.md`](exercises/03-java.md) bis [`exercises/06-csharp.md`](exercises/06-csharp.md) |
| [`course/13-verschluesselung.md`](course/13-verschluesselung.md) | Hybride Verschluesselung mit RSA-OAEP + AES-GCM | [`exercises/07-encrypt.md`](exercises/07-encrypt.md) |
| [`course/14-cms-signatur.md`](course/14-cms-signatur.md) | CMS/PKCS#7-Dokumentsignatur (RFC 5652, detached SignedData) | [`exercises/08-cms.md`](exercises/08-cms.md) |
| [`course/15-streaming.md`](course/15-streaming.md) | Multi-Part-Operationen fuer Grossdateien (Sign + AES-Encrypt) | [`exercises/09-streaming.md`](exercises/09-streaming.md) |
| [`course/16-hmac.md`](course/16-hmac.md) | HMAC, symmetrische Keys (CKK_GENERIC_SECRET), HS256-JWT | [`exercises/10-hmac.md`](exercises/10-hmac.md) |
| [`course/17-session-pooling.md`](course/17-session-pooling.md) | Thread-Safety, Pool-Pattern, C_Login-Lebensdauer, fork-Falle | [`exercises/11-session-pooling.md`](exercises/11-session-pooling.md) |
| [`course/18-tls-mit-hsm.md`](course/18-tls-mit-hsm.md) | nginx mit HSM-Key via openssl pkcs11-engine | [`exercises/12-tls-mit-hsm.md`](exercises/12-tls-mit-hsm.md) |
| [`course/19-ssh-mit-hsm.md`](course/19-ssh-mit-hsm.md) | SSH-Login ueber PKCS11Provider, ssh-agent, Smartcard-Pattern | [`exercises/13-ssh-mit-hsm.md`](exercises/13-ssh-mit-hsm.md) |
| [`course/20-key-wrap.md`](course/20-key-wrap.md) | Backup/Escrow via C_WrapKey, KEK-Policy, CKA_EXTRACTABLE-Gate | [`exercises/14-key-wrap.md`](exercises/14-key-wrap.md) |
| [`course/21-pin-management.md`](course/21-pin-management.md) | PIN-Lifecycle, CKF-Flags, SO-Recovery, Lockout-Realitaet | [`exercises/15-pin-management.md`](exercises/15-pin-management.md) |
| [`course/22-csr-und-ca-workflow.md`](course/22-csr-und-ca-workflow.md) | CSR-Generierung ueber HSM, Mini-CA, CA-Signing, Cert-Import | [`exercises/16-csr-und-ca-workflow.md`](exercises/16-csr-und-ca-workflow.md) |

Weitere Materialien:

- [`exercises/`](exercises/) - Aufgaben im einheitlichen Format
- [`solutions/`](solutions/) - Musterloesungen und erwartete Ergebnisse
- [`lab/`](lab/) - ausfuehrbares Docker-/Devcontainer-Lab
- [`docs/cheatsheet.md`](docs/cheatsheet.md) - schneller Spickzettel
- [`docs/api.md`](docs/api.md) - Leitfaden zur PKCS#11-API
- [`docs/glossar.md`](docs/glossar.md) - Abkuerzungen und zentrale Begriffe
- [`roadmap.md`](roadmap.md) - offene Erweiterungs-Themen (C_GenerateRandom, ECDH, RFC-3161-Timestamps, Cloud-HSM-Vergleich)
- [`CHANGELOG.md`](CHANGELOG.md) - Versionierte Aenderungen der Lab/Kurs-Inhalte

## Wichtige Make-Targets

### Setup und Grundlagen

| Target | Zweck |
|---|---|
| `make build` | Docker-Images bauen, ausserhalb des Devcontainers |
| `make shell` | Lab-Shell oeffnen |
| `make restore` | C#-NuGet-Abhaengigkeiten fuer Editor/Language Server wiederherstellen |
| `make init-token` | SoftHSM-Token `dev-token` initialisieren |
| `make list-slots` / `make list-mechanisms` | Slots und unterstuetzte Mechanismen anzeigen |
| `make gen-rsa` / `make gen-ec` | RSA- bzw. EC-Keypair erzeugen |
| `make sign` / `make verify` / `make sign-pss` | RSA-PKCS#1-v1.5 und RSA-PSS signieren/verifizieren |
| `make sign-ec` / `make verify-ec` | ECDSA-Pfad |
| `make import-cert` | Self-signed Zertifikat fuer signing-key importieren |
| `make java-demo` / `make go-demo` / `make kotlin-demo` / `make csharp-demo` | Basis-Sprach-Demos (sign + verify) |
| `make clean` / `make clean-tokens` / `make distclean` | generierte Artefakte / Token-DB / alles entfernen |

### Erweiterte Module (Kapitel 13-22)

Jedes Modul liefert ein Bash-Target plus die vier Sprach-Demos (Go/C#/Java/Kotlin), wo anwendbar. Java/Kotlin entfaellt in einzelnen Modulen wegen dokumentierter JCA-Limitierungen.

| Modul | Targets | Inhalt |
|---|---|---|
| 13 — Verschluesselung | `make encrypt` / `make decrypt` / `make {go,csharp,java,kotlin}-encrypt-demo` | Hybrid RSA-OAEP + AES-GCM, neuer wrap-key |
| 14 — CMS-Signatur | `make cms-sign` / `make cms-verify` / `make {go,csharp,java,kotlin}-cms-demo` | Detached CMS/PKCS#7-SignedData |
| 15 — Streaming | `make stream-sign` / `make stream-encrypt` / `make stream-decrypt` / `make {go,csharp,java,kotlin}-stream-demo` | Multi-Part Ops fuer 100MB-Files, neuer aes-stream-key |
| 16 — HMAC | `make hmac-sign` / `make hmac-verify` / `make {go,csharp,java,kotlin}-hmac-demo` | HMAC-SHA256, GENERIC_SECRET-Key, HS256-JWT |
| 17 — Session-Pooling | `make {go,csharp,java,kotlin}-pool-demo` | Sequential vs parallel Benchmark, Pool-Patterns |
| 18 — HSM-TLS | `make gen-tls-cert` / `make tls-serve` | nginx mit ssl_certificate_key engine:pkcs11:... |
| 19 — HSM-SSH | `make ssh-pubkey` / `make ssh-test` | sshd auf 2222, Login via PKCS11Provider |
| 20 — Key Wrap | `make gen-kek` / `make wrap-backup` / `make {go,csharp}-wrap-demo` | C_WrapKey/UnwrapKey-Roundtrip; Java/Kotlin entfaellt (kein JCA-Wrap) |
| 21 — PIN-Management | `make pin-info` / `make pin-change` / `make pin-recovery` / `make {go,csharp}-pin-demo` | C_SetPIN/InitPIN, CKF_USER_PIN_*-Flags; Java/Kotlin entfaellt (kein JCA-PIN-API) |
| 22 — CSR + CA | `make gen-ca-key` / `make issue-ca-cert` / `make issue-leaf-cert` / `make {go,csharp,java,kotlin}-csr-demo` | Mini-CA mit HSM-CA-Key, CSR-Generierung pro Sprache |

Die Make-Dependency-Kette stellt vorgelagerte Targets automatisch sicher. `make tls-serve` zieht z.B. `import-cert` → `gen-rsa` → `init-token` mit.

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
