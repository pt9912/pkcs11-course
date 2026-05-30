# 00 - Kursuebersicht

## Zielgruppe

Dieser Kurs richtet sich an Entwickler, die PKCS#11 praktisch verstehen muessen: fuer Signaturen, TLS-Keys, Smartcards, HSMs, Java-Anwendungen oder Backend-Services.

## Kursziel

Am Ende kannst du:

- PKCS#11-Begriffe sauber erklaeren.
- SoftHSM lokal als Test-HSM verwenden.
- Slots, Tokens und Objekte mit `pkcs11-tool` untersuchen.
- RSA- und EC-Schluessel im Token erzeugen.
- Daten ueber PKCS#11 signieren.
- Signaturen ausserhalb des Tokens verifizieren.
- Zertifikate mit privaten Keys ueber `CKA_ID` koppeln.
- Java, Kotlin, Go und C# gegen dasselbe Token anbinden.
- typische `CKR_*`-Fehler einordnen.
- abschaetzen, was sich bei echten HSMs aendert.

## Lernpfad

| Schritt | Kapitel | Praxis |
|---|---|---|
| 1 | `01-grundlagen.md` | Begriffe und Ablaufmodell verstehen |
| 2 | `02-lab-setup.md` | Lab starten, Devcontainer-Modus verstehen |
| 3 | `03-token-und-objekte.md` | Token initialisieren, Objekte ansehen |
| 4 | `04-signieren-und-verifizieren.md` | RSA signieren und mit OpenSSL verifizieren |
| 5 | `05-zertifikate.md` | Zertifikat mit gleicher `CKA_ID` importieren |
| 6 | `06-java-sunpkcs11.md` | Java ueber JCA/SunPKCS11 anbinden |
| 7 | `12-sprachbindings.md` | Java, Go, Kotlin und C# vergleichen |
| 8 | `08-debugging.md` | Fehler systematisch isolieren |
| 9 | `11-ec-und-pss.md` | ECDSA und RSA-PSS ergaenzen |
| 10 | `07-service-integration.md` | Signatur-Service als Architektur-Skizze |
| 11 | `09-production-checkliste.md` | Unterschiede zu echten HSMs klaeren |
| 12 | `13-verschluesselung.md` | Hybride RSA-OAEP + AES-GCM Verschluesselung |
| 13 | `14-cms-signatur.md` | CMS/PKCS#7-Dokumentsignatur (S/MIME-Format) |
| 14 | `15-streaming.md` | Multi-Part-Ops fuer Grossdateien (Sign + Encrypt) |
| 15 | `16-hmac.md` | HMAC, symmetrische Keys (GENERIC_SECRET), JWT-HS256 |
| 16 | `17-session-pooling.md` | Pool-Pattern, Thread-Safety, fork-Falle |
| 17 | `18-tls-mit-hsm.md` | nginx mit HSM-Key via openssl pkcs11-engine |
| 18 | `10-abschlussprojekt.md` | Signatur-Service bauen |

## Arbeitsweise

Jedes Kapitel folgt demselben Muster:

- **Lernziele**: Was du danach verstanden haben solltest.
- **Lab-Bezug**: Welche Targets oder Skripte du ausfuehrst.
- **Kernaussagen**: Was fuer reale Systeme wichtig ist.
- **Uebung**: Ein reproduzierbarer Auftrag mit Fehlerfall.

## Devcontainer vs. Docker Compose

Ausserhalb eines Devcontainers startet `make` die passenden Docker-Compose-Services. Im Devcontainer setzt die Umgebung `PKCS11_IN_DEVCONTAINER=1`; `make` fuehrt die Skripte dann direkt im aktuellen Container aus. Dadurch brauchst du im Devcontainer keinen Docker-Socket und kein Docker-in-Docker.

## Uebungs- und Loesungsstruktur

- Aufgaben liegen in `exercises/`.
- Musterloesungen liegen in `solutions/`.
- Jede Uebung beschreibt Ziel, Vorbereitung, Aufgabe, erwartete Ausgabe, Fehlerfall und Reflexionsfragen.

Nicht schummeln: Wenn du `CKR_*`-Fehler bekommst, bist du im richtigen Lernmodus.
