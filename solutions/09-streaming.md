# Loesung 09 - Multi-Part / Streaming

## Bash-Pfad

```bash
make gen-aes-stream stream-decrypt
```

Die Dependency-Kette des Makefiles laeuft automatisch durch: `init-token → gen-rsa → stream-sign → stream-verify` und `gen-aes-stream → stream-encrypt → stream-decrypt`. Final-Zeile `Round-Trip OK (104857600 Bytes)`.

## pkcs11-spy Beweis

Ausgabe-Snippet aus dem Spy-Log:

```text
C_SignInit
C_SignUpdate
C_SignUpdate
... (viele Update-Calls)
C_SignFinal
```

Bei 100 MB Input und 4 KB internem pkcs11-tool-Buffer entstehen ca. 25 000 Update-Calls (pkcs11-tool waehlt eine kleinere Default-Chunksize als unsere Sprach-Demos).

## Speicher-Messung

```text
Maximum resident set size (kbytes): 18432
Elapsed (wall clock) time: 0:01.23
```

~18 MB RSS bei 100 MB Input ist konstanter Overhead von Glibc + SoftHSM-Lib + Token-State. Bei 1 GB Input liegt die Zahl in der gleichen Groessenordnung — Streaming ist also nicht nur ein Code-Stil, sondern messbarer Speicher-Effekt.

## Antworten zu den Reflexionsfragen

**Warum `C_*Final` nicht vergessen:** Der Token haelt nach `C_*Init` einen State (Hash, Cipher, IV-Counter). Erst `C_*Final` finalisiert den Output (Hash-Padding, letzter Cipher-Block mit PKCS#7-Padding) **und** raeumt den State auf. Ohne Final bleibt die Session "active" — ein weiterer `C_*Init`-Versuch scheitert mit `CKR_OPERATION_ACTIVE`.

**Session mittendrin schliessen:** Token verwirft den State, alle bisherigen Update-Calls sind verloren. Bei Encrypt ist der teilweise geschriebene Ciphertext-File ohne den fehlenden Final-Block (16 Byte AES-Block + Padding) unbrauchbar — das Decrypt scheitert am unvollstaendigen letzten Block. **Lesson:** Streaming-Operationen immer in einem try/finally absichern.

**AES-CBC-PAD vs AES-GCM Streaming:** AES-CBC-PAD ist Inkrement-freundlich: jeder Update liefert vollstaendige verschluesselte Bloecke, Final liefert den letzten gepaddeten Block. AES-GCM erzeugt den 16-Byte-Auth-Tag erst nach Verarbeitung des **gesamten** Inputs — der Tag-Output kommt komplett aus `C_EncryptFinal`. Manche HSM-Firmwares vereinfachen das, indem sie Multi-Part fuer GCM verbieten und nur Single-Shot anbieten. Wer trotzdem AEAD streamen will: AES-CCM mit feststehender Laenge (CCM braucht die Total-Length im voraus) oder AES-GCM-SIV (deterministisch, weniger Stream-freundlich).

**Streamt das Token wirklich?** Drei Tests:

1. **pkcs11-spy zaehlen** (siehe oben): wenn nur ein `C_Sign` ohne Updates auftaucht, hat die Bridge alles gepuffert.
2. **Speicher messen** (`/usr/bin/time -v`): bleibt RSS unter ein paar zig MB bei mehrhundert-MB-Input, wird tatsaechlich gestreamt.
3. **Klar absurd grossen Input** (z.B. 5 GB) — wenn die Operation ohne OOM laeuft, gibt es nirgends einen Vollpuffer.
