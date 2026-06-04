# Loesung 02 - Key erzeugen und signieren

```bash
make gen-rsa
make list-objects
make sign
make verify
```

Erwartete Objekte:

- Public Key Object mit Label `signing-key`
- Private Key Object mit Label `signing-key`
- beide mit ID `01`

Erwartete Verifikation:

```text
Verified OK
```

Bonus:

```bash
echo changed >> lab/work/data.txt
make verify
```

Die Verifikation muss fehlschlagen, weil die Signatur zu den urspruenglichen Daten gehoert.

## Antworten zu den Reflexionsfragen

**1. (Recall) OpenSSL verifiziert ohne privaten Key:** Asymmetrische Signaturen sind genau dafuer gebaut. Der Signer hat den privaten Key (`d` bei RSA, `k` bei ECDSA), der Verifier braucht nur den oeffentlichen Teil (`n, e` bzw. die Kurve + Punkt). RSA-Verifikation rechnet `s^e mod n` und prueft, ob das Ergebnis das DigestInfo-Pattern enthaelt; der Privkey wird mathematisch nicht gebraucht. Im Lab exportiert `make sign` den Pubkey aus dem Token als DER-Datei (Pubkeys haben `CKA_SENSITIVE=false` und `CKA_EXTRACTABLE=true` per Default) und gibt sie OpenSSL.

**2. (Analyse) Wo hasht wer?** `lab/scripts/06-sign.sh` ruft `pkcs11-tool --mechanism SHA256-RSA-PKCS` (= `CKM_SHA256_RSA_PKCS`) auf. Das ist die Token-hasht-Variante: das gesamte File `data.txt` wandert ins Token, das Token bildet selbst SHA-256, paddet mit PKCS#1-v1.5 und signiert. `lab/scripts/07-verify.sh` ruft `openssl dgst -sha256 -verify` auf — OpenSSL hasht hier ein zweites Mal, vergleicht aber nicht die Hashes, sondern bildet seinerseits `s^e mod n` und prueft, ob das ergebene Pattern `DigestInfo(SHA-256, hash(data))` ist. Das passt nur, wenn beide Seiten dasselbe Hash-Verfahren meinen. Die Mechanism-Familien-Tabelle aus Kap. 04 §"Wer hasht, wer paddet?" hat genau diese Falle dokumentiert.

**3. (Evaluate) Mechanismus-Umstellung auf `RSA-PKCS`:** Die Schluesselfrage ist *"Wer baut dann die DigestInfo-Struktur — der Token-Caller oder ein anderer Stack?"*. Bei `CKM_RSA_PKCS` hasht das Token nicht; der Aufrufer muss die vollstaendige DER-Struktur `SEQUENCE { algorithmIdentifier, OCTET STRING hash }` selbst bilden und uebergeben. `pkcs11-tool --mechanism RSA-PKCS` macht das **nicht automatisch** — wenn die Eingabe nur der rohe Hash ist, signiert der Token ihn als rohe Zahl, und `openssl dgst -verify` lehnt das mit `Verification Failure` ab (oder im schlimmeren Fall mit einem Bouncy-Castle-Verifier mit `SignatureException: bad DigestInfo`). Die wahrscheinliche Konsequenz waere ein Lab-Lauf, der "korrekt aussieht" (Token sagt OK) aber Cross-Tool-Verify scheitert — genau die Stolperfalle, die in Kap. 14 (CMS) und Kap. 22 (CSR) dazu fuehrt, dass die Sprach-Demos bei `CKM_SHA256_RSA_PKCS` bleiben.
