#!/usr/bin/env bash
set -euo pipefail
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
SO_PIN="${PKCS11_SO_PIN:-1234}"
USER_PIN="${PKCS11_USER_PIN:-987654}"

# softhsm2.conf zeigt auf /workspace/lab/work/tokens — sicherstellen, dass das
# Verzeichnis existiert und beschreibbar ist, auch wenn der Bind-Mount neu ist.
TOKEN_DIR="${SOFTHSM2_TOKEN_DIR:-/workspace/lab/work/tokens}"
mkdir -p "$TOKEN_DIR"
if [ ! -w "$TOKEN_DIR" ]; then
  echo "Token-Verzeichnis nicht beschreibbar: $TOKEN_DIR" >&2
  echo "Hinweis: Bind-Mount-Eigentumsrechte pruefen oder PKCS11_LAB_USER an die Host-UID anpassen." >&2
  exit 1
fi

if softhsm2-util --show-slots | awk -v want="$LABEL" '
     /Label:/ {
       sub(/^[[:space:]]*Label:[[:space:]]*/, "")
       sub(/[[:space:]]+$/, "")
       if ($0 == want) { print "match"; exit }
     }' | grep -q match; then
  echo "Token '$LABEL' existiert bereits."
  exit 0
fi

softhsm2-util --init-token --free --label "$LABEL" --so-pin "$SO_PIN" --pin "$USER_PIN"
softhsm2-util --show-slots
