#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"

# pkcs11-tool --list-token-slots druckt die Token-Flags in lesbarer Form.
# Wir extrahieren die "token flags"-Zeile und werten die PIN-relevanten
# Sub-Flags aus (CKF_USER_PIN_COUNT_LOW, CKF_USER_PIN_FINAL_TRY,
# CKF_USER_PIN_LOCKED, sowie die SO-Pendants).

FLAGS_LINE="$(pkcs11-tool --module "$MODULE" --list-token-slots 2>&1 \
  | awk -v label="$LABEL" '
      /token label/ { match_label = ($0 ~ label); next }
      match_label && /token flags/ {
        sub(/^.*token flags[[:space:]]*:[[:space:]]*/, ""); print; exit
      }')"

if [ -z "$FLAGS_LINE" ]; then
  echo "Token mit Label '$LABEL' nicht gefunden." >&2
  exit 2
fi

echo "Token:        $LABEL"
echo "Roh-Flags:    $FLAGS_LINE"
echo
echo "--- PIN-Status (User) ---"
echo "$FLAGS_LINE" | grep -q "user PIN count low" \
  && echo "  CKF_USER_PIN_COUNT_LOW:    JA   (mindestens 1 Fehlversuch seit letzter erfolgreicher Login)" \
  || echo "  CKF_USER_PIN_COUNT_LOW:    nein"
echo "$FLAGS_LINE" | grep -q "user PIN final try" \
  && echo "  CKF_USER_PIN_FINAL_TRY:    JA   (Nur noch EIN Versuch bis zum Lockout!)" \
  || echo "  CKF_USER_PIN_FINAL_TRY:    nein"
echo "$FLAGS_LINE" | grep -q "user PIN locked" \
  && echo "  CKF_USER_PIN_LOCKED:       JA   (Recovery durch SO via init-pin noetig)" \
  || echo "  CKF_USER_PIN_LOCKED:       nein"
echo "$FLAGS_LINE" | grep -q "user PIN to be changed" \
  && echo "  CKF_USER_PIN_TO_BE_CHANGED: JA  (PIN muss vor Nutzung geaendert werden)" \
  || echo "  CKF_USER_PIN_TO_BE_CHANGED: nein"

echo
echo "--- PIN-Status (SO) ---"
echo "$FLAGS_LINE" | grep -q "SO PIN count low" \
  && echo "  CKF_SO_PIN_COUNT_LOW:      JA" \
  || echo "  CKF_SO_PIN_COUNT_LOW:      nein"
echo "$FLAGS_LINE" | grep -q "SO PIN locked" \
  && echo "  CKF_SO_PIN_LOCKED:         JA   (Token effektiv gebrickt!)" \
  || echo "  CKF_SO_PIN_LOCKED:         nein"
