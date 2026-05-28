# 04 — Signieren und Verifizieren

## Signieren

```bash
lab/scripts/06-sign.sh
```

Das Skript:

1. schreibt Testdaten nach `lab/work/data.txt`,
2. signiert über PKCS#11 mit dem privaten Schlüssel,
3. speichert die Signatur als `lab/work/data.sig`,
4. exportiert den Public Key als DER-Datei.

## Verifizieren

```bash
lab/scripts/07-verify.sh
```

Die Verifikation passiert mit OpenSSL außerhalb des Tokens. Das ist wichtig: Signieren braucht den privaten Schlüssel. Verifizieren braucht nur den Public Key.

## Mechanism-Falle

Wenn du mit `SHA256-RSA-PKCS` signierst, darfst du nicht noch einmal anders hashen oder mit falschem Padding verifizieren. Anwendung und Token müssen dieselbe Signatursemantik verwenden.

## Übung

Ändere den Mechanism testweise auf einen nicht unterstützten oder falschen Mechanism. Beobachte die Fehlermeldung. Genau so sieht HSM-Debugging im echten Leben aus.
