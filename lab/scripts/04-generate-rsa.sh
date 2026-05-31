#!/usr/bin/env bash
set -euo pipefail
# RSA-2048 signing-key — strikt CKA_SIGN-only.
#
# Seit 0.16.0 ueber den pkcs11-keygen-Go-Helper mit explizitem CKA-Template.
# Vorher: pkcs11-tool --usage-sign (SoftHSM-Default-Profil fuegt decrypt,
# signRecover, unwrap dazu — siehe roadmap.md 0.15.1-Disclaimer).
# Existenz-Check macht der Helper selbst per Label-Lookup (--skip-if-exists).

KEY_LABEL="${PKCS11_KEY_LABEL:-signing-key}"
KEY_ID="${PKCS11_KEY_ID:-01}"

cd lab/go/pkcs11-keygen
go run . \
  --type rsa --bits 2048 \
  --label "$KEY_LABEL" --id "$KEY_ID" \
  --sign
