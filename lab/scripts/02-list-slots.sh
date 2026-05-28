#!/usr/bin/env bash
set -euo pipefail
pkcs11-tool --module "${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}" --list-slots
