#!/usr/bin/env bash
set -euo pipefail
cd lab/java/pkcs11-random-demo
./gradlew --quiet --no-daemon run
