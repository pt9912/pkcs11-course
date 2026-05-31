#!/usr/bin/env bash
set -euo pipefail
# AES-256 KEK — strikt CKA_WRAP/CKA_UNWRAP only.
# Bewusst KEIN CKA_ENCRYPT/CKA_DECRYPT — siehe Use-Case-Trennung in Kapitel 20.
# Vorher (pkcs11-tool --usage-wrap): SoftHSM gab encrypt, decrypt, sign, verify
# zusaetzlich dazu. Mit dem Go-Helper sind die strikt false.
KEY_LABEL="${PKCS11_KEK_LABEL:-backup-kek}"
KEY_ID="${PKCS11_KEK_ID:-06}"

cd lab/go/pkcs11-keygen
go run . \
  --type aes --bits 256 \
  --label "$KEY_LABEL" --id "$KEY_ID" \
  --wrap
