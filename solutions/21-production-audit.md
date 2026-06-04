# Loesung 21 - Production-Readiness-Audit

Beispielreport gegen die Micronaut-Skizze aus [`course/07-service-integration.md`](../course/07-service-integration.md) §"Konkretes Skelett". Dein eigener Service kann je nach Track abweichen — die Befunde sind dann konkreter, die Sollwerte und Aufwandsklassen typisch dieselben.

## Audit-Report

### 1. Wie werden Keys erzeugt?

- **Befund:** `make gen-rsa` ueber `pkcs11-keygen` als Lab-Helper. Keine Ceremony, kein Mehraugen-Prinzip.
- **Soll bei echtem HSM:** Key-Generierung ueber HSM-Konsole oder Vendor-Tool, oft mit M-of-N-Smartcard-Quorum. Output ist ein protokollierter Key mit eindeutiger Hardware-ID.
- **Aufwand:** gross (Prozess + Schulung; Code aendert sich nicht).

### 2. Wer darf Keys erzeugen?

- **Befund:** wer User-PIN kennt. Nicht von wer-darf-Crypto-Operationen getrennt.
- **Soll:** SO-Rolle fuer Key-Gen, User-Rolle fuer Signing. Auf manchen HSMs zusaetzlich `Crypto User` vs `Crypto Officer` (Thales-Begriffe).
- **Aufwand:** mittel (zwei separate Configs/Anwendungs-Identitaeten).

### 3. Wie werden PINs/Secrets verwaltet?

- **Befund:** `Pkcs11Configuration.pinEnv` zeigt auf ENV-Variable. PIN landet im Pod-Spec.
- **Soll:** PIN aus Vault/SSM/Key Vault, on-demand geholt, im Speicher nach Login mit `Arrays.fill(pin, '\0')` ueberschrieben (die Skizze tut das schon, Z. 100 — siehe `course/07-service-integration.md`).
- **Aufwand:** mittel (Vault-SDK-Integration und Rotation-Logik).

### 4. Wie laeuft Key-Rotation?

- **Befund:** kein Mechanismus. Rotation bedeutet "Service neu deployen mit anderem `keyLabel`".
- **Soll:** zwei parallele Aliase (`signing-key-current`, `signing-key-next`), Service kennt beide. Bei Rotation wird `current` archiviert und `next` umbenannt. Verifier-Pfad muss alte Signaturen weiter pruefen koennen — d.h. **Cert-Validity-Window**, nicht Key-Existence, bestimmt die Lebensdauer.
- **Aufwand:** mittel (Code) + gross (Org: wer triggert wann?).

### 5. Wie laeuft Backup/Restore?

- **Befund:** keiner. `CKA_EXTRACTABLE=false` ist der Default — wenn der Token verloren geht, ist der Schluessel weg.
- **Soll:** entweder dedizierter Backup-Key mit `CKA_EXTRACTABLE=true` und einem KEK (Pattern aus [Kap. 20](../course/20-key-wrap.md)), oder HSM-Vendor-Backup (Cluster-Sync, encrypted backup file). Ohne explizite Entscheidung kommt das schlechte Ergebnis: kein Backup.
- **Aufwand:** gross (Architektur, Risiko-Entscheidung).

### 6. Was passiert bei HSM-Ausfall?

- **Befund:** `Signature.getInstance` wirft, die HTTP-Schicht antwortet mit `502 BAD_GATEWAY`. Keine Failover-Logik.
- **Soll:** Cluster-HSM mit zwei Partitionen (Active-Active), Library erkennt `CKR_DEVICE_REMOVED` und reinitialisiert (`C_Finalize` + `C_Initialize`). Healthcheck markiert Pod `unready`, bis HSM zurueck ist.
- **Aufwand:** gross (HSM-Cluster + Reconnect-Logik).

### 7. Welche Mechanisms sind erlaubt?

- **Befund:** `cfg.getMechanism()` ist ein freier String. Wer einen abweichenden Wert konfiguriert, bekommt was er bestellt.
- **Soll:** Allowlist im Config (`mechanisms: [SHA256withRSA, RSASSA-PSS]`). Eingehende Requests mit Mechanism ausserhalb der Allowlist → `400 Bad Request`. **Achtung:** Standard-Default `SHA256withRSA` darf nicht implizit auf `CKM_RSA_PKCS` fallen — siehe DigestInfo-Falle in [Kap. 04](../course/04-signieren-und-verifizieren.md).
- **Aufwand:** klein (Validation im Controller).

### 8. Gibt es Audit-Logs und werden sie ausgewertet?

- **Befund:** Skizze hat `// CKR_* hier in stabile API-Fehler uebersetzen` als Kommentar. Kein strukturiertes Audit-Log.
- **Soll:** JSON-Lines-Log nach dem Schema aus [Kap. 10 §Audit-Log-Schema](../course/10-abschlussprojekt.md). Append-only-Sink (S3 mit Object Lock, journald, Splunk-Index). SIEM-Forwarder. Auswertung mit Alert auf z.B. `CKR_PIN_INCORRECT > N pro Minute`.
- **Aufwand:** mittel (Code) + gross (SIEM-Anbindung, Alerts).

### 9. Welche Latenz ist akzeptabel?

- **Befund:** Skizze hat keine Latenz-SLO. Im Lab vermisst niemand etwas.
- **Soll:** p50/p95-Budget pro Endpoint definiert, Healthcheck pruef es. Realistisch: SoftHSM ~1ms, Cloud-HSM 5-15ms, Smartcard 50-500ms. SLO drueber.
- **Aufwand:** klein (Metric) + mittel (Tracing-Integration).

### 10. Wie viele parallele Sessions sind erlaubt?

- **Befund:** Skizze hat `Semaphore concurrency = 8` (Z. 109 in `course/07-service-integration.md`, hardcoded Default). Nicht gegen das HSM-Limit kalibriert.
- **Soll:** Pool-Groesse aus HSM-Vendor-Doku (z.B. Thales Luna: 64 pro Partition, AWS CloudHSM: 1024), als ENV-Variable konfigurierbar. **Unter** dem Limit bleiben, sonst kollidieren parallel deployte Services.
- **Aufwand:** klein (Config-Variable) + mittel (Kalibrierung durch Lasttest).

### 11. Wie werden Zertifikate erneuert?

- **Befund:** `08-import-cert.sh` macht ein self-signed Cert. In Produktion offensichtlich falsch.
- **Soll:** CSR ueber den HSM-Key ([Kap. 22](../course/22-csr-und-ca-workflow.md)), CA-Signing durch interne PKI oder Public CA, Cert via `pkcs11-tool --write-object` reinkopiert. Erneuerung typisch alle 12-24 Monate. Operator-Task, **nicht** automatisierter Service.
- **Aufwand:** gross (PKI-Anbindung, Renewal-Prozess).

### 12. Wie wird das HSM ueberwacht?

- **Befund:** kein Monitoring. `Pkcs11HealthIndicator` ist als geplante Komponente erwaehnt (Z. 61 in `course/07-service-integration.md`), nicht implementiert.
- **Soll:** Healthcheck ruft `C_GetTokenInfo` + `C_GetMechanismInfo` gegen den Pool (siehe Kap. 09 Z. 92), exportiert Metriken (`sign_total`, `sign_errors_total`, `pool_in_use`, `hsm_latency_seconds`). Alert bei `CKR_DEVICE_*` ueber drei Konsekutiv-Polls.
- **Aufwand:** mittel (Metric-Endpoint, Dashboard).

## Risiko-Ranking

**Showstopper (kein Production-Go ohne Loesung):**

- **1, 2, 3** — Key-Genese, Rollen-Trennung und Secret-Verwaltung sind Compliance- und Audit-Themen. Ohne Loesung scheitert jedes IT-Security-Review.
- **8** — kein Audit-Log heisst keine Forensik. Bei eIDAS/Bank-Kontexten regulatorischer Showstopper.
- **12** — wer Ausfaelle nicht sieht, bemerkt sie erst durch Kunden. Mindest-Healthcheck und ein Dashboard reichen.

**Operational Debt (live moeglich, aber Schulden):**

- **4, 5, 6, 11** — Rotation, Backup, HA, Cert-Renewal. Loesbar im laufenden Betrieb, aber jeder Tag ohne explizite Strategie ist ein Tag Risiko.

**Optimization:**

- **7, 9, 10** — Mechanism-Allowlist ist zwar gute Praxis aber leicht nachruestbar; Latenz und Pool-Groesse sind Tuning-Themen mit klarem Mess- und Iterations-Pfad.

## Migrations-Skizze: Showstopper Nr. 3 (PIN-Management)

Wir ersetzen `Pkcs11Configuration.pinEnv` durch eine `PinResolver`-Komponente. Schnittstelle:

```java
interface PinResolver { char[] resolve(); void onRotation(Consumer<char[]> handler); }
```

Implementierung `VaultPinResolver` zieht beim Login einen Lease aus HashiCorp Vault (`secret/pkcs11/dev-token`), liest den `pin`-Key, gibt das char-Array zurueck. Der Lease laeuft ueber Vault-Renewals weiter. Bei Lease-Ablauf ruft Vault einen Callback im `onRotation`-Handler — der KeyStore wird neu geladen, `Arrays.fill(pin, '\0')` wischt die alte Kopie.

Alternativ: SSM Parameter Store mit Versionierung (`pin-current`/`pin-next` als zwei Parameter), Service liest beide und probiert in Reihenfolge. Vorteil: kein Vault-Stack noetig. Nachteil: Rotation ist explizit, nicht lease-getrieben.

**Warum nicht KeyStore-Provider direkt aus Vault holen lassen?** JCA hat dafuer keinen Standard-Mechanism — eigene `KeyStore.LoadStoreParameter`-Subklasse ginge, ist aber JDK-internal-nah und schlecht testbar. Das `PinResolver`-Pattern ist sprach-portabel und macht den Vault-Pfad explizit.

## Selbst-Einschaetzung

- **<6 saubere Audit-Eintraege:** Service ist deutlich vom Production-Pfad entfernt — vorerst nicht deployen, oder bewusst als Lab-only kennzeichnen.
- **6-9 Eintraege:** typische Mittelstellung. Showstopper systematisch abarbeiten, Operational Debt in den Backlog.
- **10-12 Eintraege:** das Outcome "abschaetzen, was sich bei echten HSMs aendert" ist erfuellt. Das Audit selbst ist jetzt das Artefakt, das du in eine Risikobeurteilung haengen kannst.
