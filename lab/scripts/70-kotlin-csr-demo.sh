#!/usr/bin/env bash
set -euo pipefail
cd lab/kotlin/pkcs11-csr-demo
./gradlew --quiet --no-daemon run
cd - >/dev/null
openssl req -in lab/work/kotlin-app.csr -noout -verify -subject 2>&1 | sed 's/^/  /'
