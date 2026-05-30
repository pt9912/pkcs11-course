#!/usr/bin/env bash
set -euo pipefail
cd lab/kotlin/pkcs11-hmac-demo
./gradlew --quiet --no-daemon run
