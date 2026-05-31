# Roadmap

Themen, die in bestehenden Kapiteln gestreift wurden und ein eigenes Modul vertragen wuerden. Jeder Eintrag enthaelt: was rein muesste, wo es bereits referenziert wird, Scope-Skizze fuer Lab-Demos.

> `C_GenerateRandom` ist in Version 0.15.0 als Kapitel 23 umgesetzt — siehe [`course/23-random.md`](course/23-random.md), [`exercises/17-random.md`](exercises/17-random.md), Lab-Skripte `lab/scripts/71-76*`.

## Strikte CKA-Templates fuer Key-Generate (0.16.0)

Aktuell erzeugen alle Generate-Skripte (`04-generate-rsa.sh`, `09-generate-ec.sh`, `16-generate-rsa-wrap.sh`, `30-generate-aes-stream-key.sh`, `39-generate-hmac-key.sh`, `54-generate-kek.sh`, `64-generate-ca-key.sh`) ihre Keys ueber `pkcs11-tool --keygen / --keypairgen --usage-*`. Damit setzt SoftHSM 2.6 / OpenSC ein **breites Default-Profil**: ein `--usage-sign`-RSA-Key kommt mit `decrypt, sign, signRecover, unwrap` raus, der KEK aus `--usage-wrap` sogar mit `encrypt, decrypt, sign, verify, wrap, unwrap`. Die Use-Case-Trennung, die Kapitel 13/20/22 didaktisch lehren, wird im Lab also **nicht** vom HSM erzwungen (in der Doku seit 0.15.1 ehrlich vermerkt — siehe Disclaimer in [`course/13-verschluesselung.md`](course/13-verschluesselung.md#softhsm-realitaet--usage--ist-intent-kein-constraint)).

**Wo aktuell gestreift:** Kapitel 13, 18, 20, 22 nennen die Soll-Policy (`CKA_SIGN=true, CKA_DECRYPT=false` etc.) jeweils mit Disclaimer. Cheatsheet ebenso.

**Skizze:**
- Helper `lab/scripts/_keygen.py` oder Go-Tool, das `C_GenerateKey`/`C_GenerateKeyPair` mit **explizitem** CKA-Template direkt ueber `python-pkcs11` oder `miekg/pkcs11` aufruft — `CKA_SIGN/VERIFY/DECRYPT/ENCRYPT/WRAP/UNWRAP/DERIVE` strikt nach Use-Case gesetzt.
- Die sieben Generate-Skripte auf diesen Helper umstellen. Defaults bleiben dieselben Labels und IDs, damit nachgelagerte Skripte weiter funktionieren.
- Neues Make-Target `make validate-key-usage`, das fuer jeden Key per `pkcs11-tool --list-objects` die `Usage:`-Zeile parst und gegen ein Soll vergleicht (rot, wenn `signing-key` `decrypt` oder `unwrap` zeigt).
- Validierungs-Tests, die nach jedem Generate die Use-Case-Trennung beweisen (`signing-key` darf nicht `C_Decrypt`, `wrap-key` darf nicht `C_Sign`).
- Disclaimer in 13/18/20/22 streichen oder umformulieren: "Lab erzwingt die Policy jetzt".

**Scope:** mittel-gross. Sieben Skripte, ein neues Validierungs-Target, mehrere Doku-Stellen zurueckziehen. Beruehrt keine Sprach-Demos direkt — die nutzen die generierten Keys nur.

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

**Scope:** gross. Neuer Service-Komponent (TSA), Verifier-Logik komplexer. Wuerde eigenes Modul 24 ergeben (Modul 23 ist seit 0.15.0 mit HSM-RNG belegt).

## Cloud-HSM-Provider-Vergleich

Alle Lab-Demos laufen gegen SoftHSM. Reale Deployments setzen oft Cloud-HSMs ein. Ein Vergleichskapitel wuerde die Unterschiede zwischen lokalem HSM, Cloud-managed HSM und Cloud-KMS einordnen.

**Wo aktuell gestreift:** Kursmodule referenzieren reale HSMs (Thales, Utimaco, YubiKey, AWS CloudHSM) punktuell — z.B. PIN-Lockout (Modul 21), Key-Backup (Modul 20), Cipher-Suites (Modul 18). Es fehlt eine Synthese.

**Skizze (eher Doku als Lab):**
- Vergleichstabelle: AWS CloudHSM, AWS KMS Custom Key Store, Azure Dedicated HSM, Azure Key Vault Managed HSM, GCP Cloud HSM, GCP KMS HSM-backed, OCI Vault
- Achsen: PKCS#11-API-Verfuegbarkeit, Standard-Compliance (FIPS-140-2/3 Level), Key-Material-Eigentum (single-tenant vs multi-tenant), Backup-Strategie, Pricing-Modell, Latenz typisch
- Migration-Pfade: SoftHSM → Cloud-HSM (PKCS#11-URI-Wechsel + Library-Pfad-Wechsel reicht selten; meist Backup-Restore via Vendor-Format)
- Hands-on-Variante: ein Provider mit Free-Tier (z.B. AWS CloudHSM-Cluster mit minimaler HSM, kostet $$$/Stunde) als optionales Lab — vermutlich zu teuer fuer den Standard-Kurs

**Scope:** eher Lesematerial als Lab. Wuerde gut in das Production-Checklisten-Kapitel ([`course/09-production-checkliste.md`](course/09-production-checkliste.md)) als Erweiterung passen.

## HSM-Kategorien und Einsatzfaelle didaktisch schaerfen

Die Uebungen zu Hardware-Sicherheitsmodulen im Lehrbuch Cyber-Sicherheit von Norbert Pohlmann zeigen gute Entscheidungsszenarien fuer TPM, Smartcard und High-Level Security Module. Der Kurs koennte diese Einordnung frueher explizit machen, bevor PKCS#11 als konkrete API eingefuehrt wird.

**Wo aktuell gestreift:** Einfuehrung, Production-Checkliste, YubiKey-/SoftHSM-Vergleiche, Key-Backup und PIN-Lockout.

**Skizze:**
- Kurze Tabelle: TPM vs Smartcard/Token vs HSM/HLSM
- Mapping auf PKCS#11: welche Geraeteklassen typischerweise PKCS#11 sprechen und welche nicht
- Kleine Entscheidungsuebung mit eigenen Szenarien: Notebook-Key, Benutzer-Authentisierung, Bank-Fernsignatur, zentraler CA-Key
- Externer Hinweis auf Pohlmanns Uebungen als weiterfuehrende Quelle: https://norbert-pohlmann.com/cyber-sicherheit/uebungen/kapitel-hardware-sicherheitsmodule/

**Scope:** klein. Eher didaktische Schaerfung als neues Lab-Modul.

## Priorisierungs-Hinweise

Wenn jemand auf der Roadmap weitermacht: **ECDH** ist der naechste natuerliche kleine Schritt (Pendant zum HSM-RNG-Kapitel aus 0.15.0). **RFC 3161** ist der wertvollste fuer rechtliche Anwendungsfaelle (eIDAS-Signaturen). **Cloud-HSM** ist das wichtigste Praxis-Wissen fuer den ueblichen Wechsel von Lab zu Production — kann ohne Lab-Setup als Wiki-Eintrag entstehen.

Keine der drei verbleibenden Themen ist Voraussetzung fuer eines der bestehenden Module — der aktuelle Kursinhalt ist standalone-konsumierbar.
