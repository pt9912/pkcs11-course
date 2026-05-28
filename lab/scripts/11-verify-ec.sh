#!/usr/bin/env bash
set -euo pipefail
openssl ec -pubin -inform DER -in lab/work/public-ec.der -out lab/work/public-ec.pem 2>/dev/null
openssl dgst -sha256 -verify lab/work/public-ec.pem -signature lab/work/data-ec.sig lab/work/data-ec.txt
