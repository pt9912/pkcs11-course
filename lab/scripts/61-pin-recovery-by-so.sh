#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
USER_PIN="${PKCS11_USER_PIN:-987654}"
SO_PIN="${PKCS11_SO_PIN:-1234}"
LOCKOUT_PIN="${PKCS11_LOCKOUT_PIN:-000000}"

# Demonstriert das Recovery-Szenario:
#  1) User-PIN-Failures provozieren — Token signalisiert CKF_USER_PIN_COUNT_LOW.
#     Echte HSMs locken nach N Versuchen (typisch 3); SoftHSM 2.6 setzt nur das
#     Flag, lockt aber NIE wirklich (in der Demo deshalb keine echte Lockout-
#     Wiederherstellung — nur die SO-Reset-Operation).
#  2) SO loggt sich ein und ruft C_InitPIN — setzt die User-PIN auf einen neuen
#     Wert. Damit waere ein echter Lockout aufgehoben.
#  3) Login mit der neuen User-PIN funktioniert.
#  4) Cleanup: SO setzt PIN wieder auf den Standard-Wert zurueck.

echo "=== 1) Drei Fehlversuche mit falschem PIN ==="
for i in 1 2 3; do
  # pkcs11-tool exited non-zero bei PIN-Fehler — wir wollen den Fehler-String
  # extrahieren, ohne dass das Skript wegen set -e abbricht.
  OUTPUT="$(pkcs11-tool --module "$MODULE" --token-label "$LABEL" \
    --login --pin "$LOCKOUT_PIN" --list-objects 2>&1 || true)"
  ERR="$(echo "$OUTPUT" | grep -m1 -oE 'rv = CKR_[A-Z_]+ \(0x[0-9a-f]+\)' || echo 'kein Fehler-String erkannt')"
  echo "  Versuch $i: $ERR"
done

echo "=== 2) Token-Status nach Fehlversuchen ==="
pkcs11-tool --module "$MODULE" --list-token-slots 2>&1 | grep "token flags" \
  | sed "s/.*token flags[[:space:]]*:[[:space:]]*/  Flags: /"
echo "  Hinweis: SoftHSM lockt nicht wirklich (siehe course/21-pin-management.md)."

echo "=== 3) SO setzt User-PIN per C_InitPIN auf 111111 ==="
pkcs11-tool --module "$MODULE" --token-label "$LABEL" \
  --init-pin --login --login-type so --so-pin "$SO_PIN" --new-pin 111111 2>&1 | tail -1

echo "  Login mit der neu gesetzten PIN..."
pkcs11-tool --module "$MODULE" --token-label "$LABEL" --login --pin 111111 --list-objects 2>&1 \
  | grep -c "Object" \
  | xargs -I{} echo "  Login mit 111111 OK, {} Objekte sichtbar."

echo "=== 4) Cleanup: SO setzt PIN zurueck auf Standard ==="
pkcs11-tool --module "$MODULE" --token-label "$LABEL" \
  --init-pin --login --login-type so --so-pin "$SO_PIN" --new-pin "$USER_PIN" 2>&1 | tail -1
echo "  Ausgangs-State wiederhergestellt."
