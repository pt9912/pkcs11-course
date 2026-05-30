# Loesung 07 - Hybride Verschluesselung

## Bash-Pfad

```bash
make gen-rsa-wrap
make list-objects        # zeigt jetzt 'wrap-key' mit ID=03
make encrypt
make decrypt
```

Erwartete Ausgabe von `make decrypt`:

```text
Klartext: lab/work/document.dec
Round-Trip OK
```

## Aufgabe 3 — Tampering

```bash
make encrypt
printf 'X' | dd of=lab/work/document.enc bs=1 seek=0 conv=notrunc 2>/dev/null
make decrypt
```

Erwartet: das Skript bricht ab. Die Python-Helper-Ausgabe enthaelt:

```text
AES-GCM: ungueltiger Auth-Tag (Datei wurde veraendert oder falscher Key)
```

Der HSM hat den AES-Key trotzdem korrekt entpackt — das Tamper-Signal kommt **erst** aus der AES-GCM-Pruefung. Wer den Wrap-Layer ohne GCM nutzt (z.B. AES-CBC), bekommt dieses Signal **nicht** und akzeptiert manipulierte Daten still.

## Aufgabe 4 — Sprach-Demos

Erwartete Ausgabe (Go-Beispiel):

```text
Token:        dev-token
Wrap-Key-ID:  03
Wrapped Key:  /workspace/lab/work/go-wrapped-key.bin (256 Bytes)
Ciphertext:   /workspace/lab/work/go-document.enc (… Bytes inkl. GCM-Tag)
Round-Trip:   OK
```

Bei Java/Kotlin steht zusaetzlich `OAEP-Hash: SHA-1 (SoftHSM-Quirk + SunPKCS11-Limitation)` — der Hintergrund dazu ist in `course/13-verschluesselung.md` beschrieben.

## Antworten zu den Reflexionsfragen

**AES-Key auf dem Host:** Hybride Verschluesselung minimiert die Anzahl HSM-Calls pro Dokument auf genau einen (Decrypt des Wrapped-Keys). Laege der AES-Key permanent im HSM, muesste fuer jedes Daten-Chunk eine HSM-Operation laufen — performancekritisch fuer grosse Dateien. Ausserdem braucht der HSM dann das passende `CKM_AES_*`-Mechanism-Profil, was viele Token-Profile bewusst nicht enthalten.

**Java/Kotlin Software-OAEP:** SunPKCS11 registriert nur `RSA/ECB/PKCS1Padding` und `RSA/ECB/NoPadding`. OAEP-Padding gibt es im SunPKCS11-Cipher-Pfad nicht. Es muss entweder bei einem anderen Provider (SunJCE) mit dem extrahierten Pubkey verschluesselt werden oder die OAEP-Pad-Algebra wird in der Anwendung auf rohes RSA-Decrypt-Output gerechnet — genau das macht die Demo.

**AES-Key wiederverwendet:** Mit AES-GCM ist **(Key, IV)-Paar einmalig** zwingend. Selber Key + neuer IV ist erlaubt; selber Key + selber IV bricht GCM komplett (Forgery, Plaintext-Recovery). Im hybriden Aufbau ist der saubere Weg: jeder neue AES-Key pro Dokument, **dann** ist die IV-Wahl unkritisch.

**Hash-Mismatch:** OAEP nutzt den Hash zur Padding-Berechnung. Wenn Sender SHA-256 und Empfaenger SHA-1 verwendet, schlaegt das Unpadding fehl — die `lHash`-Vergleich-Stelle im OAEP-Algorithmus erkennt das. Symptom: `CKR_DATA_INVALID` (PKCS#11-Pfad) oder `BadPaddingException`/`InvalidTag` weiter oben.
