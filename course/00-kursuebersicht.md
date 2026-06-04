# 00 - Kursuebersicht

## Zielgruppe

Dieser Kurs richtet sich an Entwickler, die PKCS#11 praktisch verstehen muessen: fuer Signaturen, TLS-Keys, Smartcards, HSMs, Java-Anwendungen oder Backend-Services.

## Vorwissen — Selbstcheck

Der Kurs setzt Crypto-Basisbegriffe voraus, ohne sie selbst nochmal aufzubauen. Wenn du die folgenden vier in **je einem Satz** sauber erklaeren kannst (ohne Glossar-Lookup), bist du startklar:

- **RSA-Signatur** (was ist privat, was oeffentlich, was wird signiert)
- **SHA-256** (was tut ein Hash, warum ist der Output fix lang)
- **AES-GCM** (was unterscheidet authenticated von plain encryption)
- **X.509-Zertifikat** (was bindet das Cert kryptographisch an wen)

Wenn dir eines dieser vier Stichworte nichts sagt, hilft als 30-Minuten-Vorlauf:
- Crypto 101 (kurz, mit Code): <https://cryptobook.nakov.com/cryptography-overview>
- X.509 als Container: <https://www.rfc-editor.org/rfc/rfc5280#section-4>

PKCS#11-spezifische Vokabeln (CKR/CKM/CKA/CKO/…) lernst du *im Kurs* — fuer den Selbsttest dazu siehe [`exercises/00-glossar.md`](../exercises/00-glossar.md).

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

Der Pfad weicht von der Dateinummerierung ab. Die Sprung-Stellen sind didaktisch begruendet — kompakte Begruendung pro Schritt steht in der dritten Spalte. Was an jeder Stelle **bewusst weggelassen** wird, steht separat unter [`## Curriculare Reduktionen`](#curriculare-reduktionen) — primaer fuer Adaptierende (Trainer, Kursaenderer) gedacht, hilft Erstlesern aber, das Themenfeld realistisch einzuordnen.

| Schritt | Kapitel | Praxis / Begruendung |
|---|---|---|
| 0 (opt.) | `../exercises/00-glossar.md` | Vokabel-Selbsttest fuer Praefix-Familien — schliesst Outcome "Begriffe sauber erklaeren". |
| 1 | `01-grundlagen.md` | Begriffe und Ablaufmodell verstehen. |
| 2 | `02-lab-setup.md` | Lab starten, Devcontainer-Modus verstehen. |
| 3 | `03-token-und-objekte.md` | Token initialisieren, Objekte ansehen. |
| 4 | `04-signieren-und-verifizieren.md` | RSA signieren und mit OpenSSL verifizieren. |
| 5 | `05-zertifikate.md` | Zertifikat mit gleicher `CKA_ID` importieren. |
| 6 | `06-java-sunpkcs11.md` | Java ueber JCA/SunPKCS11 anbinden. |
| 7 | `12-sprachbindings.md` | Java, Go, Kotlin und C# vergleichen — direkt nach Kap. 06, damit die JCA-Eigenheiten am Stack-Vergleich konkret werden, bevor Kap. 08 Fehler systematisiert. |
| 8 | `08-debugging.md` | Fehler systematisch isolieren — vor Kap. 09/10, weil Debugging Grundwerkzeug ist. |
| 9 | `11-ec-und-pss.md` | ECDSA und RSA-PSS ergaenzen — vorgezogen, weil "Mechanism-Wahl" Hintergrund fuer Kap. 13 (OAEP) und Kap. 14 (CMS) ist. |
| 10 | `07-service-integration.md` | Signatur-Service als Architektur-Skizze — bewusst NACH Debugging, sonst happy-path-Trugschluss. |
| 11 | `09-production-checkliste.md` | Unterschiede zu echten HSMs klaeren — Sprungbrett zu den Vertiefungsmodulen. |
| 12 | `13-verschluesselung.md` | Hybride RSA-OAEP + AES-GCM Verschluesselung. |
| 13 | `14-cms-signatur.md` | CMS/PKCS#7-Dokumentsignatur — Bausteine fuer Kap. 25 (CAdES-T). |
| 14 | `15-streaming.md` | Multi-Part-Ops fuer Grossdateien (Sign + Encrypt). |
| 15 | `16-hmac.md` | HMAC, symmetrische Keys (GENERIC_SECRET), JWT-HS256. |
| 16 | `17-session-pooling.md` | Pool-Pattern, Thread-Safety, fork-Falle — voraus HMAC, weil die Pool-Demos HMAC-Throughput vermessen. |
| 17 | `18-tls-mit-hsm.md` | nginx mit HSM-Key via openssl pkcs11-engine. |
| 18 | `19-ssh-mit-hsm.md` | SSH-Login ueber PKCS11Provider, Smartcard-Pattern. |
| 19 | `20-key-wrap.md` | Backup/Escrow via C_WrapKey + KEK-Strategie — voraus HMAC/CMS, damit das KEK-Konzept eine vertraute Mechanik trifft. |
| 20 | `21-pin-management.md` | PIN-Lifecycle, CKF-Flags, SO-Recovery, Lockout-Realitaet. |
| 21 | `22-csr-und-ca-workflow.md` | CSR-Generierung ueber HSM, Mini-CA, CA-Signing, Cert-Import. |
| 22 | `23-random.md` | HSM-RNG, `C_GenerateRandom`, TRNG vs CSPRNG, NIST SP 800-90. |
| 23 | `24-ecdh-hkdf.md` | ECDH + HKDF: `C_DeriveKey(CKM_ECDH1_DERIVE)`, RFC-5869-Interop ueber 4 Sprachen. |
| 24 | `25-rfc3161-timestamps.md` | RFC-3161-TSA, CAdES-T, `signatureTimeStampToken`. |
| 25 | `10-abschlussprojekt.md` | Signatur-Service bauen — Basistrack. |
| 26 | `26-abschlussprojekt-advanced.md` | Track-2-Abschluss: CMS-T-Service mit Session-Pool und Audit-Log; optional. |
| 27 (opt.) | `../exercises/21-production-audit.md` | Production-Readiness-Audit deines Service — schliesst Outcome "abschaetzen, was sich bei echten HSMs aendert" als Artefakt. |

## Curriculare Reduktionen

Was an jeder Stelle **bewusst nicht** Teil des Kurses ist — gegliedert nach Schritt. Hilft beim Adaptieren ("kann ich diesen Stoff weglassen?") und beim Einschaetzen ("was muesste ich anderswo nachholen?"). Die genannten Themen sind nicht "unwichtig", sondern eigene Lernpfade.

- **Schritt 0** — Vollstaendige PKCS#11-Spec-Lektion; das Glossar bleibt Referenz, nicht Ziel.
- **Schritt 1** — Vollstaendige Architektur-Spec; Mechanism-Semantik (wandert nach Kap. 04).
- **Schritt 2** — Echte HSM-Anbindung (Vendor-Doku); apt-Versions-Pinning (Reproduzierbarkeitstrade-off).
- **Schritt 3** — `CKA_WRAP_TEMPLATE` und alle weiteren Constraint-Attribute (Vendor-spezifisch, kommt in Kap. 20 punktuell).
- **Schritt 4** — Vollstaendige DER-/PEM-Encoding-Theorie (`docs/api.md` und [Kap. 11](11-ec-und-pss.md) reichen aus).
- **Schritt 5** — X.509-Theorie und Path-Building (Subject Alt Names, Name Constraints) — wir bleiben bei Identity-Plumbing.
- **Schritt 6** — IAIK-PKCS11-Provider (proprietaer, eigene Lizenz); BouncyCastle als Default-JCE (separat in Vertiefungen).
- **Schritt 7** — Python (`python-pkcs11`), Rust (`cryptoki`), Node.js (`graphene-pk11`) — Pattern bleibt, Lab waere doppelt so gross.
- **Schritt 8** — Vollstaendige `CKR_*`-Tabelle (~90 Codes); wir nehmen die zehn haeufigsten.
- **Schritt 9** — EdDSA-Hands-on (SoftHSM v2 listet kein `CKM_EDDSA`); Brainpool-Curves (HSM-Verfuegbarkeit zu unsicher).
- **Schritt 10** — Spring/Quarkus/Ktor-Aequivalente; Micronaut steht stellvertretend.
- **Schritt 11** — Vendor-Configurations-Tiefe (Thales-Cluster, AWS-VPC, Azure-Roles); wir bleiben bei Patterns.
- **Schritt 12** — TLS-1.3-KEM-Mechanik (eigene Domaene); Authenticated Encryption with Associated Data jenseits GCM (CCM, ChaCha20-Poly1305).
- **Schritt 13** — JWS — kein PKCS#11-Touchpoint, kein didaktischer Mehrwert in diesem Kurs; XML-DSig (Legacy-Standard).
- **Schritt 14** — Parallel-Multi-Part (mehrere `C_*Update` simultan auf einer Session — Spec-uneindeutig).
- **Schritt 15** — JWT-Bibliotheken-Vergleich (`jose4j`, `jjwt`); HOTP/TOTP-Aufbau (eigenes RFC-4226-Thema).
- **Schritt 16** — Multi-HSM-Loadbalancing; Pool-Sharing zwischen Containern.
- **Schritt 17** — TLS-1.3-Session-Resumption mit HSM-RSA-Key (Edge-Case); mTLS-Client-Auth (kurz angerissen in Kap. 26).
- **Schritt 18** — SSH-Certificates (CA-signiertes Pubkey-Modell — komplettes eigenes Thema).
- **Schritt 19** — KMIP-Protokoll (eigene Spec); HSM-Cluster-Sync (Vendor-spezifisch).
- **Schritt 20** — PUK-Recovery bei Smartcards (`pkcs15-tool`-Domaene); biometrische Auth (PKCS#11-spec-extern).
- **Schritt 21** — OCSP-Responder; CRL-Verteilung; ACME-Automatisierung.
- **Schritt 22** — NIST-SP-800-22-Statistik-Tests (Tooling-Empfehlung statt Selbstbau); Quantum-RNG-Anbindung.
- **Schritt 23** — X25519 (PKCS#11 v3.0+, SoftHSM 2.x kann es nicht); KEM-basierte PQ-Verfahren (ML-KEM braucht v3.2).
- **Schritt 24** — CAdES-LT/-LTA (CRL/OCSP-Embedding und Archive-Timestamps); PAdES (PDF-Signaturen, eigene Domaene).
- **Schritt 25** — Production-Hardening (eigene Uebung 21); Multi-Tenant.
- **Schritt 26** — CAdES-LT-Embedding (in Erweiterungsideen genannt); echter eIDAS-TSA-Vertrag.
- **Schritt 27** — Vendor-Verhandlung; HSM-Beschaffung (Org-Themen, nicht Code-Themen).

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
