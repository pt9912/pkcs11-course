#!/usr/bin/env bash
set -euo pipefail
mkdir -p lab/work

# CMS-Verify mit Cert-Chain-Check.
# -CAfile cert.pem: das self-signed Signer-Cert dient als Trust-Anchor.
#                   Realer Einsatz: hier wuerde das CA-Cert stehen.
# -content lab/work/cms-document.txt: der Klartext zur detached Signatur.
# -binary: kein CRLF-Mapping (muss zum Sign-Aufruf passen).

openssl cms \
  -verify \
  -in lab/work/cms-document.p7s \
  -inform DER \
  -content lab/work/cms-document.txt \
  -CAfile lab/work/cert.pem \
  -binary \
  -out /dev/null

echo "CMS Verify: OK"

# Bonus: Signer-Info und signed attributes ausgeben.
echo "--- Signer-Info ---"
openssl cms -cmsout -print -inform DER -in lab/work/cms-document.p7s 2>/dev/null \
  | grep -E "signerInfos|digestAlgorithm|sid|signatureAlgorithm|^      signedAttrs|attrType|signingTime|contentType|messageDigest" \
  | head -30 || true
