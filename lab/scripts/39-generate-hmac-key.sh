#!/usr/bin/env bash
set -euo pipefail
# HMAC-Key (GENERIC_SECRET 32 Byte = 256 Bit fuer HMAC-SHA256) — strikt
# CKA_SIGN + CKA_VERIFY, keine anderen Usages.
# RFC 2104 §3: HMAC-Key sollte mindestens die Hash-Output-Laenge haben (32 Byte
# bei SHA-256). Laengere Keys werden vom HMAC-Algorithmus intern auf die Block-
# Groesse (64 Byte) heruntergebracht — bringt keine zusaetzliche Sicherheit.
KEY_LABEL="${PKCS11_HMAC_LABEL:-hmac-key}"
KEY_ID="${PKCS11_HMAC_ID:-05}"

cd lab/go/pkcs11-keygen
go run . \
  --type generic --bits 256 \
  --label "$KEY_LABEL" --id "$KEY_ID" \
  --sign
