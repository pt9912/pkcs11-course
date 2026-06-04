# 07 — Service-Integration

> **Didaktischer Pfad:** Vorher → [`11-ec-und-pss.md`](11-ec-und-pss.md) · Nachher → [`09-production-checkliste.md`](09-production-checkliste.md)

## Lernziele

Nach diesem Kapitel kannst du:

- einen Signatur-Service fachlich zuschneiden.
- PKCS#11-Details hinter einer kleinen Service-Schicht kapseln.
- Healthchecks, Fehlerabbildung und Secret-Handling sinnvoll planen.
- entscheiden, welche Operationen synchron im Request laufen und welche in Betrieb/Provisioning gehoeren.
- **(Bloom 6 — create)** ein Service-Skelett (Konfiguration, Provider-Factory, KeyService, Controller, HealthIndicator) so skizzieren, dass HSM-Details vollstaendig hinter den Schichtgrenzen verschwinden.

> **Geschaetzte Bearbeitungszeit:** ~30 min (reines Lesen + Skizzieren auf Papier). Kein Lab-Schritt — das eigentliche Bauen passiert in Kap. 10.

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
Pkcs11Configuration       // @ConfigurationProperties("pkcs11")
Pkcs11ProviderFactory     // SunPKCS11.configure(...) im @Factory
Pkcs11KeyService          // KeyStore-Login, Alias-Suche, Caching
SignatureService          // Signature.getInstance(..., provider) + Mechanism-Mapping
SignatureController       // POST /sign, POST /verify, GET /keys
Pkcs11HealthIndicator     // C_GetTokenInfo + C_GetMechanismInfo Healthcheck
```

### Konkretes Skelett (Java, Micronaut-Stil)

```java
@ConfigurationProperties("pkcs11")
public class Pkcs11Configuration {
    private String module;
    private String tokenLabel;
    private String pinEnv;          // Name der ENV-Variablen, NICHT die PIN selbst
    private String keyLabel;
    private String mechanism = "SHA256withRSA";
    // getter/setter
}

@Factory
public class Pkcs11ProviderFactory {
    @Singleton
    Provider pkcs11Provider(Pkcs11Configuration cfg) {
        Provider base = Security.getProvider("SunPKCS11");
        String inline = "--name=AppHSM\nlibrary=" + cfg.getModule() + "\n";
        return base.configure(inline);
    }
}

@Singleton
public class SignatureService {
    private final Provider provider;
    private final Pkcs11Configuration cfg;
    private final KeyStore keyStore;

    public SignatureService(Provider provider, Pkcs11Configuration cfg) throws Exception {
        this.provider = provider;
        this.cfg = cfg;
        this.keyStore = KeyStore.getInstance("PKCS11", provider);
        char[] pin = System.getenv(cfg.getPinEnv()).toCharArray();
        try {
            keyStore.load(null, pin);
        } finally {
            Arrays.fill(pin, '\0');
        }
    }

    public SignResponse sign(SignRequest req) throws Exception {
        PrivateKey pk = (PrivateKey) keyStore.getKey(req.keyAlias(), null);
        if (pk == null) {
            throw new KeyNotFoundException(req.keyAlias());
        }
        Signature sig = Signature.getInstance(cfg.getMechanism(), provider);
        sig.initSign(pk);
        sig.update(req.data());
        return new SignResponse(req.keyAlias(), cfg.getMechanism(), sig.sign());
    }
}

@Controller("/sign")
public class SignatureController {
    private final SignatureService service;
    public SignatureController(SignatureService service) { this.service = service; }

    @Post
    public SignResponse sign(@Body SignRequest req) {
        try {
            return service.sign(req);
        } catch (KeyNotFoundException e) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "unknown key alias");
        } catch (Exception e) {
            // CKR_* hier in stabile API-Fehler uebersetzen, niemals die PIN durchreichen.
            throw new HttpStatusException(HttpStatus.BAD_GATEWAY, "hsm error");
        }
    }
}
```

Zur Vertiefung: in der Praxis kommt noch ein `Pkcs11HealthIndicator` (ruft `C_GetTokenInfo` über den geladenen Provider an), eine Mechanism-Allowlist (Verhindert PSS/PKCS1-Verwechslungen), und eine Trennung in DTO/Domain. Das Skelett ignoriert das absichtlich — es soll zeigen, wo PKCS#11-Aufrufe in einer Service-Schicht hängen, nicht ein produktionsreifes Backend.

## Konfigurationsbeispiel

```yaml
pkcs11:
  module: /usr/lib/softhsm/libsofthsm2.so
  token-label: dev-token
  pin-env: PKCS11_PIN
  key-label: signing-key
  mechanism: SHA256withRSA
```

Session-Lifecycle und Thread-Safety in einem Server sind nicht trivial: SunPKCS11 ist provider-intern threadsicher, native Bindings (Pkcs11Interop, miekg/pkcs11) sind es nicht automatisch. Details in [09-production-checkliste.md §Session-Pooling](09-production-checkliste.md#session-pooling-und-threading).

## Harte Wahrheit

Ein Signatur-Service ist schnell gebaut. Ein robuster Signatur-Service braucht gute Fehlerbehandlung, saubere Observability, sichere Secret-Verwaltung und klare Betriebsprozesse für Token-Rotation, PIN-Rotation und HSM-Ausfall.

## Selbsttest

<details>
<summary>1. Warum lebt die PIN in der Skizze als <code>pinEnv</code>-Name und nicht als <code>pin</code>-Wert?</summary>

Damit der Wert nicht in der Config-Datei landet — sie ist typisch in Git, Helm-Templates, ConfigMaps eingecheckt. Nur der ENV-**Name** ist konfigurierbar; den **Wert** liest der Service zur Laufzeit aus der ENV (idealerweise von Vault/SSM dorthin injiziert). Nach dem Login wird das `char[]` mit `Arrays.fill(pin, '\0')` ueberschrieben.
</details>

<details>
<summary>2. Worin liegt der Unterschied zwischen "Healthcheck pruef den Token" und "Healthcheck signiert eine Test-Nachricht"?</summary>

`C_GetTokenInfo`/`C_GetSessionInfo` sind billige Operationen — sie pruefen Verfuegbarkeit ohne Audit-Spur. Eine Test-Signatur kostet Audit-Log-Eintraege auf produktiven HSMs (z.B. AWS CloudHSM trackt jeden `C_Sign`), erzeugt Lasten am HSM und verfaelscht Metriken. Healthchecks daher mit `C_GetTokenInfo` machen, nicht mit Test-Signaturen.
</details>

<details>
<summary>3. Welche zwei Fehlerklassen muss der <code>SignatureController</code>-Fehlerhandler aufteilen, bevor er Status-Codes vergibt?</summary>

Erstens **Client-Fehler** (`KeyNotFoundException` → 404, falscher Mechanism → 400, Body-Validation → 422) — der Caller hat falsch angefragt. Zweitens **Upstream-Fehler** (`CKR_DEVICE_ERROR`, `CKR_TOKEN_NOT_PRESENT`, `CKR_FUNCTION_FAILED` → 502/503) — das HSM ist nicht erreichbar. Die Trennung ist wichtig, damit Monitoring/Alerting den `5xx`-Anteil als Infrastruktur-Indikator nutzen kann und Client-Bugs nicht als HSM-Ausfall erscheinen.
</details>
