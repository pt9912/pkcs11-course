# 09 — Produktionscheckliste

> **Didaktischer Pfad:** Vorher → [`07-service-integration.md`](07-service-integration.md) · Nachher → [`13-verschluesselung.md`](13-verschluesselung.md) (Eintritt in die Vertiefungsmodule)

## Lernziele

Nach diesem Kapitel kannst du:

- SoftHSM-Lab-Annahmen von echtem HSM-Betrieb trennen.
- Sicherheits-, Betriebs- und Compliance-Fragen vor Projektstart stellen.
- Mechanism-, Session-, Backup- und Audit-Risiken benennen.
- Cloud-HSMs als eigene Integrationsklasse einordnen.
- **(Bloom 5 — evaluate)** die zwoelf Produktionsfragen gegen ein konkretes Service-Setup gewichten und Showstopper von Operational Debt trennen.

> **Geschaetzte Bearbeitungszeit:** ~30 min reines Lesen. Die Vertiefung passiert in [`exercises/21-production-audit.md`](../exercises/21-production-audit.md) — dort werden die zwoelf Fragen gegen einen realen Service gefuehrt.

## Lab-Bezug

Nutze das Lab, um Mechanisms, Objekte und Fehlerszenarien vorzubereiten. Uebertrage keine SoftHSM-Annahme ungeprueft auf Produktion.

## Lokales SoftHSM vs. echtes HSM

| Thema | SoftHSM | Echtes HSM |
|---|---|---|
| Sicherheit | Test/Lernzweck | Hardware-/Cloud-Sicherheitsgrenze |
| Performance | lokal, einfach | Latenz, Limits, Sessions relevant |
| Mechanisms | abhängig vom Build | hersteller- und modellabhängig |
| Backup | Dateibasiert | Vendor-Prozess |
| HA | selbst gebaut | Cluster/Partitionen/Vendor |
| Audit | schwach | oft stark, aber aufwendig |
| FIPS-Modus | nein | meist optional, schaltet Mechanisms ab |

## Produktionsfragen

- Wie werden Keys erzeugt?
- Wer darf Keys erzeugen?
- Wie werden PINs/Secrets verwaltet (Vault, KMS, sealed secrets)?
- Wie läuft Key-Rotation?
- Wie läuft Backup/Restore?
- Was passiert bei HSM-Ausfall?
- Welche Mechanisms sind erlaubt?
- Gibt es Audit-Logs und werden sie ausgewertet?
- Welche Latenz ist akzeptabel?
- Wie viele parallele Sessions sind erlaubt?
- Wie werden Zertifikate erneuert?
- Wie wird das HSM überwacht (Heartbeats, Fehlerquoten)?

## Sicherheitsregeln

- Keine PINs in Git.
- Keine Private Keys exportieren (`CKA_SENSITIVE=TRUE`, `CKA_EXTRACTABLE=FALSE`).
- Mechanisms begrenzen (nur erlaubte `CKM_*`).
- Rollen trennen: Admin, Operator, Anwendung, Auditor.
- Test- und Produktions-HSM strikt trennen.
- Logs auf sensitive Daten prüfen.
- Monitoring für Fehlerquoten und Latenz einbauen.
- Notfall-Wiederherstellungs-Verfahren regelmäßig üben.

## FIPS und Compliance

- FIPS 140-2/140-3 zertifizierte HSMs schalten im FIPS-Mode bestimmte Mechanisms ab (z. B. nicht zugelassene Padding-Modi). Vorher mit `--list-mechanisms` prüfen.
- Common Criteria / eIDAS relevant, wenn qualifizierte Signaturen erzeugt werden.
- BSI TR-03145 für Vertrauensdiensteanbieter.

## Cloud- und Managed-HSMs

Cloud-HSMs sind nur eine von mehreren Geraeteklassen mit unterschiedlichem Schutzniveau und PKCS#11-Bezug. Eine Einordnung TPM ↔ Smartcard ↔ PCIe-HSM ↔ HLSM ↔ Cloud-HSM ↔ Cloud-KMS plus Entscheidungsmatrix steht in [docs/hsm-kategorien.md](../docs/hsm-kategorien.md). Ein detaillierter Anbieter-Vergleich mit FIPS-Level, Tenancy, Backup, Latenz, Pricing und Migrationspfaden steht in [docs/cloud-hsm-vergleich.md](../docs/cloud-hsm-vergleich.md).

| Anbieter | Schnittstelle | Besonderheit |
|---|---|---|
| AWS CloudHSM | PKCS#11, JCE, KMIP | Cluster, Mandant pro VPC |
| Azure Managed HSM | PKCS#11 über `azure-keyvault-pkcs11`, REST | Rollenmodell über Azure RBAC |
| Google Cloud HSM | PKCS#11-Provider (`libkmsp11`) als Wrapper über die KMS-API | Auth über Service-Account, Keys leben in Cloud KMS |
| Thales DPoD | PKCS#11 | Subscription, Cloud-natives Partitionsmodell |

Bei Cloud-HSMs ist die PKCS#11-Bibliothek meist proprietär und braucht zusätzliche Auth-Schritte (Cluster-Cert, IAM-Token). Tests auf SoftHSM übertragen sich nicht 1:1.

## Vendor-Lock-in

PKCS#11 standardisiert die API, aber nicht alle Betriebsdetails. Mechanisms, Attribute, Session-Limits, Login-Verhalten und Zertifikatsimport unterscheiden sich. Plane Vendor-spezifische Tests ein.

## Session-Pooling und Threading

In Server-Anwendungen ist `C_OpenSession`/`C_Login`/`C_CloseSession` pro Request der haeufigste Performance-Fehler. Operationen sind preiswert, Logins sind teuer und auditrelevant. Realistische Modelle:

| Stack | Empfohlenes Modell |
|---|---|
| Java/Kotlin (SunPKCS11) | Ein Provider/KeyStore pro Anwendung. Provider ist intern threadsicher, serialisiert Calls am Modul. `Signature`-Instanzen werden **nicht** geteilt — pro Request frisch via `Signature.getInstance(..., provider)` instanziieren. |
| Go (`miekg/pkcs11`) | `*pkcs11.Ctx` einmal initialisieren, Session-Pool selbst bauen (`sync.Pool` oder dedizierter Channel). Pro Session ein Login-Status. Keine Session zwischen Goroutinen teilen, sondern aus dem Pool holen/zurueckgeben. |
| C# (Pkcs11Interop) | `IPkcs11Library` einmal laden, eigenen Pool (`ConcurrentBag<ISession>` o.ä.) implementieren. Sessions sind nicht threadsicher. `Login` einmal pro Session. |
| Native C | Selber pool implementieren. Beachte: viele HSMs setzen `CKF_OS_LOCKING_OK` voraus oder erwarten eigene Locking-Callbacks (`CK_C_INITIALIZE_ARGS`). |

Daumenregeln:

- Pool-Groesse < HSM-Session-Limit. Hardware-HSMs limitieren oft auf zweistellige Sessions pro Partition.
- Logins nicht pro Request. Login-State ist tokenweit pro Prozess gueltig (siehe `course/01-grundlagen.md`).
- Health-Check schickt einen leichten Call (`C_GetTokenInfo`, `C_GetSessionInfo`) gegen den Pool — keine echte Signatur, sonst landen Test-Operationen im Audit-Log.
- Reconnect-Strategie planen: `CKR_DEVICE_REMOVED`/`CKR_SESSION_HANDLE_INVALID` bei HSM-Failover sind realistisch und brauchen `C_Finalize` + Neu-`C_Initialize`, nicht nur Session-Recovery.

### Pool-Skizze (Java)

```java
@Singleton
public class SignaturePool {
    private final Provider provider;
    private final KeyStore keyStore;
    // SunPKCS11 selbst serialisiert; Pool sorgt fuer kontrolliertes Backpressure.
    private final Semaphore concurrency;

    public SignaturePool(Provider provider, KeyStore keyStore,
                         @Value("${pkcs11.maxConcurrent:8}") int max) {
        this.provider = provider;
        this.keyStore = keyStore;
        this.concurrency = new Semaphore(max);
    }

    public byte[] sign(String alias, String mech, byte[] data) throws Exception {
        if (!concurrency.tryAcquire(1, TimeUnit.SECONDS)) {
            throw new BackpressureException("hsm saturated");
        }
        try {
            PrivateKey pk = (PrivateKey) keyStore.getKey(alias, null);
            Signature s = Signature.getInstance(mech, provider);
            s.initSign(pk);
            s.update(data);
            return s.sign();
        } finally {
            concurrency.release();
        }
    }
}
```

### Pool-Skizze (Go)

```go
type SessionPool struct {
    ctx *pkcs11.Ctx
    in  chan pkcs11.SessionHandle
}

func (p *SessionPool) Borrow(ctx context.Context) (pkcs11.SessionHandle, error) {
    select {
    case s := <-p.in:
        return s, nil
    case <-ctx.Done():
        return 0, ctx.Err()
    }
}

func (p *SessionPool) Return(s pkcs11.SessionHandle) {
    p.in <- s
}
```

## Selbsttest

<details>
<summary>1. Welche zwei der zwoelf Produktionsfragen aus diesem Kapitel sind <em>Compliance-Showstopper</em>, ohne deren Antwort kein Production-Go zustande kommt?</summary>

Mindestens **3 (PIN-Verwaltung)** und **8 (Audit-Logs)**. Beide sind regulatorisch verpflichtend (eIDAS, ISO 27001, PCI-DSS) und sind auch ohne tiefen Compliance-Stack nicht umgehbar. Je nach Kontext kommen **1, 2, 11, 12** dazu — siehe die Risiko-Ranking-Diskussion in [`exercises/21-production-audit.md`](../exercises/21-production-audit.md).
</details>

<details>
<summary>2. Warum laufen <code>C_Login</code>/<code>C_Logout</code> beim Pool-Aufbau, nicht bei jeder Anfrage?</summary>

Login-State ist **anwendungsweit gegen das Token**, nicht session-weit (PKCS#11 §11.4). Login pro Request waere semantisch ueberfluessig — auf realen HSMs ist es zudem auditrelevant und langsam (Smartcards mit PIN-Pad: hunderte Millisekunden). Ein versehentliches `Logout` pro Request bricht alle anderen Sessions desselben Pools. Login einmal beim Startup, Logout einmal beim Shutdown.
</details>

<details>
<summary>3. Welcher Healthcheck-Call ist <em>billig genug</em>, dass er gegen einen produktiven HSM-Pool laufen darf, ohne Audit-Logs zu fluten?</summary>

`C_GetTokenInfo` oder `C_GetSessionInfo`. Beide sind reine State-Reads ohne Crypto-Operation und ohne Audit-Spur. Ein "richtiger" Test-Sign wuerde pro Healthcheck einen Audit-Log-Eintrag erzeugen — bei 1 Hz Healthcheck-Frequenz und einem Dutzend Pods sind das 86400 Eintraege pro Tag, alle Lab-getrieben, alle ohne Geschaeftsbedeutung.
</details>
