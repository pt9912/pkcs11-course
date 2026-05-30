# Loesung 10 - HMAC und symmetrische Keys

## Bash-Pfad

```bash
make hmac-verify
```

Die Make-Dependency-Kette `gen-hmac → hmac-sign → hmac-verify` laeuft automatisch. Ausgabe-Schluss: `Signature is valid`.

## Tamper

```bash
make hmac-sign
printf 'X' | dd of=lab/work/hmac-data.txt bs=1 seek=0 conv=notrunc 2>/dev/null
make hmac-verify
```

Erwartete Fehlermeldung von pkcs11-tool:

```text
error: Invalid signature
Aborting.
```

Exit-Code != 0 — make bricht die Kette ab.

## Sprach-Demo Ausgabe (Beispiel Go)

```text
Raw HMAC:
  Data:  /workspace/lab/work/go-hmac-data.txt (56 Bytes)
  MAC:   /workspace/lab/work/go-hmac-data.mac (32 Bytes)
  Verify (Original):   OK
  Verify (Tampered):   abgelehnt (erwartet)
JWT (HS256):
  Token: /workspace/lab/work/go-hmac.jwt
  Wert:  eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAi…
  Verify: OK
```

## Cross-Sprach-Verifikation

Die JWT-Strings unterscheiden sich nur im Payload-Timestamp (`iat`/`exp` aus `Now()`) und in der Signatur (deterministisch ueber dem zufaelligen Zeitwert). Die Strukturpruefung passt: drei Punkt-getrennte Base64URL-Segmente.

## Antworten zu den Reflexionsfragen

**HMAC sign+verify mit demselben Key:** HMAC ist eine Hash-basierte symmetrische Konstruktion. Es gibt mathematisch kein "Pub/Priv" — der Verifier rechnet exakt dieselbe Operation wie der Signer. Wer signieren kann, kann auch verifizieren und vice versa. RSA hingegen ist asymmetrisch: signieren braucht `d`, verifizieren braucht nur `e` und `n` (oeffentlich).

**Non-constant-time Compare:** Wenn der Verifier `if (mac1 == mac2)` oder `Arrays.equals(...)` nutzt, bricht der Vergleich beim ersten unterschiedlichen Byte ab — die Laufzeit verraet, wieviele fuehrende Byte korrekt waren. Ein Angreifer kann das Token Byte fuer Byte erraten (Timing-Side-Channel). Constant-time-Compare (`MessageDigest.isEqual`, `hmac.Equal`, `crypto_compare`) vergleicht immer alle Bytes mit XOR + bitweisem OR.

**RS256 vs HS256 bei JWT:**
- **HS256** wenn Sender und Verifier denselben Trust-Boundary teilen (interner Service-Cluster, eine Datenbank-App). Schneller, kleinerer Token, einfacherer Schluesselbestand.
- **RS256** wenn Drittparteien Tokens verifizieren sollen ohne sie ausstellen zu koennen (Identity Provider gibt Tokens aus, viele Microservices verifizieren mit dem oeffentlichen JWKs-Endpoint). Klassisches OIDC-Setup.

**`alg`-Header nicht pruefen:** Klassischer JWT-Bug. Ein Angreifer schickt einen Token mit `alg: none` (kein Signaturalgorithmus) — die Verify-Lib akzeptiert ihn, weil sie nicht prueft. Variante: Angreifer aendert `alg` von `RS256` auf `HS256` und signiert mit dem oeffentlichen RSA-Key des Servers (den er kennt) als HMAC-Key. Schutz: Lib darf nur **erwartete** Algorithmen akzeptieren, und beim Match `alg=HS256` darf sie nur eine HMAC-Verifizierung anstossen.
