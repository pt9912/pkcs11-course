#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
PIN="${PKCS11_USER_PIN:-987654}"

# Erwartete CKA-Usage-Profile pro Label.
# Format: "label|object-class|expected-usage"
# object-class: privkey | pubkey | secret
# expected-usage: kommagetrennt, sortiert wie pkcs11-tool sie liest
# (siehe pkcs11-tool source: USAGE-Strings sind "sign", "verify",
# "encrypt", "decrypt", "wrap", "unwrap", "derive", "verifyRecover",
# "signRecover"). Wir ignorieren signRecover/verifyRecover, weil das
# manche HSMs als Beigabe von sign/verify automatisch setzen.
EXPECTED=(
  # signing-key (RSA, ID=01): sign-only
  "signing-key|privkey|sign"
  "signing-key|pubkey|verify"
  # ec-signing-key (EC, ID=02): sign-only
  "ec-signing-key|privkey|sign"
  "ec-signing-key|pubkey|verify"
  # wrap-key (RSA, ID=03): decrypt + unwrap (priv), encrypt + wrap (pub)
  "wrap-key|privkey|decrypt,unwrap"
  "wrap-key|pubkey|encrypt,wrap"
  # aes-stream-key (AES, ID=04): encrypt + decrypt
  "aes-stream-key|secret|encrypt,decrypt"
  # hmac-key (GENERIC, ID=05): sign + verify
  "hmac-key|secret|sign,verify"
  # backup-kek (AES, ID=06): wrap + unwrap
  "backup-kek|secret|wrap,unwrap"
  # ca-key (RSA, ID=08): sign-only
  "ca-key|privkey|sign"
  "ca-key|pubkey|verify"
)

# pkcs11-tool --list-objects gibt Objekte als Bloecke aus, getrennt durch
# Leerzeilen. Wir parsen jeden Block, extrahieren label, object class und
# Usage, normalisieren die Usage-Liste (kein signRecover/verifyRecover,
# sortiert, ohne Leerzeichen) und vergleichen mit dem Erwartet-Eintrag.
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

pkcs11-tool --module "$MODULE" --login --pin "$PIN" --token-label "$LABEL" \
  --list-objects > "$TMP" 2>/dev/null

awk '
  BEGIN { class=""; lbl=""; usg="" }
  /^Public Key Object/  { flush(); class="pubkey";  next }
  /^Private Key Object/ { flush(); class="privkey"; next }
  /^Secret Key Object/  { flush(); class="secret";  next }
  /^Certificate Object/ { flush(); class="";        next }
  /^Data object/        { flush(); class="";        next }
  /^[[:space:]]+label:/ {
    sub(/^[[:space:]]+label:[[:space:]]*/, "")
    sub(/[[:space:]]+$/, "")
    lbl=$0; next
  }
  /^[[:space:]]+Usage:/ {
    sub(/^[[:space:]]+Usage:[[:space:]]*/, "")
    sub(/[[:space:]]+$/, "")
    usg=$0; next
  }
  END { flush() }
  function flush() {
    if (class != "" && lbl != "") {
      print class "|" lbl "|" usg
    }
    lbl=""; usg=""
  }
' "$TMP" > "$TMP.parsed"

# Normalisiert die Usage-Liste: trennt an ",", strippt Leerzeichen,
# loescht signRecover/verifyRecover (SoftHSM-Implizit-Flags), sortiert
# alphabetisch, joint mit ",".
normalize_usage() {
  local raw="$1"
  echo "$raw" \
    | tr ',' '\n' \
    | sed 's/^[[:space:]]*//; s/[[:space:]]*$//' \
    | grep -vE '^(signRecover|verifyRecover|none)$' \
    | grep -v '^$' \
    | sort \
    | paste -sd,
}

FAIL=0
PASS=0
echo "=== validate-key-usage ==="
printf "%-22s %-9s %-30s %-30s %s\n" "LABEL" "CLASS" "ERWARTET" "TATSAECHLICH" "STATUS"
for expect in "${EXPECTED[@]}"; do
  IFS='|' read -r lbl cls exp_usg <<< "$expect"
  exp_norm="$(normalize_usage "$exp_usg")"
  # Den passenden Eintrag aus der parsed-Datei holen
  actual_line="$(awk -F'|' -v c="$cls" -v l="$lbl" '$1==c && $2==l { print $3; exit }' "$TMP.parsed")"
  act_norm="$(normalize_usage "$actual_line")"
  if [ "$exp_norm" = "$act_norm" ]; then
    printf "%-22s %-9s %-30s %-30s OK\n" "$lbl" "$cls" "$exp_norm" "$act_norm"
    PASS=$((PASS+1))
  else
    printf "%-22s %-9s %-30s %-30s FAIL\n" "$lbl" "$cls" "$exp_norm" "$act_norm"
    FAIL=$((FAIL+1))
  fi
done

echo
echo "Ergebnis: $PASS OK, $FAIL FAIL"
if [ "$FAIL" -gt 0 ]; then
  echo
  echo "Mindestens ein Key hat ein anderes CKA-Profil als erwartet."
  echo "Erwartete Profile sind in lab/scripts/77-validate-key-usage.sh hartcodiert."
  echo "Wenn ein Key fehlt: vorgelagertes make-Target laufen lassen (gen-rsa, gen-ec, ..)."
  echo "Wenn ein Key zu viele Flags zeigt: pkcs11-keygen-Helper liefert breitere Defaults — bug."
  exit 1
fi
