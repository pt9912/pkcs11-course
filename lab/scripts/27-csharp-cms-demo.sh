#!/usr/bin/env bash
set -euo pipefail
PROJECT_ROOT="$(pwd)"
mkdir -p lab/work
( cd lab/csharp/Pkcs11CmsDemo && PKCS11_OUTPUT_DIR="$PROJECT_ROOT/lab/work" dotnet run --configuration Release )

# Cross-Verify mit openssl — beweist Standard-Interop.
openssl cms -verify \
  -in lab/work/csharp-cms-document.p7s \
  -inform DER \
  -content lab/work/csharp-cms-document.txt \
  -CAfile lab/work/cert.pem \
  -binary \
  -out /dev/null
echo "OpenSSL Cross-Verify: OK"
