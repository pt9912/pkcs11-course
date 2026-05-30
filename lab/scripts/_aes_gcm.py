#!/usr/bin/env python3
"""Minimaler AES-GCM-Helper fuer die Bash-Demos.

Aufruf:
    _aes_gcm.py encrypt <key> <iv> <plaintext> <ciphertext>
    _aes_gcm.py decrypt <key> <iv> <ciphertext> <plaintext>

key:  32 Byte AES-256
iv:   12 Byte Nonce (in GCM "iv" genannt)
Tag wird bei encrypt an den Chiffretext angehaengt und bei decrypt erwartet.
Tamper-Erkennung erfolgt ueber InvalidTag (Exit 2).
"""
import sys
from pathlib import Path

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM


def _read(path: str) -> bytes:
    return Path(path).read_bytes()


def encrypt(key_path: str, iv_path: str, in_path: str, out_path: str) -> None:
    ct = AESGCM(_read(key_path)).encrypt(_read(iv_path), _read(in_path), None)
    Path(out_path).write_bytes(ct)


def decrypt(key_path: str, iv_path: str, in_path: str, out_path: str) -> None:
    try:
        pt = AESGCM(_read(key_path)).decrypt(_read(iv_path), _read(in_path), None)
    except InvalidTag:
        print("AES-GCM: ungueltiger Auth-Tag (Datei wurde veraendert oder falscher Key)", file=sys.stderr)
        sys.exit(2)
    Path(out_path).write_bytes(pt)


def main() -> None:
    if len(sys.argv) != 6:
        print(__doc__, file=sys.stderr)
        sys.exit(64)
    cmd, *args = sys.argv[1:]
    if cmd == "encrypt":
        encrypt(*args)
    elif cmd == "decrypt":
        decrypt(*args)
    else:
        print(f"unbekanntes Kommando: {cmd}", file=sys.stderr)
        sys.exit(64)


if __name__ == "__main__":
    main()
