#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
PIN="${PKCS11_USER_PIN:-987654}"
TOTAL_KB="${PKCS11_RANDOM_TOTAL_KB:-64}"
CHUNK="${PKCS11_RANDOM_CHUNK:-4096}"
mkdir -p lab/work

# Wir vergleichen Durchsatz und Verteilung von drei Random-Quellen:
#   - HSM via C_GenerateRandom (pkcs11-tool)
#   - Kernel via /dev/urandom (Host-PRNG, getrandom(2)-Pfad)
#   - Standardisierter ent-Klassiker: das gleiche Volume aus /dev/zero
#     reicht als Anti-Test (sollte 0.0 bit/byte Entropie zeigen)
#
# SoftHSM nutzt intern OpenSSL-RAND, also /dev/urandom unter dem HSM-Layer.
# Bei realen HSMs ist der HSM-RNG ein dedizierter Hardware-TRNG (ringoscillator-
# basiert) — Performance ist haeufig DEUTLICH SCHLECHTER als der Host-Kernel,
# dafuer compliance-relevant (FIPS-140-2/3, NIST SP 800-90B).

TOTAL_BYTES=$(( TOTAL_KB * 1024 ))
CHUNKS=$(( TOTAL_BYTES / CHUNK ))

echo "=== HSM-RNG vs /dev/urandom Throughput ==="
echo "Volumen pro Quelle: $TOTAL_KB KB ($CHUNKS Chunks a $CHUNK Byte)"
echo

HSM_OUT="lab/work/random-hsm-${TOTAL_KB}k.bin"
URAND_OUT="lab/work/random-urandom-${TOTAL_KB}k.bin"
ZERO_OUT="lab/work/random-zero-${TOTAL_KB}k.bin"

# Helper: misst Wallclock-Sekunden fuer ein Kommando, gibt MB/s zurueck.
# Sanity-Check: wenn das Kommando weniger Bytes als TOTAL_BYTES liefert,
# wuerde die MB/s-Zahl heimlich verzerren — wir warnen explizit.
bench() {
  local label="$1"; shift
  local out_path="$1"; shift
  local start end secs mbps actual
  start=$(date +%s.%N)
  "$@" > "$out_path"
  end=$(date +%s.%N)
  actual=$(stat -c%s "$out_path")
  secs=$(awk -v a="$end" -v b="$start" 'BEGIN { printf "%.3f", a - b }')
  if awk -v s="$secs" 'BEGIN { exit (s > 0 ? 0 : 1) }'; then
    mbps=$(awk -v b="$actual" -v s="$secs" 'BEGIN { printf "%.2f", (b/1048576.0)/s }')
  else
    mbps="    inf"
  fi
  printf "  %-22s  %7ss  %8s MB/s  %s\n" "$label" "$secs" "$mbps" "$out_path"
  if [ "$actual" -ne "$TOTAL_BYTES" ]; then
    printf "    !! short read: %s Byte angefordert, %s Byte geliefert\n" \
      "$TOTAL_BYTES" "$actual" >&2
  fi
}

# HSM-RNG: pkcs11-tool kann pro Aufruf einen Chunk holen — Loop noetig.
# Jeder Aufruf macht C_Initialize + C_OpenSession + C_GenerateRandom +
# C_CloseSession + C_Finalize. Das misst Pessimum: persistent geoeffnete
# Sessions (siehe Sprach-Demos) sind oft 10-100x schneller.
hsm_chunks() {
  for _ in $(seq 1 "$CHUNKS"); do
    pkcs11-tool --module "$MODULE" --login --pin "$PIN" --token-label "$LABEL" \
      --generate-random "$CHUNK"
  done
}

bench "HSM (pkcs11-tool/sess)" "$HSM_OUT" hsm_chunks
bench "/dev/urandom"           "$URAND_OUT" head -c "$TOTAL_BYTES" /dev/urandom
bench "/dev/zero (Anti-Test)"  "$ZERO_OUT"  head -c "$TOTAL_BYTES" /dev/zero

echo
echo "=== Verteilungs-Check (Shannon-Entropie, Chi^2 gegen Uniform) ==="
for f in "$HSM_OUT" "$URAND_OUT" "$ZERO_OUT"; do
  echo "$f:"
  python3 lab/scripts/_random_stats.py "$f"
done

echo
echo "Erwartung: HSM und urandom sehen statistisch gleich aus."
echo "         /dev/zero zeigt Entropie 0 und Chi^2 weit jenseits 255 — Beweis,"
echo "         dass der Check ueberhaupt etwas misst."
echo "Hinweis: pkcs11-tool oeffnet pro Chunk eine eigene Session — der HSM-Wert"
echo "         enthaelt also Session-Overhead. Reale Apps halten eine Session"
echo "         und sind dort 10-100x schneller. Sieh dazu die Sprach-Demos."
