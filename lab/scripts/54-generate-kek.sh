#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
PIN="${PKCS11_USER_PIN:-987654}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
KEY_LABEL="${PKCS11_KEK_LABEL:-backup-kek}"
KEY_ID="${PKCS11_KEK_ID:-06}"

if pkcs11-tool --module "$MODULE" --login --pin "$PIN" --token-label "$LABEL" --list-objects 2>/dev/null \
   | awk -v want="$KEY_LABEL" '
       /Object;/ { in_sec = ($0 ~ /^Secret Key Object/); next }
       in_sec && match($0, /label:[[:space:]]*/) {
         value = substr($0, RSTART + RLENGTH)
         sub(/[[:space:]]+$/, "", value)
         if (value == want) { print "match"; exit }
       }' | grep -q match; then
  echo "KEK '$KEY_LABEL' existiert bereits."
  exit 0
fi

# KEK = Key Encryption Key. AES-256 mit CKA_WRAP/CKA_UNWRAP, KEINE CKA_ENCRYPT/
# CKA_DECRYPT — der Key darf NUR andere Keys wrappen, nicht Daten verschluesseln.
# So vermeidet man, dass derselbe KEK in mehreren Domain-Operationen genutzt wird
# (Trennung der Use-Cases, wie schon bei signing-key vs wrap-key in Kapitel 13).
# pkcs11-tool hat keine --usage-wrap-only-Flag; wir nutzen --usage-wrap, was
# zusammen mit dem default CKA_ENCRYPT=FALSE fuer Secret-Keys das Richtige tut.
pkcs11-tool \
  --module "$MODULE" \
  --login \
  --pin "$PIN" \
  --token-label "$LABEL" \
  --keygen \
  --key-type aes:32 \
  --id "$KEY_ID" \
  --label "$KEY_LABEL" \
  --usage-wrap
