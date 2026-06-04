# Loesung 20 - ECDSA und RSA-PSS in der Praxis

## Lauf

```bash
make init-token gen-rsa gen-ec
make sign-ec verify-ec
make sign-pss
```

## Aufgabe 1 — Erwartete Werte

- Rohe `r||s`-Signatur auf P-256: **64 Bytes** (32 Byte `r` + 32 Byte `s`).
- DER-Signatur (`--signature-format openssl`): typisch **70–72 Bytes**, weil `r` und `s` mit fuehrendem `0x00` aufgefuellt werden, wenn das hoechstwertige Bit gesetzt ist (ASN.1 INTEGER ist signed). Bei zufaellig kleinen `r`/`s` kann die DER-Signatur auch kuerzer sein.
- `lab/scripts/10-sign-ec.sh` ruft `pkcs11-tool --mechanism ECDSA` mit `--signature-format openssl` auf, weil SoftHSM `CKM_ECDSA_SHA256` nicht meldet (siehe Kap. 11). Der SHA-256-Hash wird vorher per `openssl dgst -binary` erzeugt.

## Aufgabe 2 — DER-Falle

Erwartete OpenSSL-Ausgabe bei der rohen Signatur:

```text
Verification Failure
```

Begruendung: ECDSA gibt mathematisch ein Paar `(r, s)` zurueck. PKCS#11 (`pkcs11-tool` ohne Format-Schalter) konkateniert die beiden 32-Byte-Werte stumpf. OpenSSL erwartet `SEQUENCE { INTEGER r, INTEGER s }` in DER. Beide sind valide Repraesentationen derselben Zahlen, aber inkompatibel beim Parsing — der Verifier liest 0x20 (DER-SEQUENCE-Tag) und bekommt stattdessen das hoechstwertige Byte von `r` (oft 0x.._). Das fixt `--signature-format openssl` auf der Sign-Seite oder eine manuelle DER-Verpackung in der Anwendung.

## Aufgabe 3 — PSS-Spiegelparameter

Erwartete OpenSSL-Ausgabe bei korrektem Lauf:

```text
Verified OK
```

Mit verstellter `rsa_pss_saltlen:48`:

```text
Verification Failure
```

Mit verstelltem `rsa_mgf1_md:sha384`:

```text
Verification Failure
```

Ohne Hinweis darauf, **welcher** Parameter abweicht — PSS-Verifikation ist ein Hash-Vergleich am Ende, der entweder passt oder nicht; das Mismatching-Salt sieht aus wie ein Mismatching-MGF wie ein Mismatching-Hash.

## Aufgabe 4 — Mechanism-Entscheidung (Mustertexte)

**Szenario 1: FIPS, schnell, kein Legacy.**
ECDSA P-256 (`CKM_ECDSA_SHA256`). Begruendung: kleinerer Key (256 Bit vs. 2048 Bit), schnellere Operationen, FIPS-zugelassen (SP 800-186), keine Salt/MGF-Falle wie bei PSS. Vor Festlegung: `pkcs11-tool --list-mechanisms` muss `CKM_ECDSA_SHA256` zeigen. Ed25519 waere fachlich attraktiver, ist in FIPS-140-2-Modulen aber nicht erlaubt.

**Szenario 2: RSA-CA-Bestand.**
RSA-PSS (`CKM_SHA256_RSA_PKCS_PSS`). Begruendung: die CA bleibt RSA — eine ECDSA-Leaf-CSR brauchte einen anderen Cert-Pfad und einen anderen Verifier-Stack. PSS statt PKCS#1-v1.5, weil NIST und BSI fuer neue Systeme PSS empfehlen (Sicherheitsbeweis, randomisiertes Padding). Vor Festlegung: pruefen, ob die CA selbst PSS-Signaturen ausstellen kann (manche Enterprise-CAs sind hart auf v1.5 konfiguriert).

**Szenario 3: secp256k1 / Bitcoin.**
Nur HSMs, die `secp256k1` explizit aktiviert haben — viele Enterprise-HSMs sperren die Kurve aus FIPS-/Compliance-Gruenden (Bitcoin/Ethereum sind nicht im NIST-Korb). Wahrscheinlichste Probleme: (a) `secp256k1` nicht in `pkcs11-tool --list-mechanisms` der Mechanism-Liste fuer EC-Keys, (b) das HSM unterstuetzt EC-Keypair-Gen, aber nicht Sign mit dieser Kurve, (c) das Encoding (`r||s`) muss in Bitcoin-Welt zusaetzlich BIP-66-konform DER sein — manche Tools liefern das, manche nicht. Kandidaten: YubiHSM 2, einige Thales-Profile, ausdruecklich nicht Cloud-KMS.

## Antworten zu den Reflexionsfragen

**Warum `r||s` als Default?** PKCS#11 §6.10.1 spezifiziert die ECDSA-Signatur als die beiden Curve-Order-langen Integer hintereinander, mit fuehrenden Nullen wenn noetig — das ist die minimale, format-neutrale Form. DER zu erzeugen ist Schicht-Aufgabe des Aufrufers, nicht des Tokens.

**"Sicherer" PSS-Salt-Default.** RFC 8017 §9.1 erlaubt jede Salt-Laenge `0 ≤ sLen ≤ emLen − hLen − 2`. "Salt-Laenge gleich Hash-Laenge" (also 32 fuer SHA-256, `saltlen:-1` in OpenSSL) ist eine pragmatische Konvention, kein Pflichtwert — daher die unterschiedlichen Defaults zwischen Stacks und die haeufigen Mismatches.

**Stilles MGF-Versagen.** Beim Verifier wird intern der MGF-erzeugte Mask-Bytestream mit dem extrahierten salted-Hash XOR-verglichen. Wenn der MGF-Hash falsch ist, erzeugt das eine zufaellig aussehende Differenz — der Vergleich schlaegt fehl, aber auf der Implementations-Ebene unterscheidet das Verifier-Programm das nicht von "Signatur war falsch". Konsequenz: einzige Diagnose ist, die PSS-Parameter beider Seiten explizit gegenueberzustellen.

**`CKM_ECDSA` vs `CKM_ECDSA_SHA256`.** Pre-Hash (`CKM_ECDSA`) ist sinnvoll, wenn (a) die Anwendung den Hash sowieso schon hat (z. B. weil eine andere Schicht ihn als `digestInfo` aufgebaut hat), (b) man die Hash-Funktion frei waehlen will, ohne fuer jeden Hash einen eigenen Mechanism zu brauchen, oder (c) SoftHSM-Lab. Token-Side-Hash (`CKM_ECDSA_SHA256`) ist sauberer fuer Streaming und Single-Shot-API in den meisten Sprach-Bindings, und es spart einen Aufruf.
