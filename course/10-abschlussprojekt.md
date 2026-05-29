# 10 — Abschlussprojekt

## Lernziele

Nach diesem Kapitel kannst du:

- einen kleinen PKCS#11-Signatur-Service fachlich und technisch abgrenzen.
- Signatur-, Verify-, Health- und Key-Listing-Endpunkte definieren.
- HSM-Fehler in stabile API-Fehler uebersetzen.
- Akzeptanztests fuer Token, Key, Mechanism und Signaturverifikation formulieren.

## Aufgabe

Baue einen Signatur-Service, der Daten ueber PKCS#11 signiert und die wichtigsten Betriebsfaelle sichtbar macht.

## Mindestumfang

- Docker-Lab mit SoftHSM.
- Initialisierungsskript für Token.
- RSA-Key im Token.
- Zertifikat mit gleicher `CKA_ID`.
- HTTP-Endpunkt `GET /keys`.
- HTTP-Endpunkt `POST /sign`.
- HTTP-Endpunkt `POST /verify`.
- HTTP-Endpunkt `GET /health/pkcs11`.
- Request: Base64-Daten.
- Response: Base64-Signatur, Key-ID, Algorithmus.
- Healthcheck fuer Token-Verfuegbarkeit und Mechanism-Unterstuetzung.
- Keine PIN im Log.

## Beispiel-API

```text
GET /keys
  -> [{ "id": "01", "label": "signing-key", "type": "RSA", "canSign": true }]

POST /sign
  <- { "keyId": "01", "algorithm": "SHA256withRSA", "data": "..." }
  -> { "keyId": "01", "algorithm": "SHA256withRSA", "signature": "..." }

POST /verify
  <- { "keyId": "01", "algorithm": "SHA256withRSA", "data": "...", "signature": "..." }
  -> { "valid": true }

GET /health/pkcs11
  -> { "token": "dev-token", "available": true, "mechanisms": ["SHA256-RSA-PKCS"] }
```

## Erweiterung

- mehrere Key-Aliase
- RSA-PSS und ECDSA
- OpenTelemetry-Traces
- strukturierte Logs
- Micronaut-Konfiguration via Environment Variables
- Integrationstest im Container
- Audit-Log fuer Signaturversuche ohne Payload-Daten

## Audit-Log-Schema

Das Audit-Log haelt fest, *was* an einem Schluessel passiert ist, ohne die Rohdaten oder die PIN zu enthalten. Empfohlene Minimal-Felder (JSON Lines, ein Event pro Zeile):

```json
{
  "ts": "2026-05-29T08:14:55.137Z",
  "event": "sign.attempt",
  "trace_id": "5f2c…",
  "actor": {
    "principal": "service-account://signing-api",
    "client_ip": "10.0.4.17"
  },
  "key": {
    "alias": "signing-key",
    "ckaId": "01",
    "algorithm": "SHA256withRSA"
  },
  "request": {
    "data_sha256": "f7c3b3…",
    "data_len": 128
  },
  "result": {
    "status": "ok",
    "signature_len": 256,
    "duration_ms": 42
  }
}
```

Bei Fehlern:

```json
{
  "ts": "2026-05-29T08:14:56.041Z",
  "event": "sign.error",
  "trace_id": "5f2c…",
  "actor": { "principal": "service-account://signing-api" },
  "key": { "alias": "signing-key", "ckaId": "01", "algorithm": "SHA256withRSA" },
  "result": {
    "status": "error",
    "error_class": "ProviderException",
    "ckr_code": "CKR_MECHANISM_PARAM_INVALID",
    "duration_ms": 3
  }
}
```

Regeln:

- **Kein Klartext der Payload** — nur Hash und Laenge.
- **Keine PIN, kein PIN-Hash** — selbst der PIN-Hash ist unter PKCS#11 sinnlos und ein Compliance-Risiko.
- **CKR-Code in `error.ckr_code` aus der Exception-Kette extrahieren** — `ProviderException.getCause().getMessage()` enthaelt bei SunPKCS11 typischerweise `CKR_*` als Textfragment.
- **Trace-ID** korrelieren mit dem APM/OTLP-Stack.
- **Append-only Sink** (z. B. journald, S3 mit Object Lock, Splunk-Index ohne Edit-Recht). Audit-Log darf vom Service selbst nicht ueberschreibbar sein.
- **Rotation und Aufbewahrung** richten sich nach Compliance (eIDAS QSig oft 35 Jahre, intern oft 90 Tage).

## Akzeptanzkriterien

- Private Key ist nicht exportierbar.
- Signatur ist mit OpenSSL oder Java Public Key verifizierbar.
- Falsche PIN erzeugt verständlichen Fehler.
- Falscher Mechanism erzeugt verständlichen Fehler.
- README erklärt Setup und Betrieb.
- Healthcheck erkennt fehlenden Token.
- Logs enthalten Key-ID, Algorithmus und Fehlerklasse, aber keine PIN und keine Rohdaten.

## Bewertung

Wenn du das Abschlussprojekt sauber baust, kannst du PKCS#11 produktiv einsetzen. Nicht perfekt, aber weit über Tutorial-Niveau.
