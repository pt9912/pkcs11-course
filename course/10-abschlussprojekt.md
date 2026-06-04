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
- **CKR-Code in `error.ckr_code` aus der Exception-Kette extrahieren** — SunPKCS11 verpackt den `CKR_*`-Code typischerweise zwei bis drei Ebenen tief in der Cause-Kette (`ProviderException` -> `sun.security.pkcs11.wrapper.PKCS11Exception`). Statt nur `getCause().getMessage()` zu nehmen, wie in `Pkcs11Demo.java#reportFailure` durch die gesamte Kette walken und nach dem ersten `CKR_`-Token suchen.
- **Trace-ID** korrelieren mit dem APM/OTLP-Stack.
- **Append-only Sink** (z. B. journald, S3 mit Object Lock, Splunk-Index ohne Edit-Recht). Audit-Log darf vom Service selbst nicht ueberschreibbar sein.
- **Rotation und Aufbewahrung** richten sich nach Compliance (eIDAS QSig oft 35 Jahre, intern oft 90 Tage).

## Cross-Language-Akzeptanz

Eines der deklarierten Kursziele ist, "Java, Kotlin, Go und C# gegen dasselbe Token anzubinden" (`course/00-kursuebersicht.md`). Damit das nicht nur eine Lese-Erfahrung bleibt, gehoert eine Cross-Language-Verifikation in das Assessment:

1. Der Signatur-Service ist in einer Sprache implementiert (typischerweise Java/Micronaut nach der Skizze in Kap. 07).
2. Ein **zweiter** Client in einer anderen Sprache (Go, C# oder Kotlin) ruft `POST /sign` auf und verifiziert die zurueckgegebene Signatur **lokal** mit dem Cert oder Pubkey, das `GET /keys` ausliefert — ohne den Service oder dieselbe JCA-Implementierung.
3. Damit ist bewiesen, dass die Signatur als Bytefolge standard-kompatibel ist, nicht nur "in derselben JVM zurueck-verifizierbar".

Praktisch genuegen ~50 Zeilen Skript pro Verifier:

- **Bash**: `openssl dgst -sha256 -verify pub.pem -signature sig.bin payload.txt`
- **Go**: `crypto/rsa.VerifyPKCS1v15` mit dem PEM-Pubkey aus `/keys`.
- **C#**: `RSA.VerifyData(payload, signature, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1)`.
- **Kotlin (ohne SunPKCS11)**: JCA-Default-Provider mit dem extrahierten Pubkey.

Wer das Projekt didaktisch konsequent durchziehen will, baut den Verifier-Client als eigenen kleinen Service mit `POST /verify-remote` und laesst ihn im Lab-Compose zusammen mit dem Sign-Service laufen — dann ist die Cross-Stack-Kompatibilitaet automatisiert ueberpruefbar.

## Akzeptanzkriterien

- Private Key ist nicht exportierbar.
- Signatur ist mit OpenSSL oder Java Public Key verifizierbar.
- **Eine Signatur des Service ist zusaetzlich in einer anderen Sprache (Go, C#, Kotlin oder Bash/OpenSSL) verifizierbar — Cross-Language-Roundtrip.**
- Falsche PIN erzeugt verständlichen Fehler.
- Falscher Mechanism erzeugt verständlichen Fehler.
- README erklärt Setup und Betrieb.
- Healthcheck erkennt fehlenden Token.
- Logs enthalten Key-ID, Algorithmus und Fehlerklasse, aber keine PIN und keine Rohdaten.

## Bewertung — drei Niveau-Stufen

Statt einer pauschalen "fertig"-Wertung gibt es drei explizite Stufen. Ordne dich selbst zu, *nachdem* du den Service gebaut und die Akzeptanzkriterien gegengeprueft hast.

### Stufe 1 — Akzeptanz erfuellt

Du erreichst diese Stufe, wenn **alle acht Akzeptanzkriterien** (`## Akzeptanzkriterien`) gruen sind. Das bedeutet konkret:

- `POST /sign` liefert eine Signatur, die `openssl dgst -verify` validiert.
- **Cross-Language-Roundtrip** klappt — siehe `## Cross-Language-Akzeptanz`.
- Falsche PIN und falscher Mechanism geben verstaendliche Fehler, nicht 500er.
- Healthcheck erkennt fehlenden Token.
- Logs enthalten keine PIN und keine Rohdaten.

Auf dieser Stufe ist die Lab-Lernleistung dokumentiert. PKCS#11 ist verstanden, ein produktionsnaher Aufbau ist noch nicht beleget.

### Stufe 2 — Akzeptanz + Erweiterungen

Stufe 1 plus mindestens **drei** der folgenden Erweiterungen aus `## Erweiterung`, sichtbar im Code und ueber einen Smoke-Test belegt:

- mehrere Key-Aliase mit unterschiedlichen Mechanism-Whitelists pro Alias.
- RSA-PSS und/oder ECDSA als zusaetzliche Mechanism-Familien.
- strukturiertes Audit-Log nach dem Schema aus `## Audit-Log-Schema`, append-only-Sink.
- Integrationstest, der den Service im Container startet und gegen das Lab-SoftHSM faehrt.
- OpenTelemetry-Traces mit Mechanism, Key-ID und CKR-Code als Span-Attribute.

Hier zeigst du, dass du den Service nicht nur zum Laufen, sondern in mehrere produktions-typische Belastungsdimensionen gebracht hast.

### Stufe 3 — Production-ready

Stufe 2 plus das **vollstaendige Audit aus [`exercises/21-production-audit.md`](../exercises/21-production-audit.md)**: alle zwoelf Produktionsfragen mit Befund/Soll/Aufwand dokumentiert, Showstopper-Liste gepflegt, eine Migrations-Skizze. Zusaetzlich:

- PIN nicht in der Config (Vault, KMS oder mindestens dokumentierter Migrationspfad).
- Pool-Groesse aus Config, Healthcheck pruef Pool-Auslastung gegen Limit.
- Reconnect-Strategie fuer `CKR_DEVICE_REMOVED` / `CKR_SESSION_HANDLE_INVALID`.
- README dokumentiert: Mechanism-Allowlist, PIN-Strategie, Audit-Sink, Recovery-Pfad bei HSM-Ausfall.

Auf dieser Stufe haettest du den Service einem internen Tech-Review vorlegen koennen, ohne erst von vorne anzufangen. Track 2 ([Kap. 26](26-abschlussprojekt-advanced.md)) ist hier der konsequente naechste Schritt: dieselbe Stufe-3-Idee, anderes Feature-Set (CMS, Pool, RFC-3161).

### Wo stehst du wahrscheinlich?

- Nach erstmaligem Durchlauf des Kurses: **Stufe 1**.
- Nach zwei Iterationen in einem Projekt-Kontext oder dem Track-2-Abschluss: **Stufe 2**.
- **Stufe 3** ist die Marke fuer "ich kann PKCS#11 nicht nur lernen, sondern betreiben" — Bewerbungsrelevanz, nicht Kurs-Pflicht.

## Selbsttest

<details>
<summary>1. Warum reicht der OpenSSL-Verify mit demselben Pubkey nicht aus, um das Outcome "Cross-Language-Roundtrip" zu beweisen?</summary>

OpenSSL ist *eine* Verifikations-Implementierung. Wer in derselben Sprache und Library wie der Signer arbeitet, garantiert nicht, dass die Bytefolge **standard-kompatibel** ist — Bouncy-Castle, Go-`crypto/rsa`, .NET-`RSA.VerifyData` koennten alle scheitern, obwohl OpenSSL passt. Das Outcome verlangt einen Verifier in **anderer Sprache und ohne SunPKCS11** (Go/C#/Kotlin-Default-Provider/Bash), damit die Standard-Kompatibilitaet wirklich nachgewiesen ist.
</details>

<details>
<summary>2. Welche zwei Felder eines Audit-Events sind <em>verboten</em>, auch wenn der Stakeholder sie verlangt?</summary>

**Payload-Klartext** und **PIN (auch nicht als Hash)**. Der Payload landet in der `data_sha256`-Spalte mit Hash + Laenge; das volle Dokument waere ein Datenleck-Magnet. Die PIN ist unter PKCS#11 nicht hashbar (kein KDF dahinter), ein Hash-Wert ist Compliance-Findung. Selbst auf Stakeholder-Druck: schriftlich begruenden, nicht einbauen.
</details>

<details>
<summary>3. Warum ist eine WhiteList <code>mechanisms: [SHA256withRSA, RSASSA-PSS]</code> wichtiger als eine <em>BlackList</em> <code>blocked: [SHA1withRSA, MD5withRSA]</code>?</summary>

Whitelist faellt auf der sicheren Seite: jeder neue PKCS#11-Mechanism, der spaeter aufgenommen wird, ist automatisch nicht erlaubt. Blacklist erweitert sich bei jedem neuen unsicheren Algorithmus retroaktiv — wer den Codepfad nicht updated, transportiert das Risiko unbemerkt. Mit dem Whitelist-Ansatz ist die Aussagekraft "wir signieren nur mit X, Y" stabil.
</details>
