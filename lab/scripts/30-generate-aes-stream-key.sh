#!/usr/bin/env bash
set -euo pipefail
# AES-256 fuer Bulk-Crypto — strikt CKA_ENCRYPT/CKA_DECRYPT, kein WRAP/SIGN.
# Sortenreine Trennung vom KEK auf ID=06 (der wrappt, aber nicht encrypt).
KEY_LABEL="${PKCS11_AES_STREAM_LABEL:-aes-stream-key}"
KEY_ID="${PKCS11_AES_STREAM_ID:-04}"

cd lab/go/pkcs11-keygen
go run . \
  --type aes --bits 256 \
  --label "$KEY_LABEL" --id "$KEY_ID" \
  --encrypt
