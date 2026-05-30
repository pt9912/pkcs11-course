#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
PIN="${PKCS11_USER_PIN:-987654}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
KEY_ID="${PKCS11_KEY_ID:-01}"
SIZE_MB="${PKCS11_STREAM_SIZE_MB:-100}"
mkdir -p lab/work

# Testfile mit der gewuenschten Groesse erzeugen, falls fehlend/falsche Groesse.
WANT=$((SIZE_MB * 1024 * 1024))
if [ ! -f lab/work/large.bin ] || [ "$(stat -c%s lab/work/large.bin)" -ne "$WANT" ]; then
  echo "Erzeuge ${SIZE_MB}MB Testfile lab/work/large.bin ..."
  dd if=/dev/zero of=lab/work/large.bin bs=1M count="$SIZE_MB" status=none
fi

# Mechanism SHA256-RSA-PKCS = CKM_SHA256_RSA_PKCS: das TOKEN haelt den SHA-256-State.
# pkcs11-tool ruft intern C_SignInit, dann C_SignUpdate(chunk) in Schleife,
# zum Schluss C_SignFinal — egal wie gross das Input-File.
# Mit pkcs11-spy beobachtbar (siehe Uebung 09).
pkcs11-tool \
  --module "$MODULE" \
  --login --pin "$PIN" \
  --token-label "$LABEL" \
  --sign --mechanism SHA256-RSA-PKCS \
  --id "$KEY_ID" \
  --input-file lab/work/large.bin \
  --output-file lab/work/large.sig

echo "Input:    lab/work/large.bin ($(stat -c%s lab/work/large.bin) Bytes)"
echo "Signatur: lab/work/large.sig ($(stat -c%s lab/work/large.sig) Bytes)"
