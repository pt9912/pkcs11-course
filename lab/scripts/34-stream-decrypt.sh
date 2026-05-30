#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
PIN="${PKCS11_USER_PIN:-987654}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
KEY_ID="${PKCS11_AES_STREAM_ID:-04}"

if [ ! -f lab/work/large.iv.hex ] || [ ! -f lab/work/large.enc ]; then
  echo "Erst 'make stream-encrypt' ausfuehren." >&2
  exit 1
fi
IV_HEX="$(cat lab/work/large.iv.hex)"

pkcs11-tool \
  --module "$MODULE" \
  --login --pin "$PIN" \
  --token-label "$LABEL" \
  --decrypt --mechanism AES-CBC-PAD \
  --id "$KEY_ID" \
  --iv "$IV_HEX" \
  --input-file lab/work/large.enc \
  --output-file lab/work/large.dec

if diff -q lab/work/large.bin lab/work/large.dec >/dev/null; then
  echo "Round-Trip OK ($(stat -c%s lab/work/large.dec) Bytes)"
else
  echo "Round-Trip FEHLGESCHLAGEN" >&2
  exit 3
fi
