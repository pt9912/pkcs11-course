# Uebung 20 - ECDSA und RSA-PSS in der Praxis

## Ziel

Du wendest die drei Hash/Padding/Encoding-Achsen aus [Kapitel 11](../course/11-ec-und-pss.md) konkret an: ECDSA gegen OpenSSL verifizieren (DER-Encoding-Falle), RSA-PSS-Parameter spiegeln (Salt + MGF-Hash) und am Ende eine Mechanism-Entscheidung schriftlich begruenden.

## Vorbereitung

```bash
make init-token gen-rsa gen-ec
```

## Aufgabe 1 — ECDSA-Signatur mit OpenSSL-Encoding

1. Signiere mit Token-internem Hashing:
   ```bash
   make sign-ec
   make verify-ec
   ```
2. Schau in `lab/scripts/10-sign-ec.sh`, **welcher** Mechanismus tatsaechlich aufgerufen wird (`pkcs11-tool --mechanism ECDSA` und expliziter Pre-Hash) und warum `--signature-format openssl` Pflicht ist.
3. Notiere zwei Werte: die rohe Signatur-Laenge in Bytes (64 bei P-256) und die DER-Signatur-Laenge (typischerweise 70–72 Byte, leicht variabel).

## Aufgabe 2 — DER-Encoding-Falle reproduzieren

1. Hash erneut erzeugen:
   ```bash
   docker compose -f lab/docker-compose.yml run --rm pkcs11-lab bash -lc '
     printf "ec demo\n" > lab/work/ec-data.txt &&
     openssl dgst -sha256 -binary -out lab/work/ec-hash.bin lab/work/ec-data.txt &&
     pkcs11-tool --module $PKCS11_MODULE --login --pin $PKCS11_USER_PIN \
       --token-label $PKCS11_TOKEN_LABEL --sign --mechanism ECDSA \
       --id 02 --input-file lab/work/ec-hash.bin --output-file lab/work/ec-raw.sig
   '
   ```
   (Im Devcontainer: ohne `docker compose run --rm pkcs11-lab bash -lc`, sonst identisch.)
2. Versuche, die rohe `ec-raw.sig` mit OpenSSL zu verifizieren:
   ```bash
   docker compose -f lab/docker-compose.yml run --rm pkcs11-lab bash -lc '
     openssl dgst -sha256 -verify lab/work/ec-public.pem \
       -signature lab/work/ec-raw.sig lab/work/ec-data.txt
   '
   ```
3. Erwartet: `Verification Failure`. Begruenden in einem Satz, warum die Mathematik korrekt, das Encoding aber falsch ist — und welcher CLI-Schalter (`--signature-format openssl`) das auf der Sign-Seite repariert haette.

## Aufgabe 3 — RSA-PSS-Spiegelparameter

1. Signiere mit PSS:
   ```bash
   make sign-pss
   ```
2. Verifiziere die Signatur **explizit** mit allen Parametern:
   ```bash
   docker compose -f lab/docker-compose.yml run --rm pkcs11-lab bash -lc '
     openssl dgst -sha256 -verify lab/work/public.pem \
       -sigopt rsa_padding_mode:pss \
       -sigopt rsa_mgf1_md:sha256 \
       -sigopt rsa_pss_saltlen:32 \
       -signature lab/work/data.pss.sig lab/work/data.txt
   '
   ```
3. Aendere `rsa_pss_saltlen:32` auf `:48` und beobachte den Fehler. Erwartet: `Verification Failure` ohne klare Begruendung — typisch PSS.
4. Aendere `rsa_mgf1_md:sha256` auf `:sha384` und beobachte erneut: ebenfalls `Verification Failure`. Beide Achsen (Salt-Laenge, MGF-Hash) muessen exakt mit dem Signer uebereinstimmen.

## Aufgabe 4 — Mechanism-Entscheidung schriftlich

Folgende drei Anforderungs-Szenarien — jeweils mit einer Antwort (2–4 Saetze): welcher Mechanism, **warum**, was musst du beim HSM vorher pruefen?

1. **Neuer interner Microservice, FIPS-Anforderung, schnelle Signaturen, keine Bestandskompatibilitaet.** Was waehlst du?
2. **Bestehende Enterprise-PKI mit RSA-2048-CA, neuer Service haengt sich an dieselbe CA. Was waehlst du, und warum nicht ECDSA?**
3. **Bitcoin-Wallet-Integration mit `secp256k1`-Keys.** Welche HSM-Klassen kommen ueberhaupt in Frage? Was ist das wahrscheinlichste Problem?

## Fehlerfall

Setze in der RSA-PSS-Demo die JCA-Demo bewusst ohne `PSSParameterSpec` auf:

```bash
docker compose -f lab/docker-compose.yml run --rm \
  -e PKCS11_MECHANISM=RSASSA-PSS \
  -e PKCS11_PSS_SKIP_PARAMS=1 \
  pkcs11-java bash -lc 'cd lab/java/pkcs11-demo && ./gradlew --quiet --no-daemon run'
```

(Im Devcontainer ohne `docker compose run --rm pkcs11-java bash -lc`.)

Wenn die Lab-Demo diese ENV nicht implementiert, das Verhalten als Reflexionsteil dokumentieren: was sind die JCA-Defaults fuer `PSSParameterSpec.DEFAULT`, und warum stimmen sie selten mit dem HSM ueberein? Hinweis: `MGF1ParameterSpec.SHA1` plus Salt-Laenge 20 ist der historische JCA-Default — auf SoftHSM mit `CKM_SHA256_RSA_PKCS_PSS` ergibt das `CKR_MECHANISM_PARAM_INVALID`.

## Reflexionsfragen

- Warum gibt `pkcs11-tool` ECDSA standardmaessig als `r||s` aus und nicht als DER? (Tipp: PKCS#11-Spec §6.10.1.)
- Welche Salt-Laenge ist bei `RSA-PSS` der "sichere Default", und warum schreibt RFC 8017 das nicht als Pflicht vor?
- Warum bricht eine Veraenderung des MGF-Hashes immer "still", also ohne hilfreiche Fehlermeldung beim Verifier?
- Wann wuerdest du `CKM_ECDSA` (Pre-Hash) gegenueber `CKM_ECDSA_SHA256` (Token hasht) bevorzugen?

## Musterloesung

Siehe `solutions/20-ec-und-pss.md`.
