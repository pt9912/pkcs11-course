#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
mkdir -p lab/work

# ssh-keygen -D <pkcs11-lib> dumpt alle nutzbaren Public Keys aus dem Token
# im OpenSSH-authorized_keys-Format (ssh-rsa AAAA... <uri-comment>).
# Kein PIN-Prompt — Public Keys sind ohne Login lesbar (SoftHSM-Default).
#
# Wir filtern auf RSA-Keys (Signing-/Wrap-/Stream-Key), die EC-Variante
# bekommt einen ecdsa-sha2-* Eintrag (auch nutzbar).
ssh-keygen -D "$MODULE" > lab/work/ssh-pubkeys.txt

echo "--- aus dem Token sichtbare SSH-Pubkeys ---"
cat lab/work/ssh-pubkeys.txt
echo "--- Datei: lab/work/ssh-pubkeys.txt ---"
