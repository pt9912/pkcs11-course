#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
PIN="${PKCS11_USER_PIN:-987654}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
CA_KEY_LABEL="${PKCS11_CA_KEY_LABEL:-ca-key}"
CA_KEY_ID="${PKCS11_CA_KEY_ID:-08}"
SUBJECT="${PKCS11_CA_SUBJECT:-/CN=Lab Root CA/O=PKCS11 Lab/OU=Course}"
DAYS="${PKCS11_CA_DAYS:-3650}"
mkdir -p lab/work

# Pruefen, ob das Cert schon im Token liegt — analog zu 08-import-cert.sh
if pkcs11-tool --module "$MODULE" --login --pin "$PIN" --token-label "$LABEL" --list-objects 2>/dev/null \
   | awk -v want="$CA_KEY_LABEL" '
       /Object;/ { in_cert = ($0 ~ /^Certificate Object/); next }
       in_cert && match($0, /label:[[:space:]]*/) {
         value = substr($0, RSTART + RLENGTH)
         sub(/[[:space:]]+$/, "", value)
         if (value == want) { print "match"; exit }
       }' | grep -q match; then
  echo "CA-Cert mit Label '$CA_KEY_LABEL' existiert bereits."
  exit 0
fi

PKCS11_ENGINE="${PKCS11_ENGINE_PATH:-}"
if [ -z "$PKCS11_ENGINE" ]; then
  PKCS11_ENGINE="$(find /usr/lib /usr/lib64 -maxdepth 5 -type f -name pkcs11.so -path '*engines*' 2>/dev/null | head -n 1 || true)"
fi
[ -n "$PKCS11_ENGINE" ] || { echo "pkcs11-Engine nicht gefunden." >&2; exit 1; }

OPENSSL_CONF_FILE="$(mktemp)"
trap 'rm -f "$OPENSSL_CONF_FILE"' EXIT
cat > "$OPENSSL_CONF_FILE" <<EOF
openssl_conf = openssl_init

[openssl_init]
engines = engine_section

[engine_section]
pkcs11 = pkcs11_section

[pkcs11_section]
engine_id = pkcs11
dynamic_path = ${PKCS11_ENGINE}
MODULE_PATH = ${MODULE}
init = 0
EOF

CA_KEY_URI="pkcs11:token=${LABEL};object=${CA_KEY_LABEL};type=private;pin-value=${PIN}"

# Self-signed Root CA: openssl req -x509 mit pkcs11-Engine als Key-Quelle.
# basicConstraints CA:TRUE markiert das Cert als CA, keyUsage cert/CRL-Sign
# erlaubt das Signieren weiterer Certs. SKI/AKI helfen Verifier-Chains.
OPENSSL_CONF="$OPENSSL_CONF_FILE" openssl req \
  -new -x509 -days "$DAYS" \
  -engine pkcs11 -keyform engine -key "$CA_KEY_URI" \
  -sha256 \
  -subj "$SUBJECT" \
  -addext "basicConstraints=critical,CA:TRUE" \
  -addext "keyUsage=critical,keyCertSign,cRLSign" \
  -addext "subjectKeyIdentifier=hash" \
  -out lab/work/ca-cert.pem

openssl x509 -in lab/work/ca-cert.pem -outform DER -out lab/work/ca-cert.der

pkcs11-tool \
  --module "$MODULE" \
  --login --pin "$PIN" \
  --token-label "$LABEL" \
  --write-object lab/work/ca-cert.der \
  --type cert \
  --id "$CA_KEY_ID" \
  --label "$CA_KEY_LABEL"

echo "Root-CA-Cert: lab/work/ca-cert.pem"
echo "Subject: $(openssl x509 -in lab/work/ca-cert.pem -noout -subject | sed 's/subject= *//')"
echo "Importiert ins Token: id=$CA_KEY_ID label=$CA_KEY_LABEL"
