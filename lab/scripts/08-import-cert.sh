#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
PIN="${PKCS11_USER_PIN:-987654}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
KEY_LABEL="${PKCS11_KEY_LABEL:-signing-key}"
KEY_ID="${PKCS11_KEY_ID:-01}"
SUBJECT="${PKCS11_CERT_SUBJECT:-/CN=signing-key/O=PKCS11 Lab}"
DAYS="${PKCS11_CERT_DAYS:-365}"

mkdir -p lab/work

if pkcs11-tool --module "$MODULE" --login --pin "$PIN" --token-label "$LABEL" --list-objects 2>/dev/null \
   | awk '/^Certificate Object/,/^$/' | grep -q "label:[[:space:]]*$KEY_LABEL"; then
  echo "Zertifikat mit Label '$KEY_LABEL' existiert bereits im Token."
  exit 0
fi

KEY_URI="pkcs11:token=${LABEL};object=${KEY_LABEL};type=private;pin-value=${PIN}"

OPENSSL_CONF="$(mktemp)"
cat > "$OPENSSL_CONF" <<EOF
openssl_conf = openssl_init

[openssl_init]
engines = engine_section

[engine_section]
pkcs11 = pkcs11_section

[pkcs11_section]
engine_id = pkcs11
dynamic_path = /usr/lib/x86_64-linux-gnu/engines-3/pkcs11.so
MODULE_PATH = ${MODULE}
init = 0
EOF

OPENSSL_CONF="$OPENSSL_CONF" openssl req \
  -new -x509 -days "$DAYS" \
  -engine pkcs11 -keyform engine \
  -key "$KEY_URI" \
  -sha256 \
  -subj "$SUBJECT" \
  -out lab/work/cert.pem

rm -f "$OPENSSL_CONF"

openssl x509 -in lab/work/cert.pem -outform DER -out lab/work/cert.der

pkcs11-tool \
  --module "$MODULE" \
  --login \
  --pin "$PIN" \
  --token-label "$LABEL" \
  --write-object lab/work/cert.der \
  --type cert \
  --id "$KEY_ID" \
  --label "$KEY_LABEL"

echo "Zertifikat importiert: id=$KEY_ID label=$KEY_LABEL"
