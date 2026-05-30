#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
PIN="${PKCS11_USER_PIN:-987654}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
KEY_ID="${PKCS11_AES_STREAM_ID:-04}"
SIZE_MB="${PKCS11_STREAM_SIZE_MB:-100}"
mkdir -p lab/work

WANT=$((SIZE_MB * 1024 * 1024))
if [ ! -f lab/work/large.bin ] || [ "$(stat -c%s lab/work/large.bin)" -ne "$WANT" ]; then
  echo "Erzeuge ${SIZE_MB}MB Testfile lab/work/large.bin ..."
  dd if=/dev/zero of=lab/work/large.bin bs=1M count="$SIZE_MB" status=none
fi

# IV einmalig erzeugen und als Hex persistieren — Encrypt + Decrypt brauchen
# denselben Wert. 16 Byte = AES-Blockgroesse, openssl liefert direkt hex.
if [ ! -f lab/work/large.iv.hex ]; then
  openssl rand -hex 16 > lab/work/large.iv.hex
fi
IV_HEX="$(cat lab/work/large.iv.hex)"

# AES-CBC-PAD: Cipher-Block-Chaining mit PKCS#7-Padding im letzten Block.
# pkcs11-tool ruft intern C_EncryptInit, C_EncryptUpdate(chunk) in Schleife,
# C_EncryptFinal (liefert den letzten gepaddeten Block). Speicherbedarf bleibt
# konstant unabhaengig von der File-Groesse.
# CKM_AES_CBC_PAD ist NICHT AEAD — fuer Tamper-Erkennung waere zusaetzlich
# HMAC oder ein AEAD-Modus (AES-GCM) noetig. Siehe course/15-streaming.md.
pkcs11-tool \
  --module "$MODULE" \
  --login --pin "$PIN" \
  --token-label "$LABEL" \
  --encrypt --mechanism AES-CBC-PAD \
  --id "$KEY_ID" \
  --iv "$IV_HEX" \
  --input-file lab/work/large.bin \
  --output-file lab/work/large.enc

echo "Input:      lab/work/large.bin ($(stat -c%s lab/work/large.bin) Bytes)"
echo "IV:         lab/work/large.iv.hex (16 Bytes, hex-codiert)"
echo "Ciphertext: lab/work/large.enc ($(stat -c%s lab/work/large.enc) Bytes inkl. PKCS#7-Padding)"
