#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
PIN="${PKCS11_USER_PIN:-987654}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
KEY_ID="${PKCS11_KEY_ID:-01}"
mkdir -p lab/work
printf 'hello pkcs11 pss\n' > lab/work/data-pss.txt

pkcs11-tool \
  --module "$MODULE" \
  --login \
  --pin "$PIN" \
  --token-label "$LABEL" \
  --sign \
  --mechanism RSA-PKCS-PSS \
  --hash-algorithm SHA256 \
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
