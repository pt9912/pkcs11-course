#!/usr/bin/env bash
set -euo pipefail
PROJECT_ROOT="$(pwd)"
mkdir -p lab/work
( cd lab/go/pkcs11-csr-demo && PKCS11_OUTPUT_DIR="$PROJECT_ROOT/lab/work" go run . )
openssl req -in lab/work/go-app.csr -noout -verify -subject 2>&1 | sed 's/^/  /'
