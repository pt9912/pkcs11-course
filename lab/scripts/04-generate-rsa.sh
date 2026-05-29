#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
PIN="${PKCS11_USER_PIN:-987654}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
KEY_LABEL="${PKCS11_KEY_LABEL:-signing-key}"
KEY_ID="${PKCS11_KEY_ID:-01}"

if pkcs11-tool --module "$MODULE" --login --pin "$PIN" --token-label "$LABEL" --list-objects 2>/dev/null \
   | awk -v want="$KEY_LABEL" '
       /^Private Key Object/,/^$/ {
         if (match($0, /label:[[:space:]]*/)) {
           value = substr($0, RSTART + RLENGTH)
           sub(/[[:space:]]+$/, "", value)
           if (value == want) { print "match"; exit }
         }
       }' | grep -q match; then
  echo "Key '$KEY_LABEL' existiert bereits."
  exit 0
fi

pkcs11-tool \
  --module "$MODULE" \
  --login \
  --pin "$PIN" \
  --token-label "$LABEL" \
  --keypairgen \
  --key-type rsa:2048 \
  --id "$KEY_ID" \
  --label "$KEY_LABEL" \
  --usage-sign
