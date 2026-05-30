# Uebung 13 - HSM-gestuetzte SSH-Authentifizierung

## Ziel

Du extrahierst die Public Keys aus dem HSM, startest einen lokalen sshd im Lab-Container, autorisierst die Keys und loggst dich mit dem HSM-Key ein — ohne Passwort, ohne lokal gespeicherten Privkey.

## Vorbereitung

```bash
make init-token gen-rsa
```

(EC-Key auf ID=02 ist optional — wird vom `ssh-keygen -D` ebenfalls erfasst, ist aber nicht zwingend.)

## Aufgabe 1 — Pubkeys extrahieren

```bash
make ssh-pubkey
```

Erwartete Ausgabe-Snippets:

```text
ssh-rsa AAAAB3NzaC1yc2EA... pkcs11:id=%01;type=public
```

Eine Zeile pro RSA/EC-Key im Token, alles SSH-authorized_keys-kompatibel.

## Aufgabe 2 — SSH-Roundtrip

```bash
make ssh-test
```

Erwartet:
- sshd auf Port 2222 startet im Container
- ssh-Client verbindet sich mit `PKCS11Provider=/usr/lib/softhsm/libsofthsm2.so`
- PIN ueber SSH_ASKPASS-Skript automatisch geliefert
- Output: `Eingeloggt als course auf <container-hostname>. Authentication = HSM-Pubkey.`

## Aufgabe 3 — Ohne PKCS11Provider verbinden

```bash
docker compose -f lab/docker-compose.yml run --rm pkcs11-lab bash -lc '
  lab/scripts/53-ssh-start-and-test.sh &
  sleep 1
  ssh -o BatchMode=yes -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
    -p 2222 $(whoami)@127.0.0.1 whoami 2>&1
  kill %1 2>/dev/null || true
'
```

Erwartet: `Permission denied (publickey).` — ohne den HSM-Key kann ssh nicht authentifizieren, weil der Server keine Passwort-Auth erlaubt.

## Aufgabe 4 — Falsche PIN provozieren

In `lab/scripts/53-ssh-start-and-test.sh` die PIN auf `"000000"` setzen und neu starten. Erwartet: ssh meldet einen Authentifizierungs-Fehler. Auf realen Tokens **Vorsicht**: drei falsche Versuche sperren den Key. Im Lab egal — SoftHSM zaehlt nicht hoch.

## Aufgabe 5 — Bonus: ssh-agent statt ASKPASS

```bash
docker compose -f lab/docker-compose.yml run --rm pkcs11-lab bash -lc '
  eval "$(ssh-agent -s)"
  # ssh-add -s prompted nach PIN — fuer Lab via expect/echo:
  echo "987654" | DISPLAY=dummy SSH_ASKPASS=/dev/stdin SSH_ASKPASS_REQUIRE=force \
    ssh-add -s /usr/lib/softhsm/libsofthsm2.so 2>&1 || true
  ssh-add -L
'
```

Erwartet: ssh-agent listet die HSM-Pubkeys via `ssh-add -L`. Ein folgender `ssh`-Login wuerde die Agent-Identitaet nutzen — kein erneuter PIN-Prompt.

## Reflexionsfragen

- Wo passiert der einzige PKCS#11-`C_Sign`-Aufruf im SSH-Login-Ablauf, und ueber welche Daten?
- Warum schicken wir den **Public Key** unverschluesselt an den SSH-Server — ist das ein Leak?
- Wenn dein HSM 5 Pubkeys exponiert und `authorized_keys` nur einen davon enthaelt: wieviele Versuche macht ssh maximal? Welche Folgekosten hat das?
- Welcher Angreifer-Move ist mit SSH-Agent-Forwarding (`ssh -A`) ueber einen kompromittierten Jump-Host moeglich?

## Musterloesung

Siehe `solutions/13-ssh-mit-hsm.md`.
