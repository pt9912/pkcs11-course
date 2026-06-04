# 04 — Signieren und Verifizieren

## Lernziele

Nach diesem Kapitel kannst du:

- Daten mit einem privaten Key im Token signieren.
- den Public Key aus dem Token exportieren.
- eine Signatur mit OpenSSL ausserhalb des Tokens verifizieren.
- Hashing, Padding und Mechanism-Wahl als zusammenhaengendes Problem behandeln.

## Lab-Bezug

Passende Targets:

```bash
make gen-rsa
make sign
make verify
```

## Signieren

```bash
make sign
```

(Direktaufruf `lab/scripts/06-sign.sh` funktioniert ebenfalls, umgeht aber die `init-token` / `gen-rsa`-Dependency-Kette aus dem Makefile.)

Das Skript:

1. schreibt Testdaten nach `lab/work/data.txt`,
2. signiert über PKCS#11 mit dem privaten Schlüssel,
3. speichert die Signatur als `lab/work/data.sig`,
4. exportiert den Public Key als DER-Datei.

## Verifizieren

```bash
make verify
```

Die Verifikation passiert mit OpenSSL außerhalb des Tokens. Das ist wichtig: Signieren braucht den privaten Schlüssel. Verifizieren braucht nur den Public Key.

## Wer hasht, wer paddet? Die zwei RSA-Mechanism-Familien

Bei Hash-basierten RSA-Signaturen entscheidet der Mechanism-Name darueber, **wo** der Hash entsteht und **was** das Token tut.

| Mechanism | Eingabe vom Aufrufer | Was tut das Token? |
|---|---|---|
| `CKM_RSA_PKCS` | rohe Bytes, max. Modulus-Laenge minus 11 | nur PKCS#1-v1.5-Padding und RSA — KEIN Hashing |
| `CKM_SHA256_RSA_PKCS` | rohe Bytes beliebiger Laenge | SHA-256 hashen + Padding + RSA |

`CKM_RSA_PKCS` ist nicht falsch, aber tueckisch: wer daraus eine "SHA256withRSA"-konforme Signatur bauen will, muss vorher selbst eine vollstaendige **DigestInfo** bilden — eine DER-Struktur `SEQUENCE { algorithmIdentifier, OCTET STRING hash }` — und genau diese an `C_Sign` uebergeben. Sonst signiert das Token den Hash als rohe Zahl, und der Verifier findet keine valide DigestInfo. Die Lab-Sprachdemos in Kap. 14 (CMS) und Kap. 22 (CSR) sind genau aus diesem Grund auf `CKM_SHA256_RSA_PKCS` umgestellt, wo das Token hasht.

Bei RSA-PSS wiederholt sich das Muster: `CKM_RSA_PKCS_PSS` (Anwendung hasht, Token paddet) vs. `CKM_SHA256_RSA_PKCS_PSS` (Token hasht und paddet). Salt-Laenge und MGF-Hash muessen passen — Details in [11 — ECDSA und RSA-PSS](11-ec-und-pss.md).

## Mechanism-Falle

Wenn du mit `SHA256-RSA-PKCS` signierst, darfst du nicht noch einmal anders hashen oder mit falschem Padding verifizieren. Anwendung und Token müssen dieselbe Signatursemantik verwenden. Eine vollstaendige Uebersetzungstabelle ueber `pkcs11-tool`, OpenSSL und JCA hinweg steht in [08 — Debugging](08-debugging.md).

## Eigenexperiment

Ändere den Mechanism testweise auf einen nicht unterstützten oder falschen Mechanism. Beobachte die Fehlermeldung. Genau so sieht HSM-Debugging im echten Leben aus. Strukturierte Aufgaben dazu findest du in [`exercises/02-key-signature.md`](../exercises/02-key-signature.md).
