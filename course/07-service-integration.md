# 07 — Service-Integration

## Lernziele

Nach diesem Kapitel kannst du:

- einen Signatur-Service fachlich zuschneiden.
- PKCS#11-Details hinter einer kleinen Service-Schicht kapseln.
- Healthchecks, Fehlerabbildung und Secret-Handling sinnvoll planen.
- entscheiden, welche Operationen synchron im Request laufen und welche in Betrieb/Provisioning gehoeren.

## Lab-Bezug

Passende Grundlage:

```bash
make import-cert
make java-demo
```

## Zielarchitektur

```text
HTTP API
  -> Signatur-Service
    -> Java Security API
      -> SunPKCS11 Provider
        -> PKCS#11 Module
          -> HSM/Token
```

## Beispiel-Endpunkte

```text
GET  /health/pkcs11
POST /sign
POST /verify
```

## Wichtige Designregeln

- Private Keys nie exportieren.
- PINs nicht loggen.
- Signaturdaten, Hashes und Key-IDs getrennt loggen.
- Mechanism explizit konfigurieren.
- Token-Verfügbarkeit im Healthcheck prüfen.
- Keine HSM-Operation im Startup erzwingen, wenn dadurch der Service hart blockiert.
- Fehlercodes normalisieren, aber intern detailreich beobachten.

## Micronaut-Skizze

Für Micronaut würdest du typischerweise bauen:

```text
Pkcs11Configuration
Pkcs11ProviderFactory
Pkcs11KeyService
SignatureService
SignatureController
Pkcs11HealthIndicator
```

## Konfigurationsbeispiel

```yaml
pkcs11:
  module: /usr/lib/softhsm/libsofthsm2.so
  token-label: dev-token
  pin-env: PKCS11_PIN
  key-label: signing-key
  mechanism: SHA256withRSA
```

## Harte Wahrheit

Ein Signatur-Service ist schnell gebaut. Ein robuster Signatur-Service braucht gute Fehlerbehandlung, saubere Observability, sichere Secret-Verwaltung und klare Betriebsprozesse für Token-Rotation, PIN-Rotation und HSM-Ausfall.
