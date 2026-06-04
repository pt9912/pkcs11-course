# 15 — Multi-Part / Streaming-Operationen

> **Didaktischer Pfad:** Vorher → [`14-cms-signatur.md`](14-cms-signatur.md) · Nachher → [`16-hmac.md`](16-hmac.md)

## Lernziele

Nach diesem Kapitel kannst du:

- den Unterschied zwischen Single-Shot (`C_Sign`/`C_Encrypt`) und Multi-Part (`C_*Init` + `C_*Update*` + `C_*Final`) erklaeren.
- ein mehrere hundert MB grosses Dokument mit konstantem Speicherbedarf signieren und verschluesseln.
- begruenden, warum AES-CBC-PAD im Streaming-Modus ueberlebt, AES-GCM aber Probleme macht.
- typische HSM-Limits (Single-Shot-Buffer, Mechanism-Support fuer Update) benennen.
- **(Bloom 5 — evaluate)** fuer einen gegebenen Throughput- und Latenz-Bedarf entscheiden, ob Chunk-Groessen-Tuning, Mechanism-Wechsel oder ein paralleler Pool (Kap. 17) die effektive Stellschraube ist — und welche Messung den Engpass beweist (`pkcs11-spy`-Trace oder Wallclock-Differenz).

> **Geschaetzte Bearbeitungszeit:** ~60 min (Lesen 20 min + Bash-Worked-Example mit 100 MB-File 20 min + ein Sprach-Faded 20 min). Die Speicher-Messung (`/usr/bin/time -v`) ist der Lerneffekt-Knoten — keep RSS klein, file beliebig gross.

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

## Sprach-API-Patterns: Ueberblick

Jede der vier Bindings hat eine eigene Hoehenebene fuer Streaming. Wir arbeiten den Bash-Pfad mit `pkcs11-spy` vollstaendig durch und lassen die drei Sprach-Pfade als Faded Examples folgen.

### Worked Example: der Bash-Pfad mit pkcs11-spy-Beweis (vollstaendig)

```bash
make gen-aes-stream    # Voraussetzung: AES-Key auf ID=04
make stream-sign       # 100 MB-File via CKM_SHA256_RSA_PKCS signieren
make stream-verify     # openssl dgst -verify
```

**Schritt 1 — File-Erzeugung.** Das Lab generiert `lab/work/large.bin` mit 100 MB (default; via `PKCS11_STREAM_SIZE_MB=1000` auf 1 GB hochdrehbar). Das Ziel ist, dass **keine** Software-Komponente das File komplett im Heap halten muss.

**Schritt 2 — Init: HSM allokiert Hash-Context.** `pkcs11-tool --sign --mechanism SHA256-RSA-PKCS --input-file large.bin --output-file large.sig` ruft intern `C_SignInit(CKM_SHA256_RSA_PKCS, signing-key)`. Der Token allokiert einen leeren SHA-256-State. **Kein** Byte vom Dokument ist bisher transportiert.

**Schritt 3 — Update-Loop: Chunks fliessen ins Token, RSS bleibt konstant.** `pkcs11-tool` liest das File in Chunks (Default 64 KB) und ruft pro Chunk `C_SignUpdate(buf, 65536)`. Das Token mischt den Chunk in den SHA-256-State und **vergisst** ihn dann — kein Buffering im Token. Der Host-Heap braucht nur einen Chunk-Buffer (64 KB), das File auf der Platte bleibt unangetastet.

**Schritt 4 — Final: SHA-256 wird abgeschlossen + signiert.** `C_SignFinal()` liefert die fertige Signatur (256 Byte bei RSA-2048). Hash-Closure + RSA-Operation auf 32 Byte SHA-Output passieren in einem Step im Token. Output: `large.sig`, 256 Byte.

**Schritt 5 — Beweis via pkcs11-spy.** Genau diesen Loop kannst du sichtbar machen:

```bash
export PKCS11SPY=/usr/lib/softhsm/libsofthsm2.so
export PKCS11SPY_OUTPUT=/tmp/spy.log
PKCS11_MODULE=/usr/lib/x86_64-linux-gnu/pkcs11-spy.so make stream-sign
grep -cE "C_Sign(Init|Update|Final)" /tmp/spy.log
```

Bei 100 MB / 64 KB = ~1600 `C_SignUpdate`-Zeilen, je eine `C_SignInit` und `C_SignFinal`.

**Schritt 6 — RSS-Beweis.** `/usr/bin/time -v` auf das Sign-Skript zeigt "Maximum resident set size" ≪ 100 MB — typisch 5–30 MB. Das ist der harte Beweis, dass das File nie als Ganzes im Heap stand.

Der gewonnene Schema-Kern: **Multi-Part = Token haelt den State, Host pumpt nur Chunks. Das funktioniert nur fuer Mechanismen, deren Operationen ueber den Stream linear akkumulierbar sind (Hash, CBC-Padding). Mechanismen mit terminalem Tag (GCM) verlangen Sonderbehandlung.**

### Faded Examples — die drei Sprach-Pfade

Pro Pfad: kurze Tabellen-Beschreibung und **drei Leitfragen**.

#### Go (`pkcs11-stream-demo`)

| Pattern | Chunk-Groesse |
|---|---|
| Expliziter Loop: `SignInit + for { read chunk; SignUpdate(chunk) } + SignFinal`. Maximale Kontrolle, jeder Schritt sichtbar. | `chunkSize` als Konstante, default 64 KB. |

Leitfragen:

1. **Was unterscheidet diesen Pfad vom Bash-Pfad in Schritt 3?** (Stichwort: Bash `pkcs11-tool` macht den Loop intern; Go macht ihn sichtbar im Anwendungscode.)
2. **Setze `chunkSize` auf 4 KB. Welche zwei Effekte erwartest du — und welcher ist auf SoftHSM kaum spuerbar, auf realem HSM aber dominant?** (Hinweis: PKCS#11-Roundtrip-Overhead.)
3. **Wo im Go-Code passiert die "RSS bleibt konstant"-Garantie?** Verfolge den Buffer-Lifecycle: wird der gleiche Slice wiederverwendet, oder allokiert jeder `Read` einen neuen?

#### Java/Kotlin (`pkcs11-stream-demo`)

| Pattern | Streaming-Stelle |
|---|---|
| `Signature.update(buf, off, len)` und `Cipher.update(buf)` mappen 1:1 auf `C_SignUpdate`/`C_EncryptUpdate`. Mit `CipherInputStream` wird daraus ein Standard-Java-Stream-Pattern. | Die Update-Schicht ist hinter SunPKCS11 versteckt; der Aufrufer sieht nur die JCA-Standard-API. |

Leitfragen:

1. **Wo siehst du, dass SunPKCS11 wirklich `C_SignUpdate` aufruft und nicht intern alles puffert?** (Antwort: `pkcs11-spy`-Trace zeigt es; der Code allein ist transparent.)
2. **`CipherInputStream` ist ein Standard-Java-Pattern. Was muss bei `Cipher.doFinal()` passieren, damit das im HSM mit `C_EncryptFinal` zusammenfaellt?** Verfolge die `read()`-Schleife der Stream-Klasse.
3. **Warum kann der Java-Code SunPKCS11 transparent durch BouncyCastle ersetzen — ausser bei einer entscheidenden Eigenschaft?** (Tipp: HSM-Resident vs Software-Key.)

#### C# (`Pkcs11StreamDemo`)

| Pattern | Streaming-Stelle |
|---|---|
| `ISession.Sign(mech, key, Stream)` und `ISession.Encrypt(mech, key, in, out)` machen Update/Final hinter den Kulissen. Die `Stream`-Ueberladung in Pkcs11Interop iteriert intern. | Pkcs11Interop chunkt mit fester Buffer-Groesse (4096 Byte als interner Default — pruefbar im Source). |

Leitfragen:

1. **Was unterscheidet die `Stream`-Ueberladung von einer manuellen `Update`-Schleife?** Ist das fuer SoftHSM ein praktischer Unterschied — ist es das auf einem PCIe-HSM mit Pro-Call-Overhead?
2. **Wenn du eine eigene Chunk-Groesse erzwingen willst — welche zwei Pkcs11Interop-API-Stellen wuerdest du nutzen?** (Tipp: `ISession.SignUpdate` und `ISession.SignFinal` direkt, ohne die `Stream`-Ueberladung.)
3. **Warum sind `using`/`Dispose` hier dramatischer als in Go/Java?** Folge dem Cleanup einer halb-laufenden Sign-Operation, wenn eine Exception zwischen Update und Final fliegt.

### Wenn alle vier Pfade im Kopf zusammenkommen

Die Lehre: Streaming ist eine HSM-Eigenschaft, nicht eine Library-Eigenschaft. Solange der Mechanism Multi-Part erlaubt und das HSM den State haelt, sind 4 KB / 64 KB / 1 MB Chunk-Groesse nur Performance-Knoepfe. Die Korrektheit ist von der Mechanism-Wahl abhaengig — und genau dort macht der Wechsel `CKM_RSA_PKCS` → `CKM_SHA256_RSA_PKCS` (Token hasht selbst) den Unterschied zwischen "245-Byte-Single-Shot-Limit" und "unbegrenzte Stream-Groesse".

## Eigenexperiment

- Setze `PKCS11_STREAM_SIZE_MB=500` und rufe `make stream-decrypt` — beobachte Laufzeit und Speicherbedarf via `/usr/bin/time -v`.
- Aendere im Go-Demo `chunkSize` von 64KB auf 4KB oder 1MB und beobachte den Throughput-Unterschied. Sehr kleine Chunks erzeugen viele PKCS#11-Roundtrips (jeder Update-Call ist ein syscall fuer SoftHSM, ein USB/Netz-Roundtrip fuer reale HSMs).
- Versuche `CKM_AES_GCM` statt `CKM_AES_CBC_PAD` in einer der Sprach-Demos. Wenn SoftHSM nicht mitspielt (siehe Kapitel 13 — SHA-256-OAEP-Quirk), bekommst du eine erhellende Fehlermeldung.

Strukturierte Aufgaben in [`exercises/09-streaming.md`](../exercises/09-streaming.md).

## Selbsttest

<details>
<summary>1. Warum sind <code>CKM_RSA_PKCS</code> und <code>CKM_SHA256_RSA_PKCS</code> bei Streaming unterschiedlich?</summary>

`CKM_RSA_PKCS` (rohes Sign) ist Single-Shot mit max ~245 Byte Input. `CKM_SHA256_RSA_PKCS` ist streamfaehig — das Token hashed selbst, daher reichen `C_SignUpdate`-Aufrufe in Chunks. Praktisch heisst das: bei grossen Files **muss** Token-hasht-Variante her, sonst gibt es `CKR_DATA_LEN_RANGE`.
</details>

<details>
<summary>2. Wieso ist AES-GCM beim Streaming oft problematisch, AES-CBC-PAD nicht?</summary>

GCM-Tag sitzt am Ende und ergibt sich aus dem gesamten Klartext. `C_EncryptFinal` muss den Tag liefern; manche HSMs verbieten Multi-Part fuer GCM komplett, andere implementieren es eingeschraenkt. AES-CBC-PAD ist linear (IV + Block-Rest fuer Padding) und stream-friendly. Trade-off: CBC ist nicht authenticated — Tamper-Erkennung muss separat (HMAC-then-Encrypt, eigenes Auth-Tag) gebaut werden.
</details>

<details>
<summary>3. Du sollst entscheiden, ob 4 KB, 64 KB oder 1 MB die richtige Chunk-Groesse sind. Welche zwei Faktoren bestimmen die Antwort?</summary>

**RTT zum HSM** und **HSM-Side-Buffer-Limit**. Bei SoftHSM (in-Process): kleine Chunks kosten kaum, weil keine Netzwerk-Roundtrips. Bei einem PCIe-HSM mit ~50us Pro-Call-Overhead: 4 KB heisst 1600 Calls je 100 MB, also ~80 ms reine Overhead-Zeit. Bei Netz-HSMs (Cloud-HSM, ~5 ms RTT) wird das prohibitiv. Faustregel: 64 KB ist ein guter Default, der bei den meisten Setups durchblickt. Beweis ueber `pkcs11-spy`-Trace + Wallclock-Vergleich.
</details>
