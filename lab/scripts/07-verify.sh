#!/usr/bin/env bash
set -euo pipefail
openssl rsa -pubin -inform DER -in lab/work/public.der -out lab/work/public.pem 2>/dev/null
openssl dgst -sha256 -verify lab/work/public.pem -signature lab/work/data.sig lab/work/data.txt
