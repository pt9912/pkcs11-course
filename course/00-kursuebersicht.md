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

Der Pfad weicht von der Dateinummerierung ab. Die Sprung-Stellen sind didaktisch begruendet — die Begruendung steht in der dritten Spalte. Die vierte Spalte macht die curricularen **Reduktionsentscheidungen** sichtbar: was wird bewusst weggelassen und warum. Das ist primaer fuer Adaptierende (Trainer, Kursaenderer) gedacht, hilft Erstlesern aber, das Themenfeld realistisch einzuordnen.

| Schritt | Kapitel | Praxis / Begruendung | Bewusst weggelassen |
|---|---|---|---|
| 0 (opt.) | `../exercises/00-glossar.md` | Vokabel-Selbsttest fuer Praefix-Familien — schliesst Outcome "Begriffe sauber erklaeren". | Vollstaendige PKCS#11-Spec-Lektion; das Glossar bleibt Referenz, nicht Ziel. |
| 1 | `01-grundlagen.md` | Begriffe und Ablaufmodell verstehen | Vollstaendige Architektur-Spec; Mechanism-Semantik (wandert nach Kap. 04). |
| 2 | `02-lab-setup.md` | Lab starten, Devcontainer-Modus verstehen | Echte HSM-Anbindung (Vendor-Doku); apt-Versions-Pinning (Reproduzierbarkeitstrade-off). |
| 3 | `03-token-und-objekte.md` | Token initialisieren, Objekte ansehen | `CKA_WRAP_TEMPLATE` und alle weiteren Constraint-Attribute (Vendor-spezifisch, kommt in Kap. 20 punktuell). |
| 4 | `04-signieren-und-verifizieren.md` | RSA signieren und mit OpenSSL verifizieren | Vollstaendige DER-/PEM-Encoding-Theorie (`docs/api.md` und [Kap. 11](11-ec-und-pss.md) reichen aus). |
| 5 | `05-zertifikate.md` | Zertifikat mit gleicher `CKA_ID` importieren | X.509-Theorie und Path-Building (Subject Alt Names, Name Constraints) — wir bleiben bei Identity-Plumbing. |
| 6 | `06-java-sunpkcs11.md` | Java ueber JCA/SunPKCS11 anbinden | IAIK-PKCS11-Provider (proprietaer, eigene Lizenz); BouncyCastle als Default-JCE (separat in Vertiefungen). |
| 7 | `12-sprachbindings.md` | Java, Go, Kotlin und C# vergleichen — bewusst direkt nach Kap. 06 gezogen, damit die JCA-Eigenheiten an einem stack-uebergreifenden Vergleich konkret werden, bevor die Fehlersystematik in Kap. 08 daran anknuepft. | Python (`python-pkcs11`), Rust (`cryptoki`), Node.js (`graphene-pk11`) — Pattern bleibt, Lab waere doppelt so gross. |
| 8 | `08-debugging.md` | Fehler systematisch isolieren — vor Kap. 09/10, weil Debugging ein Grundwerkzeug ist und nicht erst kurz vor dem Abschlussprojekt nuetzlich wird. | Vollstaendige `CKR_*`-Tabelle (~90 Codes); wir nehmen die zehn haeufigsten. |
| 9 | `11-ec-und-pss.md` | ECDSA und RSA-PSS ergaenzen — vorgezogen, weil "Mechanism-Wahl verstehen" das Hintergrundwissen ist, mit dem Kap. 13 (OAEP) und Kap. 14 (CMS-Signaturalgorithmen) operieren. | EdDSA-Hands-on (SoftHSM v2 listet kein `CKM_EDDSA`); Brainpool-Curves (HSM-Verfuegbarkeit zu unsicher). |
| 10 | `07-service-integration.md` | Signatur-Service als Architektur-Skizze — bewusst NACH Debugging, sonst landet man im "happy path"-Trugschluss. | Spring/Quarkus/Ktor-Aequivalente; Micronaut steht stellvertretend. |
| 11 | `09-production-checkliste.md` | Unterschiede zu echten HSMs klaeren — bildet das Reflexions-Sprungbrett zu den Vertiefungsmodulen. | Vendor-Configurations-Tiefe (Thales-Cluster, AWS-VPC, Azure-Roles); wir bleiben bei Patterns. |
| 12 | `13-verschluesselung.md` | Hybride RSA-OAEP + AES-GCM Verschluesselung | TLS-1.3-KEM-Mechanik (eigene Domaene); Authenticated Encryption with Associated Data jenseits GCM (CCM, ChaCha20-Poly1305). |
| 13 | `14-cms-signatur.md` | CMS/PKCS#7-Dokumentsignatur — direkt nach der Verschluesselung, damit beide Schichten (Wrap, Sign) als Bausteine fuer Kap. 25 (CAdES-T) bereitstehen. | JWS — kein PKCS#11-Touchpoint, kein didaktischer Mehrwert in diesem Kurs; XML-DSig (Legacy-Standard). |
| 14 | `15-streaming.md` | Multi-Part-Ops fuer Grossdateien (Sign + Encrypt) | Parallel-Multi-Part (mehrere `C_*Update` simultan auf einer Session — Spec-uneindeutig). |
| 15 | `16-hmac.md` | HMAC, symmetrische Keys (GENERIC_SECRET), JWT-HS256 | JWT-Bibliotheken-Vergleich (`jose4j`, `jjwt`); HOTP/TOTP-Aufbau (eigenes RFC-4226-Thema). |
| 16 | `17-session-pooling.md` | Pool-Pattern, Thread-Safety, fork-Falle — voraus HMAC, weil die Pool-Demos HMAC-Throughput vermessen. | Multi-HSM-Loadbalancing; Pool-Sharing zwischen Containern. |
| 17 | `18-tls-mit-hsm.md` | nginx mit HSM-Key via openssl pkcs11-engine | TLS-1.3-Session-Resumption mit HSM-RSA-Key (Edge-Case); mTLS-Client-Auth (kurz angerissen in Kap. 26). |
| 18 | `19-ssh-mit-hsm.md` | SSH-Login ueber PKCS11Provider, Smartcard-Pattern | SSH-Certificates (CA-signiertes Pubkey-Modell — komplettes eigenes Thema). |
| 19 | `20-key-wrap.md` | Backup/Escrow via C_WrapKey + KEK-Strategie — voraus HMAC/CMS, damit das KEK-Konzept eine vertraute Mechanik trifft. | KMIP-Protokoll (eigene Spec); HSM-Cluster-Sync (Vendor-spezifisch). |
| 20 | `21-pin-management.md` | PIN-Lifecycle, CKF-Flags, SO-Recovery, Lockout-Realitaet | PUK-Recovery bei Smartcards (`pkcs15-tool`-Domaene); biometrische Auth (PKCS#11-spec-extern). |
| 21 | `22-csr-und-ca-workflow.md` | CSR-Generierung ueber HSM, Mini-CA, CA-Signing, Cert-Import | OCSP-Responder; CRL-Verteilung; ACME-Automatisierung. |
| 22 | `23-random.md` | HSM-RNG, `C_GenerateRandom`, TRNG vs CSPRNG, NIST SP 800-90 | NIST-SP-800-22-Statistik-Tests (Tooling-Empfehlung statt Selbstbau); Quantum-RNG-Anbindung. |
| 23 | `24-ecdh-hkdf.md` | ECDH + HKDF: `C_DeriveKey(CKM_ECDH1_DERIVE)`, RFC-5869-Interop ueber 4 Sprachen | X25519 (PKCS#11 v3.0+, SoftHSM 2.x kann es nicht); KEM-basierte PQ-Verfahren (ML-KEM braucht v3.2). |
| 24 | `25-rfc3161-timestamps.md` | RFC-3161-TSA, CAdES-T, `signatureTimeStampToken` | CAdES-LT/-LTA (CRL/OCSP-Embedding und Archive-Timestamps); PAdES (PDF-Signaturen, eigene Domaene). |
| 25 | `10-abschlussprojekt.md` | Signatur-Service bauen — Basistrack. | Production-Hardening (eigene Uebung 21); Multi-Tenant. |
| 26 | `26-abschlussprojekt-advanced.md` | Track-2-Abschluss: CMS-T-Service mit Session-Pool und Audit-Log; optional fuer alle, die Kap. 13-25 systematisch abschliessen wollen. | CAdES-LT-Embedding (in Erweiterungsideen genannt); echter eIDAS-TSA-Vertrag. |
| 27 (opt.) | `../exercises/21-production-audit.md` | Production-Readiness-Audit deines Service — schliesst Outcome "abschaetzen, was sich bei echten HSMs aendert" als Artefakt. | Vendor-Verhandlung; HSM-Beschaffung (Org-Themen, nicht Code-Themen). |

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
- Abkuerzungen und zentrale Begriffe stehen in [docs/glossar.md](../docs/glossar.md).
- Jede Uebung beschreibt Ziel, Vorbereitung, Aufgabe, erwartete Ausgabe, Fehlerfall und Reflexionsfragen.
- Zwei rahmende Selbsttest-Uebungen: [`exercises/00-glossar.md`](../exercises/00-glossar.md) (Vokabel-Check vor Kapitel 01) und [`exercises/21-production-audit.md`](../exercises/21-production-audit.md) (Production-Readiness-Audit nach den Capstones).
- Jedes Kursbild-Kapitel schliesst mit einem `## Selbsttest`-Block ab: drei Closed-Form-Fragen mit Spoiler-Antworten als Retrieval-Anker zwischen den Kapiteln.

Nicht schummeln: Wenn du `CKR_*`-Fehler bekommst, bist du im richtigen Lernmodus.
