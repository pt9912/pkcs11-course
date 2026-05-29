# 03 — Token und Objekte

## Lernziele

Nach diesem Kapitel kannst du:

- einen SoftHSM-Token initialisieren.
- Slots und Token-Labels unterscheiden.
- RSA-Keypairs im Token erzeugen.
- Public Key, Private Key und Zertifikat als PKCS#11-Objekte einordnen.
- `CKA_LABEL` und `CKA_ID` fuer Anwendungen erklaeren.

## Lab-Bezug

Passende Targets:

```bash
make init-token
make list-slots
make gen-rsa
make list-objects
```

## Token initialisieren

```bash
lab/scripts/01-init-token.sh
```

Danach Slots anzeigen:

```bash
lab/scripts/02-list-slots.sh
```

SoftHSM verschiebt initialisierte Tokens häufig in einen anderen Slot. Verlasse dich deshalb nicht blind auf Slot `0`. Für Skripte ist `--token-label dev-token` stabiler.

## RSA-Keypair erzeugen

```bash
lab/scripts/04-generate-rsa.sh
```

Das erzeugt ein RSA-2048-Keypair im Token:

- Label: `signing-key`
- ID: `01`
- Verwendungszweck: Signieren/Verifizieren

## Objekte anzeigen

```bash
lab/scripts/05-list-objects.sh
```

Du solltest mindestens sehen:

- Public Key Object
- Private Key Object

## Objektidentität

In PKCS#11 sind `CKA_LABEL` und `CKA_ID` wichtig.

- `CKA_LABEL` ist menschenlesbar.
- `CKA_ID` ist für Zuordnung wichtig, z. B. Private Key ↔ Zertifikat.

Bei Java wird daraus oft ein Alias. Wenn Alias-Mapping nicht passt, findet Java den Schlüssel nicht, obwohl er im Token existiert.
