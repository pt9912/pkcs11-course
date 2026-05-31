#!/usr/bin/env bash
set -euo pipefail
# TSA-HTTP-Wrapper auf 127.0.0.1:8088 starten. openssl ts -reply ist
# selbst kein HTTP-Daemon — wir wrappen es ueber den Python-Helper.
#
# Das Skript laeuft im Foreground und blockiert. Beenden mit Ctrl-C.
# Fuer scripted Tests (z.B. 87-cms-tsa-sign.sh) den Daemon im Hintergrund
# starten und am Ende killen — das Bash-Demo macht das.

TSA_PORT="${PKCS11_TSA_PORT:-8088}"
mkdir -p lab/work

# Software-TSA-Key (siehe 85-tsa-setup.sh fuer das Warum) und TSA-Cert.
TSA_KEY_FILE="lab/work/tsa-key.pem"
TSA_CERT_FILE="lab/work/tsa-cert.pem"
[ -f "$TSA_KEY_FILE" ] || { echo "TSA-Key fehlt: $TSA_KEY_FILE (make tsa-setup ausfuehren?)" >&2; exit 1; }
[ -f "$TSA_CERT_FILE" ] || { echo "TSA-Cert fehlt: $TSA_CERT_FILE" >&2; exit 1; }

# openssl ts -reply Config-Sektion: Pfade zu TSA-Cert + Signer-Key (Filesystem),
# zulaessige Digest-Algorithmen und Policy-OID. Wir bauen das zur Laufzeit.
TSA_CONF="lab/work/tsa.cnf"
TSA_SERIAL_FILE="lab/work/tsa.serial"
[ -f "$TSA_SERIAL_FILE" ] || echo "01" > "$TSA_SERIAL_FILE"

# Policy-OID frei waehlbar — Lab-OID unter dem privaten OID-Baum {1, 3, 6, 1, 4, 1}.
# Echte TSAs registrieren ihre Policy bei der IANA bzw. ihrer NCA.
LAB_TSA_POLICY="${PKCS11_TSA_POLICY:-1.3.6.1.4.1.99999.1}"

cat > "$TSA_CONF" <<EOF
oid_section = oids

[oids]
LabTSAPolicy = ${LAB_TSA_POLICY}

[lab_tsa]
serial         = ${TSA_SERIAL_FILE}
crypto_device  = builtin
signer_cert    = ${TSA_CERT_FILE}
certs          = lab/work/ca-cert.pem
signer_key     = ${TSA_KEY_FILE}
signer_digest  = sha256
default_policy = LabTSAPolicy
other_policies = LabTSAPolicy
digests        = sha256, sha384, sha512
accuracy       = secs:1, millisecs:500
clock_precision_digits = 0
ordering       = no
tsa_name       = no
ess_cert_id_chain = no
ess_cert_id_alg = sha256
EOF

echo "Lab-TSA-Konfiguration:"
echo "  Config-File: $TSA_CONF"
echo "  Section:     lab_tsa"
echo "  Port:        $TSA_PORT"
echo "  Signer-Key:  $TSA_KEY_FILE (Software, Lab-Kompromiss)"
echo "  Policy-OID:  $LAB_TSA_POLICY"
echo

exec python3 lab/scripts/_tsa_server.py "$TSA_CONF" "lab_tsa" "$TSA_PORT"
