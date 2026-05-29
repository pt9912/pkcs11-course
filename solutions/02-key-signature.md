# Lösung 02 — Key erzeugen und signieren

```bash
make gen-rsa
make list-objects
make sign
make verify
```

Bonus — direkt im `make shell`:

```bash
echo changed >> lab/work/data.txt
lab/scripts/07-verify.sh
```

Die Verifikation muss fehlschlagen, weil die Signatur zu den ursprünglichen Daten gehört.
