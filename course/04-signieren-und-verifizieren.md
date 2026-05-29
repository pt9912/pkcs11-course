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

## Mechanism-Falle

Wenn du mit `SHA256-RSA-PKCS` signierst, darfst du nicht noch einmal anders hashen oder mit falschem Padding verifizieren. Anwendung und Token müssen dieselbe Signatursemantik verwenden.

Bei RSA-PSS verschiebt sich die Aufteilung: ob die Anwendung oder das Token hasht, haengt davon ab, ob `CKM_RSA_PKCS_PSS` (Anwendung hasht) oder `CKM_SHA256_RSA_PKCS_PSS` (Token hasht) gewaehlt wird. Details in [11 — ECDSA und RSA-PSS](11-ec-und-pss.md).

## Eigenexperiment

Ändere den Mechanism testweise auf einen nicht unterstützten oder falschen Mechanism. Beobachte die Fehlermeldung. Genau so sieht HSM-Debugging im echten Leben aus. Strukturierte Aufgaben dazu findest du in [`exercises/02-key-signature.md`](../exercises/02-key-signature.md).
