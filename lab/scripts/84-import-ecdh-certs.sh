#!/usr/bin/env bash
set -euo pipefail
# Self-signed Zertifikate fuer alice-ec-key und bob-ec-key importieren.
# Plumbing fuer SunPKCS11 — Java/Kotlin sehen einen KeyStore-Alias nur, wenn
# ein Certificate Object mit gleicher CKA_ID existiert. Funktional sinnlos
# fuer ECDH selbst (das Protokoll braucht keine Zertifikate), aber didaktisch
# unauffaellig: die Zertifikate werden im Demo gar nicht benutzt, sie machen
# nur den KeyStore-Alias sichtbar. Go/C# (CKA_EC_POINT lesen) kommen ohne aus.
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
PIN="${PKCS11_USER_PIN:-987654}"
TOKEN_LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
ALICE_LABEL="${PKCS11_ECDH_ALICE_LABEL:-alice-ec-key}"
ALICE_ID="${PKCS11_ECDH_ALICE_ID:-0a}"
BOB_LABEL="${PKCS11_ECDH_BOB_LABEL:-bob-ec-key}"
BOB_ID="${PKCS11_ECDH_BOB_ID:-0b}"
DAYS="${PKCS11_ECDH_CERT_DAYS:-365}"
mkdir -p lab/work

# Engine-Pfad-Suche (identisch zu 08-import-cert.sh).
ENGINE="${PKCS11_ENGINE_PATH:-}"
if [ -z "$ENGINE" ]; then
  ENGINE="$(find /usr/lib /usr/lib64 -maxdepth 5 -type f -name pkcs11.so -path '*engines*' 2>/dev/null | head -n 1 || true)"
fi
if [ -z "$ENGINE" ]; then
  echo "pkcs11-Engine nicht gefunden. PKCS11_ENGINE_PATH explizit setzen." >&2
  exit 1
fi

OPENSSL_CONF="$(mktemp)"
trap 'rm -f "$OPENSSL_CONF"' EXIT
cat > "$OPENSSL_CONF" <<EOF
openssl_conf = openssl_init

[openssl_init]
engines = engine_section

[engine_section]
pkcs11 = pkcs11_section

[pkcs11_section]
engine_id = pkcs11
dynamic_path = ${ENGINE}
MODULE_PATH = ${MODULE}
init = 0
EOF

issue_cert() {
  local label="$1" id="$2" subject="$3"
  # Existenz-Check via State-Machine: pkcs11-tool hat keine Leerzeilen
  # zwischen Object-Bloecken, ein Range-Pattern reicht nicht.
  if pkcs11-tool --module "$MODULE" --login --pin "$PIN" --token-label "$TOKEN_LABEL" \
       --list-objects 2>/dev/null \
     | awk -v want="$label" '
         /Object;/ { in_cert = ($0 ~ /^Certificate Object/); next }
         in_cert && match($0, /label:[[:space:]]*/) {
           v = substr($0, RSTART + RLENGTH); sub(/[[:space:]]+$/, "", v)
           if (v == want) { print "match"; exit }
         }' | grep -q match; then
    echo "Zertifikat '$label' existiert bereits."
    return
  fi
  local uri="pkcs11:token=${TOKEN_LABEL};object=${label};type=private;pin-value=${PIN}"
  local pem="lab/work/ecdh-${label}.pem"
  local der="lab/work/ecdh-${label}.der"
  OPENSSL_CONF="$OPENSSL_CONF" openssl req \
    -new -x509 -days "$DAYS" \
    -engine pkcs11 -keyform engine \
    -key "$uri" \
    -sha256 \
    -subj "$subject" \
    -out "$pem"
  openssl x509 -in "$pem" -outform DER -out "$der"
  pkcs11-tool --module "$MODULE" --login --pin "$PIN" --token-label "$TOKEN_LABEL" \
    --write-object "$der" --type cert --id "$id" --label "$label"
  echo "Zertifikat importiert: id=$id label=$label"
}

issue_cert "$ALICE_LABEL" "$ALICE_ID" "/CN=alice/O=PKCS11 ECDH Lab"
issue_cert "$BOB_LABEL"   "$BOB_ID"   "/CN=bob/O=PKCS11 ECDH Lab"
