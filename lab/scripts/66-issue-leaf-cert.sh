#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
PIN="${PKCS11_USER_PIN:-987654}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
LEAF_LABEL="${PKCS11_KEY_LABEL:-signing-key}"
LEAF_ID="${PKCS11_KEY_ID:-01}"
LEAF_CERT_LABEL="${PKCS11_LEAF_CERT_LABEL:-leaf-cert}"
LEAF_CERT_ID="${PKCS11_LEAF_CERT_ID:-09}"
CA_LABEL="${PKCS11_CA_KEY_LABEL:-ca-key}"
CA_ID="${PKCS11_CA_KEY_ID:-08}"
SUBJECT="${PKCS11_LEAF_SUBJECT:-/CN=app.example.org/O=PKCS11 Lab}"
DAYS="${PKCS11_LEAF_DAYS:-365}"

# Wichtige Lab-Konvention: das neue CA-signierte Cert kommt auf eine separate
# ID (09, Label leaf-cert), NICHT auf ID=01. Das Self-Signed Cert dort wird
# von vielen vorhandenen Demos (CMS-Verify, Java-CMS, TLS) als CAfile-
# Trust-Anchor genutzt. Ein Replace wuerde diese Demos brechen.
# In Produktion wuerde man natuerlich das Self-Signed Cert wegwerfen und das
# CA-signed Cert auf der originalen CKA_ID einsetzen — siehe Kursmodul 22.
mkdir -p lab/work

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

LEAF_KEY_URI="pkcs11:token=${LABEL};object=${LEAF_LABEL};type=private;pin-value=${PIN}"
CA_KEY_URI="pkcs11:token=${LABEL};object=${CA_LABEL};type=private;pin-value=${PIN}"

# ---------- 1) CSR mit HSM-Signing-Key bauen ----------
# openssl req mit pkcs11-Engine: liest den Privkey via Engine, schreibt den
# zugehoerigen Pubkey in die CSR, signiert die CSR-Bytes mit dem Privkey.
# Die HSM-Operation hier ist EIN C_Sign — Proof-of-Possession des Privkey.
echo "=== 1) CSR fuer ${LEAF_LABEL} bauen (HSM-signed) ==="
OPENSSL_CONF="$OPENSSL_CONF_FILE" openssl req \
  -new \
  -engine pkcs11 -keyform engine -key "$LEAF_KEY_URI" \
  -sha256 \
  -subj "$SUBJECT" \
  -addext "subjectAltName=DNS:app.example.org" \
  -addext "keyUsage=critical,digitalSignature" \
  -addext "extendedKeyUsage=clientAuth,serverAuth" \
  -out lab/work/leaf.csr

echo "  CSR: lab/work/leaf.csr"
openssl req -in lab/work/leaf.csr -noout -subject -verify 2>&1 | sed 's/^/    /'

# ---------- 2) CA signiert die CSR ----------
echo "=== 2) CA signiert die CSR (CA-Key auch im HSM) ==="
# Serialnummer-Tracking: serial-File ist Standard-CA-Pattern. Wir starten bei 0x1000.
[ -f lab/work/ca.serial ] || echo "00001000" > lab/work/ca.serial

OPENSSL_CONF="$OPENSSL_CONF_FILE" openssl x509 \
  -req -in lab/work/leaf.csr \
  -CA lab/work/ca-cert.pem \
  -CAkey "$CA_KEY_URI" \
  -CAkeyform engine \
  -engine pkcs11 \
  -CAserial lab/work/ca.serial \
  -days "$DAYS" \
  -sha256 \
  -copy_extensions copy \
  -out lab/work/leaf-cert.pem

echo "  Leaf-Cert: lab/work/leaf-cert.pem"
echo "  Serial:    $(openssl x509 -in lab/work/leaf-cert.pem -noout -serial | sed 's/serial=//')"
echo "  Issuer:    $(openssl x509 -in lab/work/leaf-cert.pem -noout -issuer | sed 's/issuer= *//')"
echo "  Subject:   $(openssl x509 -in lab/work/leaf-cert.pem -noout -subject | sed 's/subject= *//')"

# ---------- 3) Chain-Verify mit CA als Trust-Anchor ----------
echo "=== 3) Chain-Verify ==="
openssl verify -CAfile lab/work/ca-cert.pem lab/work/leaf-cert.pem

# ---------- 4) Leaf-Cert ins Token importieren (neue ID, ohne Vorgaenger zu ersetzen) ----------
echo "=== 4) Leaf-Cert ins Token importieren (CKA_ID=${LEAF_CERT_ID}, Label ${LEAF_CERT_LABEL}) ==="
# Wir importieren auf eine SEPARATE ID, damit die bestehenden Demos
# (CMS-Verify, Java-CMS, TLS) ihr Self-Signed Cert auf ID=01 weiter
# vorfinden. Wer das in Produktion umstellt, loescht das alte ID=01-Cert
# und reimportiert das Leaf-Cert auf ID=01.
pkcs11-tool --module "$MODULE" --login --pin "$PIN" --token-label "$LABEL" \
  --delete-object --type cert --id "$LEAF_CERT_ID" 2>/dev/null || true

openssl x509 -in lab/work/leaf-cert.pem -outform DER -out lab/work/leaf-cert.der
pkcs11-tool \
  --module "$MODULE" \
  --login --pin "$PIN" \
  --token-label "$LABEL" \
  --write-object lab/work/leaf-cert.der \
  --type cert \
  --id "$LEAF_CERT_ID" \
  --label "$LEAF_CERT_LABEL"

echo "  Leaf-Cert importiert. ID=01/signing-key bleibt unangetastet (Backward-Compat)."
