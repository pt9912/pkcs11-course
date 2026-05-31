# Roadmap

Themen, die in bestehenden Kapiteln gestreift wurden und ein eigenes Modul vertragen wuerden. Jeder Eintrag enthaelt: was rein muesste, wo es bereits referenziert wird, Scope-Skizze fuer Lab-Demos.

> `C_GenerateRandom` ist in Version 0.15.0 als Kapitel 23 umgesetzt — siehe [`course/23-random.md`](course/23-random.md), [`exercises/17-random.md`](exercises/17-random.md), Lab-Skripte `lab/scripts/71-76*`.

> Strikte CKA-Templates sind in Version 0.16.0 umgesetzt — siehe [`lab/go/pkcs11-keygen/`](lab/go/pkcs11-keygen/), [`lab/scripts/77-validate-key-usage.sh`](lab/scripts/77-validate-key-usage.sh), Make-Target `validate-key-usage`. Die Disclaimer in Kapitel 13/18/20/22 sind entsprechend zurueckgezogen; nur der historische Kontext zur pkcs11-tool-Falle bleibt als Lehrstoff.

> HSM-Kategorien didaktisch schaerfen ist in Version 0.16.1 umgesetzt — siehe [`docs/hsm-kategorien.md`](docs/hsm-kategorien.md) mit Vergleichstabelle, PKCS#11-Abgrenzung und Entscheidungsmatrix. Cross-References aus `course/01-grundlagen.md`, `course/09-production-checkliste.md`, Glossar und README.

> ECDH + HKDF ist in Version 0.17.0 als Kapitel 24 umgesetzt — siehe [`course/24-ecdh-hkdf.md`](course/24-ecdh-hkdf.md), [`exercises/18-ecdh-hkdf.md`](exercises/18-ecdh-hkdf.md), [`lab/go/pkcs11-ecdh-demo/`](lab/go/pkcs11-ecdh-demo/), Sprach-Demos fuer Go/C#/Java/Kotlin. `CKM_HKDF_DERIVE` fehlt SoftHSM, HKDF laeuft host-side und ist byte-identisch ueber alle vier Sprachen.

> Cloud-HSM-Provider-Vergleich ist in Version 0.17.1 als Doku umgesetzt — siehe [`docs/cloud-hsm-vergleich.md`](docs/cloud-hsm-vergleich.md) mit sieben Anbietern ueber sechs Achsen (PKCS#11, FIPS, Tenancy, Backup, Latenz, Pricing), Migrationspfaden und Entscheidungs-Tabelle. Cross-References aus `course/09-production-checkliste.md`, README und `docs/api.md`.

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

## Priorisierungs-Hinweise

Verbleibend ist **RFC 3161** — der wertvollste der ursprueglichen Roadmap-Punkte fuer rechtliche Anwendungsfaelle (eIDAS-qualifizierte Signaturen mit Langzeit-Validierung, CAdES-T/CAdES-LT). Beruehrt Modul 14 (CMS) und 22 (CA), braucht eine TSA-Komponente, Verifier-Logik fuer vier Sprachen. Mittlerer-bis-grosser Scope.

Der Eintrag ist nicht Voraussetzung fuer eines der bestehenden Module — der aktuelle Kursinhalt ist standalone-konsumierbar.
