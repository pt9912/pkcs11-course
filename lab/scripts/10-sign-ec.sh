#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
PIN="${PKCS11_USER_PIN:-987654}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
EC_ID="${PKCS11_EC_ID:-02}"
mkdir -p lab/work
printf 'hello pkcs11 ec\n' > lab/work/data-ec.txt

# SoftHSM v2 meldet nur CKM_ECDSA (ohne Token-Side-Hash). Wir hashen also
# applikationsseitig mit SHA-256 und signieren den 32-Byte-Hash mit CKM_ECDSA.
# Verify-Skript bleibt unveraendert: openssl dgst -sha256 -verify rechnet den
# gleichen Hash und prueft die ECDSA-Signatur dagegen.
openssl dgst -binary -sha256 lab/work/data-ec.txt > lab/work/data-ec.hash

pkcs11-tool \
  --module "$MODULE" \
  --login \
  --pin "$PIN" \
  --token-label "$LABEL" \
  --sign \
  --mechanism ECDSA \
  --signature-format openssl \
  --id "$EC_ID" \
  --input-file lab/work/data-ec.hash \
  --output-file lab/work/data-ec.sig

pkcs11-tool \
  --module "$MODULE" \
  --token-label "$LABEL" \
  --read-object \
  --type pubkey \
  --id "$EC_ID" \
  --output-file lab/work/public-ec.der

echo "ECDSA-Signatur: lab/work/data-ec.sig (ueber SHA256-Hash)"
echo "EC Public Key DER: lab/work/public-ec.der"
