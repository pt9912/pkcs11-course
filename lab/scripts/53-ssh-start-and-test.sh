#!/usr/bin/env bash
set -euo pipefail
MODULE="${PKCS11_MODULE:-/usr/lib/softhsm/libsofthsm2.so}"
PIN="${PKCS11_USER_PIN:-987654}"
SSHD_PORT="${PKCS11_SSHD_PORT:-2222}"
mkdir -p lab/work

if [ ! -f lab/work/ssh-pubkeys.txt ]; then
  echo "Pubkeys fehlen — erst 'make ssh-pubkey'." >&2
  exit 1
fi

# Wir bauen einen kompletten Mini-sshd-Setup unter /tmp/ssh-lab/ auf.
# Vorteile: keine root-Rechte noetig (Port > 1024), keine PAM-Abhaengigkeit,
# alles wegwerfbar nach dem Test.
SSH_LAB_DIR="/tmp/ssh-lab"
rm -rf "$SSH_LAB_DIR"
mkdir -p "$SSH_LAB_DIR"

# Host-Key fuer sshd erzeugen (separat vom HSM-Key — der ist Client-seitig).
ssh-keygen -t ed25519 -N '' -f "$SSH_LAB_DIR/host_ed25519" -q

# Authorized-Keys: die ssh-Pubkeys aus dem Token, die der Client beim Login
# vorzeigen wird. SoftHSM listet alle Pubkeys; einer davon (signing-key)
# matched den Privkey, mit dem der ssh-Client signieren kann.
cp lab/work/ssh-pubkeys.txt "$SSH_LAB_DIR/authorized_keys"
chmod 600 "$SSH_LAB_DIR/authorized_keys"

# sshd-Config — minimaler unprivilegierter Server.
cat > "$SSH_LAB_DIR/sshd_config" <<EOF
Port ${SSHD_PORT}
ListenAddress 127.0.0.1
HostKey ${SSH_LAB_DIR}/host_ed25519
AuthorizedKeysFile ${SSH_LAB_DIR}/authorized_keys
PasswordAuthentication no
ChallengeResponseAuthentication no
PubkeyAuthentication yes
StrictModes no
UsePAM no
PidFile ${SSH_LAB_DIR}/sshd.pid
LogLevel INFO
# Wir lassen sshd nicht forken — bleibt im Vordergrund, leichter zu killen.
EOF

# sshd als unprivilegierter User starten. -e: Log nach stderr; -D: foreground.
/usr/sbin/sshd -D -e -f "$SSH_LAB_DIR/sshd_config" &
SSHD_PID=$!
trap 'kill $SSHD_PID 2>/dev/null || true; rm -rf "$SSH_LAB_DIR"' EXIT

# Auf den Port warten.
for i in 1 2 3 4 5 6 7 8 9 10; do
  if ss -ltn 2>/dev/null | grep -q ":${SSHD_PORT}"; then break; fi
  sleep 0.2
done

# PIN-Helfer fuer SSH_ASKPASS — ssh fragt den HSM-Key auf, der Login braucht PIN.
# SSH_ASKPASS_REQUIRE=force erzwingt das auch ohne TTY/DISPLAY (OpenSSH >= 8.4).
ASKPASS_SCRIPT="$(mktemp /tmp/pkcs11-askpass-XXXXXX.sh)"
trap 'kill $SSHD_PID 2>/dev/null || true; rm -rf "$SSH_LAB_DIR" "$ASKPASS_SCRIPT"' EXIT
cat > "$ASKPASS_SCRIPT" <<EOF
#!/bin/sh
echo "${PIN}"
EOF
chmod +x "$ASKPASS_SCRIPT"

echo "--- ssh-Login via HSM-Key ---"
DISPLAY=dummy SSH_ASKPASS="$ASKPASS_SCRIPT" SSH_ASKPASS_REQUIRE=force \
  ssh \
    -o "PKCS11Provider=${MODULE}" \
    -o "StrictHostKeyChecking=no" \
    -o "UserKnownHostsFile=/dev/null" \
    -o "LogLevel=ERROR" \
    -o "BatchMode=no" \
    -p "${SSHD_PORT}" \
    "$(whoami)@127.0.0.1" \
    'echo "Eingeloggt als $(whoami) auf $(hostname). Authentication = HSM-Pubkey."' 2>&1

echo "SSH-Demo OK"
