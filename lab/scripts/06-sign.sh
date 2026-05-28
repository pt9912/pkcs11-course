#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
PIN="${PKCS11_USER_PIN:-987654}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
mkdir -p lab/work
printf 'hello pkcs11\n' > lab/work/data.txt

pkcs11-tool \
  --module "$MODULE" \
  --login \
  --pin "$PIN" \
  --token-label "$LABEL" \
  --sign \
  --mechanism SHA256-RSA-PKCS \
  --id 01 \
  --input-file lab/work/data.txt \
  --output-file lab/work/data.sig

pkcs11-tool \
  --module "$MODULE" \
  --token-label "$LABEL" \
  --read-object \
  --type pubkey \
  --id 01 \
  --output-file lab/work/public.der

echo "Signatur: lab/work/data.sig"
echo "Public Key DER: lab/work/public.der"
