# Uebung 14 - Key Wrap / Unwrap

## Ziel

Du erzeugst einen KEK, exportierst einen Payload-Key als backup-faehiges Blob, simulierst den Verlust des Originals und stellst den Key aus dem Blob wieder her — alles ohne dass Plaintext-Schluesselmaterial das HSM verlaesst.

## Vorbereitung

```bash
make init-token
```

## Aufgabe 1 — KEK + Backup-Blob (Bash)

```bash
make gen-kek
make wrap-backup
```

Erwartete Ausgabe:

```text
payload-key (ID=07) frisch generiert mit CKA_EXTRACTABLE=true.
Using encrypt algorithm AES-CBC-PAD
Key wrapped
--- Backup-Artefakte ---
Wrapped Key:  lab/work/payload-key.wrapped (40 Bytes)
...
```

Pruefe: `payload-key.wrapped` ist 40 Byte (8 Byte Overhead ueber 32 Byte AES-Key, RFC 5649).

## Aufgabe 2 — Anti-Pattern: CKA_EXTRACTABLE=false

In `lab/scripts/55-wrap-key-backup.sh` das `--extractable`-Flag wegnehmen, frisch laufen lassen.

Erwartet: `error: PKCS11 function C_WrapKey failed: rv = CKR_KEY_UNEXTRACTABLE (0x6a)`.

Lesson: in Produktion **vor** dem Erzeugen entscheiden, ob ein Key jemals gewrappt werden darf — danach geht's nicht mehr.

## Aufgabe 3 — Vollstaendiger Roundtrip in einer Sprache

```bash
make go-wrap-demo
make csharp-wrap-demo
```

Erwartet (Go):

```text
--- Wrap/Unwrap Round-Trip ---
Original payload-key:    handle 3 (geloescht nach Wrap)
Restored payload-key:    handle 4 (neue Identitaet, gleiches Material)
Wrap-Blob:               /workspace/lab/work/go-wrap-backup.bin (40 Bytes)
...
Round-Trip: OK
```

Der **Handle** unterscheidet sich (3 vs 4) — das ist der Beweis: das ist nicht der Originalkey, sondern ein neu aufgebauter Token-Object mit demselben Material.

## Aufgabe 4 — Beobachten: identische Wrap-Outputs?

```bash
make go-wrap-demo
mv lab/work/go-wrap-backup.bin lab/work/go-wrap-backup.bin.1
make go-wrap-demo
diff lab/work/go-wrap-backup.bin.1 lab/work/go-wrap-backup.bin && echo IDENTISCH || echo UNTERSCHIEDLICH
```

Erwartet: UNTERSCHIEDLICH. AES-Key-Wrap nutzt einen integrity-check value (default 0xA6A6A6A6A6A6A6A6 bei RFC 3394, oder eine Laengenkodierung bei RFC 5649) und der Inhalt ist deterministisch — aber wir **erzeugen jedes Mal einen frischen** payload-key, weshalb das Blob anders aussieht. Bei demselben payload-key + demselben KEK wuerde das Blob identisch sein.

## Aufgabe 5 — Bonus: KEK loeschen, Wrap-Blob wertlos?

```bash
docker compose -f lab/docker-compose.yml run --rm pkcs11-lab bash -lc '
  pkcs11-tool --module /usr/lib/softhsm/libsofthsm2.so --login --pin 987654 \
    --token-label dev-token --delete-object --type secrkey --id 06
'
# Versuch jetzt:
make go-wrap-demo
```

Erwartet: Fehler "KEK nicht gefunden (CKA_ID=06 — make gen-kek?)". Ohne den KEK ist das Blob unbrauchbar — genau das ist der Sinn von Wrapping. Mit `make gen-kek` legt man **einen NEUEN** KEK an, der das alte Blob aber nicht oeffnen kann (anderes Material).

In Produktion: KEK-Verlust = Backup-Verlust. KEK muss separat (off-HSM, in einem zweiten HSM, mit M-of-N-Secret-Sharing) gesichert werden.

## Reflexionsfragen

- Was unterscheidet `C_WrapKey` semantisch von `C_Encrypt` mit dem gleichen Mechanism?
- Warum kann der KEK selbst **nicht** mit dem gleichen Mechanism gebackuppt werden?
- Wozu dient `CKA_WRAP_TEMPLATE` und welche Angreifer-Move verhindert es?
- Wenn dein produktiver KEK weg ist und du nur das gewrappte Blob hast: was kannst du tun?

## Musterloesung

Siehe `solutions/14-key-wrap.md`.
