# Cloud-HSM-Provider-Vergleich

Reale Deployments setzen ueberwiegend Cloud-HSMs oder Cloud-KMS ein — eigene PCIe-HSMs zu betreiben (Backups, Cluster, Quorum-Smartcards, Kuehlung) ist organisatorisch teuer. Dieses Dokument vergleicht die wichtigsten Cloud-Angebote auf den Achsen, die fuer PKCS#11-Integration und Compliance relevant sind. Die Geraeteklassen-Einordnung (TPM vs Smartcard vs HSM vs HLSM vs Cloud-HSM vs Cloud-KMS) steht in [docs/hsm-kategorien.md](hsm-kategorien.md).

Stand: 2026-05.

## Single-Tenant Cloud-HSM vs HSM-backed KMS

Drei Angebotsklassen sind in der Praxis zu unterscheiden:

| Klasse | Trust-Modell | PKCS#11 nativ? | Beispiele |
|---|---|---|---|
| **Single-Tenant Cloud-HSM** | Du bekommst eine eigene HSM-Partition. Vollstaendige PKCS#11-API, Vendor-Library, Provider hat keinen Zugriff aufs Key-Material. | ja | AWS CloudHSM, Azure Dedicated HSM, Azure Key Vault Managed HSM, GCP Cloud HSM, OCI Dedicated KMS |
| **HSM-backed KMS** | Die Provider-API verwaltet Keys, im Backend sitzen FIPS-zertifizierte HSMs. Du sprichst Provider-API (REST/SDK), nicht PKCS#11 direkt. | meistens nein (Bruecken existieren) | AWS KMS, AWS KMS Custom Key Store, GCP KMS (HSM-Schutzklasse), OCI Vault |
| **Multi-Tenant Managed HSM** | Provider-Library spricht PKCS#11, aber die HSM-Partition ist nicht zwingend single-tenant — der Provider mandantenfaehigt die Hardware. | ja | Azure Key Vault Managed HSM, Thales Data Protection on Demand (DPoD), Entrust nShield as a Service |

Die Wahl haengt stark vom Compliance-Pfad ab: eIDAS-qualifizierte Signaturen und BSI-zertifizierte Vertrauensdienste laufen praktisch nur auf Single-Tenant-Loesungen, weil die Audit-Berichte den ausschliesslichen Zugriff voraussetzen. Fuer die meisten Anwendungen (Anwendungsverschluesselung, TLS-Termination, mTLS, Code-Signing) reicht HSM-backed KMS und ist deutlich preiswerter.

## Anbieter-Vergleichstabelle

| Anbieter | PKCS#11 | FIPS-Level | Tenancy | Backup | Latenz | Pricing-Modell |
|---|---|---|---|---|---|---|
| **AWS CloudHSM** (LiquidSecurity 2) | ja, Vendor-`.so` ueber `cloudhsm-cli` | 140-3 Level 3 | single, pro HSM | Cluster-internes Backup; Cross-Region nur via Custom-Tooling | 1–3 ms intra-VPC, ~50 ms cross-region | pro HSM-Stunde + Datenvolumen |
| **AWS KMS Custom Key Store** (CKS) | nein direkt; KMS-API zeigt auf CloudHSM-Cluster | 140-3 Level 3 (via CloudHSM) | single (CloudHSM darunter) | wie CloudHSM | KMS-API-Latenz, ~10 ms | KMS-Operationen + CloudHSM-Stunden |
| **Azure Dedicated HSM** (Thales Luna 7 in Mietraegern) | ja, Thales-Library | 140-2 Level 3 (zertifiziert), 140-3 in Validierung | single | Luna-Backup-Mechanismen, manuell | 1–5 ms | hoher Fixpreis pro Geraet/Monat |
| **Azure Key Vault Managed HSM** | ja, Marvell-Library + Azure-Wrapper | 140-3 Level 3 | multi mit logisch getrennten Partitionen | provider-managed, multi-region | 5–10 ms ueber HTTPS | pro 10 000 Operationen + Partition |
| **GCP Cloud HSM** | nein direkt, `libkmsp11`-Wrapper ueber KMS-API | 140-3 Level 3 | multi mit pro-Key-Partition | provider-managed | 10–20 ms | pro Key + Operationen |
| **GCP KMS (HSM-Schutzklasse)** | wie Cloud HSM, ueber `libkmsp11` | 140-3 Level 3 | multi | provider-managed | 10–20 ms | pro Key + Operationen |
| **OCI Vault (Dedicated)** | ja, OCI Provider | 140-2 Level 3 | single | provider-managed | 5–10 ms | pro Vault-Stunde |
| **Thales DPoD** | ja, Thales-Library | 140-3 Level 3 | single, Subscription | provider-managed, Multi-Region optional | 10–50 ms (Cloud-Native) | Subscription, gestaffelt |

Werte sind Anhaltspunkte aus oeffentlich verfuegbaren Service-Beschreibungen und Erfahrungsberichten; konkrete Latenz/Preise variieren mit Region, Cluster-Groesse und Workload.

## PKCS#11-API-Verfuegbarkeit im Detail

- **Single-Tenant Cloud-HSMs** geben dir die vollstaendige PKCS#11-API, sehr nah am Vendor-Onprem-Erlebnis. Die `.so` aus dem Vendor-SDK ersetzt deine SoftHSM-Library — bestehende Demos brauchen oft nur Library-Pfad und PKCS#11-URI anders, der Rest (Mechanism-Calls, Templates) bleibt.
- **HSM-backed KMS** (AWS KMS, GCP KMS) bieten typisch kein PKCS#11 direkt. Wenn du PKCS#11 brauchst (etwa fuer eine Java-Anwendung, die nur SunPKCS11 spricht), nutzt du Provider-Bruecken: `libkmsp11` fuer GCP, AWS KMS PKCS#11 Provider (community), Azure `azure-keyvault-pkcs11`. Diese Bridges sind funktional **eingeschraenkt** — viele Mechanismen fehlen, Session-Pooling und Login-Verhalten unterscheiden sich.
- **PKCS#11 v3.0 vs v2.40:** Aeltere Vendor-Libraries sprechen oft nur v2.40; Mechanismen wie `CKM_HKDF_DERIVE` oder neue Post-Quantum-Mechs aus PKCS#11 v3.2 sind nicht ueberall verfuegbar. Vor Migration `C_GetInfo` + `C_GetMechanismList` checken (im Lab Aequivalent: `make list-mechanisms`).
- **Kein Vendor-Lock-in-Schutz:** auch wenn alles "PKCS#11" heisst, sind Library-spezifische Konfigurations- und Auth-Mechanismen nicht standardisiert. AWS CloudHSM braucht Cluster-Cert + Crypto-User-Login; Azure Key Vault Managed HSM braucht OAuth-Token-Refresh; GCP `libkmsp11` braucht Service-Account-JSON. Erwartung: PKCS#11-URI muss um Vendor-Spezifika ergaenzt werden.

## FIPS-Compliance-Pfad

| Compliance-Anforderung | Empfehlung |
|---|---|
| FIPS 140-2 Level 3 (US-Federal, alt) | alle gelisteten Anbieter erfuellen das |
| FIPS 140-3 Level 3 (Nachfolger, ab 2026 Pflicht in vielen Sektoren) | AWS CloudHSM, Azure Key Vault Managed HSM, GCP Cloud HSM bereits zertifiziert; Azure Dedicated HSM in Validierung |
| eIDAS qualifizierte Signaturen (EU) | qualifizierte Vertrauensdiensteanbieter benoetigen CC EAL4+/Common-Criteria-zertifizierte HSMs in eigener Trust-Boundary; Cloud-Service muss durch QTSP zertifiziert sein. Praxis: oft Thales DPoD oder Entrust nShield as a Service |
| BSI TR-03116 (DE) | enthaelt explizit erlaubte Mechanismen und Kurven; reale Audit-Pruefung verlangt detaillierten Mechanism-Nachweis |
| PCI-DSS | jeder zertifizierte FIPS 140-2/3 HSM-Service ist tauglich |
| SOX / FedRAMP | AWS GovCloud, Azure US Government Cloud — gleiche HSMs in regulierten Regionen |

Die Compliance-Pfade unterscheiden sich nicht durch die HSM-Hardware, sondern durch das Audit-Berichtspaket des Cloud-Anbieters. Vor jeder Architekturentscheidung mit Audit-Anforderung: die Service-spezifische Attestation-Dokumentation lesen, nicht die generische FIPS-Liste der Hardware.

## Migrationspfade

**SoftHSM-Lab → Cloud-HSM** ist selten ein reiner Library-Pfad-Wechsel. Es gibt drei Migrations-Klassen:

1. **Library + URI Wechsel** (selten). Funktioniert nur, wenn Cloud-HSM volle PKCS#11-v2.40-Mechanik abdeckt und der Vendor-`.so` als Drop-in akzeptiert wird. Bestehende `make sign`-aequivalente Demos laufen ohne Code-Aenderung, nur `PKCS11_MODULE` und `PKCS11_TOKEN_LABEL` aendern. Realitaets-Beispiel: AWS CloudHSM mit `cloudhsm-pkcs11.so`.
2. **Key-Backup-Restore via Vendor-Format** (typisch). Onprem-HSM exportiert Keys in einem Vendor-spezifischen WrappedKey-Container (Thales: encrypted DPoD-blob; Utimaco: SecCustom-Wrapper). Cloud-HSM importiert das, neue PKCS#11-URI in der Anwendung.
3. **Key-neu-Erzeugung im Cloud-HSM + parallele Migration** (sicherste Variante). Bestehende Keys bleiben onprem, neue Anwendungen erzeugen Keys direkt im Cloud-HSM, alte Workloads ziehen schrittweise um. Erzwingt Cert-Re-Issuance fuer langlebige Schluessel.

Die Migration scheitert haeufig nicht an PKCS#11, sondern an den Begleitfragen: Wie wandern die Anwender-Identitaeten (CKU_USER-PIN-Aequivalent in OAuth/IAM)? Wo liegen die Audit-Logs? Welche Mechanismen sind in der neuen Welt nicht mehr erlaubt (z.B. weil das Cloud-HSM im FIPS-Mode laeuft und CKM_RSA_PKCS abschaltet)?

## Wann was sinnvoll ist

| Use-Case | Empfehlung |
|---|---|
| Anwendungs-Datenverschluesselung, AWS-zentrisch | AWS KMS reicht; HSM-backed Schutzklasse nur, wenn Compliance es vorschreibt |
| TLS-Termination an Load Balancer, Cloud-nativ | provider-eigenes KMS (AWS ACM, Azure Front Door, GCP Cloud Load Balancer); HSM-backed nur in regulierten Branchen |
| Eigene CA (Enterprise PKI, Code-Signing) | single-tenant Cloud-HSM oder weiterhin onprem; Multi-Tenant Managed HSM oft auch ok |
| Bank-Fernsignatur, eIDAS-qualifizierte Signaturen | single-tenant HLSM-aequivalent, eIDAS-zertifizierter Dienst (Thales DPoD eIDAS, Entrust nShield as a Service in EU-Regionen) |
| Datenbank-TDE auf RDS/Aurora/Cloud SQL | provider-eigenes KMS, transparent |
| Cross-Cloud-Key-Material (Multi-Provider-Architektur) | externer Key-Store mit eigenem KMIP/PKCS#11-Frontend (z.B. HashiCorp Vault Enterprise mit HSM-Backend); KMIP-Standard hilft hier mehr als PKCS#11 |
| Forschung, Lab, Lehre | SoftHSM (dieser Kurs) oder Tinker-HSMs wie YubiHSM 2 |

## Hands-on-Variante: warum nicht im Kurs?

AWS CloudHSM-Cluster mit der kleinsten Konfiguration kostet ueber 1 EUR/Stunde, ohne Daten-Egress. Eine 4-Stunden-Lab-Session sind 4 EUR pro Teilnehmer — vertretbar, aber Setup-Aufwand (VPC, Subnet, Cluster-Init, Crypto-User-Anlage, PKCS#11-Library-Install in deinem Container) sprengt den Kursrahmen.

Wer trotzdem hands-on will: AWS CloudHSM-CLI in der Free-Tier-VM, Thales DPoD bietet "First Token Free"-Trial fuer 30 Tage, Azure Key Vault Managed HSM hat ein 30-Tage-Trial. Der `lab/scripts/77-validate-key-usage.sh`-Helper laeuft (mit Library-Pfad-Override) auch gegen diese — wer experimentieren will, hat einen Schnelltest, ob die strikte CKA-Trennung dort tatsaechlich erzwungen wird.

## Querverweise

- [course/09-production-checkliste.md](../course/09-production-checkliste.md) — kompakte Anbieter-Tabelle als Schnellueberblick
- [docs/hsm-kategorien.md](hsm-kategorien.md) — Cloud-HSM im Geraeteklassen-Kontext
- [course/21-pin-management.md](../course/21-pin-management.md) — PIN-Lockout pro Cloud-HSM-Vendor
- [course/20-key-wrap.md](../course/20-key-wrap.md) — Cross-HSM-Migration via Wrap/Unwrap
- [docs/glossar.md](glossar.md) — Begriffe FIPS, BSI, CC, EAL, KMIP, KMS
