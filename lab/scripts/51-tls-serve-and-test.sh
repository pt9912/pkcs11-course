#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
NGINX_BIN="${PKCS11_NGINX_BIN:-/usr/sbin/nginx}"

if [ ! -f lab/work/tls-cert.pem ]; then
  echo "TLS-Cert fehlt — erst 'make gen-tls-cert' (oder make tls-serve, das nimmt es mit)." >&2
  exit 1
fi

PKCS11_ENGINE="${PKCS11_ENGINE_PATH:-}"
if [ -z "$PKCS11_ENGINE" ]; then
  PKCS11_ENGINE="$(find /usr/lib /usr/lib64 -maxdepth 5 -type f -name pkcs11.so -path '*engines*' 2>/dev/null | head -n 1 || true)"
fi
if [ -z "$PKCS11_ENGINE" ]; then
  echo "pkcs11-Engine nicht gefunden. PKCS11_ENGINE_PATH explizit setzen." >&2
  exit 1
fi

# Drei temporaere Dateien:
#  - OpenSSL-Engine-Config (laedt pkcs11-Engine global)
#  - nginx-Config (mit absolutem lab/work-Pfad)
#  - PID-File (nginx erstellt es selbst, wir tracken den Pfad fuer Cleanup)
OPENSSL_CONF_FILE="$(mktemp)"
NGINX_CONF_FILE="$(mktemp /tmp/ngx-pkcs11-XXXXXX.conf)"
trap 'rm -f "$OPENSSL_CONF_FILE" "$NGINX_CONF_FILE"; if [ -f /tmp/nginx-pkcs11.pid ]; then kill "$(cat /tmp/nginx-pkcs11.pid)" 2>/dev/null || true; rm -f /tmp/nginx-pkcs11.pid; fi' EXIT

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
init = 1
EOF

LAB_WORK_ABS="$(cd lab/work && pwd)"
sed "s|LAB_WORK_DIR|${LAB_WORK_ABS}|g" lab/nginx/nginx-pkcs11.conf.template > "$NGINX_CONF_FILE"

echo "--- nginx Konfiguration validieren ---"
OPENSSL_CONF="$OPENSSL_CONF_FILE" "$NGINX_BIN" -c "$NGINX_CONF_FILE" -t 2>&1

echo "--- nginx im Hintergrund starten ---"
OPENSSL_CONF="$OPENSSL_CONF_FILE" "$NGINX_BIN" -c "$NGINX_CONF_FILE" &
NGINX_PID=$!
# Kurz warten, bis nginx den Port aufmacht.
for i in 1 2 3 4 5 6 7 8 9 10; do
  if ss -ltn 2>/dev/null | grep -q ':8443'; then break; fi
  sleep 0.2
done

echo "--- curl gegen https://localhost:8443 (mit --cacert) ---"
curl --silent --show-error \
  --cacert lab/work/tls-cert.pem \
  --resolve localhost:8443:127.0.0.1 \
  --connect-timeout 5 \
  --verbose \
  https://localhost:8443/ 2>&1 \
  | grep -E "^(\* TLS|\* SSL|\* Server cert|\* Connected|HTTP/|< HTTP|Hello)" \
  || true

echo
echo "--- nginx beenden ---"
kill "$NGINX_PID" 2>/dev/null || true
wait "$NGINX_PID" 2>/dev/null || true
echo "TLS-Demo OK"
