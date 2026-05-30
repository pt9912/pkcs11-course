# Roadmap

Themen, die in bestehenden Kapiteln gestreift wurden und ein eigenes Modul vertragen wuerden. Jeder Eintrag enthaelt: was rein muesste, wo es bereits referenziert wird, Scope-Skizze fuer Lab-Demos.

## `C_GenerateRandom` — HSM als Entropie-Quelle

PKCS#11 bietet `C_GenerateRandom`/`C_SeedRandom`. Reale HSMs haben einen Hardware-RNG mit TRNG-Eigenschaft (typisch FIPS-140-3-Level-3-zertifiziert) und sind als Entropie-Quelle interessant fuer Anwendungen, die nicht auf `/dev/urandom` setzen wollen oder muessen (Compliance).

**Wo aktuell gestreift:** [`docs/api.md`](docs/api.md) Funktionsuebersicht. Token-Flag `CKF_RNG` wird im PIN-Modul ([`course/21-pin-management.md`](course/21-pin-management.md)) implizit angezeigt.

**Skizze:**
- Bash: `pkcs11-tool --generate-random 64` ueber den Token
- Go/C#/Java/Kotlin: `C_GenerateRandom` direkt, plus Vergleich der Bytes gegen `/dev/urandom`-Output (visuelle Inspektion + ggf. ent-Tool-Statistik)
- Kursmodul: TRNG vs PRNG vs RDRAND vs HSM-RNG, NIST SP 800-90A/B/C kurz einordnen, Performance-Vergleich (HSM-RNG ist haeufig **langsamer** als der Host-Kernel-RNG, dafuer compliance-relevant)

**Scope:** klein. Eine Lab-Skript-Familie plus kurzes Kapitel.

## Key Derivation (ECDH und HKDF)

`C_DeriveKey` mit `CKM_ECDH1_DERIVE` ist die HSM-Variante des klassischen ECDH-Ablaufs (Alice + Bob tauschen Pubkeys, beide leiten denselben Shared Secret ab). Praktisch fuer:
- Hybrid-Verschluesselung **ohne** RSA-Wrap (statt Kapitel 13 Modul 13: ECIES-aehnlich)
- Schluessel-Erstellung fuer authenticated KEX-Protokolle
- Forward-Secrecy-Patterns in eigenen Protokollen

Anschluss-Mechanism: `CKM_HKDF_DERIVE`/`CKM_SP800_108_COUNTER_KDF` fuer das KDF-Expand auf den Shared Secret.

**Wo aktuell gestreift:** Modul 13 nennt EC-basierte hybride Verschluesselung als Alternative, ohne sie zu demonstrieren. Mechanism-Tabelle in [`docs/api.md`](docs/api.md) enthaelt `C_DeriveKey`.

**Skizze:**
- Lab generiert zwei EC-Keys (Alice + Bob)
- `C_DeriveKey(CKM_ECDH1_DERIVE)` mit Bobs Pubkey als Parameter → Shared Secret auf Alice-Seite
- Symmetrisch fuer Bob, gleicher Shared Secret
- Per `CKM_HKDF_DERIVE` ein AES-Key ableiten, damit eine Test-Datei ver-/entschluesseln
- Sprach-Demos: Go (miekg/pkcs11 hat DeriveKey), C# (Pkcs11Interop session.DeriveKey), Java/Kotlin (KeyAgreement via SunPKCS11 `ECDH`)

**Scope:** mittel. Neuer EC-Key-Typ, neuer Mechanism, gute Visualisierung des Shared-Secret-Match-Beweises.

## RFC-3161-Timestamps fuer CMS

CMS-Signaturen (Modul 14) haben ein `signingTime`-Attribut, das aber **vom Signer selbst** gesetzt wird — beweist also nur "der Signer sagt, es war zu diesem Zeitpunkt". Fuer rechtsverbindliche Langzeitsignaturen (CAdES, eIDAS) braucht es einen **TSA-Timestamp** (RFC 3161): ein externer Time-Stamping-Service signiert einen Hash der Signatur mit einer vertrauenswuerdigen Zeitquelle und schickt einen TSToken zurueck, der als `unsignedAttribute.signatureTimeStampToken` an die CMS-Signatur angehaengt wird.

**Wo aktuell gestreift:** [`course/14-cms-signatur.md`](course/14-cms-signatur.md) erwaehnt RFC 3161 als "Stoff fuer ein eigenes Kapitel". 

**Skizze:**
- Lab-TSA via openssl `ts -reply` (eigener kleiner TSA-Server, signiert mit dem HSM-CA-Key aus Modul 22)
- CMS-Sign-Demos erweitern: nach dem `C_Sign` der CMS-Signatur ein `openssl ts -query` an die TSA, Response in `unsignedAttrs.signatureTimeStampToken` einbauen
- Verifier: BouncyCastle `CMSSignedData.verifyTimestamp(...)` bzw. openssl-CMS mit TSA-Validierung
- Kursmodul: warum signingTime nicht reicht, was TSA macht, CAdES-T vs CAdES-LT
- Reale TSA-Anbieter (DigiCert, Sectigo) als Alternative zur Lab-TSA

**Scope:** gross. Neuer Service-Komponent (TSA), Verifier-Logik komplexer. Wuerde eigenes Modul 23 ergeben.

## Cloud-HSM-Provider-Vergleich

Alle Lab-Demos laufen gegen SoftHSM. Reale Deployments setzen oft Cloud-HSMs ein. Ein Vergleichskapitel wuerde die Unterschiede zwischen lokalem HSM, Cloud-managed HSM und Cloud-KMS einordnen.

**Wo aktuell gestreift:** Kursmodule referenzieren reale HSMs (Thales, Utimaco, YubiKey, AWS CloudHSM) punktuell — z.B. PIN-Lockout (Modul 21), Key-Backup (Modul 20), Cipher-Suites (Modul 18). Es fehlt eine Synthese.

**Skizze (eher Doku als Lab):**
- Vergleichstabelle: AWS CloudHSM, AWS KMS Custom Key Store, Azure Dedicated HSM, Azure Key Vault Managed HSM, GCP Cloud HSM, GCP KMS HSM-backed, OCI Vault
- Achsen: PKCS#11-API-Verfuegbarkeit, Standard-Compliance (FIPS-140-2/3 Level), Key-Material-Eigentum (single-tenant vs multi-tenant), Backup-Strategie, Pricing-Modell, Latenz typisch
- Migration-Pfade: SoftHSM → Cloud-HSM (PKCS#11-URI-Wechsel + Library-Pfad-Wechsel reicht selten; meist Backup-Restore via Vendor-Format)
- Hands-on-Variante: ein Provider mit Free-Tier (z.B. AWS CloudHSM-Cluster mit minimaler HSM, kostet $$$/Stunde) als optionales Lab — vermutlich zu teuer fuer den Standard-Kurs

**Scope:** eher Lesematerial als Lab. Wuerde gut in das Production-Checklisten-Kapitel ([`course/09-production-checkliste.md`](course/09-production-checkliste.md)) als Erweiterung passen.

## Priorisierungs-Hinweise

Wenn jemand auf der Roadmap weitermacht: **C_GenerateRandom** und **ECDH** sind die natuerlichen kleinen Schritte. **RFC 3161** ist der wertvollste fuer rechtliche Anwendungsfaelle (eIDAS-Signaturen). **Cloud-HSM** ist das wichtigste Praxis-Wissen fuer den ueblichen Wechsel von Lab zu Production — kann ohne Lab-Setup als Wiki-Eintrag entstehen.

Keine der vier Themen ist Voraussetzung fuer eines der bestehenden Module — der aktuelle Kursinhalt ist standalone-konsumierbar.
