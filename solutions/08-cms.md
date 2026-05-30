# Loesung 08 - CMS-Dokumentsignatur

## Bash-Pfad

```bash
make init-token gen-rsa import-cert
make cms-sign
make cms-verify
```

Erwartete `cms-verify`-Ausgabe (Auszug):

```text
CMS Verification successful
CMS Verify: OK
--- Signer-Info ---
  contentType: pkcs7-signedData (1.2.840.113549.1.7.2)
    signerInfos:
        digestAlgorithm:
            object: contentType (1.2.840.113549.1.9.3)
            object: signingTime (1.2.840.113549.1.9.5)
            object: messageDigest (1.2.840.113549.1.9.4)
        signatureAlgorithm:
```

## Tamper

```bash
make cms-sign
printf 'X' | dd of=lab/work/cms-document.txt bs=1 seek=0 conv=notrunc 2>/dev/null
make cms-verify
```

Erwartete Fehlermeldung:

```text
Verification failure
...
error: digest failure
```

Die Cert-Signatur ueber die `signedAttrs` bleibt mathematisch korrekt — der `messageDigest`-Wert darin passt aber nicht mehr zum manipulierten Klartext.

## Sprach-Demos

Jede Demo liefert am Ende zwei Zeilen:

```text
Self-Verify:    OK
OpenSSL Cross-Verify: OK
```

Der zweite Aufruf beweist, dass die in einer beliebigen Sprache erzeugte CMS-Struktur byte-kompatibel mit einem anderen Stack ist — das ist der eigentliche Sinn eines Standardformats.

## ASN.1-Lesehilfe

`openssl cms -cmsout -print` gibt Java-aehnlichen Pseudo-ASN.1 aus. Fuer eine reine ASN.1-Sicht:

```bash
openssl asn1parse -inform DER -in lab/work/cms-document.p7s | head -50
```

Hier sieht man die SEQUENCEs und SETs konkret mit Offsets — gut zum Verstehen, wo welcher Block liegt.

## Antworten zu den Reflexionsfragen

**messageDigest in signedAttrs:** Wenn der Signer nur den Hash signiert, kann ein Angreifer beliebige andere `signedAttrs` (etwa eine zukuenftige `signingTime`) anhaengen, ohne dass die Signatur ungueltig wird. Durch das Wrapping in `signedAttrs` und das Signieren der gesamten DER-Kodierung dieser Attribute werden Hash UND Metadaten gleichzeitig kryptografisch gebunden.

**1 GB Dokument:** Das Token sieht nur die DER-Kodierung der `signedAttrs` (~70 Byte). Der SHA-256-Hash des Dokuments wird host-seitig berechnet und nur als 32-Byte-Wert in das `messageDigest`-Attribut geschrieben. Selbst Multi-GB-Dokumente verursachen also nur eine einzige RSA-Operation auf dem HSM.

**Warum eine Bruecke?** CMS/PKCS#7-Libs sind generisch und kennen keine HSMs. Sie wollen einen abstrakten "Signer", der Bytes nimmt und Signaturen zurueckliefert. Die Bruecke (Engine, Provider, Adapter) implementiert dieses Signer-Interface mit einem PKCS#11-Aufruf — das saubere Trennen der Schichten ist ein gutes Designprinzip: die CMS-Lib muss nichts ueber Token wissen, das Token nichts ueber CMS.

**Faelschbar / nicht faelschbar:** `signingTime` kommt aus dem Signer-Prozess und ist trivial faelschbar, wenn der Angreifer den Signer kontrolliert (aber dann hat er sowieso den HSM-Zugriff). Was OHNE HSM-Zugriff **nicht** faelschbar ist: die RSA-Signatur ueber die `signedAttrs`. Wer eine andere Signatur produzieren will, braucht den privaten Key — der ist im HSM. Ohne ihn bleibt nur der Versuch, eine vorhandene Signatur zu reparieren, was am Hash-Vergleich scheitert.
