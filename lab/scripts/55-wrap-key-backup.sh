#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
PIN="${PKCS11_USER_PIN:-987654}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
KEK_ID="${PKCS11_KEK_ID:-06}"
PAYLOAD_ID="${PKCS11_PAYLOAD_ID:-07}"
mkdir -p lab/work

# Backup-Export per pkcs11-tool --wrap:
# 1) payload-key auf ID=07 muss EXTRACTABLE sein. pkcs11-tools Default ist
#    CKA_EXTRACTABLE=FALSE; wir erkennen alte non-extractable Vorgaenger und
#    legen sie extractable neu an.
# 2) Sample-Daten mit dem Original-Key verschluesseln (Beweis fuer den Restore).
# 3) C_WrapKey via KEK (ID=06) → opaque Blob.
#
# UNWRAP/RESTORE ist NICHT in diesem Bash-Skript: pkcs11-tool setzt beim
# Unwrap-Template CKA_VALUE_LEN, was SoftHSM 2.6 mit CKR_ATTRIBUTE_READ_ONLY
# ablehnt (bekannter OpenSC-SoftHSM-Incompat). Vollstaendiger Round-Trip
# inkl. Unwrap+Decrypt: make go-wrap-demo / make csharp-wrap-demo / etc.

NEED_REGEN=1
if pkcs11-tool --module "$MODULE" --login --pin "$PIN" --token-label "$LABEL" --list-objects --id "$PAYLOAD_ID" 2>/dev/null \
   | grep -q '^Secret Key Object'; then
  ACCESS="$(pkcs11-tool --module "$MODULE" --login --pin "$PIN" --token-label "$LABEL" \
                       --list-objects --id "$PAYLOAD_ID" 2>/dev/null \
            | awk -F': *' '/Access:/ {print $2; exit}')"
  if echo "$ACCESS" | grep -q 'extractable' && ! echo "$ACCESS" | grep -q 'never extractable'; then
    NEED_REGEN=0
  else
    pkcs11-tool --module "$MODULE" --login --pin "$PIN" --token-label "$LABEL" \
      --delete-object --type secrkey --id "$PAYLOAD_ID" >/dev/null
  fi
fi
if [ "$NEED_REGEN" = "1" ]; then
  pkcs11-tool \
    --module "$MODULE" --login --pin "$PIN" --token-label "$LABEL" \
    --keygen --key-type aes:32 --id "$PAYLOAD_ID" --label payload-key \
    --usage-decrypt --extractable >/dev/null
  echo "payload-key (ID=$PAYLOAD_ID) frisch generiert mit CKA_EXTRACTABLE=true."
fi

if [ ! -f lab/work/wrap-sample.txt ]; then
  printf 'GEHEIME DATEN ZUM TESTEN VON WRAP/UNWRAP (30.05.2026)\n' > lab/work/wrap-sample.txt
fi
openssl rand -hex 16 > lab/work/wrap-sample.iv.hex
IV_HEX="$(cat lab/work/wrap-sample.iv.hex)"

pkcs11-tool --module "$MODULE" --login --pin "$PIN" --token-label "$LABEL" \
  --encrypt --mechanism AES-CBC-PAD \
  --id "$PAYLOAD_ID" --iv "$IV_HEX" \
  --input-file lab/work/wrap-sample.txt --output-file lab/work/wrap-sample.enc

# C_WrapKey: KEK (ID=06) wrappt payload-key (ID=07) → opaque blob.
# Mechanism AES-KEY-WRAP-PAD = CKM_AES_KEY_WRAP_PAD (RFC 5649).
pkcs11-tool --module "$MODULE" --login --pin "$PIN" --token-label "$LABEL" \
  --wrap --mechanism AES-KEY-WRAP-PAD \
  --id "$KEK_ID" \
  --application-id "$PAYLOAD_ID" \
  --output-file lab/work/payload-key.wrapped

echo "--- Backup-Artefakte ---"
echo "Wrapped Key:  lab/work/payload-key.wrapped ($(stat -c%s lab/work/payload-key.wrapped) Bytes)"
echo "IV:           lab/work/wrap-sample.iv.hex (16 Bytes hex)"
echo "Ciphertext:   lab/work/wrap-sample.enc ($(stat -c%s lab/work/wrap-sample.enc) Bytes)"
echo "Klartext:     lab/work/wrap-sample.txt (Referenz)"
echo
echo "Fuer Unwrap+Restore: make go-wrap-demo / csharp-wrap-demo / java-wrap-demo / kotlin-wrap-demo"
