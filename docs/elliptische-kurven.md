# Kryptografie auf elliptischen Kurven

Kryptografie auf elliptischen Kurven (Elliptic Curve Cryptography, EC/ECC) ist asymmetrische Kryptografie mit kleineren Schluesseln als RSA bei vergleichbarer Sicherheitsstaerke. Im Kurs ist EC vor allem fuer ECDSA-Signaturen und spaeter fuer ECDH-Key-Derivation relevant.

Dieses Dokument erklaert die Grundidee ohne tiefe Mathematik. Die Lab-Praxis steht in [course/11-ec-und-pss.md](../course/11-ec-und-pss.md).

## Grundidee

Eine elliptische Kurve definiert eine Menge von Punkten und eine Rechenregel, mit der man Punkte addieren kann. Daraus entsteht eine Gruppe. Fuer Kryptografie nutzt man vor allem eine Operation:

```text
Q = d * G
```

Dabei gilt:

| Symbol | Bedeutung |
|---|---|
| `G` | fester Basispunkt der Kurve |
| `d` | privater Schluessel, eine grosse zufaellige Zahl |
| `Q` | oeffentlicher Schluessel, ein Punkt auf der Kurve |
| `d * G` | Skalarmultiplikation: `G` wird effektiv `d`-mal addiert |

Die Richtung `d -> Q` ist effizient berechenbar. Die Rueckrichtung `Q -> d` ist praktisch nicht loesbar, wenn Kurve, Parameter und Schluesselgroesse korrekt gewaehlt sind. Dieses Problem heisst Elliptic Curve Discrete Logarithm Problem (ECDLP).

## Warum EC statt RSA?

EC erreicht die gleiche Sicherheitsstaerke mit deutlich kleineren Schluesseln:

| Sicherheitsniveau | EC-Beispiel | RSA-Groessenordnung |
|---|---|---|
| ca. 128 bit | P-256 / `secp256r1` | RSA-3072 |
| ca. 192 bit | P-384 / `secp384r1` | RSA-7680 |
| ca. 256 bit | P-521 / `secp521r1` | RSA-15360 |

Praktische Folgen:

- kleinere Public Keys und Zertifikate
- kleinere Signaturen als bei RSA-PSS mit grossen RSA-Keys
- schnelle TLS-Handshakes und weniger Bandbreite
- gut geeignet fuer Smartcards, HSMs und eingebettete Systeme

## Typische EC-Verfahren

| Verfahren | Zweck | PKCS#11-Bezug |
|---|---|---|
| ECDSA | Digitale Signatur | `C_Sign` / `C_Verify` mit `CKM_ECDSA` oder `CKM_ECDSA_SHA256` |
| ECDH | Schluesselaustausch / Key Agreement | `C_DeriveKey` mit `CKM_ECDH1_DERIVE` |
| EdDSA / Ed25519 | Moderne deterministische Signatur | `CKM_EDDSA` in PKCS#11 v3.0+, nicht ueberall verfuegbar |

ECDSA und ECDH nutzen beide elliptische Kurven, loesen aber unterschiedliche Aufgaben. ECDSA beweist, dass der Besitzer des privaten Schluessels eine Nachricht signiert hat. ECDH erzeugt zwischen zwei Parteien einen gemeinsamen geheimen Wert, ohne ihn direkt zu uebertragen.

## PKCS#11-Mapping

Ein EC-Keypair besteht in PKCS#11 aus einem Public-Key-Objekt und einem Private-Key-Objekt:

| Attribut / Mechanism | Bedeutung |
|---|---|
| `CKK_EC` | Key-Typ fuer EC-Schluessel |
| `CKA_EC_PARAMS` | DER-codierte Kurvenparameter, meist eine OID |
| `CKA_EC_POINT` | oeffentlicher Punkt `Q` |
| `CKM_EC_KEY_PAIR_GEN` | Mechanism zum Erzeugen eines EC-Keypairs |
| `CKM_ECDSA` | ECDSA ueber bereits gehashte Daten |
| `CKM_ECDSA_SHA256` | ECDSA, bei dem das Token SHA-256 selbst ausfuehrt |

Im Lab erzeugt der Kurs den EC-Key so:

```bash
pkcs11-tool --module $MODULE --login --pin $PIN --token-label $TOKEN \
  --keypairgen --key-type EC:secp256r1 --id 02 --label ec-signing-key --usage-sign
```

Der private Schluessel bleibt im Token. Die Anwendung bekommt nur den Public Key und die Signaturbytes zurueck.

## Wichtige Stolperer

**ECDSA-Encoding:** PKCS#11 liefert ECDSA-Signaturen nativ meist als rohes `r || s`. OpenSSL, CMS und viele Protokolle erwarten DER-Encoding als `SEQUENCE { r, s }`. Im Lab erledigt das `pkcs11-tool --signature-format openssl`.

**Hashing-Ort:** `CKM_ECDSA` erwartet normalerweise bereits gehashte Daten. `CKM_ECDSA_SHA256` hasht im Token. Anwendung und Token muessen dieselbe Annahme haben.

**Nonce-Sicherheit:** ECDSA braucht pro Signatur einen geheimen Einmalwert `k`. Wenn `k` wiederverwendet oder vorhersagbar ist, kann der private Schluessel berechnet werden. Ein HSM sollte diesen Wert intern sicher erzeugen.

**Kurvennamen:** `P-256`, `secp256r1` und `prime256v1` bezeichnen in vielen Toolchains dieselbe Kurve. Trotzdem akzeptiert nicht jedes Tool jeden Namen.

**Mechanism-Verfuegbarkeit:** SoftHSM, Smartcards und Enterprise-HSMs unterscheiden sich. Vor produktiver Festlegung immer `pkcs11-tool --list-mechanisms` oder `C_GetMechanismList` pruefen.

**Keine Post-Quantum-Sicherheit:** EC ist wie RSA durch einen ausreichend grossen Quantencomputer brechbar. Fuer langfristige Schutzbedarfe braucht es eine Migrationsstrategie zu Post-Quantum-Verfahren.

## Einordnung im Kurs

- [course/11-ec-und-pss.md](../course/11-ec-und-pss.md) zeigt ECDSA im Lab.
- [docs/api.md](api.md) beschreibt die PKCS#11-Mechanisms und Attribute.
- [docs/cheatsheet.md](cheatsheet.md) enthaelt die konkreten `pkcs11-tool`-Befehle.
