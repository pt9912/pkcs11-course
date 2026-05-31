#!/usr/bin/env bash
set -euo pipefail
# Alice + Bob EC-P256 Keypaare fuer ECDH-Demo (Kapitel 24).
# Sortenrein: --sign + --derive, nichts anderes. Im Lab-Szenario liegen beide
# Keys im selben Token; reale Setups (zwei Parteien, zwei HSMs) sind durch
# das Lab nicht abbildbar, der ECDH-Protokollablauf ist aber identisch.
ALICE_LABEL="${PKCS11_ECDH_ALICE_LABEL:-alice-ec-key}"
ALICE_ID="${PKCS11_ECDH_ALICE_ID:-0a}"
BOB_LABEL="${PKCS11_ECDH_BOB_LABEL:-bob-ec-key}"
BOB_ID="${PKCS11_ECDH_BOB_ID:-0b}"
CURVE="${PKCS11_ECDH_CURVE:-secp256r1}"

cd lab/go/pkcs11-keygen
go run . --type ec --curve "$CURVE" --label "$ALICE_LABEL" --id "$ALICE_ID" --sign --derive
go run . --type ec --curve "$CURVE" --label "$BOB_LABEL"   --id "$BOB_ID"   --sign --derive
