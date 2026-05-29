#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
KEY_ID="${PKCS11_KEY_ID:-01}"

mkdir -p lab/work
cd lab/csharp/Pkcs11Demo
PKCS11_OUTPUT_DIR=/workspace/lab/work dotnet run --configuration Release

cd /workspace
pkcs11-tool \
  --module "$MODULE" \
  --token-label "$LABEL" \
  --read-object \
  --type pubkey \
  --id "$KEY_ID" \
  --output-file lab/work/public-csharp.der

openssl rsa -pubin -inform DER -in lab/work/public-csharp.der -out lab/work/public-csharp.pem 2>/dev/null
openssl dgst -sha256 \
  -verify lab/work/public-csharp.pem \
  -signature lab/work/csharp.sig \
  lab/work/csharp.txt
