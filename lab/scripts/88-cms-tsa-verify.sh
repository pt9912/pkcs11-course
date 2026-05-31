#!/usr/bin/env bash
set -euo pipefail
# Verifiziert die in 87-cms-tsa-sign.sh erzeugten Artefakte:
#   - CMS-Signatur (lab/work/tsa-document.p7s) gegen das Original-Dokument
#   - TimeStampResp (lab/work/tsa-document.tsr) gegen die CMS-Signatur
#
# openssl ts -verify prueft Signatur des TSTokens, TSA-Cert-Chain und dass
# der TSReq-Hash im Token mit dem Hash der gestempelten Daten uebereinstimmt.

INPUT="lab/work/tsa-document.txt"
SIG="lab/work/tsa-document.p7s"
TSR="lab/work/tsa-document.tsr"
CERT="lab/work/cert.pem"
CAFILE="lab/work/ca-cert.pem"

echo "=== 1) CMS-Signatur ueber das Dokument verifizieren ==="
openssl cms \
  -verify -binary -inform DER \
  -in "$SIG" \
  -content "$INPUT" \
  -CAfile "$CERT" \
  -out /dev/null 2>&1 | sed 's/^/  /'

echo
echo "=== 2) TimeStamp-Token verifizieren ==="
# -CAfile: Trust-Anchor fuer die TSA-Cert-Chain (Lab-CA aus Modul 22).
# -data: die Daten, ueber die der Timestamp ausgestellt wurde (=CMS-Signatur).
# openssl rekonstruiert intern den TSReq-Hash und vergleicht ihn mit dem
# Hash im TSToken.
openssl ts -verify \
  -in "$TSR" \
  -data "$SIG" \
  -CAfile "$CAFILE" \
  -untrusted lab/work/tsa-cert.pem | sed 's/^/  /'

echo
echo "=== 3) TSToken-Details (Timestamp, TSA-Subject, Policy-OID) ==="
openssl ts -reply -in "$TSR" -text | grep -E "Time stamp|TSA|Policy|Hash Algorithm" | sed 's/^/  /'

echo
echo "Fertig — CMS-Signatur und RFC-3161-Timestamp jeweils valid."
