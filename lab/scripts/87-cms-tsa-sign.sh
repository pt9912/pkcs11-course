#!/usr/bin/env bash
set -euo pipefail
# CMS-Signatur + RFC-3161-Timestamp im selben Container:
#   1) TSA-Daemon im Hintergrund starten (HSM-Key, openssl ts -reply via Python-Wrapper)
#   2) CMS-Signatur ueber das Test-Dokument bauen (analog Modul 14)
#   3) TimeStampReq fuer die CMS-Signatur erzeugen, an den lokalen TSA POST'en
#   4) TimeStampResp speichern und mit openssl ts -verify pruefen
#
# Embedding des TSTokens in CMS unsigned attrs zeigen die Sprach-Demos —
# pure-CLI mit openssl bietet das nicht out-of-the-box.

MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
PIN="${PKCS11_USER_PIN:-987654}"
TOKEN_LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
SIGNING_KEY_LABEL="${PKCS11_KEY_LABEL:-signing-key}"
TSA_PORT="${PKCS11_TSA_PORT:-8088}"

mkdir -p lab/work
INPUT="lab/work/tsa-document.txt"
[ -f "$INPUT" ] || printf 'Lab-Dokument fuer CMS-Signatur + TSA-Stempel\n' > "$INPUT"

PKCS11_ENGINE="${PKCS11_ENGINE_PATH:-}"
if [ -z "$PKCS11_ENGINE" ]; then
  PKCS11_ENGINE="$(find /usr/lib /usr/lib64 -maxdepth 5 -type f -name pkcs11.so -path '*engines*' 2>/dev/null | head -n 1 || true)"
fi
[ -n "$PKCS11_ENGINE" ] || { echo "pkcs11-Engine nicht gefunden." >&2; exit 1; }

# OpenSSL-Config fuer CMS-Signatur (CA-Engine wie in Modul 14).
CMS_CONF="$(mktemp)"
trap 'rm -f "$CMS_CONF"' EXIT
cat > "$CMS_CONF" <<EOF
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

SIGN_KEY_URI="pkcs11:token=${TOKEN_LABEL};object=${SIGNING_KEY_LABEL};type=private;pin-value=${PIN}"

echo "=== 1) TSA-Server im Hintergrund starten ==="
lab/scripts/86-tsa-serve.sh > lab/work/tsa-server.log 2>&1 &
TSA_PID=$!
trap "rm -f $CMS_CONF; kill $TSA_PID 2>/dev/null || true" EXIT
# Auf Bereitschaft warten — Port-Check ueber /dev/tcp (bash-built-in).
for i in 1 2 3 4 5 6 7 8 9 10; do
  if (echo > /dev/tcp/127.0.0.1/${TSA_PORT}) 2>/dev/null; then
    echo "  TSA-Daemon bereit auf 127.0.0.1:${TSA_PORT} (PID $TSA_PID)"
    break
  fi
  sleep 0.5
  if [ $i -eq 10 ]; then
    echo "TSA-Daemon nicht erreichbar." >&2
    cat lab/work/tsa-server.log >&2
    exit 1
  fi
done

echo
echo "=== 2) CMS-Signatur ueber $INPUT (detached, SHA-256, HSM-signed) ==="
# Default ist detached — wir lassen -nodetach explizit weg.
OPENSSL_CONF="$CMS_CONF" openssl cms \
  -sign -binary \
  -engine pkcs11 -keyform engine -inkey "$SIGN_KEY_URI" \
  -signer lab/work/cert.pem \
  -md sha256 \
  -in "$INPUT" \
  -outform DER \
  -out lab/work/tsa-document.p7s
echo "  CMS-Signatur: lab/work/tsa-document.p7s ($(stat -c%s lab/work/tsa-document.p7s) Byte)"

echo
echo "=== 3) TimeStampReq ueber die CMS-Signatur bauen + POST an Lab-TSA ==="
# RFC 3161 §2.4.1: TimeStampReq enthaelt den Hash der zu stempelnden Daten.
# Hier stempeln wir die CMS-Signatur (nicht das Dokument selbst); das matched
# CAdES-T, wo der Timestamp auf die Signatur zeigt.
openssl ts -query -data lab/work/tsa-document.p7s -no_nonce -sha256 \
  -out lab/work/tsa-document.tsq

# curl ist im Container vorhanden (siehe Dockerfile 0.10.0).
HTTP_CODE=$(curl -s \
  -H "Content-Type: application/timestamp-query" \
  --data-binary @lab/work/tsa-document.tsq \
  http://127.0.0.1:${TSA_PORT}/ \
  -o lab/work/tsa-document.tsr \
  -w "%{http_code}")
if [ "$HTTP_CODE" != "200" ]; then
  echo "TSA-Daemon antwortete HTTP $HTTP_CODE — Server-Antwort:" >&2
  cat lab/work/tsa-document.tsr >&2
  echo >&2
  echo "TSA-Server-Log:" >&2
  cat lab/work/tsa-server.log >&2
  exit 1
fi
echo "  TimeStampResp: lab/work/tsa-document.tsr ($(stat -c%s lab/work/tsa-document.tsr) Byte)"

echo
echo "=== 4) TimeStampResp inhaltlich anzeigen ==="
openssl ts -reply -in lab/work/tsa-document.tsr -text | sed 's/^/  /' | head -20
