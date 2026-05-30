#!/usr/bin/env bash
set -euo pipefail
cd lab/kotlin/pkcs11-cms-demo
./gradlew --quiet --no-daemon run
cd - >/dev/null

openssl cms -verify \
  -in lab/work/kotlin-cms-document.p7s \
  -inform DER \
  -content lab/work/kotlin-cms-document.txt \
  -CAfile lab/work/cert.pem \
  -binary \
  -out /dev/null
echo "OpenSSL Cross-Verify: OK"
