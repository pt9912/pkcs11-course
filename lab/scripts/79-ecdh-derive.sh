#!/usr/bin/env bash
set -euo pipefail
# Wrappt das Go-Demo aus lab/go/pkcs11-ecdh-demo. KDF-Pfad ueber PKCS11_ECDH_KDF
# steuerbar: hkdf (Default, RFC 5869 host-side) oder raw (P-256-x als AES-Key).
KDF="${PKCS11_ECDH_KDF:-hkdf}"

cd lab/go/pkcs11-ecdh-demo
go run . --kdf "$KDF"
