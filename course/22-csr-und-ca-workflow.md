# 22 — CSR und CA-Workflow

> **Didaktischer Pfad:** Vorher → [`21-pin-management.md`](21-pin-management.md) · Nachher → [`23-random.md`](23-random.md)

## Lernziele

Nach diesem Kapitel kannst du:

- den **Sinn** einer CSR (Certificate Signing Request) erklaeren — Proof-of-Possession des Privkeys ohne ihn herauszugeben.
- eine CSR ueber einen HSM-residenten Key generieren (Bash + alle vier Sprachen).
- eine kleine Test-CA aufsetzen, deren CA-Key ebenfalls im HSM liegt.
- den vollstaendigen Workflow Generate-CSR → CA-Sign → Cert-Import durchspielen.
- den Hack aus Kapitel 5 (self-signed Cert via `08-import-cert.sh`) sauber in einen Production-aequivalenten Pfad ueberfuehren.
- **(Bloom 5 — evaluate)** entscheiden, welche **drei** X.509-Extensions in einer Leaf-CSR fuer ein gegebenes Use-Case-Szenario (TLS-Server, Code-Signing, TSA) zwingend sind — und welche von der CA stillschweigend ueberschrieben werden duerfen, ohne den Use-Case zu brechen.

> **Geschaetzte Bearbeitungszeit:** ~60 min (Lesen 25 min + CA-Setup + Leaf-Cert + Sprach-CSR 35 min). Die Bridge-Patterns sind identisch zu Kap. 14 — Lesezeit hier daher etwas kuerzer.

## Lab-Bezug

```bash
make gen-ca-key          # CA-Key RSA-2048 auf ID=08 (strikt CKA_SIGN-only, validiert via make validate-key-usage)
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

## Selbsttest

<details>
<summary>1. Was beweist die CSR-Signatur kryptographisch — und was nicht?</summary>

Sie beweist, dass der Antragsteller den Privkey zum CSR-enthaltenen Pubkey besitzt ("Proof-of-Possession"). Sie beweist **nicht** die Identitaet — das macht die CA durch externes Prueferverfahren (Domain-Validation, Org-Vetting, Identitaetspruefung). Die CSR-Signatur ist die untere Sicherheitsschwelle "wer hier signiert hat, kann auch spaeter signieren", nicht "wer hier signiert hat, ist tatsaechlich X".
</details>

<details>
<summary>2. Warum hat die Java-Demo das Cert im Token, die Go-Demo aber nicht — und reichen beiden dieselbe CSR-Funktionalitaet?</summary>

Beide produzieren funktional identische CSRs. Der Unterschied ist Sprach-API-bedingt: Java braucht ueber SunPKCS11 ein Cert mit gleicher `CKA_ID`, damit `keyStore.getCertificate(alias).getPublicKey()` den Pubkey liefert. Go (miekg) liest `CKA_MODULUS`/`CKA_PUBLIC_EXPONENT` direkt aus dem Privkey-Objekt und rekonstruiert den Pubkey ohne Cert.
</details>

<details>
<summary>3. Welche Extension hat dieser CA-Key, die der Leaf-Key NICHT haben darf?</summary>

`basicConstraints=critical,CA:TRUE` plus `keyUsage=critical,keyCertSign,cRLSign`. Der CA-Key darf andere Certs signieren — der Leaf-Key nicht. Wer auf einem Leaf-Cert `basicConstraints=CA:TRUE` ausstellt, hat eine versehentliche Intermediate-CA — ein klassischer Cross-Signing-Bug, der zu kompromittierten Vertrauensketten fuehrt.
</details>

<details>
<summary>4. <strong>(evaluate)</strong> Du sollst eine interne CA fuer ein 50-Service-Mesh aufsetzen. Zwei Optionen: (A) Root-CA und Issuing-CA beide im selben HSM, beide Privkeys <code>CKA_EXTRACTABLE=false</code>; (B) Root-CA Offline (Air-Gap-Maschine, ein Cert ausstellen pro Quartal), Issuing-CA online im HSM. Welche Option gewinnt, und welche **zwei** Bedrohungsszenarien aus dem Kapitel-Kontext entscheiden die Wahl?</summary>

Option **B** gewinnt. Szenario 1 — **Issuing-CA-Kompromittierung**: ein Angreifer mit Issuing-CA-Zugriff kann Leaf-Certs ausstellen, die Vertrauenskette bleibt aber durch Revocation der Issuing-CA reparierbar; bei (A) wuerde dieselbe Kompromittierung auch den Root treffen, und ein Root-Wechsel ist in 50 Services ein Mehr-Wochen-Projekt (alle Truststores tauschen). Szenario 2 — **Insider mit HSM-Zugriff**: bei (A) ist der Root-Key zur Laufzeit immer adressierbar, ein bösartiger Operator kann mit gestohlenen Admin-Credentials den Root direkt nutzen; bei (B) lebt der Root-Privkey in einem System, das per Default offline ist und nur per physischer Ceremony erreichbar wird. Der "ein-HSM-spart-Geld"-Reflex uebersieht: ein Root-Tausch ist die teuerste Operation in einer PKI, ihn unwahrscheinlich zu machen ist Geldsparen erster Ordnung. Im Lab macht Kap. 22 den HSM-CA-Key als Beispiel — produktiv steht der Root woanders.
</details>
