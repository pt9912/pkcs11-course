# Roadmap

Alle ursprueglichen Roadmap-Themen sind umgesetzt. Dieses Dokument bleibt als Status-Referenz fuer kuenftige Erweiterungen.

> `C_GenerateRandom` ist in Version 0.15.0 als Kapitel 23 umgesetzt — siehe [`course/23-random.md`](course/23-random.md), [`exercises/17-random.md`](exercises/17-random.md), Lab-Skripte `lab/scripts/71-76*`.

> Strikte CKA-Templates sind in Version 0.16.0 umgesetzt — siehe [`lab/go/pkcs11-keygen/`](lab/go/pkcs11-keygen/), [`lab/scripts/77-validate-key-usage.sh`](lab/scripts/77-validate-key-usage.sh), Make-Target `validate-key-usage`. Die Disclaimer in Kapitel 13/18/20/22 sind entsprechend zurueckgezogen; nur der historische Kontext zur pkcs11-tool-Falle bleibt als Lehrstoff.

> HSM-Kategorien didaktisch schaerfen ist in Version 0.16.1 umgesetzt — siehe [`docs/hsm-kategorien.md`](docs/hsm-kategorien.md) mit Vergleichstabelle, PKCS#11-Abgrenzung und Entscheidungsmatrix. Cross-References aus `course/01-grundlagen.md`, `course/09-production-checkliste.md`, Glossar und README.

> ECDH + HKDF ist in Version 0.17.0 als Kapitel 24 umgesetzt — siehe [`course/24-ecdh-hkdf.md`](course/24-ecdh-hkdf.md), [`exercises/18-ecdh-hkdf.md`](exercises/18-ecdh-hkdf.md), [`lab/go/pkcs11-ecdh-demo/`](lab/go/pkcs11-ecdh-demo/), Sprach-Demos fuer Go/C#/Java/Kotlin. `CKM_HKDF_DERIVE` fehlt SoftHSM, HKDF laeuft host-side und ist byte-identisch ueber alle vier Sprachen.

> Cloud-HSM-Provider-Vergleich ist in Version 0.17.1 als Doku umgesetzt — siehe [`docs/cloud-hsm-vergleich.md`](docs/cloud-hsm-vergleich.md) mit sieben Anbietern ueber sechs Achsen (PKCS#11, FIPS, Tenancy, Backup, Latenz, Pricing), Migrationspfaden und Entscheidungs-Tabelle. Cross-References aus `course/09-production-checkliste.md`, README und `docs/api.md`.

> RFC-3161-Timestamps sind in Version 0.18.0 als Kapitel 25 umgesetzt — siehe [`course/25-rfc3161-timestamps.md`](course/25-rfc3161-timestamps.md), [`exercises/19-rfc3161-timestamps.md`](exercises/19-rfc3161-timestamps.md), Lab-TSA via openssl + Python-Wrapper, voller CAdES-T-Embedding-Flow in Java/Kotlin/C#, separate Artefakte in Go (Library-Limitierung dokumentiert).

## Moegliche Folgethemen

Themen, die in den 0.15-0.18-Releases gestreift wurden, aber nicht im urspruenglichen Roadmap-Set standen:

- **CAdES-LT und CAdES-A** (Kapitel 25 baut nur CAdES-T): Embedding von Revocation-Material (CRL/OCSP) und periodische Archive-Timestamps fuer Langzeit-Beweis. Beruehrt CRL/OCSP-Logik und einen Scheduler — eigener Scope.
- **PKCS#11 v3.0/v3.2-Mechanismen** (SoftHSM unterstuetzt v2.40): `CKM_HKDF_DERIVE`, `CKM_ML_KEM_*`, `CKM_ML_DSA_*` als reales Lab. Setzt einen v3-faehigen HSM voraus (Cloud-HSM oder BouncyHsm-Trunk).
- **Pyhanko-Pfad** fuer Python: PDF-Signaturen mit HSM-Backed-Keys, eigenes Modul moeglich.
- **HSM-Migration spielen**: SoftHSM-Token in BouncyHsm-Token kopieren (Operator-Driven Locked-Test wie in Modul 21 erwaehnt) als Spielwiese fuer Multi-HSM-Patterns.

Wer einen dieser Punkte umsetzen will: gleicher Stil wie die 0.15-0.18-Releases — Doku-Kapitel + Lab + Uebung + ggf. Sprach-Demos in dem Mass, das die Library-Landschaft hergibt.
