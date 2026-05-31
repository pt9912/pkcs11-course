#!/usr/bin/env bash
set -euo pipefail
# TSA-Daemon hochziehen und Java-Demo aufrufen.
TSA_PORT="${PKCS11_TSA_PORT:-8088}"

lab/scripts/86-tsa-serve.sh > lab/work/tsa-server.log 2>&1 &
TSA_PID=$!
trap "kill $TSA_PID 2>/dev/null || true" EXIT
for i in 1 2 3 4 5 6 7 8 9 10; do
  if (echo > /dev/tcp/127.0.0.1/${TSA_PORT}) 2>/dev/null; then break; fi
  sleep 0.5
  [ $i -eq 10 ] && { echo "TSA-Daemon nicht erreichbar." >&2; cat lab/work/tsa-server.log >&2; exit 1; }
done

cd lab/java/pkcs11-cms-tsa-demo
./gradlew --quiet --no-daemon run
