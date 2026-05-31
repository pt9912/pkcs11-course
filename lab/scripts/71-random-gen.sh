#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
PIN="${PKCS11_USER_PIN:-987654}"
SIZE="${PKCS11_RANDOM_SIZE:-32}"
mkdir -p lab/work

# pkcs11-tool --generate-random ist der bequeme Wrapper um C_GenerateRandom.
# Login ist NICHT formal noetig (CKF_LOGIN_REQUIRED schuetzt private Objekte,
# nicht den RNG-Pool) — pkcs11-tool macht es trotzdem, weil ein Login per
# --login auch sonst nichts kaputt macht. Auf manchen HSMs ist der Login fuer
# den RNG-Pfad Pflicht, dokumentiert in der Vendor-PKCS#11-Erweiterung.

echo "=== C_GenerateRandom ueber pkcs11-tool ==="
echo "Token: $LABEL"
echo "Groesse pro Aufruf: $SIZE Byte"
echo

# CKF_RNG steht in den Token-Flags. Ohne dieses Bit ist C_GenerateRandom
# nicht garantiert — pkcs11-tool macht den Aufruf trotzdem, fuer reale
# HSMs sollte CKF_RNG vor dem ersten RNG-Call gepruft werden.
# Label-Vergleich per Field-Match (kein Regex), damit Token-Labels mit
# Regex-Meta wie `.`, `*` oder `+` nicht mismatch produzieren — gleiche
# Konvention wie 01-/04-/08-/09-*.sh seit v0.4.0.
FLAGS_LINE="$(pkcs11-tool --module "$MODULE" --list-token-slots 2>&1 \
  | awk -v want="$LABEL" '
      /token label/ {
        line = $0
        sub(/^[[:space:]]*token label[[:space:]]*:[[:space:]]*/, "", line)
        sub(/[[:space:]]+$/, "", line)
        match_label = (line == want)
        next
      }
      match_label && /token flags/ {
        sub(/^.*token flags[[:space:]]*:[[:space:]]*/, ""); print; exit
      }')"
if echo "$FLAGS_LINE" | grep -qw "rng"; then
  echo "CKF_RNG: gesetzt (Token bietet einen Random-Source an)"
else
  echo "CKF_RNG: NICHT gesetzt — C_GenerateRandom darf fehlschlagen!"
fi
echo "Roh-Flags: $FLAGS_LINE"
echo

# Drei Aufrufe in unterschiedlichen Groessen, persistiert fuer den Bench-Schritt.
for n in 16 32 "$SIZE"; do
  out="lab/work/random-${n}.bin"
  pkcs11-tool --module "$MODULE" --login --pin "$PIN" --token-label "$LABEL" \
    --generate-random "$n" > "$out"
  size_real=$(stat -c%s "$out")
  # od -An -tx1: keine Adressspalte, hex-Bytes mit Spaces — danach Spaces raus,
  # damit wir den klassischen Hex-Stream haben.
  hex="$(od -An -tx1 -N 32 "$out" | tr -d ' \n')"
  echo "  $n Byte angefordert -> $out ($size_real Byte geliefert)"
  echo "    Hex (erste 32 Byte): $hex"
done

echo
echo "Hinweis: derselbe Aufruf liefert bei jedem Run andere Bytes — wenn er das"
echo "         nicht tut, ist der HSM-RNG kaputt oder geseedet/deterministisch."
