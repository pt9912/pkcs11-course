# Uebung 08 - CMS-Dokumentsignatur

## Ziel

Du signierst ein Dokument detached im CMS-Format ueber den HSM-Signing-Key, verifizierst die Signatur, beobachtest Tamper-Erkennung und reproduzierst den Round-Trip in einer der vier Sprach-Demos. Bonus: Du laesst dir die ASN.1-Struktur einer SignedData anzeigen.

## Vorbereitung

```bash
make init-token gen-rsa import-cert
```

(Die `import-cert`-Stufe schreibt das self-signed Cert `lab/work/cert.pem` und legt es im Token unter ID=01 ab.)

## Aufgabe 1 — Bash-Sign/Verify

1. Schreib deinen Vertragstext nach `lab/work/cms-document.txt` (oder benutze den Default).
2. Signieren:
   ```bash
   make cms-sign
   ```
3. Verifizieren:
   ```bash
   make cms-verify
   ```
   Erwartet: `CMS Verify: OK` plus Listing der signed attributes (contentType, signingTime, messageDigest).

## Aufgabe 2 — Tamper-Erkennung

1. Sign einmal sauber durchlaufen lassen.
2. Eine einzige Byte-Position in `lab/work/cms-document.txt` aendern:
   ```bash
   printf 'X' | dd of=lab/work/cms-document.txt bs=1 seek=0 conv=notrunc
   ```
3. `make cms-verify` erneut starten.
4. Erwartet: `openssl cms -verify` meldet `Verification failure`. Begruendung: der `messageDigest` in den signedAttrs gehoert zum Original; das Cert + die Signatur sind weiterhin gueltig, aber der Hash matched den manipulierten Text nicht mehr.

## Aufgabe 3 — Sprach-Demo durchspielen

Waehle eine:

```bash
make go-cms-demo        # crypto.Signer-Bridge — das didaktisch ergiebigste Beispiel
make csharp-cms-demo    # BouncyCastle.Cryptography + ISignatureFactory
make java-cms-demo      # JCA + BouncyCastle, kuerzester Code-Pfad
make kotlin-cms-demo    # Kotlin-Pendant zu Java
```

Alle vier produzieren `lab/work/<sprache>-cms-document.p7s` und cross-verifizieren am Ende mit openssl — das beweist Standard-Interop.

## Aufgabe 4 — Bonus: ASN.1 lesen

```bash
openssl cms -cmsout -print -inform DER -in lab/work/cms-document.p7s | head -40
```

Suche im Output:
- `signerInfos` → `sid` (IssuerAndSerialNumber)
- `digestAlgorithm` (SHA-256-OID `2.16.840.1.101.3.4.2.1`)
- `signedAttrs` → drei Attribute mit OIDs `1.2.840.113549.1.9.3/4/5`
- `signatureAlgorithm: sha256WithRsaEncryption` (OID `1.2.840.113549.1.1.11`)
- `signature` als grosser OCTET STRING (256 Byte bei RSA-2048)

## Erwartete Ausgabe

- `make cms-sign` schreibt `lab/work/cms-document.p7s` (ca. 1.4 kB detached).
- `make cms-verify` endet mit `CMS Verify: OK` und einem signed-attrs-Auszug.
- Jede Sprach-Demo endet mit `Self-Verify: OK` plus `OpenSSL Cross-Verify: OK`.

## Reflexionsfragen

- Welche Aufgabe hat `messageDigest` in den signedAttrs — wozu nicht einfach den Hash direkt signieren?
- Was wird konkret signiert, wenn das Dokument 1 GB gross ist? Wandert das ganze Dokument zum HSM?
- Warum nutzen alle vier Sprach-Demos eine "Bruecke" zum HSM (Engine, Provider, Adapter) — was lehrt das ueber die API-Annahmen typischer Crypto-Libs?
- Welches Stueck im SignerInfo wuerde ein Angreifer ohne HSM-Zugriff faelschen koennen, welches nicht?

## Musterloesung

Siehe `solutions/08-cms.md`.
