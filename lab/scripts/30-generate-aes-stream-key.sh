#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
PIN="${PKCS11_USER_PIN:-987654}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
KEY_LABEL="${PKCS11_AES_STREAM_LABEL:-aes-stream-key}"
KEY_ID="${PKCS11_AES_STREAM_ID:-04}"

# Existenz-Check ueber Secret-Key-Bloecke. pkcs11-tool listet SECRET_KEY
# nur mit --login, daher loggen wir ein.
if pkcs11-tool --module "$MODULE" --login --pin "$PIN" --token-label "$LABEL" --list-objects 2>/dev/null \
   | awk -v want="$KEY_LABEL" '
       /Object;/ { in_sec = ($0 ~ /^Secret Key Object/); next }
       in_sec && match($0, /label:[[:space:]]*/) {
         value = substr($0, RSTART + RLENGTH)
         sub(/[[:space:]]+$/, "", value)
         if (value == want) { print "match"; exit }
       }' | grep -q match; then
  echo "AES-Stream-Key '$KEY_LABEL' existiert bereits."
  exit 0
fi

# AES-256 als Token-Objekt mit CKA_ENCRYPT/CKA_DECRYPT.
# Kein --usage-wrap — dieser Key wird nicht zum Wrappen genutzt, nur fuer Bulk-Crypto.
pkcs11-tool \
  --module "$MODULE" \
  --login \
  --pin "$PIN" \
  --token-label "$LABEL" \
  --keygen \
  --key-type aes:32 \
  --id "$KEY_ID" \
  --label "$KEY_LABEL" \
  --usage-decrypt
# Hinweis: --usage-decrypt setzt CKA_DECRYPT=true; CKA_ENCRYPT folgt fuer
# symmetrische Keys implizit (pkcs11-tool 0.25+ leitet das ab).
