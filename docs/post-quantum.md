# Post-Quantum-Kryptografie

Post-Quantum-Kryptografie (PQC) bezeichnet klassische kryptografische Verfahren, die auf normalen Computern laufen, aber nach heutigem Kenntnisstand auch gegen Angriffe mit grossen Quantencomputern widerstandsfaehig sein sollen. "Post-Quantum-sicher" ist dabei kein mathematisches Ewigkeitsversprechen, sondern eine Sicherheitsannahme auf Basis aktueller Kryptanalyse und Standardisierung.

Stand dieses Dokuments: 2026-05-31.

## Warum das relevant ist

RSA, klassischer Diffie-Hellman, ECDH, ECDSA und EdDSA basieren auf mathematischen Problemen, die ein ausreichend grosser fehlertoleranter Quantencomputer mit Shor-artigen Algorithmen brechen koennte. Besonders kritisch ist das fuer Daten mit langer Vertraulichkeitsdauer: ein Angreifer kann heute verschluesselte Kommunikation aufzeichnen und spaeter entschluesseln, sobald passende Quantenhardware existiert.

Symmetrische Kryptografie ist weniger hart betroffen. Grover-artige Angriffe halbieren grob die effektive Sicherheitsstaerke. Deshalb bleiben AES-256, SHA-384/SHA-512 und HMAC mit ausreichenden Schluessellaengen brauchbare Bausteine.

## Heute relevante Verfahren

| Zweck | Verfahren | Herkunft / alter Name | Status |
|---|---|---|---|
| Key Encapsulation / Schluesselaustausch | `ML-KEM` | CRYSTALS-Kyber | Finaler NIST-Standard, FIPS 203 |
| Digitale Signatur | `ML-DSA` | CRYSTALS-Dilithium | Finaler NIST-Standard, FIPS 204 |
| Digitale Signatur | `SLH-DSA` | SPHINCS+ | Finaler NIST-Standard, FIPS 205; konservative hashbasierte Alternative |
| Key Encapsulation / Schluesselaustausch | `HQC` | Hamming Quasi-Cyclic | Von NIST als Backup zu ML-KEM ausgewaehlt; finaler Standard fuer 2027 erwartet |
| Digitale Signatur | `FN-DSA` | Falcon | Von NIST ausgewaehlt; FIPS noch nicht final |
| Stateful Signaturen | `LMS`, `XMSS` | Hashbasierte Signaturen | NIST SP 800-208; Spezialfall, weil Signaturzustand strikt verwaltet werden muss |

Fuer die meisten neuen Designs ist `ML-KEM` der wichtigste Baustein fuer Schluesselaustausch und `ML-DSA` der naheliegende Signatur-Baustein. `SLH-DSA` ist wertvoll als andere Sicherheitsfamilie, hat aber groessere Signaturen und ist langsamer.

## KEM statt klassischer Verschluesselung

Viele PQC-Verfahren fuer "Verschluesselung" sind in der Praxis Key Encapsulation Mechanisms (KEMs). Ein KEM verschluesselt nicht direkt beliebige Nutzdaten. Es erzeugt stattdessen ein gemeinsames Geheimnis:

```text
Sender:   Encapsulate(public_key)  -> ciphertext, shared_secret
Empfaenger: Decapsulate(private_key, ciphertext) -> shared_secret
```

Das `shared_secret` wird anschliessend in einem KDF verarbeitet und daraus entstehen symmetrische Keys fuer AES-GCM, ChaCha20-Poly1305, HMAC oder aehnliche Protokollbausteine.

Das ersetzt funktional Rollen, die heute oft ECDH oder RSA-Key-Transport haben.

## Parameterwahl

Bei `ML-KEM` gibt es drei Parameter-Sets:

| Parameter | Einordnung |
|---|---|
| `ML-KEM-512` | klein/schnell, niedrigere Sicherheitsstufe |
| `ML-KEM-768` | typischer Default fuer viele Protokolle |
| `ML-KEM-1024` | hoehere Sicherheitsstufe, groessere Keys/Ciphertexts |

Bei Signaturen gilt die gleiche Grundlogik: hoehere Parameter bedeuten mehr Sicherheitsmarge, aber groessere Public Keys, Signaturen und mehr Rechenaufwand. Die konkrete Wahl haengt vom Protokoll, Lebensdauer der Daten, Compliance-Anforderungen und Implementierungsunterstuetzung ab.

## Hybrid-Migration

Fuer Transportprotokolle ist ein hybrider Ansatz oft pragmatisch:

```text
klassischer Austausch + PQC-KEM -> gemeinsames Geheimnis
```

Beispielhaft: `X25519` plus `ML-KEM-768`. Der Vorteil ist, dass die Verbindung bei korrekter Kombination nicht allein von einem neuen Verfahren abhaengt. Wenn das PQC-Verfahren spaeter geschwaecht wird, bleibt der klassische Anteil; wenn spaeter ein grosser Quantencomputer kommt, bleibt der PQC-Anteil.

Hybrid ist kein Ersatz fuer saubere Implementierung, aber ein guter Migrationspfad fuer TLS, VPNs und interne Service-Kommunikation.

## Was nicht post-quantum-sicher ist

Diese Verfahren sollten fuer neue Langfrist-Sicherheitsziele nicht als alleinige Public-Key-Basis geplant werden:

- RSA
- klassischer Diffie-Hellman
- ECDH
- ECDSA
- Ed25519 / EdDSA

Sie bleiben kurzfristig praktisch wichtig und werden in Hybrid-Designs weiterhin verwendet, sind aber nicht quantum-resistent.

## HSM- und PKCS#11-Bezug

PKCS#11 hat historisch RSA, EC, AES, HMAC und klassische Mechanisms standardisiert. PQC-Unterstuetzung in HSMs, Smartcards, Provider-Layern und Sprachbindings ist noch uneinheitlich. Fuer den Kurs heisst das:

- Bestehende Module erklaeren weiter klassische PKCS#11-Mechanismen wie RSA, ECDSA, AES und HMAC.
- PQC ist ein Migrationsthema: Algorithmusauswahl, Zertifikatsformate, TLS-Stacks, Provider-Unterstuetzung und HSM-Firmware muessen zusammenpassen.
- Produktiv sollte man vorab pruefen, ob das Ziel-HSM PQC-Keys wirklich intern erzeugen, schuetzen, signieren bzw. decapsulieren kann.
- Fuer `ML-KEM` ist wichtig: Decapsulation mit dem Private Key ist der sensible Vorgang und gehoert in den geschuetzten Trust-Boundary.

## Typische Stolperer

- **"PQC" heisst nicht automatisch sicher.** Nur gut untersuchte, standardisierte Verfahren mit korrekten Parametern verwenden.
- **Implementierung zaehlt.** Side-Channel-Schutz, konstante Laufzeiten und saubere Zufallsquellen bleiben kritisch.
- **Protokolle muessen angepasst werden.** Ein KEM ist nicht dasselbe wie RSA-Encrypt oder ECDH.
- **Signaturen werden groesser.** Zertifikate, CSRs, CMS, Firmware-Signaturen und Protokoll-Limits muessen das tragen.
- **Crypto-Agility ist Pflicht.** Die Migration sollte Algorithmuswechsel erlauben, ohne Datenformate und APIs komplett neu zu bauen.

## Quellen

- NIST FIPS 203, ML-KEM: https://csrc.nist.gov/pubs/fips/203/final
- NIST FIPS 204, ML-DSA: https://csrc.nist.gov/pubs/fips/204/final
- NIST FIPS 205, SLH-DSA: https://csrc.nist.gov/pubs/fips/205/final
- NIST HQC-Auswahl 2025: https://www.nist.gov/news-events/news/2025/03/nist-selects-hqc-fifth-algorithm-post-quantum-encryption
- NIST Selected Algorithms: https://csrc.nist.gov/projects/post-quantum-cryptography/post-quantum-cryptography-standardization/selected-algorithms
- NIST SP 800-208, LMS/XMSS: https://csrc.nist.gov/pubs/sp/800/208/final
