# 16 — HMAC und symmetrische Keys

## Lernziele

Nach diesem Kapitel kannst du:

- den Unterschied zwischen einer **Signatur** (asymmetrisch, Pub/Priv) und einem **MAC** (symmetrisch, shared secret) benennen.
- den Key-Typ `CKK_GENERIC_SECRET` von `CKK_AES` und `CKK_RSA` abgrenzen.
- einen HMAC-Key im Token erzeugen, der weder ex- noch importierbar ist.
- HMAC-SHA256 ueber Daten signieren und HSM-seitig via `C_Verify` pruefen.
- einen HS256-JWT mit einem HSM-Key signieren und verifizieren.

## Lab-Bezug

```bash
make gen-hmac          # GENERIC_SECRET 32 Byte auf ID=05
make hmac-sign         # CKM_SHA256_HMAC ueber ein Test-File
make hmac-verify       # C_Verify-Roundtrip
make go-hmac-demo      # Raw HMAC + HS256-JWT
make csharp-hmac-demo  # session.Verify mit explizitem isValid-Out
make java-hmac-demo    # JCA Mac.doFinal + MessageDigest.isEqual
make kotlin-hmac-demo  # Kotlin-Pendant
```

## MAC vs Signatur

Reflex bei "etwas signieren" sind RSA/EC-Signaturen. Wenn aber **Sender und Empfaenger derselbe Trust-Boundary** angehoeren (oder beide einen geteilten HSM-Zugriff haben), ist ein MAC die einfachere, schnellere Wahl:

| | RSA-Signatur | HMAC |
|---|---|---|
| Schluessel | asymmetrisch (priv signiert, pub verifiziert) | symmetrisch (gleicher Key fuer beides) |
| Output-Groesse | Modulus-Laenge (256 Byte bei RSA-2048) | Hash-Laenge (32 Byte bei SHA-256) |
| Geschwindigkeit | ~1 ms pro Sign (CPU-Heavy) | ~Mikrosekunden (Hash + XOR-Runden) |
| Public Verifiability | ja — jeder mit dem Pubkey | nein — nur wer den Key hat |
| Typischer Use-Case | Dokumentensignatur, Code Signing, JWT mit RS256 | API-Auth, Session-Cookies, JWT mit HS256, Webhook-Signaturen |

In dieser Demo bleibt der HMAC-Key auf dem HSM (`CKA_SENSITIVE=true`, `CKA_EXTRACTABLE=false`) — Sender und Verifier sind beide derselbe Service-Cluster mit Zugriff auf denselben Token.

## Key-Generierung: `GENERIC_SECRET` vs `SHA256_HMAC`

PKCS#11 kennt mehrere Key-Typen fuer symmetrische Keys:

- `CKK_AES` — fuer Block-Cipher-Operationen (siehe Kapitel 13 und 15)
- `CKK_GENERIC_SECRET` — anonyme Bytes, vom Mechanism interpretiert
- `CKK_SHA256_HMAC`, `CKK_SHA512_HMAC` — HMAC-spezifische Typen

Die meisten HSMs (auch SoftHSM) akzeptieren `CKK_GENERIC_SECRET` fuer alle HMAC-Mechanismen — eine `CKK_SHA256_HMAC`-Hardware-Bindung gibt es selten, sie waere auch ueberrestriktiv. Wir nehmen deshalb `--key-type GENERIC:32` (32 Byte = 256 Bit Entropie):

```bash
pkcs11-tool --keygen --key-type GENERIC:32 --id 05 --label hmac-key --usage-sign
```

**Warum 32 Byte?** RFC 2104 §3: HMAC-Key sollte mindestens die Hash-Output-Laenge haben. Laengere Keys werden vom HMAC-Algorithmus intern auf die Block-Groesse des Hashes (64 Byte bei SHA-256) heruntergebracht — bringt also keine Sicherheit, kostet aber Token-Speicher.

## Verify-Pfad: HSM oder Host?

Drei Wege fuer den Verify-Schritt:

| Pfad | Wo prueft? | API |
|---|---|---|
| `C_Verify` direkt | im HSM, constant-time | Go: `Verify()`, C#: `session.Verify(... out bool)` |
| Recompute + `==` | Host, **nicht** constant-time | Anti-Pattern, timing-anfaellig |
| Recompute + constant-time-Vergleich | Host, sicher | JCA: `MessageDigest.isEqual`, Go: `hmac.Equal` (wenn keine PKCS#11-Verify-Variante) |

In unseren Sprach-Demos:
- **Go und C#** nutzen den HSM-`C_Verify`-Pfad — sauberste Semantik.
- **Java/Kotlin** koennen JCA-`Mac` nicht direkt verifizieren; sie rechnen ueber den HSM neu und vergleichen via `MessageDigest.isEqual` — auch ok, kostet aber einen zweiten HSM-Roundtrip.

Auf langsamen HSMs ist der zweite Roundtrip messbar (1-5 ms im Netz). Im LAN/USB-Umfeld irrelevant.

## JWT (HS256) als praktischer Use-Case

JSON Web Tokens (RFC 7519) mit `alg: HS256` sind der wahrscheinlich haeufigste reale HMAC-Use-Case. Aufbau:

```
base64url(header) . base64url(payload) . base64url(hmac_sha256(key, "header.payload"))
```

Header (standardisiert):
```json
{ "alg": "HS256", "typ": "JWT" }
```

Payload (Anwendung waehlt Claims, hier Standard-Klassiker):
```json
{ "sub": "user-42", "iss": "pkcs11-lab", "iat": 1780138141, "exp": 1780141741 }
```

Unsere Sprach-Demos bauen genau das. Die Output-Strings sind alle byte-kompatibel — du kannst einen Go-erzeugten JWT mit der Java-Demo verifizieren, indem du `PKCS11_HMAC_LABEL` setzt und den Token uebergibst (eigene Variation).

Sicherheitshinweis: bei JWTs immer **die `alg`-Header pruefen** und nicht blind annehmen — historisch gab es JWT-Libs, die `alg: none` akzeptierten. Eine Lib, die strikt nur eine Algorithm-Familie zulaesst (z.B. `verify(..., expectedAlg = "HS256")`), ist Pflicht. Unsere Demos sind didaktisch und prufen den Header nicht — fuer Produktion ist `jose4j` (Java), `jose-jwt` (Go), `Microsoft.IdentityModel.Tokens` (C#) der Standardweg.

## Eigenexperiment

- Aendere ein Byte in `lab/work/hmac-data.txt` zwischen `make hmac-sign` und `make hmac-verify` — pkcs11-tool meldet `error: Invalid signature`.
- Erzeuge den JWT in einer Sprache, kopiere die String-Ausgabe in eine andere Demo (z.B. via `PKCS11_HMAC_VERIFY_TOKEN=...` in einer ergaenzten Variante) und beobachte, dass Verify dort genauso funktioniert — selber HSM-Key, byte-kompatibles Format.
- Tausch in einer Sprach-Demo `CKM_SHA256_HMAC` auf `CKM_SHA384_HMAC` oder `CKM_SHA512_HMAC`. JCA: `HmacSHA384`. Output-Laenge wechselt entsprechend (48 bzw. 64 Byte).

Strukturierte Aufgaben in [`exercises/10-hmac.md`](../exercises/10-hmac.md).
