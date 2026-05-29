# PKCS#11 Cheatsheet

Variablen (im Lab als ENV gesetzt):

```bash
MODULE=/usr/lib/softhsm/libsofthsm2.so
TOKEN=dev-token
PIN=987654
```

## Slots und Mechanisms

```bash
pkcs11-tool --module $MODULE --list-slots
pkcs11-tool --module $MODULE --list-mechanisms
pkcs11-tool --module $MODULE --test --login --pin $PIN --token-label $TOKEN
```

## Objekte

```bash
pkcs11-tool --module $MODULE --login --pin $PIN --token-label $TOKEN --list-objects
pkcs11-tool --module $MODULE --login --pin $PIN --token-label $TOKEN --delete-object --type privkey --id 01
```

## RSA-Keypair

```bash
pkcs11-tool --module $MODULE --login --pin $PIN --token-label $TOKEN \
  --keypairgen --key-type rsa:2048 --id 01 --label signing-key --usage-sign
```

## EC-Keypair

```bash
pkcs11-tool --module $MODULE --login --pin $PIN --token-label $TOKEN \
  --keypairgen --key-type EC:secp256r1 --id 02 --label ec-signing-key --usage-sign
```

## Signieren

```bash
# RSA-PKCS#1 v1.5
pkcs11-tool --module $MODULE --login --pin $PIN --token-label $TOKEN \
  --sign --mechanism SHA256-RSA-PKCS --id 01 \
  --input-file data.txt --output-file data.sig

# RSA-PSS (Token hasht — entspricht make sign-pss im Lab)
pkcs11-tool --module $MODULE --login --pin $PIN --token-label $TOKEN \
  --sign --mechanism SHA256-RSA-PKCS-PSS \
  --mgf MGF1-SHA256 --id 01 \
  --input-file data.txt --output-file data.sig

# RSA-PSS (Anwendung hasht — falls das Token `CKM_SHA256_RSA_PKCS_PSS` nicht
# anbietet; pkcs11-tool-Mechanismus heisst dann `RSA-PKCS-PSS`)
openssl dgst -binary -sha256 data.txt > data.hash
pkcs11-tool --module $MODULE --login --pin $PIN --token-label $TOKEN \
  --sign --mechanism RSA-PKCS-PSS \
  --hash-algorithm SHA256 --mgf MGF1-SHA256 --id 01 \
  --input-file data.hash --output-file data.sig

# ECDSA (OpenSSL-kompatibles DER-Encoding!)
pkcs11-tool --module $MODULE --login --pin $PIN --token-label $TOKEN \
  --sign --mechanism ECDSA-SHA256 --signature-format openssl --id 02 \
  --input-file data.txt --output-file data.sig
```

## Verifizieren (OpenSSL)

```bash
# Public Key aus Token holen (Login je nach Token-Policy noetig)
pkcs11-tool --module $MODULE --token-label $TOKEN \
  --read-object --type pubkey --id 01 --output-file public.der

# RSA-PKCS#1 v1.5
openssl rsa -pubin -inform DER -in public.der -out public.pem
openssl dgst -sha256 -verify public.pem -signature data.sig data.txt

# RSA-PSS
openssl dgst -sha256 \
  -sigopt rsa_padding_mode:pss -sigopt rsa_pss_saltlen:-1 \
  -verify public.pem -signature data.sig data.txt

# EC — vorher Public Key des EC-Slots extrahieren
pkcs11-tool --module $MODULE --token-label $TOKEN \
  --read-object --type pubkey --id 02 --output-file public-ec.der
openssl ec -pubin -inform DER -in public-ec.der -out public-ec.pem
openssl dgst -sha256 -verify public-ec.pem -signature data.sig data.txt
```

## Zertifikat importieren (per PKCS#11-Engine)

```bash
KEY_URI="pkcs11:token=$TOKEN;object=signing-key;type=private;pin-value=$PIN"
openssl req -new -x509 -engine pkcs11 -keyform engine \
  -key "$KEY_URI" -sha256 -days 365 \
  -subj "/CN=signing-key" -out cert.pem
openssl x509 -in cert.pem -outform DER -out cert.der
pkcs11-tool --module $MODULE --login --pin $PIN --token-label $TOKEN \
  --write-object cert.der --type cert --id 01 --label signing-key
```

## Häufige Stolperer

- `--token-label` statt `--slot` benutzen — Slots wandern.
- ECDSA-Signaturen brauchen `--signature-format openssl` für OpenSSL-Verifikation.
- PSS: Hash und MGF müssen auf beiden Seiten identisch sein.
- Private Key und Zertifikat müssen dieselbe `CKA_ID` haben, sonst kein Java-Alias.
- `--read-object --type pubkey` kann auf Tokens mit `CKA_PRIVATE=TRUE`-Pubkeys ein `--login --pin $PIN` erfordern.
