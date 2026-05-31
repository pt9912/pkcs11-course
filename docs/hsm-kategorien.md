# HSM-Kategorien und typische Einsatzfaelle

Hardware-Sicherheitsmodul ist ein Sammelbegriff. In der Praxis stecken hinter "HSM" sehr verschiedene Geraeteklassen mit unterschiedlichem Schutzniveau, Formfaktor, Performance und Compliance-Pfad. Wer eine Architekturentscheidung trifft, sollte die Kategorien sauber auseinanderhalten — sonst wird ein TPM mit einem PCIe-HSM verglichen, was nicht zu vernuenftigen Antworten fuehrt.

Dieses Dokument ordnet die wichtigsten Klassen ein und zeigt, welche davon PKCS#11 sprechen. Die Lab-Praxis bleibt SoftHSM; die Einordnung hilft, Lab-Annahmen nicht ungeprueft auf Produktion zu uebertragen (siehe [course/09-production-checkliste.md](../course/09-production-checkliste.md)).

## Vergleichstabelle

| Klasse | Formfaktor | Typischer Use-Case | PKCS#11 | FIPS-Niveau |
|---|---|---|---|---|
| `TPM 2.0` | On-Board-Chip (LPC/SPI) | Plattform-Identitaet, Measured Boot, Disk-Encryption-Anchor | meist nicht direkt; eigene API (TSS/tpm2-tools), tpm2-pkcs11 als Bruecke | FIPS 140-2/3 Level 1-2 (je nach Geraet) |
| `Smartcard / USB-Token` | extern, eingestoepselt | Benutzer-Authentisierung, persoenliches Signieren, SSH-/VPN-Login | ja (vendor `.so` oder OpenSC-CCID-Stack) | Level 2-3 typisch; Common Criteria EAL4+ moeglich |
| `Cloud-Smartcard` (Mobile eID, Wallet-Apps) | Smartphone-Secure-Element | Bank-Login, mobile Signaturen, Government-eID | indirekt ueber Server-Komponente | herstellerabhaengig |
| `PCIe-HSM` | Server-Slot, lokal | Backend-Signaturen, TLS-Termination, Mass-Issuance | ja, Vendor-`.so` (Thales Luna, Utimaco SecurityServer) | Level 3-4 |
| `Netzwerk-HSM` | LAN-Geraet, Cluster | wie PCIe, aber als Service ueber TLS/IPSec | ja, Vendor-`.so` | Level 3-4 |
| `HLSM` (High-Level Security Module) | Hochsicherheits-Rack, Cluster mit Quorum | CA-Wurzel, Bank-Master-Keys, Zentralbanken-PKI | ja | Level 4, oft kombiniert mit CC EAL4+ und BSI-Zulassung |
| `Cloud-HSM` (managed) | Provider-Backend | als-a-Service ohne eigenes Rechenzentrum | ja, ueber Provider-Library | meist Level 3 (AWS CloudHSM, Azure Dedicated HSM, GCP Cloud HSM) |
| `Cloud-KMS` (managed) | Provider-API ohne PKCS#11 | Schluesselverwaltung als Service, oft HSM-backed | meist nein (eigene REST/SDK), KMIP-Bridges fuer manche | Level 3 (HSM-backed Tier) |

## Abgrenzung zu PKCS#11

Nicht jede Geraeteklasse spricht PKCS#11 nativ:

- **TPM 2.0** hat eine eigene Specification (TCG). PKCS#11-Zugriff geht ueber `tpm2-pkcs11`, der intern an die TSS bindet. Funktional ok fuer Signaturen, aber nicht alle PKCS#11-Mechanismen lassen sich abbilden (z.B. AES-Key-Wrap fehlt typisch).
- **Smartcards und USB-Tokens** liefern eine Vendor-`.so`. OpenSCs `pkcs11-tool` und `opensc-pkcs11.so` sind oft die Drop-In-Variante, aber Cards mit proprietaeren Applets (PIV, OpenPGP-Card, IDPrime) brauchen den Hersteller-Treiber fuer den vollen Funktionsumfang.
- **PCIe-/Netzwerk-/HLSM und Cloud-HSM** sind die Hauptzielgruppe von PKCS#11. Die Vendor-`.so` ist der Standardweg, auch wenn manche Anbieter zusaetzlich Java-PKCS11-Provider, KMIP-Endpoints oder REST-APIs bieten.
- **Cloud-KMS** (AWS KMS, Azure Key Vault Standard-Tier, GCP KMS) hat selten eine PKCS#11-Bibliothek. Anwendungen sprechen direkt das Provider-SDK; HSM-Backing ist transparent zur Anwendung. KMIP- oder PKCS#11-Frontends existieren in der Praxis als Bruecke, sind aber nicht der Default.

Wer im Kurs `make sign` ausfuehrt, hat die einfachste PKCS#11-Bindung: eine `.so` (SoftHSM-Simulation) bietet alle Funktionen lokal an. In Produktion ist die `.so` der Vendor-Anteil; sie zu wechseln ist meist der erste Migrationsschritt, wenn von SoftHSM auf reale Hardware gegangen wird.

## Entscheidungsmatrix (Mini-Uebung)

Vier typische Szenarien — welche HSM-Klasse passt jeweils, und warum:

| Szenario | Geeignete Klasse | Begruendung |
|---|---|---|
| Notebook-Disk-Encryption-Key, an die Hardware gebunden | `TPM 2.0` | per Definition platformgebunden, BitLocker/LUKS-LSS koppeln daran an. PCIe-HSM waere ueberdimensioniert. |
| Persoenlicher SSH-Login-Schluessel mit PIN-Schutz | `Smartcard` / `USB-Token` (YubiKey 5, Nitrokey) | mobil, Benutzer-eigene Authentisierung, PIN-/PUK-Recovery passt zum Use-Case. PKCS#11-Bindung sauber, siehe Kapitel 19. |
| Bank-Fernsignatur fuer eIDAS-qualifizierte elektronische Signaturen | `HLSM` mit FIPS 140-3 Level 4 / CC EAL4+ | regulatorisch erforderlich, Single-Tenant-Cluster mit M-of-N-Smartcards fuer den SO-Login. Cloud-KMS reicht hier nicht. |
| CA-Root-Key einer internen PKI mit ~100 Issuance-Operationen/Tag | `Netzwerk-HSM` oder `Cloud-HSM` (single-tenant) | niedrige Throughput-Anforderungen, aber zentrales Hochsicherheits-Ziel. Cluster mit zwei Geraeten reicht; HLSM-Rack ist oft Overkill, ein TPM ist zu wenig. |

Die Antworten sind nicht eindeutig — eine Bank wird auch fuer den CA-Key eher Richtung HLSM gehen, ein Startup mit AWS-Stack haeufig Cloud-HSM. Wichtig ist, die Frage zu stellen, **bevor** die Vendor-Bibliothek in den Build gezogen wird.

## Weiterfuehrende Quelle

Norbert Pohlmann hat zum Kapitel "Hardware-Sicherheitsmodule" in seinem Lehrbuch *Cyber-Sicherheit* eine ausfuehrliche Uebungssammlung online: <https://norbert-pohlmann.com/cyber-sicherheit/uebungen/kapitel-hardware-sicherheitsmodule/>. Die dortigen Aufgaben gehen weiter in die Tiefe, besonders bei Vergleichen TPM ↔ HSM und bei den FIPS- und Common-Criteria-Zertifizierungsstufen.

## Querverweise

- [course/01-grundlagen.md](../course/01-grundlagen.md) — PKCS#11-Grundbegriffe
- [course/09-production-checkliste.md](../course/09-production-checkliste.md) — Lab vs Produktion, Cloud-HSM-Block
- [course/19-ssh-mit-hsm.md](../course/19-ssh-mit-hsm.md) — Smartcard/YubiKey als SSH-Token
- [course/21-pin-management.md](../course/21-pin-management.md) — PIN-Lockout-Verhalten unterscheidet sich pro Klasse
- [docs/glossar.md](glossar.md) — Begriffe TPM, HSM, HLSM, FIPS
