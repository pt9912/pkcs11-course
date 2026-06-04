# 14 — CMS-Dokumentsignatur (PKCS#7) mit HSM

> **Didaktischer Pfad:** Vorher → [`13-verschluesselung.md`](13-verschluesselung.md) · Nachher → [`15-streaming.md`](15-streaming.md)

## Lernziele

Nach diesem Kapitel kannst du:

- erklaeren, warum eine "rohe" RSA-Signatur fuer Dokumente nicht reicht.
- den Aufbau einer CMS-`SignedData`-Struktur lesen (signed attributes, signerInfo).
- ein Dokument detached mit dem HSM-Signing-Key signieren.
- die Signatur ueber `openssl cms -verify` (oder die jeweilige Sprach-Lib) pruefen.
- die zwei wiederkehrenden Bruecken-Probleme zwischen HSMs und Standard-CMS-Libs benennen.
- **(Bloom 5 — evaluate)** fuer einen neuen .NET-/JVM-/Go-Service entscheiden, welche **eine** CMS-Library die geringste Brueckenkomplexitaet zum HSM produziert — und bei welcher Plattform die Bibliotheks-Wahl die Architektur dominiert (Linux-`SignedCms`-Verbot).

> **Geschaetzte Bearbeitungszeit:** ~75 min (Lesen + Bash-Worked-Example 30 min + ein Sprach-Faded 25 min + ASN.1-Eigenexperiment 20 min). Das `signedAttrs`-Indirekt-Modell und die Bridge-Patterns wiederholen sich in Kap. 22 (CSR) — Zeit hier reinzustecken zahlt sich dort aus.

## Lab-Bezug

```bash
make import-cert            # Voraussetzung: signing-key + Cert im Token
make cms-sign               # Bash: openssl cms -sign via pkcs11-engine
make cms-verify             # Bash: openssl cms -verify mit signer cert
make java-cms-demo          # JCA/SunPKCS11 + BouncyCastle
make go-cms-demo            # miekg/pkcs11 + digitorus/pkcs7 + crypto.Signer-Bridge
make kotlin-cms-demo        # Kotlin-Pendant zu Java
make csharp-cms-demo        # Pkcs11Interop + BouncyCastle.Cryptography
```

## Warum CMS und nicht "nur RSA"?

Eine rohe RSA-Signatur ist ein 256-Byte-Block ohne Kontext: kein Cert, kein Timestamp, kein Hash-Algorithmus-Hinweis. Wer das verifizieren will, muss out-of-band wissen:

- mit welchem Public Key,
- ueber welche Bytes genau,
- mit welchem Hash-Algorithmus,
- ob attached oder detached.

CMS (Cryptographic Message Syntax, RFC 5652 — frueher PKCS#7) packt all das in einen standardisierten ASN.1-Container:

```
ContentInfo
  contentType: signedData
  content: SignedData
    digestAlgorithms          # SHA-256
    encapContentInfo
      eContentType: data
      eContent: [0] OPTIONAL OCTET STRING  # NICHT vorhanden bei detached
    certificates              # Signer-Cert (und ggf. Chain)
    signerInfos: SET OF SignerInfo
      sid: IssuerAndSerialNumber
      digestAlgorithm
      signedAttrs
        contentType            # signedData
        signingTime            # UTCTime
        messageDigest          # SHA-256(eContent)
      signatureAlgorithm: sha256WithRSAEncryption
      signature                # RSA-Signatur ueber DER(signedAttrs)
```

**Wichtig:** Signiert wird **nicht** das Dokument direkt. Signiert wird die DER-Kodierung der `signedAttrs`-Menge — die enthaelt den Hash des Dokuments als ein Attribut. So binden contentType, signingTime und messageDigest kryptografisch zusammen.

## Attached vs Detached

Beide Varianten kommen vor:

| Variante | `eContent` | Aufbewahrung | Typische Anwendung |
|---|---|---|---|
| Attached | enthalten | eine Datei (`.p7m`) | S/MIME-Mail, kleine Vertraege |
| Detached | leer | zwei Dateien (`document.txt` + `document.p7s`) | PDF-Signaturen, grosse Files, Audit-Trails |

Dieses Lab benutzt **detached** — passt zum gewohnten Sign/Verify-Modell aus Kapitel 4 (`data.txt` + `data.sig`).

## Bridge-Problem 1: HSM-Key in Standard-CMS-Libs einbinden

Jede CMS-Bibliothek erwartet einen "Signer", der irgendwann tatsaechlich Bytes signiert. Der HSM-Private-Key ist aber nicht extractable und liegt hinter PKCS#11. Wie kommt das zusammen?

Ueberblick — vier Pfade, in jedem verlaesst der private Key den HSM nicht (nur die DER-Kodierung der signedAttrs, ~70 Byte, wandert hin; 256 Byte Signatur zurueck):

| Stack | Bruecke (kurz) |
|---|---|
| Bash + OpenSSL | Direkt: openssl-engine kapselt PKCS#11. |
| Java/Kotlin + JCA + BouncyCastle | Direkt: BC vertraut der JCA-Signature-Pipeline. |
| Go + miekg/pkcs11 + digitorus/pkcs7 | Adapter ueber `crypto.Signer`. |
| C# + Pkcs11Interop + BouncyCastle.Cryptography | Adapter ueber `ISignatureFactory`. |

Wir arbeiten **einen Pfad vollstaendig durch** (Bash) und lassen dich an den anderen drei pruefen, ob du das Schema verstehst.

### Worked Example: der Bash-Pfad (vollstaendig)

```bash
make cms-sign      # alles in einem Aufruf
make cms-verify    # Cross-Tool-Verify
```

**Schritt 1 — Vorbereitung.** `make import-cert` legt den signing-key plus das self-signed Cert auf `CKA_ID=01` ab. Die Engine wird OpenSSL ueber eine temporaere `openssl.cnf` zugaenglich gemacht (siehe Kap. 05) — Schema `engine:pkcs11:<uri>`.

**Schritt 2 — `signedAttrs` werden vom CMS-Builder gebaut, nicht vom Signer.** `openssl cms -sign -binary -nodetach …` (oder `-md sha256 -outform DER -in lab/work/cms-document.txt`) konstruiert intern die `SignerInfo` mit `contentType`, `signingTime`, `messageDigest` als signed attributes. Du gibst nur Input-File und Cert-Pfad an.

**Schritt 3 — Engine ruft `C_Sign` auf den DER-`signedAttrs`-Bytes.** Genau **eine** PKCS#11-Operation pro CMS: `C_SignInit(CKM_SHA256_RSA_PKCS, signing-key-handle)`, dann `C_Sign(der_signed_attrs)`. Die rund 70 Byte signedAttrs wandern zum HSM, 256 Byte Signatur kommen zurueck. Das Dokument selbst sieht der HSM **nie** — der Hash daraus steht als `messageDigest`-Attribut bereits in den signedAttrs.

**Schritt 4 — Output ist eine `.p7s`-Datei mit detached SignedData.** `openssl cms -verify -binary -inform DER -in lab/work/cms-document.p7s -content lab/work/cms-document.txt -CAfile lab/work/cert.pem -out /dev/null` zeigt `CMS Verification successful`. Der Verifier macht intern: SignerInfo lesen, `messageDigest`-Attr lesen, Doku hashen, vergleichen, signature-Bytes mit Pubkey gegenpruefen.

Der gewonnene Schema-Kern: **Der HSM signiert die `signedAttrs`-DER-Bytes (klein, ~70 B), nicht das Dokument. Die CMS-Library macht die ASN.1-Verpackung. Die Bruecke ist je nach Stack entweder direkt (Bash-Engine, Java-Provider) oder ueber einen Adapter (Go-`crypto.Signer`, C#-`ISignatureFactory`).**

### Faded Examples — die drei Sprach-Pfade

Du hast jetzt das Schema. Pruefe an den drei anderen Pfaden, ob du die jeweils relevante Variante erkennst. Pro Pfad: kurze Tabellen-Beschreibung und **drei Leitfragen**, die du beantworten koennen solltest, bevor du `make ...-cms-demo` aufrufst.

#### Java/Kotlin (`pkcs11-cms-demo`)

| Bruecke | Mechanism-Wahl |
|---|---|
| `JcaContentSignerBuilder("SHA256withRSA").setProvider(sunPkcs11Provider).build(privKey)` — BC bekommt einen JCA-`Signature`-konformen Signer und leitet alles ueber SunPKCS11 ans HSM. | JCA-Mapping: `SHA256withRSA` → `CKM_SHA256_RSA_PKCS`. Token hasht und paddet. |

Leitfragen:

1. **Welche Bibliothek baut die `signedAttrs`-DER-Bytes — JCA, SunPKCS11 oder BouncyCastle?** (Tipp: BC; SunPKCS11 weiss nichts von CMS.)
2. **Was wuerde passieren, wenn du `SHA256withRSAandMGF1` (PSS) statt `SHA256withRSA` setzt — laeuft das durch, und wo wuerde es in Kap. 11 behandelt?**
3. **Warum reicht hier ein Cert im Token, in Bash und Go aber nicht?** Vergleiche `KeyStore.getCertificate(alias).getPublicKey()` mit dem Bash-Pfad.

#### C# (`Pkcs11CmsDemo`)

| Bruecke | Mechanism-Wahl |
|---|---|
| `ISignatureFactory` (BouncyCastle.Cryptography) liefert einen `IStreamCalculator<IBlockResult>`, der `signedAttrs`-Bytes puffert. Im `IBlockResult.GetResult()`-Callback wird `session.Sign(CKM_SHA256_RSA_PKCS, …)` aufgerufen. | wie Java. Token hasht und paddet. |

Leitfragen:

1. **Warum nutzt der Lab-Code BC.Cryptography und nicht `System.Security.Cryptography.Pkcs.SignedCms`?** Antwort steht in Bridge-Problem 2 weiter unten.
2. **Welche zwei API-Schichten sind aufeinander gestapelt** — Pkcs11Interop unten, BouncyCastle.Cryptography oben? Was tut welche?
3. **Wie laeuft `signedAttrs`-Bau hier ab?** BC-internal vor dem `ISignatureFactory`-Aufruf oder erst danach? (Antwort: davor — `ISignatureFactory` sieht nur die fertigen DER-Bytes.)

#### Go (`pkcs11-cms-demo`)

| Bruecke | Mechanism-Wahl |
|---|---|
| Eigener Typ `pkcs11RSASigner` implementiert `crypto.Signer`. Sein `Sign(rand io.Reader, digest []byte, opts crypto.SignerOpts) (sig []byte, err error)` baut die `DigestInfo`-Struktur (SHA-256 OID + Hash) und ruft `C_Sign(CKM_RSA_PKCS, digestinfo_bytes)`. `digitorus/pkcs7` nimmt den Adapter via `signedData.AddSigner(cert, signer, …)`. | DigestInfo wird in der Anwendung gebaut → `CKM_RSA_PKCS` (Token paddet nur, hasht nicht). |

Leitfragen:

1. **Warum baut der Go-Pfad die DigestInfo selbst, der Bash-Pfad aber nicht?** Verbinde mit Kap. 04 §"Wer hasht, wer paddet?". (Tipp: das Go-`crypto.Signer`-Interface gibt dem Signer einen vorgefertigten Hash; das HSM kann von dem nicht wissen, *was* fuer ein Hash das ist — DigestInfo macht das explizit.)
2. **Was passiert, wenn du im Adapter `CKM_SHA256_RSA_PKCS` statt `CKM_RSA_PKCS` setzt — und das Dokument als Input gibst?** Reproduziere und vergleiche die Fehlermeldung.
3. **Warum macht `digitorus/pkcs7` kein UnsignedAttributes-API verfuegbar?** Das wird in Kap. 25 fuer den TSA-Embedding-Pfad relevant.

### Wenn alle vier Pfade im Kopf zusammenkommen

Reale CMS-Workloads (S/MIME-Mail-Gateways, eIDAS-Vertragsdienste, Code-Signing-Pipelines) waehlen den Stack nicht nach Geschmack: die Bridge ist der knappste Faktor. Wer eine der vier Bruecken einmal durchgebaut hat, hat den Reflex: **erst sich klarmachen, *welche* Bytes der HSM tatsaechlich signiert (signedAttrs vs DigestInfo vs raw), dann die Bibliothek darum bauen.**

## Bridge-Problem 2: SignedCms auf Linux funktioniert nicht mit HSM-Keys

.NET hat eine eingebaute CMS-Klasse `System.Security.Cryptography.Pkcs.SignedCms`. Sie verlangt aber, dass das Signer-Cert via `X509Certificate2.CopyWithPrivateKey(RSA)` mit einer RSA-Instanz verknuepft ist. Auf **Linux** prueft das OpenSSL-Backend dabei die Mathematik (n = p·q), indem es `ExportParameters(true)` aufruft. Ein HSM-Key kann diese privaten Felder per Definition nicht liefern (`CKA_EXTRACTABLE=false`).

Daraus folgt fuer dieses Lab: die C#-Demo nutzt **nicht** `SignedCms`, sondern BouncyCastle.Cryptography — analog zum Java/Kotlin-Pfad. Auf Windows mit dem CNG-Backend waere `SignedCms` machbar (CNG hat keine OpenSSL-Math-Validierung), aber unser Container ist Linux.

## Signed-Attribute, die jedes CMS sehen sollte

| OID | Name | Inhalt |
|---|---|---|
| 1.2.840.113549.1.9.3 | contentType | OID der eContentType (hier: `data`) |
| 1.2.840.113549.1.9.4 | messageDigest | SHA-256 ueber das eContent |
| 1.2.840.113549.1.9.5 | signingTime | UTC-Zeitpunkt der Signatur (vom Signer gesetzt, **nicht** vom Empfaenger validierbar) |

`signingTime` ist die haeufigste Angriffsflaeche: sie kommt aus dem Signer-Prozess, nicht aus einer vertrauenswuerdigen Zeitquelle. Wer Beweiskraft braucht, ergaenzt einen RFC-3161-Timestamp (`unsignedAttrs.signatureTimeStampToken`) — das ist Stoff fuer ein eigenes Kapitel.

## Eigenexperiment

- Aendere ein Byte in `lab/work/cms-document.txt` nach dem Sign und rufe `make cms-verify` — der `messageDigest`-Attribut-Vergleich schlaegt fehl, openssl meldet `Verification failure`.
- Tausche in einer Sprach-Demo den Hash auf SHA-384 (Java: `SHA384withRSA`, Go: `crypto.SHA384`, C#: passende OID). Beobachte, dass das resultierende `signatureAlgorithm` im SignerInfo sich aendert und dass openssl trotzdem verifiziert, solange Algorithm und Digest konsistent gewaehlt sind.
- Lass dir die SignedData-Struktur anzeigen: `openssl cms -cmsout -print -inform DER -in lab/work/cms-document.p7s`. Die ASN.1-Felder werden direkt lesbar.

Strukturierte Aufgaben in [`exercises/08-cms.md`](../exercises/08-cms.md).

## Selbsttest

<details>
<summary>1. Was wird in einer CMS-Signatur tatsaechlich signiert — das Dokument oder die <code>signedAttrs</code>?</summary>

Die DER-Kodierung der `signedAttrs`-Menge. Sie enthaelt den Hash des Dokuments als ein Attribut (`messageDigest`), ist also ein indirektes Commitment auf das Dokument. Vorteil: contentType, signingTime und messageDigest sind alle kryptographisch zusammengebunden.
</details>

<details>
<summary>2. Warum funktioniert <code>System.Security.Cryptography.Pkcs.SignedCms</code> auf Linux nicht mit HSM-Keys?</summary>

Das OpenSSL-Backend von .NET ruft `ExportParameters(true)` auf das `X509Certificate2.CopyWithPrivateKey(RSA)`-Pendant — es will die Mathematik (n=p·q) pruefen. Ein HSM-Key kann die privaten Felder per Definition nicht liefern (`CKA_EXTRACTABLE=false`, `CKA_SENSITIVE=true`). Daher: BouncyCastle.Cryptography auf Linux, `SignedCms` nur auf Windows-CNG-Backend (das keine Math-Validierung macht).
</details>

<details>
<summary>3. Was ist die Hauptlimitation von <code>signingTime</code> in CMS, und welcher Mechanismus loest sie?</summary>

`signingTime` ist die Zeit, **die der Signer behauptet**. Sie kommt aus der Signer-Uhr, ist nicht extern verifizierbar, und kann vor- oder rueckdatiert werden. Loesung: ein RFC-3161-Timestamp als `unsignedAttribute.signatureTimeStampToken` (CAdES-T, Kap. 25) — eine vertrauenswuerdige Time Stamping Authority signiert mit einer audit-zertifizierten Uhr.
</details>

<details>
<summary>4. <strong>(evaluate)</strong> Du sollst fuer ein neues Vertrags-Signing-Backend auf .NET 8 (Linux-Deploy) und einen Document-Hub auf Spring Boot eine CMS-Bibliothek empfehlen. Welche **eine** Bibliothek pro Stack — und welcher Faktor entscheidet bei .NET, der bei der JVM nicht greift?</summary>

.NET: **BouncyCastle.Cryptography**, nicht `System.Security.Cryptography.Pkcs.SignedCms`. JVM: **BouncyCastle (bcpkix)**. Der .NET-spezifische Faktor ist das `SignedCms`-Linux-Verbot — das OpenSSL-Backend versucht `ExportParameters(true)` zur Math-Validierung des Keys, was bei `CKA_EXTRACTABLE=false` per Definition scheitert. Auf Windows haette das CNG-Backend dasselbe `SignedCms` problemlos angenommen. Auf der JVM gibt es kein vergleichbares Math-Validation-Verbot — die Wahl gegen `SunPKCS11+CMS` faellt aus anderem Grund (SunPKCS11 hat schlicht kein CMS-API, BC liefert es ueber `CMSSignedDataGenerator`). Lehrwert: die Bibliotheks-Wahl ist im .NET-Fall **vom OS** abhaengig, im JVM-Fall nur vom Feature-Set. Wer diesen Unterschied nicht kennt, baut den Service zwei Mal um.
</details>
