#!/usr/bin/env bash
set -euo pipefail
cd lab/java/pkcs11-ecdh-demo
./gradlew --quiet --no-daemon run
