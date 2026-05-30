# 14 — CMS-Dokumentsignatur (PKCS#7) mit HSM

## Lernziele

Nach diesem Kapitel kannst du:

- erklaeren, warum eine "rohe" RSA-Signatur fuer Dokumente nicht reicht.
- den Aufbau einer CMS-`SignedData`-Struktur lesen (signed attributes, signerInfo).
- ein Dokument detached mit dem HSM-Signing-Key signieren.
- die Signatur ueber `openssl cms -verify` (oder die jeweilige Sprach-Lib) pruefen.
- die zwei wiederkehrenden Bruecken-Probleme zwischen HSMs und Standard-CMS-Libs benennen.

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

| Stack | Bruecke |
|---|---|
| Bash + OpenSSL | Direkt: `openssl cms -sign -engine pkcs11 -keyform engine -inkey "pkcs11:…"`. Die Engine kapselt PKCS#11. |
| Java/Kotlin + JCA + BouncyCastle | Direkt: `new JcaContentSignerBuilder("SHA256withRSA").setProvider(sunPkcs11Provider).build(privKey)`. BC vertraut der JCA-Pipeline. |
| Go + miekg/pkcs11 + digitorus/pkcs7 | Adapter: eigener Typ implementiert `crypto.Signer`, dessen `Sign()` ein DigestInfo wrappt und `C_Sign` mit `CKM_RSA_PKCS` aufruft. digitorus/pkcs7 nimmt den Adapter via `AddSigner(cert, signer, …)`. |
| C# + Pkcs11Interop + BouncyCastle.Cryptography | Adapter: eigener `ISignatureFactory` liefert einen `IStreamCalculator<IBlockResult>`, der gepufferte signedAttrs-Bytes an einen Callback uebergibt — der ruft `Session.Sign(CKM_SHA256_RSA_PKCS, …)`. |

In **allen** Faellen verlaesst der private Key den HSM nicht. Nur die DER-Kodierung der signedAttrs (~70 Byte) wandert zum HSM hin und 256 Byte Signatur zurueck.

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
