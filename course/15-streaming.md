# 15 — Multi-Part / Streaming-Operationen

## Lernziele

Nach diesem Kapitel kannst du:

- den Unterschied zwischen Single-Shot (`C_Sign`/`C_Encrypt`) und Multi-Part (`C_*Init` + `C_*Update*` + `C_*Final`) erklaeren.
- ein mehrere hundert MB grosses Dokument mit konstantem Speicherbedarf signieren und verschluesseln.
- begruenden, warum AES-CBC-PAD im Streaming-Modus ueberlebt, AES-GCM aber Probleme macht.
- typische HSM-Limits (Single-Shot-Buffer, Mechanism-Support fuer Update) benennen.

## Lab-Bezug

```bash
make gen-aes-stream         # AES-256 als CKO_SECRET_KEY auf ID=04
make stream-sign            # 100MB-Testfile via CKM_SHA256_RSA_PKCS signieren
make stream-verify          # mit openssl dgst -verify pruefen
make stream-encrypt         # AES-CBC-PAD-Streaming
make stream-decrypt         # Round-Trip
make java-stream-demo       # SunPKCS11 Signature.update + Cipher.update
make go-stream-demo         # miekg/pkcs11 expliziter Update/Final-Loop
make kotlin-stream-demo     # Kotlin-Pendant
make csharp-stream-demo     # Pkcs11Interop Stream-Ueberladungen
```

## Warum Multi-Part?

PKCS#11 single-shot Ops (`C_Sign`, `C_Encrypt`, `C_Decrypt`) verlangen, dass der gesamte Klartext in einem Aufruf uebergeben wird. Daraus folgen drei Limits:

1. **Speicher auf dem Host**: ein 1 GB Dokument muesste komplett im Heap stehen. Bei 50 parallelen Signiervorgaengen sind das 50 GB.
2. **Buffer im HSM**: viele HSMs haben einen festen RX-Buffer (oft 16 KB bis 1 MB). Single-Shot mit groesserem Input bricht mit `CKR_DATA_LEN_RANGE`.
3. **Latenz beim Streaming**: ein Pipeline-Producer kann nicht warten, bis das gesamte Dokument vorliegt — er will Bytes schon waehrend des Erzeugens signieren.

Multi-Part loest alle drei:

```
C_SignInit(session, mech, key)       # Token allokiert Hash-Context
loop:
  C_SignUpdate(session, chunk)       # Token aktualisiert Hash-State, KEIN Buffer-Bedarf
C_SignFinal(session)                 # Token finalisiert Hash, signiert, gibt Signatur zurueck
```

Der Token haelt den **State** (Hash, Cipher-Position, IV-Counter). Der Host muss nur Chunks reinpumpen.

## Mechanism-Wahl: Was streamt das Token, was nicht?

| Mechanism | Token streamt | Anmerkung |
|---|---|---|
| `CKM_RSA_PKCS` (rohes Sign) | Nein | Single-Shot, max ~245 Byte Input bei RSA-2048. |
| `CKM_SHA256_RSA_PKCS` | **Ja** | Token hashed selbst — `C_SignUpdate` reicht Chunks an SHA-256-State. |
| `CKM_AES_CBC_PAD` | **Ja** | Token speichert IV + letzten Block-Rest fuer Padding. |
| `CKM_AES_GCM` | Teilweise | Wegen Tag-Position am Ende muss `C_EncryptFinal` den Tag liefern; manche HSMs verbieten Multi-Part fuer GCM komplett. |
| `CKM_SHA256` (reines Digest) | Ja | `C_DigestUpdate`/`C_DigestFinal` — selten genutzt, weil Software-Hash schneller ist. |

Fuer **Bulk-Verschluesselung grosser Files** ist AES-CBC-PAD die HSM-freundlichste Wahl. Trade-off: kein AEAD — Tamper-Erkennung muss separat (HMAC-then-Encrypt, eigenes Auth-Tag) gebaut werden.

## Sprach-API-Patterns

Jede der vier Bindings hat eine eigene Hoehenebene fuer Streaming:

| Stack | Pattern |
|---|---|
| pkcs11-tool (Bash) | `--sign --input-file large.bin` — wenn Mechanism Streaming kann, ruft pkcs11-tool intern C_SignUpdate in Chunks. Beobachtbar via `pkcs11-spy`. |
| miekg/pkcs11 (Go) | Expliziter Loop: `SignInit + for { read chunk; SignUpdate(chunk) } + SignFinal`. Maximale Kontrolle. |
| SunPKCS11 (Java/Kotlin) | `Signature.update(buf, off, len)` und `Cipher.update(buf)` mappen 1:1 auf `C_SignUpdate`/`C_EncryptUpdate`. Mit `CipherInputStream` wird daraus ein Standard-Java-Stream-Pattern. |
| Pkcs11Interop (C#) | `ISession.Sign(mech, key, Stream)` und `ISession.Encrypt(mech, key, in, out)` machen Update/Final hinter den Kulissen. |

## Speicher-Beweis: das Lab-Setup

Das Lab generiert `lab/work/large.bin` mit 100 MB. Mit `PKCS11_STREAM_SIZE_MB=1000` kannst du auf 1 GB hochdrehen — alle vier Sprach-Demos und der Bash-Pfad bleiben bei ~30 MB RSS, weil intern Chunk-Buffer von 64 KB genutzt werden.

Beweis fuer den Bash-Pfad ueber `pkcs11-spy`:

```bash
export PKCS11SPY=/usr/lib/softhsm/libsofthsm2.so
export PKCS11SPY_OUTPUT=/tmp/spy.log
PKCS11_MODULE=/usr/lib/x86_64-linux-gnu/pkcs11-spy.so make stream-sign
grep -E "C_Sign(Init|Update|Final)" /tmp/spy.log | wc -l
```

Bei einem 100MB-File mit 64KB-Chunks ergibt das eine `C_SignInit`-Zeile, ~1600 `C_SignUpdate`-Zeilen und eine `C_SignFinal`-Zeile.

## Eigenexperiment

- Setze `PKCS11_STREAM_SIZE_MB=500` und rufe `make stream-decrypt` — beobachte Laufzeit und Speicherbedarf via `/usr/bin/time -v`.
- Aendere im Go-Demo `chunkSize` von 64KB auf 4KB oder 1MB und beobachte den Throughput-Unterschied. Sehr kleine Chunks erzeugen viele PKCS#11-Roundtrips (jeder Update-Call ist ein syscall fuer SoftHSM, ein USB/Netz-Roundtrip fuer reale HSMs).
- Versuche `CKM_AES_GCM` statt `CKM_AES_CBC_PAD` in einer der Sprach-Demos. Wenn SoftHSM nicht mitspielt (siehe Kapitel 13 — SHA-256-OAEP-Quirk), bekommst du eine erhellende Fehlermeldung.

Strukturierte Aufgaben in [`exercises/09-streaming.md`](../exercises/09-streaming.md).
