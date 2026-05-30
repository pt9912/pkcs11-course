#!/usr/bin/env bash
set -euo pipefail
PROJECT_ROOT="$(pwd)"
mkdir -p lab/work
( cd lab/csharp/Pkcs11CsrDemo && PKCS11_OUTPUT_DIR="$PROJECT_ROOT/lab/work" dotnet run --configuration Release )
openssl req -in lab/work/csharp-app.csr -noout -verify -subject 2>&1 | sed 's/^/  /'
