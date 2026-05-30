#!/usr/bin/env bash
set -euo pipefail
PROJECT_ROOT="$(pwd)"
mkdir -p lab/work
( cd lab/csharp/Pkcs11EncryptDemo && PKCS11_OUTPUT_DIR="$PROJECT_ROOT/lab/work" dotnet run --configuration Release )
