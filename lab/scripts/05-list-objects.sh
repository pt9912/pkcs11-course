#!/usr/bin/env bash
set -euo pipefail
pkcs11-tool \
  --module "${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}" \
  --login \
  --pin "${PKCS11_USER_PIN:-987654}" \
  --token-label "${PKCS11_TOKEN_LABEL:-dev-token}" \
  --list-objects
