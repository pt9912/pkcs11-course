#!/usr/bin/env bash
set -euo pipefail
# openssl dgst -verify streamed die Hash-Berechnung auf der Host-Seite,
# RSA-Verify ist single-shot. Genau wie in 07-verify.sh.
if [ ! -f lab/work/public.pem ] && [ -f lab/work/public.der ]; then
  openssl rsa -pubin -inform DER -in lab/work/public.der -out lab/work/public.pem 2>/dev/null
fi
openssl dgst -sha256 -verify lab/work/public.pem -signature lab/work/large.sig lab/work/large.bin
