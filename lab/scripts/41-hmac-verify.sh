#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
PIN="${PKCS11_USER_PIN:-987654}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
KEY_ID="${PKCS11_HMAC_ID:-05}"

# C_Verify-Pfad: pkcs11-tool ruft C_VerifyInit + C_Verify mit dem gegebenen MAC.
# Der Token vergleicht intern in constant time — vermeidet Timing-Leaks im Host-Code.
# Output "Verification successful" bei korrektem MAC, Exit-Code != 0 bei Mismatch.
pkcs11-tool \
  --module "$MODULE" \
  --login --pin "$PIN" \
  --token-label "$LABEL" \
  --verify --mechanism SHA256-HMAC \
  --id "$KEY_ID" \
  --input-file lab/work/hmac-data.txt \
  --signature-file lab/work/hmac-data.mac
