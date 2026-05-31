#!/usr/bin/env bash
set -euo pipefail
# CA-Key RSA-2048 — strikt CKA_SIGN-only (keyCertSign-aequivalent).
# Wird in 65-issue-ca-cert.sh fuer das CA-Self-Sign genutzt und in
# 66-issue-leaf-cert.sh fuer CSR-CA-Signatur. Nichts anderes darf der Key.
KEY_LABEL="${PKCS11_CA_KEY_LABEL:-ca-key}"
KEY_ID="${PKCS11_CA_KEY_ID:-08}"

cd lab/go/pkcs11-keygen
go run . \
  --type rsa --bits 2048 \
  --label "$KEY_LABEL" --id "$KEY_ID" \
  --sign
