#!/usr/bin/env bash
set -euo pipefail
# Identisch zum 79-ecdh-derive-Pfad (selbe Codebasis), separat als
# "Sprach-Demo"-Target gefuehrt, damit die Modul-23-Konvention
# (make {go,csharp,java,kotlin}-MODUL-demo) konsistent bleibt.
cd lab/go/pkcs11-ecdh-demo
go run . --kdf "${PKCS11_ECDH_KDF:-hkdf}"
