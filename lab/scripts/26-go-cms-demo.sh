#!/usr/bin/env bash
set -euo pipefail
PROJECT_ROOT="$(pwd)"
mkdir -p lab/work
( cd lab/go/pkcs11-cms-demo && PKCS11_OUTPUT_DIR="$PROJECT_ROOT/lab/work" go run . )

# Bonus: extern via openssl verifizieren — beweist Interop-Standardformat.
openssl cms -verify \
  -in lab/work/go-cms-document.p7s \
  -inform DER \
  -content lab/work/go-cms-document.txt \
  -CAfile lab/work/cert.pem \
  -binary \
  -out /dev/null
echo "OpenSSL Cross-Verify: OK"
