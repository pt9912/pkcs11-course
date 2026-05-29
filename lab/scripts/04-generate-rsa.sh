#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
PIN="${PKCS11_USER_PIN:-987654}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"

if pkcs11-tool --module "$MODULE" --login --pin "$PIN" --token-label "$LABEL" --list-objects 2>/dev/null \
   | awk '/^Private Key Object/,/^$/' | grep -q "label:[[:space:]]*signing-key$"; then
  echo "Key 'signing-key' existiert bereits."
  exit 0
fi

pkcs11-tool \
  --module "$MODULE" \
  --login \
  --pin "$PIN" \
  --token-label "$LABEL" \
  --keypairgen \
  --key-type rsa:2048 \
  --id 01 \
  --label signing-key \
  --usage-sign
