# Uebung 09 - Multi-Part / Streaming

## Ziel

Du signierst und verschluesselst ein 100 MB grosses File im Streaming-Modus ueber den HSM, beobachtest, dass dabei viele `C_*Update`-Aufrufe statt eines einzigen `C_Sign` passieren, und reproduzierst den Pfad in einer Sprache deiner Wahl.

## Vorbereitung

```bash
make init-token gen-rsa import-cert
```

## Aufgabe 1 — Bash-Pfad

```bash
make gen-aes-stream
make stream-sign       # erzeugt lab/work/large.bin und signiert es
make stream-verify     # openssl dgst -sha256 -verify
make stream-encrypt    # AES-CBC-PAD
make stream-decrypt    # Round-Trip
```

Erwartete Ausgabe (Auszug):

```text
Input:    lab/work/large.bin (104857600 Bytes)
Signatur: lab/work/large.sig (256 Bytes)
Verified OK
Ciphertext: lab/work/large.enc (104857616 Bytes inkl. PKCS#7-Padding)
Round-Trip OK (104857600 Bytes)
```

## Aufgabe 2 — Streaming nachweisen mit pkcs11-spy

```bash
docker compose -f lab/docker-compose.yml run --rm \
  -e PKCS11_MODULE=/usr/lib/x86_64-linux-gnu/pkcs11-spy.so \
  -e PKCS11SPY=/usr/lib/softhsm/libsofthsm2.so \
  -e PKCS11SPY_OUTPUT=/tmp/spy.log \
  pkcs11-lab bash -lc 'lab/scripts/31-stream-sign.sh && grep -cE "C_Sign(Init|Update|Final)" /tmp/spy.log'
```

Erwartet: eine zweistellige bis vierstellige Zahl. Das `C_SignInit` und `C_SignFinal` tauchen je einmal auf, dazwischen viele `C_SignUpdate`.

## Aufgabe 3 — Sprach-Demo

Eine waehlen:

```bash
make go-stream-demo       # expliziter Update/Final-Loop in Go
make csharp-stream-demo   # Stream-Ueberladungen in Pkcs11Interop
make java-stream-demo     # Signature.update + CipherInputStream
make kotlin-stream-demo   # Kotlin-Variante
```

Alle vier produzieren `<sprache>-stream.sig`, `<sprache>-stream.enc`, `<sprache>-stream.dec` in `lab/work/` und enden mit `Round-Trip: OK`.

## Aufgabe 4 — Speicher messen

```bash
docker compose -f lab/docker-compose.yml run --rm pkcs11-lab \
  bash -lc '/usr/bin/time -v lab/scripts/31-stream-sign.sh 2>&1 | grep -E "Maximum resident|wall clock"'
```

Erwartet: Maximum resident set size deutlich unter 100 MB (typisch 5-30 MB), obwohl das Input-File 100 MB hat. **Das** ist der Streaming-Beweis.

## Aufgabe 5 — Bonus: Chunk-Groesse variieren

In `lab/go/pkcs11-stream-demo/main.go` die Konstante `chunkSize` auf 4 KB bzw. 1 MB setzen und neu starten:

```bash
make go-stream-demo
```

Beobachte Laufzeit und Anzahl PKCS#11-Aufrufe ueber pkcs11-spy. Kleinere Chunks → mehr Roundtrips, weniger Throughput. Groessere Chunks → mehr Host-Speicher pro Operation.

## Reflexionsfragen

- Warum darf der **Sign**-Pfad mehrere `C_SignUpdate`-Calls haben, aber den `C_Sign` finalen Aufruf am Ende NICHT vergessen?
- Was passiert, wenn du in der Mitte des Encrypt-Streamings die Session schliesst? (Hinweis: Token-State geht verloren, Final-Buffer enthaelt halben Block — Daten unverwendbar.)
- Warum erlaubt SoftHSM `CKM_AES_CBC_PAD` als Multi-Part, aber `CKM_AES_GCM` macht oft Probleme?
- Wie messen, ob das Token tatsaechlich streamt — oder ob die PKCS#11-Bridge im Hintergrund alles puffert?

## Musterloesung

Siehe `solutions/09-streaming.md`.
