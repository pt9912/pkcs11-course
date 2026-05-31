# 22 — CSR und CA-Workflow

## Lernziele

Nach diesem Kapitel kannst du:

- den **Sinn** einer CSR (Certificate Signing Request) erklaeren — Proof-of-Possession des Privkeys ohne ihn herauszugeben.
- eine CSR ueber einen HSM-residenten Key generieren (Bash + alle vier Sprachen).
- eine kleine Test-CA aufsetzen, deren CA-Key ebenfalls im HSM liegt.
- den vollstaendigen Workflow Generate-CSR → CA-Sign → Cert-Import durchspielen.
- den Hack aus Kapitel 5 (self-signed Cert via `08-import-cert.sh`) sauber in einen Production-aequivalenten Pfad ueberfuehren.

## Lab-Bezug

```bash
make gen-ca-key          # CA-Key RSA-2048 auf ID=08 (Intent: CKA_SIGN-only; SoftHSM gibt weitere Flags dazu — siehe Modul 13)
make issue-ca-cert       # Self-signed Root-CA-Cert via openssl + pkcs11-engine
make issue-leaf-cert     # CSR fuer signing-key + CA-Sign + Import auf ID=09
make go-csr-demo         # CSR-Generierung via crypto.Signer-Bridge
make csharp-csr-demo     # ... BouncyCastle.Cryptography + ISignatureFactory
make java-csr-demo       # ... BouncyCastle PKCS10CertificationRequestBuilder
make kotlin-csr-demo     # ... Kotlin-Pendant
```

## Was ist eine CSR, und warum?

Ein **Certificate Signing Request** ist ein ASN.1-Container, der

- den **Subject Distinguished Name** des Antragstellers enthaelt,
- den **Public Key** des Antragstellers transportiert,
- optionale **Extensions** (SAN, Key Usage, EKU) als "extensionRequest"-Attribut traegt,
- ueber alle obigen Bytes mit dem **zugehoerigen Privkey** signiert ist — **Proof-of-Possession**.

Der Privkey verlaesst den HSM dabei nicht. Was die CA sieht, ist:
1. Eine CSR-Bytes-Sequenz, die alle Subject-Felder enthaelt.
2. Eine Signatur ueber diese Bytes, die nur mit dem Privkey zu erzeugen war.

Die CA verifiziert die CSR-Signatur (Pubkey aus der CSR selbst). Wenn das passt, ist bewiesen, dass der Antragsteller den Privkey hat — sie kann gefahrlos ein Cert ausstellen, das diesen Pubkey an den Subject bindet.

## Self-signed vs CA-signed: was war im Lab bisher faul?

`08-import-cert.sh` erzeugt einen **self-signed** Cert ueber den Signing-Key. Funktional ok fuer Java-/SunPKCS11-KeyStore-Alias-Plumbing, aber semantisch ein Hack:

- Self-signed Certs trauen nur sich selbst — kein Trust-Anchor in einer Chain.
- In Produktion kommt der Cert von einer **echten** CA (Enterprise PKI, Public CA, AWS Private CA).
- Verifier brauchen die CA als Trust-Anchor, nicht den Signer-Cert direkt.

Dieses Kapitel ersetzt den Hack durch den richtigen Workflow:

```
Lab Root CA (CA-Key auf ID=08, self-signed CA-Cert)
        │
        ▼ signiert
Leaf-Cert (Subject: app.example.org, Pubkey: signing-key, Issuer: Lab Root CA)
        │
        ▼ importiert nach
Token: ID=09, Label "leaf-cert"
```

**Warum ID=09 und nicht ID=01?** Das `08-import-cert.sh`-Cert auf ID=01 wird von vielen anderen Lab-Demos (CMS-Verify, Java-CMS, TLS) als CAfile-Trust-Anchor genutzt. Es zu ersetzen, wuerde diese Demos brechen. In Produktion ist die Antwort einfach: man **wuerde** das Cert auf ID=01 ersetzen, weil der Self-Signed Cert nichts mehr beweist.

## Mini-CA: was ist im Skript drin?

`65-issue-ca-cert.sh` setzt drei wichtige Extensions:

| Extension | Wert | Bedeutung |
|---|---|---|
| `basicConstraints` | `critical,CA:TRUE` | Cert ist eine CA — darf andere Certs ausstellen |
| `keyUsage` | `critical,keyCertSign,cRLSign` | Privkey darf nur Certs und CRLs signieren — keine TLS-/Signature-Use-Cases |
| `subjectKeyIdentifier` | `hash` | Identifier fuer Chain-Aufbau (matched mit `authorityKeyIdentifier` der Leaf-Certs) |

`66-issue-leaf-cert.sh` produziert pro Aufruf einen neuen Leaf-Cert mit aufsteigender Seriennummer (Tracking via `lab/work/ca.serial`). Das ist Standard-CA-Verhalten: jeder Cert hat eine eindeutige Serial, sonst wird Revocation und Tracking unmoeglich.

## Bridge-Patterns pro Sprache (Reuse aus Modul 14 / CMS)

CSR-Generierung braucht — wie CMS-Signatur — eine "Signer-Bridge", die der Lib statt eines lokalen Privkeys einen Adapter unterschiebt, der intern PKCS#11 aufruft. Die Patterns sind exakt dieselben wie im CMS-Modul:

| Stack | Bridge | CSR-API |
|---|---|---|
| Bash | `openssl req -engine pkcs11 -keyform engine -key "pkcs11:..."` | `openssl req -new` mit `-addext` fuer SAN/KeyUsage |
| Go | `crypto.Signer`-Adapter (`pkcs11RSASigner`) mit DigestInfo + `CKM_RSA_PKCS` | `crypto/x509.CreateCertificateRequest` |
| C# | `ISignatureFactory` mit Callback → `session.Sign(CKM_SHA256_RSA_PKCS)` | `Pkcs10CertificationRequest` aus BouncyCastle.Cryptography |
| Java/Kotlin | `JcaContentSignerBuilder("SHA256withRSA").setProvider(SunPKCS11).build(privKey)` | `JcaPKCS10CertificationRequestBuilder` aus BouncyCastle bcpkix |

In allen Faellen verlaesst der private Key den HSM nicht — nur die rund 600 Byte CSR-TBS-Struktur wandert kurzzeitig vom Anwendungsspeicher zum HSM und 256 Byte Signatur zurueck. Genau **eine** `C_Sign`-Operation pro CSR.

## Pubkey-Beschaffung: ein subtiler Unterschied

Damit die CA-Lib die CSR bauen kann, braucht sie den **Pubkey-Wert** (Modulus, Exponent), nicht nur einen Handle. Drei Wege, wie ihn die Sprachen besorgen:

| Stack | Wie kommt der Pubkey-Wert ins App-Memory? |
|---|---|
| Bash + openssl-engine | openssl-engine zieht den Pubkey beim Engine-Init automatisch aus dem Token (lesbar ohne Login). |
| Go (miekg/pkcs11) | Manuell: `C_GetAttributeValue(CKA_MODULUS, CKA_PUBLIC_EXPONENT)` → `*rsa.PublicKey` rekonstruieren. |
| Java/Kotlin (SunPKCS11) | Via `keyStore.getCertificate(alias).getPublicKey()` — der Cert im Token traegt den Pubkey. |
| C# (Pkcs11Interop + BC) | Manuell: `GetAttributeValue(CKA_MODULUS, ...)` → `BigInteger` → `RsaKeyParameters`. |

Wer die Java-Variante nutzt, braucht **zwingend** den Cert im Token (das `08-import-cert.sh`-Plumbing). Ohne Cert kein Alias, ohne Alias kein Pubkey-Read. Go und C# kommen ohne Cert aus, weil sie direkt CKA_MODULUS lesen.

## Eigenexperiment

- Generiere eine CSR mit der Go-Demo, signiere sie mit der Bash-CA: `cp lab/work/go-app.csr lab/work/leaf.csr && make issue-leaf-cert PKCS11_LEAF_SUBJECT="/CN=combined-app" PKCS11_KEY_LABEL=signing-key`. Beobachte: die CA akzeptiert eine fremd-erzeugte CSR. **Genau das** ist der Workflow in der Realitaet.
- Aendere in der Java-Demo den SignAlgo auf `SHA256withRSAandMGF1` (RSA-PSS). Das laeuft via SunPKCS11 mit `CKM_SHA256_RSA_PKCS_PSS` durch.
- Importiere das Leaf-Cert in einen Browser oder ein TLS-Tool und vergleiche, wie das Subject vs SAN angezeigt wird. Praktischer Aha-Moment fuer "Common-Name vs SubjectAltName" (Browser akzeptieren Hostnames seit RFC 6125 nur noch ueber SAN, nicht mehr ueber CN).

Strukturierte Aufgaben in [`exercises/16-csr-und-ca-workflow.md`](../exercises/16-csr-und-ca-workflow.md).
