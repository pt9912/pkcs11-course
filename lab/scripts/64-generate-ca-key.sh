#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
PIN="${PKCS11_USER_PIN:-987654}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
KEY_LABEL="${PKCS11_CA_KEY_LABEL:-ca-key}"
KEY_ID="${PKCS11_CA_KEY_ID:-08}"

if pkcs11-tool --module "$MODULE" --login --pin "$PIN" --token-label "$LABEL" --list-objects 2>/dev/null \
   | awk -v want="$KEY_LABEL" '
       /Object;/ { in_priv = ($0 ~ /^Private Key Object/); next }
       in_priv && match($0, /label:[[:space:]]*/) {
         value = substr($0, RSTART + RLENGTH)
         sub(/[[:space:]]+$/, "", value)
         if (value == want) { print "match"; exit }
       }' | grep -q match; then
  echo "CA-Key '$KEY_LABEL' existiert bereits."
  exit 0
fi

# Reiner Signing-Key fuer die Mini-CA: --usage-sign setzt CKA_SIGN (privat) und
# CKA_VERIFY (oeffentlich). KEIN CKA_DECRYPT/CKA_WRAP — eine CA signiert nur,
# alles andere waere Cross-Use-Case und macht das Audit unklar.
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
