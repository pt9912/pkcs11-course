#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
LABEL="${PKCS11_TOKEN_LABEL:-dev-token}"
WRAP_KEY_ID="${PKCS11_WRAP_KEY_ID:-03}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mkdir -p lab/work

# Hybrid-Verschluesselung — Sender-Seite (braucht keinen HSM-Login):
# 1) AES-Session-Key + IV als reines Host-Material erzeugen.
# 2) Wrap-Pubkey aus dem Token exportieren (Public Key, kein Login noetig).
# 3) AES-Key mit RSA-OAEP (SHA-256, MGF1-SHA-256) wrappen -> wrapped-key.bin.
# 4) Dokument mit AES-256-GCM verschluesseln -> document.enc (Ciphertext+Tag).
# 5) Plain-AES-Key direkt loeschen, damit nur noch die gewrappte Variante existiert.

if [ ! -f lab/work/document.txt ]; then
  printf 'Vertrauliches Dokument fuer den HSM-Test.\nZeile zwei.\n' > lab/work/document.txt
fi

openssl rand 32 > lab/work/aes-key.bin
openssl rand 12 > lab/work/aes-iv.bin

pkcs11-tool \
  --module "$MODULE" \
  --token-label "$LABEL" \
  --read-object \
  --type pubkey \
  --id "$WRAP_KEY_ID" \
  --output-file lab/work/wrap-public.der
openssl rsa -pubin -inform DER -in lab/work/wrap-public.der -out lab/work/wrap-public.pem 2>/dev/null

# OAEP-Parameter explizit setzen — der Default in OpenSSL ist SHA-1, der zwar
# noch arbeitet, aber von vielen HSM-Profilen nicht mehr erlaubt wird.
openssl pkeyutl \
  -encrypt \
  -pubin -inkey lab/work/wrap-public.pem \
  -pkeyopt rsa_padding_mode:oaep \
  -pkeyopt rsa_oaep_md:sha256 \
  -pkeyopt rsa_mgf1_md:sha256 \
  -in lab/work/aes-key.bin \
  -out lab/work/wrapped-key.bin

python3 "$SCRIPT_DIR/_aes_gcm.py" encrypt \
  lab/work/aes-key.bin \
  lab/work/aes-iv.bin \
  lab/work/document.txt \
  lab/work/document.enc

# Plain-AES-Key entfernen — die einzige verbleibende Kopie ist die gewrappte
# Datei, die nur mit dem privaten RSA-Key im HSM aufgemacht werden kann.
shred -u lab/work/aes-key.bin 2>/dev/null || rm -f lab/work/aes-key.bin

echo "Wrapped Key:  lab/work/wrapped-key.bin ($(stat -c%s lab/work/wrapped-key.bin) Bytes)"
echo "IV:           lab/work/aes-iv.bin ($(stat -c%s lab/work/aes-iv.bin) Bytes)"
echo "Ciphertext:   lab/work/document.enc ($(stat -c%s lab/work/document.enc) Bytes)"
