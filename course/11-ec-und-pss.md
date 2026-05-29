# 11 — ECDSA und RSA-PSS

## Warum nicht nur RSA-PKCS#1-v1.5?

`SHA256-RSA-PKCS` ist weit verbreitet, aber für neue Systeme empfehlen NIST und BSI in der Regel:

- **RSA-PSS** statt RSA-PKCS#1-v1.5, weil PSS einen Sicherheitsbeweis und randomisiertes Padding hat.
- **ECDSA** mit Kurven wie `secp256r1` (NIST P-256) oder `secp384r1`, weil EC-Keys deutlich kleiner und Operationen schneller sind.

Ein HSM-Kurs ist unvollständig, ohne beide Varianten praktisch zu zeigen.

## ECDSA im Lab

Key erzeugen:

```bash
make gen-ec
```

Das Skript legt `ec-signing-key` mit `secp256r1` an. Signieren:

```bash
make sign-ec
```

Wichtig: `pkcs11-tool` gibt ECDSA-Signaturen standardmäßig als rohe `r || s`-Konkatenation aus. OpenSSL erwartet DER-codiertes `SEQUENCE { r, s }`. Deshalb steht im Skript `--signature-format openssl`. Wer das vergisst, bekommt eine korrekte Signatur, die OpenSSL trotzdem ablehnt — ein klassischer Stolperer.

Verifizieren:

```bash
make verify-ec
```

## RSA-PSS im Lab

Voraussetzung: RSA-Key existiert (`make gen-rsa`). Das Skript exportiert den Public Key bei Bedarf selbst aus dem Token und konvertiert ihn nach PEM für die OpenSSL-Verifikation.

```bash
make sign-pss
```

Wichtige PSS-Parameter:

| Parameter | Bedeutung |
|---|---|
| `--hash-algorithm SHA256` | Hash für die Nachricht |
| `--mgf MGF1-SHA256` | Mask Generation Function für PSS |
| `rsa_pss_saltlen:-1` (OpenSSL) | Salt-Länge gleich Hashlänge |

Wenn HSM und Anwendung unterschiedliche Salt-Längen oder unterschiedliche MGF-Hashes verwenden, schlägt die Verifikation fehl, obwohl Key und Daten korrekt sind. Das ist die häufigste PSS-Falle.

## JCA-Namen

| `pkcs11-tool` Mechanism | JCA `Signature` |
|---|---|
| `SHA256-RSA-PKCS` | `SHA256withRSA` |
| `RSA-PKCS-PSS` (SHA256/MGF1-SHA256/SaltLen=32) | `RSASSA-PSS` mit `PSSParameterSpec` |
| `ECDSA-SHA256` | `SHA256withECDSA` |
| `ECDSA-SHA384` | `SHA384withECDSA` |

## Wann was?

- Neuer Code, frei wählbar: ECDSA P-256 oder P-384.
- Bestehender PKI-Stack mit RSA-CA: RSA-PSS.
- Legacy-Kompatibilität: RSA-PKCS#1-v1.5.

## Harte Wahrheit

Viele HSMs unterstützen PSS, aber mit Einschränkungen bei MGF-Hash und Salt-Länge. Vor dem produktiven Einsatz: `pkcs11-tool --list-mechanisms` lesen, im Zweifel beim Hersteller nachfragen.
