#!/usr/bin/env bash
set -euo pipefail
# EC-Signing-Key — strikt CKA_SIGN-only (Helper seit 0.16.0).
EC_LABEL="${PKCS11_EC_LABEL:-ec-signing-key}"
EC_ID="${PKCS11_EC_ID:-02}"
CURVE="${PKCS11_EC_CURVE:-secp256r1}"

cd lab/go/pkcs11-keygen
go run . \
  --type ec --curve "$CURVE" \
  --label "$EC_LABEL" --id "$EC_ID" \
  --sign
