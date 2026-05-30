#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
PIN="${PKCS11_USER_PIN:-987654}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
KEY_ID="${PKCS11_HMAC_ID:-05}"
mkdir -p lab/work

if [ ! -f lab/work/hmac-data.txt ]; then
  printf 'API-Token-Anfrage von client-42 am 30.05.2026T12:00:00Z\n' > lab/work/hmac-data.txt
fi

# CKM_SHA256_HMAC: Token rechnet HMAC-SHA256 unter dem geheimen Key.
# Output ist 32 Byte (Hash-Laenge). Im Gegensatz zu RSA-Signaturen kann nur
# wer den GLEICHEN geheimen Key hat verifizieren — auch der Verifizierer
# braucht HSM-Zugriff (oder zumindest den Key).
pkcs11-tool \
  --module "$MODULE" \
  --login --pin "$PIN" \
  --token-label "$LABEL" \
  --sign --mechanism SHA256-HMAC \
  --id "$KEY_ID" \
  --input-file lab/work/hmac-data.txt \
  --output-file lab/work/hmac-data.mac

echo "Input: lab/work/hmac-data.txt ($(stat -c%s lab/work/hmac-data.txt) Bytes)"
echo "MAC:   lab/work/hmac-data.mac ($(stat -c%s lab/work/hmac-data.mac) Bytes)"
