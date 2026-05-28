#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
PIN="${PKCS11_USER_PIN:-987654}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
EC_ID="${PKCS11_EC_ID:-02}"
mkdir -p lab/work
printf 'hello pkcs11 ec\n' > lab/work/data-ec.txt

pkcs11-tool \
  --module "$MODULE" \
  --login \
  --pin "$PIN" \
  --token-label "$LABEL" \
  --sign \
  --mechanism ECDSA-SHA256 \
  --signature-format openssl \
  --id "$EC_ID" \
  --input-file lab/work/data-ec.txt \
  --output-file lab/work/data-ec.sig

pkcs11-tool \
  --module "$MODULE" \
  --token-label "$LABEL" \
  --read-object \
  --type pubkey \
  --id "$EC_ID" \
  --output-file lab/work/public-ec.der

echo "ECDSA-Signatur: lab/work/data-ec.sig"
echo "EC Public Key DER: lab/work/public-ec.der"
