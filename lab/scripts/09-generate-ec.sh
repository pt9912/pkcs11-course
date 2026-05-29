#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
PIN="${PKCS11_USER_PIN:-987654}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
EC_LABEL="${PKCS11_EC_LABEL:-ec-signing-key}"
EC_ID="${PKCS11_EC_ID:-02}"
CURVE="${PKCS11_EC_CURVE:-secp256r1}"

if pkcs11-tool --module "$MODULE" --login --pin "$PIN" --token-label "$LABEL" --list-objects 2>/dev/null \
   | awk -v want="$EC_LABEL" '
       /^Private Key Object/,/^$/ {
         if (match($0, /label:[[:space:]]*/)) {
           value = substr($0, RSTART + RLENGTH)
           sub(/[[:space:]]+$/, "", value)
           if (value == want) { print "match"; exit }
         }
       }' | grep -q match; then
  echo "EC-Key '$EC_LABEL' existiert bereits."
  exit 0
fi

pkcs11-tool \
  --module "$MODULE" \
  --login \
  --pin "$PIN" \
  --token-label "$LABEL" \
  --keypairgen \
  --key-type "EC:$CURVE" \
  --id "$EC_ID" \
  --label "$EC_LABEL" \
  --usage-sign

echo "EC-Keypair erzeugt: id=$EC_ID label=$EC_LABEL curve=$CURVE"
