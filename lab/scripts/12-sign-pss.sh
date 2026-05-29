#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
PIN="${PKCS11_USER_PIN:-987654}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
KEY_ID="${PKCS11_KEY_ID:-01}"
mkdir -p lab/work
printf 'hello pkcs11 pss\n' > lab/work/data-pss.txt

# Public Key self-contained besorgen: aus dem Token exportieren und in PEM wandeln,
# damit das Skript unabhaengig von 'make sign'/'make verify' laeuft.
if [ ! -f lab/work/public.der ]; then
  pkcs11-tool \
    --module "$MODULE" \
    --token-label "$LABEL" \
    --read-object \
    --type pubkey \
    --id "$KEY_ID" \
    --output-file lab/work/public.der
fi
if [ ! -f lab/work/public.pem ] || [ lab/work/public.der -nt lab/work/public.pem ]; then
  openssl rsa -pubin -inform DER -in lab/work/public.der -out lab/work/public.pem 2>/dev/null
fi

# SHA256-RSA-PKCS-PSS = CKM_SHA256_RSA_PKCS_PSS: das Token hasht selbst.
# Bei dieser Variante laesst pkcs11-tool das --hash-algorithm-Flag weg
# (das gilt nur fuer CKM_RSA_PKCS_PSS mit vorgehashtem Input). MGF bleibt sinnvoll.
pkcs11-tool \
  --module "$MODULE" \
  --login \
  --pin "$PIN" \
  --token-label "$LABEL" \
  --sign \
  --mechanism SHA256-RSA-PKCS-PSS \
  --mgf MGF1-SHA256 \
  --id "$KEY_ID" \
  --input-file lab/work/data-pss.txt \
  --output-file lab/work/data-pss.sig

openssl dgst -sha256 \
  -sigopt rsa_padding_mode:pss \
  -sigopt rsa_pss_saltlen:-1 \
  -verify lab/work/public.pem \
  -signature lab/work/data-pss.sig \
  lab/work/data-pss.txt
