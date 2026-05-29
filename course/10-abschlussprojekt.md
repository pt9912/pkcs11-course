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
