#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
PIN="${PKCS11_USER_PIN:-987654}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
WRAP_KEY_LABEL="${PKCS11_WRAP_KEY_LABEL:-wrap-key}"
WRAP_KEY_ID="${PKCS11_WRAP_KEY_ID:-03}"
SUBJECT="${PKCS11_WRAP_CERT_SUBJECT:-/CN=wrap-key/O=PKCS11 Lab}"
DAYS="${PKCS11_WRAP_CERT_DAYS:-365}"
mkdir -p lab/work

# pkcs11-tool listet Objekte ohne Trennzeilen — eine Range bis /^$/ wuerde bis
# Output-Ende laufen und Labels aus Public/Private-Key-Bloecken faelschlich
# als "Certificate"-Treffer zaehlen. Stattdessen tracken wir die aktuelle
# Objekt-Klasse expliziter.
if pkcs11-tool --module "$MODULE" --login --pin "$PIN" --token-label "$LABEL" --list-objects 2>/dev/null \
   | awk -v want="$WRAP_KEY_LABEL" '
       /Object;/ { in_cert = ($0 ~ /^Certificate Object/); next }
       in_cert && match($0, /label:[[:space:]]*/) {
         value = substr($0, RSTART + RLENGTH)
         sub(/[[:space:]]+$/, "", value)
         if (value == want) { print "match"; exit }
       }' | grep -q match; then
  echo "Zertifikat mit Label '$WRAP_KEY_LABEL' existiert bereits im Token."
  exit 0
fi

# Wrap-Pubkey aus dem Token holen
pkcs11-tool \
  --module "$MODULE" \
  --token-label "$LABEL" \
  --read-object \
  --type pubkey \
  --id "$WRAP_KEY_ID" \
  --output-file lab/work/wrap-public.der
openssl rsa -pubin -inform DER -in lab/work/wrap-public.der -out lab/work/wrap-public.pem 2>/dev/null

# Plumbing-Cert fuer den Wrap-Key:
# Der Wrap-Key hat bewusst CKA_SIGN=FALSE und kann sich nicht selbst signieren.
# Wir generieren deshalb einen Wegwerf-Host-Key als nominellen Issuer und
# zwingen mit -force_pubkey den HSM-Wrap-Pubkey in das Cert.
# Das Cert ist kryptografisch NICHT verifizierbar (Issuer-Signatur passt nicht
# zum Subject-Pubkey) und macht keine Vertrauensaussage. Es dient ausschliesslich
# als Metadaten-Anker, damit der SunPKCS11-KeyStore den Wrap-Key ueber einen
# Alias adressieren kann (Java/Kotlin-Demo). Bash, Go und C# brauchen es nicht.
# In realen Setups uebernimmt eine echte CA diese Rolle.
ISSUER_DUMMY_KEY="$(mktemp lab/work/wrap-cert-issuer-XXXXXX.key)"
CSR="$(mktemp lab/work/wrap-XXXXXX.csr)"
trap 'rm -f "$ISSUER_DUMMY_KEY" "$CSR"' EXIT

openssl genrsa -out "$ISSUER_DUMMY_KEY" 2048 2>/dev/null
openssl req -new -key "$ISSUER_DUMMY_KEY" -subj "$SUBJECT" -out "$CSR"
openssl x509 -req \
  -in "$CSR" \
  -signkey "$ISSUER_DUMMY_KEY" \
  -force_pubkey lab/work/wrap-public.pem \
  -days "$DAYS" \
  -sha256 \
  -out lab/work/wrap-cert.pem 2>/dev/null

openssl x509 -in lab/work/wrap-cert.pem -outform DER -out lab/work/wrap-cert.der

pkcs11-tool \
  --module "$MODULE" \
  --login --pin "$PIN" \
  --token-label "$LABEL" \
  --write-object lab/work/wrap-cert.der \
  --type cert \
  --id "$WRAP_KEY_ID" \
  --label "$WRAP_KEY_LABEL"

echo "Plumbing-Cert importiert: id=$WRAP_KEY_ID label=$WRAP_KEY_LABEL"
