#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
KEY_ID="${PKCS11_KEY_ID:-01}"

PROJECT_ROOT="$(pwd)"
mkdir -p lab/work
( cd lab/go/pkcs11-demo && PKCS11_OUTPUT_DIR="$PROJECT_ROOT/lab/work" go run . )

cd "$PROJECT_ROOT"
pkcs11-tool \
  --module "$MODULE" \
  --token-label "$LABEL" \
  --read-object \
  --type pubkey \
  --id "$KEY_ID" \
  --output-file lab/work/public-go.der

openssl rsa -pubin -inform DER -in lab/work/public-go.der -out lab/work/public-go.pem 2>/dev/null
openssl dgst -sha256 \
  -verify lab/work/public-go.pem \
  -signature lab/work/go.sig \
  lab/work/go.txt
