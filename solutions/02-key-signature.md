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

**OpenSSL verifiziert ohne privaten Key:** Asymmetrische Signaturen sind genau dafuer gebaut. Der Signer hat den privaten Key (`d` bei RSA, `k` bei ECDSA), der Verifier braucht nur den oeffentlichen Teil (`n, e` bzw. die Kurve + Punkt). RSA-Verifikation rechnet `s^e mod n` und prueft, ob das Ergebnis das DigestInfo-Pattern enthaelt; der Privkey wird mathematisch nicht gebraucht. Im Lab exportiert `make sign` den Pubkey aus dem Token als DER-Datei (Pubkeys haben `CKA_SENSITIVE=false` und `CKA_EXTRACTABLE=true` per Default) und gibt sie OpenSSL.

**`CKA_ID=01` und das Zertifikat:** PKCS#11-Objekte sind ueber ihre Attribute verknuepft — das Token kennt keinen "Datensatz Schluesselbund + Cert", sondern lose Objekte mit gleichen IDs. Wer in Kap. 05/06 das Zertifikat importiert, muss exakt `CKA_ID=01` setzen, damit Java-SunPKCS11 den `signing-key`-Alias findet und das Cert ihm zuordnet. Andere Stacks (Go, C#) ignorieren diese Konvention, weil sie direkt ueber `CKA_ID` arbeiten — fuer sie ist der CKA_ID-Wert nur ein Filter.
