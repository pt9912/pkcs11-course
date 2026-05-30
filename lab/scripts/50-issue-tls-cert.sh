#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
PIN="${PKCS11_USER_PIN:-987654}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
KEY_LABEL="${PKCS11_KEY_LABEL:-signing-key}"
SUBJECT="${PKCS11_TLS_CERT_SUBJECT:-/CN=localhost}"
DAYS="${PKCS11_TLS_CERT_DAYS:-365}"
mkdir -p lab/work

# Self-signed TLS-Cert ueber den HSM-Signing-Key:
# - Cert-Pubkey = HSM-Signing-Pubkey (Subject Key) -> Client traut diesem Key
# - Cert-Signatur = HSM-Signing-Key (self-signed)
# - CN=localhost + SAN=DNS:localhost -> curl akzeptiert ohne --insecure
# Wir nutzen openssl req -new -x509 mit pkcs11-Engine — derselbe Pattern
# wie 08-import-cert.sh, plus -addext fuer den SAN.

PKCS11_ENGINE="${PKCS11_ENGINE_PATH:-}"
if [ -z "$PKCS11_ENGINE" ]; then
  PKCS11_ENGINE="$(find /usr/lib /usr/lib64 -maxdepth 5 -type f -name pkcs11.so -path '*engines*' 2>/dev/null | head -n 1 || true)"
fi
if [ -z "$PKCS11_ENGINE" ]; then
  echo "pkcs11-Engine nicht gefunden. PKCS11_ENGINE_PATH explizit setzen." >&2
  exit 1
fi

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

KEY_URI="pkcs11:token=${LABEL};object=${KEY_LABEL};type=private;pin-value=${PIN}"

OPENSSL_CONF="$OPENSSL_CONF_FILE" openssl req \
  -new -x509 \
  -engine pkcs11 -keyform engine \
  -key "$KEY_URI" \
  -subj "$SUBJECT" \
  -addext "subjectAltName=DNS:localhost,IP:127.0.0.1" \
  -days "$DAYS" \
  -sha256 \
  -out lab/work/tls-cert.pem

echo "TLS-Cert: lab/work/tls-cert.pem ($(openssl x509 -in lab/work/tls-cert.pem -noout -subject))"
echo "         $(openssl x509 -in lab/work/tls-cert.pem -noout -ext subjectAltName | tail -1)"
