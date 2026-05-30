#!/usr/bin/env bash
set -euo pipefail
cd lab/java/pkcs11-cms-demo
./gradlew --quiet --no-daemon run
cd - >/dev/null

# Cross-Verify mit openssl — Java/BC vs. OpenSSL.
openssl cms -verify \
  -in lab/work/java-cms-document.p7s \
  -inform DER \
  -content lab/work/java-cms-document.txt \
  -CAfile lab/work/cert.pem \
  -binary \
  -out /dev/null
echo "OpenSSL Cross-Verify: OK"
