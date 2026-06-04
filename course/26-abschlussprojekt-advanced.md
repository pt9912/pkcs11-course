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

## Bewertung — drei Niveau-Stufen

Track 2 spiegelt die Stufen-Logik aus [Kap. 10 §Bewertung](10-abschlussprojekt.md#bewertung--drei-niveau-stufen) wider, mit anderem Feature-Set. Die drei Stufen sind so kalibriert, dass sie sich zwischen den Tracks vergleichen lassen.

### Stufe 1 — Akzeptanz erfuellt

Die sieben Akzeptanzkriterien (`## Akzeptanzkriterien`) sind gruen. Konkret:

- `POST /cms-sign` liefert `openssl cms -verify`-kompatible Datei (Cross-Stack-Verify).
- `POST /cms-sign?timestamp=true` traegt einen `signatureTimeStampToken` ein, `openssl ts -verify` validiert ihn.
- 50 Concurrent-Requests laufen ohne `CKR_OPERATION_ACTIVE`.
- Mechanism-Allowlist greift, ungueltige Werte enden mit `400`.
- Audit-Log enthaelt Event-Ketten mit gleicher `trace_id`, keine Payload als Klartext.
- Healthcheck antwortet mit `503` bei TSA-Ausfall.
- Service startet aus Compose/Devcontainer mit ENV-konfigurierbarer Pool-Groesse, Mechanism-Liste, TSA-URL.

Auf Stufe 1 ist CAdES-T plus Pool plus Audit-Log gebaut. Production-Pattern sind sichtbar; Production-Reife noch nicht beleget.

### Stufe 2 — Akzeptanz + Erweiterungen

Stufe 1 plus mindestens **zwei** der folgenden Erweiterungen aus `## Erweiterungsideen`, mit Smoke-Test belegt:

- **CAdES-LT**: Embedding von CRL/OCSP-Material als unsigned attribute, sobald der Service mit einer echten CA arbeitet.
- **OpenTelemetry-Traces**: jede HSM-Operation als Span; Audit-Log und Trace teilen sich `trace_id`.
- **mTLS am API-Endpoint**: Aufruferauthentifizierung selbst via HSM-Key.
- **PIN-Rotation**: `POST /admin/rotate-pin` mit eigenen Audit-Events.
- **Multi-Tenant-Mandantentrennung**: pro Mandant ein eigener `signing-key` und Cert.

Stufe 2 zeigt, dass du eine der zwei Production-Achsen aus Kap. 09 (Non-Repudiation oder Observability oder Multi-Tenancy) explizit gebaut hast.

### Stufe 3 — Production-ready

Stufe 2 plus das **vollstaendige Audit aus [`exercises/21-production-audit.md`](../exercises/21-production-audit.md)** auf den Track-2-Service angewendet. Zusaetzlich:

- TSA-URL ueber Service-Discovery (nicht hardcoded), Fallback auf einen Backup-TSA.
- Pool-Groesse kalibriert gegen das **dokumentierte** HSM-Session-Limit (nicht geraten).
- Audit-Sink ist append-only und an ein SIEM angeschlossen (S3 mit Object Lock + Forwarder, journald + remote, Splunk-Index ohne Edit-Recht).
- Reconnect-Strategie fuer TSA- und HSM-Ausfall, mit Circuit-Breaker-Verhalten unter Last.
- README dokumentiert Mechanism-Allowlist, TSA-Vertragspartner-Variante, PIN-Rotation, Recovery-Pfade.

Auf dieser Stufe haettest du einen Service, der bei einem realen Wirtschaftsprueferaudit auf eIDAS-T oder Code-Signing-Compliance verteidigbar waere — natuerlich noch nicht zertifiziert, aber als Pattern und Doku tragfaehig.

### Track-1 vs Track-2 im Stufen-Vergleich

| Track | Stufe 1 | Stufe 2 | Stufe 3 |
|---|---|---|---|
| 1 (Kap. 10) | RSA-Sign + Cross-Language-Verify | + Audit, Multi-Key, PSS/ECDSA | + Production-Audit, PIN-Strategie, Reconnect |
| 2 (Kap. 26) | CMS + CAdES-T + Pool + Audit | + CAdES-LT oder mTLS oder OTel | + Production-Audit, kalibrierter Pool, SIEM-Sink |

Beide Stufen-3-Erreichungen markieren denselben Punkt: PKCS#11 nicht mehr lernen, sondern betreiben. Track 1 ist der schnellere Weg dorthin, Track 2 zeigt mehr Production-Pattern.

## Selbsttest

<details>
<summary>1. Welche zwei Module aus den Vertiefungskapiteln (13-25) treffen sich im Track-2-Service in einer einzigen API-Operation, und wo?</summary>

Kap. 14 (CMS) und Kap. 25 (RFC-3161). Beide treffen sich in `POST /cms-sign?timestamp=true`: der Service erstellt erst die CMS-Signatur (Kap. 14-Pattern), dann holt er einen TSA-Stempel und bettet ihn als `signatureTimeStampToken` in die `unsignedAttributes` ein (Kap. 25-Pattern). CAdES-T-Profil.
</details>

<details>
<summary>2. Warum kann Track 2 in Go nur einen <em>nicht-embeddenden</em> CAdES-T-Pfad anbieten?</summary>

Die digitorus/pkcs7-Library hat keine UnsignedAttributes-API. Embedding wuerde nachtraegliche ASN.1-Manipulation erfordern, die fragil und schlecht maintainbar ist. Der Go-Pfad gibt CMS und TSR als zwei Dateien aus und dokumentiert die Library-Limitierung — die Architektur des Service muss das wissen, wenn Go als Stack gewaehlt wird.
</details>

<details>
<summary>3. Welcher HTTP-Status fuer ein <code>POST /cms-sign?mechanism=CKM_FOO</code> ist die richtige Antwort, und warum nicht 500?</summary>

`400 Bad Request`. Der Caller hat einen ungueltigen Mechanism angefragt — das ist ein Client-Fehler, kein Server-Fehler. Ein `500` wuerde Monitoring/Alerting ausloesen und dem Caller suggerieren, "das HSM ist kaputt". Eine Mechanism-Allowlist im Controller (mit `400` bei Miss) trennt Client- von Infrastruktur-Fehlern sauber — siehe auch Kap. 07 Selbsttest Frage 3.
</details>
