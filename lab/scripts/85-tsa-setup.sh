#!/usr/bin/env bash
set -euo pipefail
# TSA-Setup fuer Kapitel 25 (RFC 3161).
#
# Schritt 1: TSA-Key als Software-Key erzeugen (lab/work/tsa-key.pem).
#            *Lab-Simplification*: openssl ts -reply unterstuetzt keine
#            pkcs11-engine-URIs als signer_key — der Config-Loader ruft
#            fopen() statt der Engine. Wir nutzen deshalb einen Software-
#            Key. Der Document-Signing-Key (signing-key) bleibt unveraendert
#            HSM-resident; die Sprach-Demos zeigen den Pfad mit HSM-TSA-Key.
# Schritt 2: CSR fuer den Software-TSA-Key bauen.
# Schritt 3: CA-Key (Modul 22, weiterhin im HSM) signiert die CSR mit
#            extendedKeyUsage=critical,timeStamping (RFC 3161 §2.3).
# Schritt 4: TSA-Cert ablegen — der openssl-ts-Server laedt es spaeter.
#
# In Produktion haette die TSA ihr eigenes dediziertes HSM und eigenes
# CA-Anchoring. Im Lab teilen sich Signer-CA und TSA-CA denselben ca-key,
# und der TSA-Signing-Key liegt im Filesystem (Lab-Kompromiss).

MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
PIN="${PKCS11_USER_PIN:-987654}"
TOKEN_LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
CA_LABEL="${PKCS11_CA_KEY_LABEL:-ca-key}"
SUBJECT="${PKCS11_TSA_SUBJECT:-/CN=Lab TSA/O=PKCS11 Lab/OU=Time Stamping}"
DAYS="${PKCS11_TSA_DAYS:-3650}"

mkdir -p lab/work

TSA_KEY_FILE="lab/work/tsa-key.pem"
TSA_CERT_FILE="lab/work/tsa-cert.pem"
CA_KEY_URI="pkcs11:token=${TOKEN_LABEL};object=${CA_LABEL};type=private;pin-value=${PIN}"

# --- Idempotenz: alles fertig, wenn TSA-Cert + Key da sind ---
if [ -f "$TSA_KEY_FILE" ] && [ -f "$TSA_CERT_FILE" ]; then
  echo "TSA-Key und TSA-Cert existieren bereits."
  echo "  Key:  $TSA_KEY_FILE"
  echo "  Cert: $TSA_CERT_FILE"
  exit 0
fi

# --- openssl-Engine-Pfad finden (gleiche Logik wie 65/66) ---
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

echo "=== 1) TSA-Key als Software-RSA-2048 erzeugen ==="
openssl genrsa -out "$TSA_KEY_FILE" 2048 2>&1 | sed 's/^/  /'
chmod 600 "$TSA_KEY_FILE"
echo "  Key: $TSA_KEY_FILE"

echo "=== 2) CSR fuer TSA-Key bauen ==="
openssl req \
  -new \
  -key "$TSA_KEY_FILE" \
  -sha256 \
  -subj "$SUBJECT" \
  -addext "extendedKeyUsage=critical,timeStamping" \
  -addext "keyUsage=critical,digitalSignature,nonRepudiation" \
  -out lab/work/tsa.csr
openssl req -in lab/work/tsa.csr -noout -subject -verify 2>&1 | sed 's/^/    /'

echo "=== 3) CA signiert TSA-CSR (CA-Key ist HSM-resident) ==="
[ -f lab/work/ca.serial ] || echo "00001000" > lab/work/ca.serial

# RFC 3161 §2.3 verlangt extendedKeyUsage=critical,timeStamping. Aus der CSR
# kopieren (--copy_extensions copy) — die hat es bereits kritisch.
OPENSSL_CONF="$OPENSSL_CONF_FILE" openssl x509 \
  -req -in lab/work/tsa.csr \
  -CA lab/work/ca-cert.pem \
  -CAkey "$CA_KEY_URI" \
  -CAkeyform engine \
  -engine pkcs11 \
  -CAserial lab/work/ca.serial \
  -days "$DAYS" \
  -sha256 \
  -copy_extensions copy \
  -out "$TSA_CERT_FILE"

echo "  TSA-Cert: $TSA_CERT_FILE"
echo "  Serial:   $(openssl x509 -in $TSA_CERT_FILE -noout -serial | sed 's/serial=//')"
echo "  Subject:  $(openssl x509 -in $TSA_CERT_FILE -noout -subject | sed 's/subject= *//')"
openssl x509 -in "$TSA_CERT_FILE" -noout -ext extendedKeyUsage | sed 's/^/    /'

echo "=== 4) Chain-Verify ==="
openssl verify -CAfile lab/work/ca-cert.pem "$TSA_CERT_FILE"

echo "TSA-Setup fertig: tsa-key (Software, Lab-Kompromiss) + tsa-cert (extKU=timeStamping, CA-signiert via HSM)."
