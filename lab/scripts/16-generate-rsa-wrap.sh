#!/usr/bin/env bash
set -euo pipefail
# RSA wrap-key — strikt CKA_DECRYPT/UNWRAP (priv) bzw. CKA_ENCRYPT/WRAP (pub),
# kein CKA_SIGN/VERIFY. Sortenreine Trennung vom signing-key auf ID=01.
KEY_LABEL="${PKCS11_WRAP_KEY_LABEL:-wrap-key}"
KEY_ID="${PKCS11_WRAP_KEY_ID:-03}"

cd lab/go/pkcs11-keygen
go run . \
  --type rsa --bits 2048 \
  --label "$KEY_LABEL" --id "$KEY_ID" \
  --encrypt --wrap
