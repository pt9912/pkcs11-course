# Uebung 10 - HMAC und symmetrische Keys

## Ziel

Du erzeugst einen GENERIC-SECRET-Key im Token, signierst Daten mit HMAC-SHA256 ueber den HSM, beobachtest Tampering-Erkennung via `C_Verify`, baust einen HS256-JWT und entschluesselst den Konflikt MAC-vs-Signatur fuer dich.

## Vorbereitung

```bash
make init-token
```

## Aufgabe 1 — Keygen + Bash-Roundtrip

```bash
make gen-hmac
make hmac-sign
make hmac-verify
```

Erwartete Ausgabe:

```text
Input: lab/work/hmac-data.txt (... Bytes)
MAC:   lab/work/hmac-data.mac (32 Bytes)
Signature is valid
```

## Aufgabe 2 — Tamper-Erkennung

```bash
make hmac-sign
printf 'X' | dd of=lab/work/hmac-data.txt bs=1 seek=0 conv=notrunc 2>/dev/null
make hmac-verify
```

Erwartet: pkcs11-tool bricht mit `error: Invalid signature` ab.

## Aufgabe 3 — Sprach-Demo + JWT

Eine Sprache waehlen:

```bash
make go-hmac-demo
make csharp-hmac-demo
make java-hmac-demo
make kotlin-hmac-demo
```

Jede produziert `<sprache>-hmac-data.txt`, `<sprache>-hmac-data.mac`, `<sprache>-hmac.jwt` und einen Console-Output mit drei OK-Zeilen (Original-Verify, Tampered-Reject, JWT-Verify).

## Aufgabe 4 — Cross-Sprach-Verifikation

```bash
make go-hmac-demo                              # erzeugt go-hmac.jwt
JWT="$(cat lab/work/go-hmac.jwt)"
# Manuell pruefen: HMAC = base64url(SHA256-HMAC(key, header.payload))
# Mit pkcs11-tool:
printf '%s' "${JWT%.*}" > /tmp/jwt-input
printf '%s' "${JWT##*.}" | base64 -d > /tmp/jwt-sig 2>/dev/null || \
  printf '%s' "${JWT##*.}" | tr '_-' '/+' | base64 -d > /tmp/jwt-sig
docker compose -f lab/docker-compose.yml run --rm pkcs11-lab bash -lc \
  'pkcs11-tool --module /usr/lib/softhsm/libsofthsm2.so --token-label dev-token --login --pin 987654 \
    --verify --mechanism SHA256-HMAC --id 05 --input-file /tmp/jwt-input --signature-file /tmp/jwt-sig'
```

Hinweis: base64url benoetigt Padding-Wiederherstellung — `tr '_-' '/+'` macht das Mapping zurueck, dann `base64 -d` (das fehlende `=`-Padding erlaubt openssl, nicht aber GNU coreutils — Linux base64 toleriert es seit coreutils 8.32).

## Aufgabe 5 — Bonus: Hash-Familie wechseln

Aendere in einer Sprach-Demo `CKM_SHA256_HMAC` auf `CKM_SHA512_HMAC` (in Java/Kotlin: `HmacSHA512`). Output-Laenge waechst auf 64 Byte, JWT-`alg`-Header muss auf `HS512` umgestellt werden.

## Reflexionsfragen

- Warum kann man mit dem HMAC-Key sowohl sign als auch verify, mit einem RSA-Privkey aber nur sign?
- Welcher Angriffspfad bleibt offen, wenn der HMAC-Verifier seinen MAC-Vergleich nicht in constant time macht?
- Wann ist `RS256` (RSA-Signatur) besser als `HS256` (HMAC) bei JWT — und wann umgekehrt?
- Was passiert, wenn ein JWT-Verifier nicht prueft, dass der `alg`-Header tatsaechlich `HS256` ist?

## Musterloesung

Siehe `solutions/10-hmac.md`.
