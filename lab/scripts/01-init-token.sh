#!/usr/bin/env bash
set -euo pipefail
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
SO_PIN="${PKCS11_SO_PIN:-1234}"
USER_PIN="${PKCS11_USER_PIN:-987654}"

if softhsm2-util --show-slots | grep -q "Label:[[:space:]]*$LABEL"; then
  echo "Token '$LABEL' existiert bereits."
  exit 0
fi

softhsm2-util --init-token --free --label "$LABEL" --so-pin "$SO_PIN" --pin "$USER_PIN"
softhsm2-util --show-slots
