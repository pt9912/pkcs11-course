#!/usr/bin/env bash
set -euo pipefail

TARGET_DIR="${1:-pkcs11-course}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ "$SCRIPT_DIR" == "$(pwd)/$TARGET_DIR" ]]; then
  echo "Du bist bereits im Zielverzeichnis."
  exit 0
fi

mkdir -p "$TARGET_DIR"
rsync -a --exclude "$TARGET_DIR" "$SCRIPT_DIR/" "$TARGET_DIR/"

echo "Kurs wurde nach ./$TARGET_DIR kopiert."
echo "Nächste Schritte:"
echo "  cd $TARGET_DIR"
echo "  make build"
echo "  make shell"
