#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
OLD_PIN="${PKCS11_USER_PIN:-987654}"
TMP_PIN="${PKCS11_TMP_PIN:-555444}"

# Demonstriert C_SetPIN: ein eingeloggter User aendert seine eigene PIN.
# Ablauf:
#   1) Ist die aktuelle PIN nutzbar? (Sanity-Check)
#   2) C_SetPIN OLD -> TMP, kurzer Login-Test mit TMP
#   3) C_SetPIN TMP -> OLD, kurzer Login-Test mit OLD
#
# Wichtig: das Skript stellt am Ende immer den Ausgangs-PIN wieder her.
# Bricht es zwischendrin ab, ist die PIN auf TMP — Wiederherstellung dann
# manuell via:
#   pkcs11-tool --change-pin --login --pin $TMP_PIN --new-pin $OLD_PIN

echo "=== 1) Sanity-Check mit aktueller PIN ==="
pkcs11-tool --module "$MODULE" --token-label "$LABEL" --login --pin "$OLD_PIN" --list-objects 2>&1 \
  | grep -c "Object" \
  | xargs -I{} echo "  Login OK, {} Token-Objekte sichtbar."

echo "=== 2) PIN aendern $OLD_PIN -> $TMP_PIN ==="
pkcs11-tool --module "$MODULE" --token-label "$LABEL" \
  --change-pin --login --pin "$OLD_PIN" --new-pin "$TMP_PIN" 2>&1 | tail -1

echo "  Login-Test mit neuer PIN..."
pkcs11-tool --module "$MODULE" --token-label "$LABEL" --login --pin "$TMP_PIN" --list-objects 2>&1 \
  | grep -c "Object" \
  | xargs -I{} echo "  Login mit $TMP_PIN OK, {} Objekte."

echo "=== 3) PIN zurueck $TMP_PIN -> $OLD_PIN ==="
pkcs11-tool --module "$MODULE" --token-label "$LABEL" \
  --change-pin --login --pin "$TMP_PIN" --new-pin "$OLD_PIN" 2>&1 | tail -1

echo "  Login-Test mit Ausgangs-PIN..."
pkcs11-tool --module "$MODULE" --token-label "$LABEL" --login --pin "$OLD_PIN" --list-objects 2>&1 \
  | grep -c "Object" \
  | xargs -I{} echo "  Login mit $OLD_PIN OK, {} Objekte. Ausgangs-State wiederhergestellt."
