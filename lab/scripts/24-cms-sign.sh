#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
PIN="${PKCS11_USER_PIN:-987654}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
KEY_LABEL="${PKCS11_KEY_LABEL:-signing-key}"
mkdir -p lab/work

# CMS-Signatur (PKCS#7, RFC 5652) — detached:
# Output enthaelt das Signer-Cert + signed attributes (signing-time,
# contentType, messageDigest) + Signatur — aber NICHT den Klartext.
# Der Verifier braucht beide: cms-document.p7s und das Original.
#
# Voraussetzung: signing-key + Cert im Token (make import-cert).
# Signiert wird ueber die openssl pkcs11-Engine, der private Key bleibt im HSM.

if [ ! -f lab/work/cert.pem ]; then
  echo "lab/work/cert.pem fehlt — erst 'make import-cert' ausfuehren." >&2
  exit 1
fi

if [ ! -f lab/work/cms-document.txt ]; then
  printf 'Vertrag XYZ vom 30.05.2026\nUnterzeichnender: PKCS11 Lab\n' > lab/work/cms-document.txt
fi

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

# -binary verhindert das S/MIME-CRLF-Mapping (sonst differieren Signatur und Klartext-Bytes).
# -md sha256 -> CMS-MessageDigest-Attribute SHA-256.
# -outform DER -> binaere ASN.1-Repraesentation (kompakter, .p7s-Standard).
OPENSSL_CONF="$OPENSSL_CONF_FILE" openssl cms \
  -sign \
  -in lab/work/cms-document.txt \
  -signer lab/work/cert.pem \
  -engine pkcs11 -keyform engine -inkey "$KEY_URI" \
  -md sha256 \
  -binary \
  -outform DER \
  -out lab/work/cms-document.p7s

echo "CMS-Signatur: lab/work/cms-document.p7s ($(stat -c%s lab/work/cms-document.p7s) Bytes, detached)"
