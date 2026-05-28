# 10 — Abschlussprojekt

## Aufgabe

Baue einen Signatur-Service, der Daten über PKCS#11 signiert.

## Mindestumfang

- Docker-Lab mit SoftHSM.
- Initialisierungsskript für Token.
- RSA-Key im Token.
- HTTP-Endpunkt `POST /sign`.
- Request: Base64-Daten.
- Response: Base64-Signatur, Key-ID, Algorithmus.
- Healthcheck für Token-Verfügbarkeit.
- Keine PIN im Log.

## Erweiterung

- `POST /verify`
- Zertifikatsimport
- mehrere Key-Aliase
- OpenTelemetry-Traces
- strukturierte Logs
- Micronaut-Konfiguration via Environment Variables
- Integrationstest im Container

## Akzeptanzkriterien

- Private Key ist nicht exportierbar.
- Signatur ist mit OpenSSL oder Java Public Key verifizierbar.
- Falsche PIN erzeugt verständlichen Fehler.
- Falscher Mechanism erzeugt verständlichen Fehler.
- README erklärt Setup und Betrieb.

## Bewertung

Wenn du das Abschlussprojekt sauber baust, kannst du PKCS#11 produktiv einsetzen. Nicht perfekt, aber weit über Tutorial-Niveau.
