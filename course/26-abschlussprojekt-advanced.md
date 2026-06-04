# 26 — Abschlussprojekt Track 2: CMS-Service mit Audit-Log und RFC-3161-Timestamps

## Lernziele

Nach diesem Projekt kannst du:

- die Vertiefungsmodule (Kap. 13–25) als zusammenhaengende Architektur denken, nicht als Einzelteile.
- einen CMS-Signaturservice betreiben, der unter Last (Session-Pool) korrekt bleibt, qualifizierte Zeitnachweise produziert (CAdES-T) und einen revisionssicheren Audit-Log fuehrt.
- die Schnittstellen, Fehlerklassen und Health-Checks fuer einen produktionsnahen HSM-gestuetzten Signaturdienst formulieren.

## Abgrenzung zu Track 1 (Kap. 10)

[Kap. 10 — Abschlussprojekt](10-abschlussprojekt.md) baut den **Basis**-Signatur-Service: rohes RSA-PKCS#1 ueber `POST /sign`, ein einzelner Key, Cross-Language-Verify als Akzeptanzkriterium. Das ist Stoff aus Kap. 01–12.

Dieses Track-2-Projekt sitzt oben drauf und nimmt die Themen aus Kap. 13–25 ernst:

| Kapitel           | Was kommt rein?                                                                                                                        |
| ----------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| 14 (CMS)          | `POST /cms-sign`: detached CMS/PKCS#7 statt rohem RSA. Sender bekommt eine `.p7s`-Datei zurueck.                                       |
| 25 (RFC 3161)     | `POST /cms-sign?timestamp=true`: Service holt selbst einen Lab-TSA-Stempel und bettet ihn als `signatureTimeStampToken` ein (CAdES-T). |
| 17 (Session-Pool) | Service haelt einen `BlockingQueue<Mac>`/`Channel`/`BlockingCollection`-Pool ueber die gewaehlte Sprache. Pool-Groesse ueber Config.   |
| 10 (Audit-Log)    | Jeder erfolgreiche Sign und jeder Fehler erzeugt eine JSON-Lines-Zeile nach dem in Kap. 10 dokumentierten Schema. Append-only.         |
| 09 (Production)   | Healthcheck pruef Pool-Auslastung, Token, Mechanism, TSA-Erreichbarkeit.                                                               |

## Mindestumfang

- HTTP-API:
  - `GET /health/pkcs11` — Token, Mechanism, Pool-Auslastung, TSA-Status.
  - `GET /keys` — Liste der CKA-IDs, Algorithmus, Cert-Subject.
  - `POST /cms-sign` — Base64-Payload, optional `timestamp=true`. Antwort: `application/pkcs7-signature` (Base64).
  - `POST /cms-verify` — Cross-Verify-Endpunkt.
- Session/Mac/Cipher-Pool fuer Concurrent-Requests (Pool-Pattern aus Kap. 17).
- Audit-Log nach dem Schema aus [Kap. 10 §Audit-Log-Schema](10-abschlussprojekt.md). Mindestens drei Events: `sign.attempt`, `sign.ok`, `sign.error`. Bei Timestamp-Pfad zusaetzlich `tsa.request`, `tsa.ok`, `tsa.error`.
- Konfigurierbare TSA-URL (Default: Lab-TSA aus `make tsa-serve`).
- Mechanism-Allowlist (mindestens `SHA256withRSA`, optional `RSASSA-PSS` und `SHA256withECDSA`). Eingehende Requests mit nicht erlaubtem Mechanism erzeugen `400 Bad Request`, nicht `500`.
- Kein Klartext-PIN in Logs, Configs oder Antworten.

## Architekturskizze

```text
                                                                ┌────────────────────┐
                                                                │  Lab-TSA           │
                                                                │  (make tsa-serve)  │
                                                                └─────────▲──────────┘
                                                                          │ RFC 3161
                                                                          │ (POST tsq)
                                                                          │
┌─────────┐    HTTPS    ┌─────────────────────────────────────────────────┴────────┐
│ Client  │ ──────────► │  CMS-Signature-Service                                   │
│ (any    │             │                                                          │
│  lang)  │             │  ┌────────────┐  ┌────────────┐  ┌──────────────────┐    │
│         │             │  │ /cms-sign  │──│ Session/   │──│  PKCS#11 Bridge  │──┐ │
│         │             │  │ /cms-verify│  │  Mac-Pool  │  │  (SunPKCS11 etc) │  │ │
│         │ ◄────────── │  │ /health    │  │            │  │                  │  │ │
│         │             │  └─────┬──────┘  └────────────┘  └──────────────────┘  │ │
└─────────┘             │        │                                               │ │
                        │  ┌─────▼──────┐                                        │ │
                        │  │ Audit-Log  │                                        │ │
                        │  │ JSON Lines │                                        │ │
                        │  │ append-only│                                        │ │
                        │  └────────────┘                                        │ │
                        └──────────────────────────────────────────────────────────┘
                                                                                 │
                                                                          ┌──────▼──────────┐
                                                                          │  HSM/Token      │
                                                                          │  (signing-key)  │
                                                                          └─────────────────┘
```

## Empfohlener Tech-Stack (Java/Kotlin)

Baut auf den existierenden Lab-Demos auf:

- **CMS-Erzeugung**: BouncyCastle bcpkix — `CMSSignedDataGenerator` mit `JcaContentSignerBuilder("SHA256withRSA").setProvider(sunPkcs11Provider)` (Kap. 14, `lab/java/pkcs11-cms-demo`).
- **TSA-Client**: BouncyCastle `TimeStampRequestGenerator` + `TimeStampResponse.validate` (Kap. 25, `lab/java/pkcs11-cms-tsa-demo`).
- **HTTP-Schicht**: Micronaut nach der Skizze in Kap. 07.
- **Pool**: `BlockingQueue<Mac>`-Pattern aus Kap. 17 — fuer CMS reicht ein `Semaphore` als Concurrency-Limit, weil der HSM-Sign-Call die Bottleneck-Ressource ist.

Go/C#-Tracks sind moeglich, brauchen aber zusaetzlich die `digitorus/pkcs7`- bzw. `BouncyCastle.Cryptography`-CMS-Bibliotheken; CAdES-T-Embedding im Go-Pfad ist nach Kap. 25 als "nicht-trivial" dokumentiert.

## Akzeptanzkriterien

- `POST /cms-sign` liefert eine `openssl cms -verify`-kompatible Datei. **Cross-Stack-Verify** (z. B. mit Bash + OpenSSL gegen das Cert aus `/keys`) ist Pflicht.
- `POST /cms-sign?timestamp=true` liefert eine Datei mit eingebettetem `signatureTimeStampToken`. `openssl ts -verify` validiert den Token, `openssl cms -verify` validiert die Signatur. Wer ueber das BouncyCastle-Pendant verifiziert, sieht beide Stufen.
- Der Service haelt unter `wrk`/`ab` mit 50 Concurrent-Requests ohne `CKR_OPERATION_ACTIVE`-Fehler durch. Pool-Groesse so dimensionieren, dass sie unter dem dokumentierten HSM-Session-Limit bleibt.
- Audit-Log enthaelt fuer jeden Sign eine zusammenhaengende Event-Kette mit gleicher `trace_id`. **Kein** Event enthaelt die Payload als Klartext und **keine** PIN.
- `GET /health/pkcs11` antwortet mit Status `503`, wenn die TSA nicht erreichbar ist oder der Token nicht initialisiert.
- Mechanism-Allowlist wird durchgesetzt — Test mit `?mechanism=CKM_FOO` antwortet `400`, nicht `500`.
- Service startet aus Compose oder Devcontainer mit ENV-konfigurierbarer Pool-Groesse, Mechanism-Allowlist und TSA-URL.

## Erweiterungsideen

- **CAdES-LT**: Embedding von Revocation-Material (CRL/OCSP) als unsigned attribute, sobald der Service mit einer echten CA arbeitet.
- **mTLS am API-Endpoint**: Service-Account des Aufrufers wird ebenfalls per HSM authentifiziert. Kap. 18 + Kap. 22 liefern die Bausteine.
- **OpenTelemetry-Traces**: jede HSM-Operation als Span, mit Mechanism, Key-ID und CKR-Code als Span-Attribute. Audit-Log und Tracing nutzen dieselbe `trace_id`.
- **Multi-Tenant-Mandantentrennung**: pro Mandant eigener `signing-key`, Cert-Plumbing, Pool-Quote. Wird in Kap. 09 als Cloud-HSM-Pattern angerissen.
- **PIN-Rotation als Operator-Task**: separater Endpunkt `POST /admin/rotate-pin`, im Audit-Log als `pin.rotate.attempt`/`pin.rotate.ok` festgehalten. Kap. 21 dokumentiert das Lockout-Risiko.

## Bewertung

Wer Track 2 sauber abschliesst, hat einen HSM-gestuetzten Signaturdienst mit echtem Production-Pattern: Pool, qualifizierte Zeit, revisionssicherer Audit-Log, klare API-Fehler. Das ist der Punkt, an dem man PKCS#11 nicht mehr "lernt", sondern "betreibt".
