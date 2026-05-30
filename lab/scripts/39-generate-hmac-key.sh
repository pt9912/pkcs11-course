#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
PIN="${PKCS11_USER_PIN:-987654}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
KEY_LABEL="${PKCS11_HMAC_LABEL:-hmac-key}"
KEY_ID="${PKCS11_HMAC_ID:-05}"

if pkcs11-tool --module "$MODULE" --login --pin "$PIN" --token-label "$LABEL" --list-objects 2>/dev/null \
   | awk -v want="$KEY_LABEL" '
       /Object;/ { in_sec = ($0 ~ /^Secret Key Object/); next }
       in_sec && match($0, /label:[[:space:]]*/) {
         value = substr($0, RSTART + RLENGTH)
         sub(/[[:space:]]+$/, "", value)
         if (value == want) { print "match"; exit }
       }' | grep -q match; then
  echo "HMAC-Key '$KEY_LABEL' existiert bereits."
  exit 0
fi

# CKK_GENERIC_SECRET 32 Byte = 256 Bit Entropie, passt zu HMAC-SHA256.
# RFC 2104 empfiehlt: HMAC-Key ≥ Hash-Output-Laenge (32 Byte fuer SHA-256).
# Laengere Keys werden vom HMAC-Init auf Hash-Block-Groesse (64 Byte fuer SHA-256)
# heruntergebracht — bringt also nichts.
# --usage-sign aktiviert CKA_SIGN + CKA_VERIFY auf dem Secret-Key.
pkcs11-tool \
  --module "$MODULE" \
  --login \
  --pin "$PIN" \
  --token-label "$LABEL" \
  --keygen \
  --key-type GENERIC:32 \
  --id "$KEY_ID" \
  --label "$KEY_LABEL" \
  --usage-sign
