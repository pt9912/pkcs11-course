# Loesung 14 - Key Wrap / Unwrap

## Bash-Backup

```bash
make wrap-backup
```

Artefakte in `lab/work/`:
- `payload-key.wrapped` (40 Bytes = 32 Byte AES-256 + 8 Byte RFC-5649-Overhead)
- `wrap-sample.iv.hex`, `wrap-sample.enc`, `wrap-sample.txt`

## Anti-Pattern ohne --extractable

```text
error: PKCS11 function C_WrapKey failed: rv = CKR_KEY_UNEXTRACTABLE (0x6a)
```

PKCS#11 §10.2.6: `CKA_EXTRACTABLE` ist eine **one-way**-Flag. Einmal auf FALSE gesetzt, geht sie nie zurueck auf TRUE. Der Workflow fuer backup-faehige Keys muss das **vor** der Erzeugung wissen.

## Roundtrip in Go

Final-Block:

```text
Original payload-key:    handle 3 (geloescht nach Wrap)
Restored payload-key:    handle 4 (neue Identitaet, gleiches Material)
Wrap-Blob:               /workspace/lab/work/go-wrap-backup.bin (40 Bytes)
Original-Klartext:       69 Bytes
Nach Decrypt mit Restore: 69 Bytes — match.
Round-Trip: OK
```

## Wrap-Outputs identisch?

```text
UNTERSCHIEDLICH
```

Jeder Go-Lauf erzeugt einen frischen `C_GenerateKey`-AES-Key (anderes Material) — das wrap-output sind also unterschiedliche Bytes. Ein Tausch von "selber payload-key + selber KEK" wuerde dasselbe Blob produzieren (AES-Key-Wrap ist deterministisch).

## KEK loeschen

```text
Fehler: KEK nicht gefunden (CKA_ID=06 — make gen-kek?)
```

Nach `make gen-kek` wird ein neuer KEK mit anderem Material angelegt. Das alte Blob ist mit dem alten KEK verschluesselt — der existiert nicht mehr. Symptom beim Unwrap: `CKR_ENCRYPTED_DATA_INVALID` oder `CKR_WRAPPED_KEY_INVALID`.

## Antworten zu den Reflexionsfragen

**`C_WrapKey` vs `C_Encrypt`:** semantisch fast identisch (beide nehmen Klartext-Bytes, geben Ciphertext zurueck). Operationell unterschiedlich:
- `C_Encrypt` operiert auf **Daten**, die der Aufrufer schon hat.
- `C_WrapKey` operiert auf **HSM-Objekten** mit `CKA_SENSITIVE=true` — Werte, die der Aufrufer **gar nicht** kennt. Der Token sieht: "okay, du willst das Material des Objekts X mit dem Key Y wrappen", liest intern `CKA_VALUE` des Source-Objekts (das fuer den Aufrufer unsichtbar ist), und verschluesselt es. Aufrufer bekommt das Ciphertext-Blob, ohne je den Plaintext gesehen zu haben.

Audit-Logs unterscheiden die beiden: `C_Encrypt`-Calls sind millionenfach pro Stunde normal, `C_WrapKey`-Calls auf produktive KEKs sind selten und sollten alarmieren.

**KEK selbst backuppen:** Rekursives Problem. Wer den KEK wrappen will, braucht einen "KEK-KEK", der dann auch gebackuppt werden muss, und so weiter. Reale Loesungen:
1. **HSM-Backup-Format** (Vendor-spezifisch): Thales/Utimaco haben ein Hersteller-internes Backup-Format, das den gesamten HSM-State (inkl. Master-Wrapping-Key) verschluesselt unter einem **Smartcard-Quorum** (M-of-N) sichert. Erfordert physischen Zugriff auf die Smartcards beim Restore.
2. **HSM-Cluster**: zwei oder mehr HSMs werden bei der Einrichtung gepairt, der KEK wird auf alle synchronisiert. Disaster Recovery = "anderen HSM aus dem Cluster nutzen".
3. **Cloud-KMS**: man hat ueberhaupt keinen "eigenen" KEK, sondern delegiert an den Cloud-Provider, der HA und Backup selbst macht.

**`CKA_WRAP_TEMPLATE`:** verhindert "Privilege Escalation per Unwrap". Beispiel-Angreifer-Szenario:
- Angreifer hat Zugriff auf das Wrap-Blob eines hochsensiblen Keys.
- Angreifer hat (irgendwie) Zugriff auf den KEK + eine PKCS#11-Session.
- Angreifer ruft `C_UnwrapKey` mit Template `{CKA_EXTRACTABLE=true, CKA_SENSITIVE=false}` — der restaurierte Key ist plain abrufbar.

Mit `CKA_WRAP_TEMPLATE` am KEK gibt das Token vor: "alles, was unter mir unwrappt wird, hat **automatisch** CKA_EXTRACTABLE=false, CKA_SENSITIVE=true". Angreifer-Template wird ignoriert oder mit `CKR_TEMPLATE_INCONSISTENT` abgelehnt.

**KEK weg, nur Blob da:** dann ist das Blob unleseliche Bytes. Das ist by-design — die einzige Hoffnung ist:
- Wenn der ehemalige KEK in einem zweiten HSM existiert (Cluster) — dort restoren.
- Wenn man HSM-internes Backup (Vendor-Format) hat: Smartcards einsammeln, KEK rekonstruieren, dann Blob unwrappen.
- Wenn keins von beidem: der gewrappte Key ist verloren. Daten, die mit dem gewrappten Key verschluesselt waren, sind ebenfalls weg.

Die wichtigste Lesson: **KEK-Verlust ist Daten-Verlust**. Backup-Strategie braucht zwei voneinander unabhaengige Pfade fuer den KEK.
