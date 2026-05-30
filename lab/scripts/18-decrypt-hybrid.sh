#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
PIN="${PKCS11_USER_PIN:-987654}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
WRAP_KEY_LABEL="${PKCS11_WRAP_KEY_LABEL:-wrap-key}"
WRAP_KEY_ID="${PKCS11_WRAP_KEY_ID:-03}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mkdir -p lab/work

# Hybrid-Verschluesselung — Empfaenger-Seite:
# 1) HSM macht den teuren Teil: RSA-OAEP-Decrypt der gewrappten Datei
#    rekonstruiert den AES-Session-Key auf dem Host.
# 2) Host entschluesselt das Dokument mit AES-256-GCM. Tampering schlaegt
#    durch InvalidTag im Helper auf (Exit 2).
# 3) Plain-AES-Key wird sofort wieder geloescht.
#
# Hinweis: Wir nehmen openssl + pkcs11-Engine statt pkcs11-tool --decrypt,
# weil pkcs11-tool die OAEP-Parameter mit source_type=CKZ_DATA_SPECIFIED und
# Null-Pointer kodiert — eine Kombination, die SoftHSM2 mit CKR_ARGUMENTS_BAD
# ablehnt. Die Engine kodiert sauber. Selbe Engine wie in 08-import-cert.sh.

PKCS11_ENGINE="${PKCS11_ENGINE_PATH:-}"
if [ -z "$PKCS11_ENGINE" ]; then
  PKCS11_ENGINE="$(find /usr/lib /usr/lib64 -maxdepth 5 -type f -name pkcs11.so -path '*engines*' 2>/dev/null | head -n 1 || true)"
fi
if [ -z "$PKCS11_ENGINE" ]; then
  echo "pkcs11-Engine nicht gefunden. PKCS11_ENGINE_PATH explizit setzen." >&2
  exit 1
fi

OPENSSL_CONF_FILE="$(mktemp)"
trap 'rm -f "$OPENSSL_CONF_FILE" lab/work/aes-key.bin' EXIT
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

KEY_URI="pkcs11:token=${LABEL};object=${WRAP_KEY_LABEL};type=private;pin-value=${PIN}"

OPENSSL_CONF="$OPENSSL_CONF_FILE" openssl pkeyutl \
  -decrypt \
  -engine pkcs11 -keyform engine \
  -inkey "$KEY_URI" \
  -pkeyopt rsa_padding_mode:oaep \
  -pkeyopt rsa_oaep_md:sha256 \
  -pkeyopt rsa_mgf1_md:sha256 \
  -in lab/work/wrapped-key.bin \
  -out lab/work/aes-key.bin

python3 "$SCRIPT_DIR/_aes_gcm.py" decrypt \
  lab/work/aes-key.bin \
  lab/work/aes-iv.bin \
  lab/work/document.enc \
  lab/work/document.dec

# aes-key.bin wird im trap geloescht.

echo "Klartext: lab/work/document.dec"
if diff -q lab/work/document.txt lab/work/document.dec >/dev/null; then
  echo "Round-Trip OK"
else
  echo "Round-Trip FEHLGESCHLAGEN" >&2
  exit 3
fi
