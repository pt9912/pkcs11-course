#!/usr/bin/env bash
set -euo pipefail
PROJECT_ROOT="$(pwd)"
mkdir -p lab/work
( cd lab/go/pkcs11-encrypt-demo && PKCS11_OUTPUT_DIR="$PROJECT_ROOT/lab/work" go run . )
