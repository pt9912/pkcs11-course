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
