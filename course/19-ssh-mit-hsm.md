# 19 — HSM-gestuetzte SSH-Authentifizierung

## Lernziele

Nach diesem Kapitel kannst du:

- erklaeren, was bei einem SSH-Pubkey-Login mit einem HSM-Key kryptografisch passiert.
- die Public Keys aus dem HSM in OpenSSH-Format extrahieren und in `authorized_keys` einsetzen.
- ssh mit `PKCS11Provider`-Option oder ueber ssh-agent loginieren lassen.
- die typischen Smartcard-/YubiKey-Use-Cases erkennen, die hier dieselbe Mechanik nutzen.

## Lab-Bezug

```bash
make ssh-pubkey       # Pubkeys aus dem Token extrahieren
make ssh-test         # sshd starten, Login ueber HSM-Key, Cleanup
```

Nachschlag zu SSH, PKCS#11-URI, `CKA_*` und Token-Begriffen: [Glossar](../docs/glossar.md).

## Was passiert beim SSH-Pubkey-Auth?

SSH-Pubkey-Authentifizierung ist drei Schritte:

1. Client sendet seinen Public Key an den Server (kein Geheimnis).
2. Server prueft, ob dieser Pubkey in `~/.ssh/authorized_keys` des Ziel-Users steht.
3. Falls ja: Server schickt einen Challenge-String. Client signiert ihn mit dem zugehoerigen **privaten Schluessel** und schickt die Signatur zurueck. Server verifiziert mit dem Pubkey.

Schritt 3 ist der einzige, der einen Privkey-Zugriff braucht. Genau das ist die HSM-Aufgabe:

- Pubkey kann **ohne HSM-Login** aus dem Token gelesen werden (`CKA_PRIVATE=FALSE` auf der Pubkey-Seite).
- Sign-Operation braucht **eingeloggte Session** + Privkey mit `CKA_SIGN=TRUE`.

Unser `signing-key` (RSA-2048, `CKA_SIGN`) erfuellt beides. EC-Keys gehen genauso (`ssh-rsa` ↔ `ecdsa-sha2-nistp256`).

## Pubkey extrahieren: `ssh-keygen -D`

```bash
ssh-keygen -D /usr/lib/softhsm/libsofthsm2.so
```

Output (verkuerzt):

```
ssh-rsa AAAAB3NzaC1yc2EA... pkcs11:id=%01;type=public
ecdsa-sha2-nistp256 AAAAE2... pkcs11:id=%02;type=public
ssh-rsa AAAAB3NzaC1yc2EA... pkcs11:id=%03;type=public
```

Jede Zeile ist genau das, was in `authorized_keys` reinkommt. Der dritte Spalten-Wert (Kommentar) traegt die PKCS#11-URI — pure Lesehilfe, ssh nutzt sie nicht.

## Client-Seite: `PKCS11Provider`

```bash
ssh -o PKCS11Provider=/usr/lib/softhsm/libsofthsm2.so user@host
```

Beim Login passiert intern:

1. ssh laedt die PKCS#11-Library und sucht alle Pubkeys via `C_FindObjects(CKO_PUBLIC_KEY)`.
2. ssh probiert sie der Reihe nach gegen den Server.
3. Sobald der Server einen akzeptiert, fordert er die Challenge-Signatur an.
4. ssh ruft `C_Login` mit der PIN (Prompt, ASKPASS oder via ssh-agent), dann `C_SignInit + C_Sign`.

## PIN-Handling: drei Varianten

| Variante | Wie | Wann sinnvoll |
|---|---|---|
| Interaktiver Prompt | ssh fragt im Terminal | Lokale Workstation, einzelner Login |
| `SSH_ASKPASS` mit Skript | Skript druckt PIN auf stdout, env `SSH_ASKPASS_REQUIRE=force` (OpenSSH ≥ 8.4) | CI, automatisierte Lab-Tests (so im Lab-Skript) |
| ssh-agent + ssh-add -s | `ssh-add -s libsofthsm2.so` laedt PKCS#11-Modul in Agent, PIN einmalig | Mehrere Logins pro Session, Production-Workstation |

Variante 3 — ssh-agent — ist im Alltag der angenehmste Weg:

```bash
eval "$(ssh-agent -s)"
ssh-add -s /usr/lib/softhsm/libsofthsm2.so       # einmalig PIN eingeben
ssh user@host1
ssh user@host2                                    # nutzt gecachten HSM-Login
ssh-add -e /usr/lib/softhsm/libsofthsm2.so       # Modul wieder rausnehmen
```

## Smartcards und YubiKey: derselbe Mechanism

YubiKey (mit PIV-Applet), Smartcards (CardOS, IDPrime, Estonian ID), Nitrokey, Smart-HSM USB-Tokens — alle exponieren ein PKCS#11-Interface. Aus SSH-Sicht ist es **derselbe Pfad** wie unsere SoftHSM-Demo:

```bash
ssh -o PKCS11Provider=/usr/lib/x86_64-linux-gnu/opensc-pkcs11.so user@host
```

Die Library wechselt; alles andere bleibt gleich. Das ist der Witz an PKCS#11 — HSMs, Smartcards und USB-Tokens sind aus Anwendungssicht austauschbar.

## Setup auf dem Server

Pubkey rein in `authorized_keys`:

```bash
ssh-keygen -D /usr/lib/softhsm/libsofthsm2.so > /tmp/hsm-keys.pub
# entweder direkt auf den Server kopieren:
ssh-copy-id -i /tmp/hsm-keys.pub user@host
# oder per Hand:
cat /tmp/hsm-keys.pub >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
```

In Enterprise-Umgebungen wird `authorized_keys` zentral verwaltet (LDAP-Attribute, Ansible-Inventory, SSH-Certs via CA). Mit SSH-Certs braucht man die Pubkey-Verteilung gar nicht: die CA signiert ein Cert ueber den HSM-Pubkey, der Server vertraut der CA, der HSM signiert weiterhin Logins.

## Stolperfallen

- **`PasswordAuthentication=no`** schliesst auch SSH-Passwort-Fallback. Wer die PIN vergisst, kommt nicht rein.
- **`StrictModes`** auf dem Server checkt, dass `authorized_keys` und das Home nicht world-writable sind. In Container-Labs (alles unter /tmp) muss `StrictModes no` rein.
- **SSH-Agent-Forwarding (`ssh -A`)** macht den HSM-Login auf entfernten Hosts verfuegbar — produktiv extrem nuetzlich (jump-host-Pattern), aber sicherheitsrelevant: der entfernte Server-Admin kann waehrend deiner Session deinen HSM nutzen. Forwarding nur auf vertrauenswuerdigen Hops aktivieren.
- **PIN-Lockout**: viele Token sperren nach 3 falschen PINs. Wer SSH_ASKPASS-Skripte mit falscher PIN deployed, sperrt sich aus dem Token aus (siehe Kapitel zu PIN-Management).
- **OpenSSH ohne PKCS#11-Support**: manche Distros (Alpine, minimal builds) kompilieren `--without-pkcs11`. `ssh -V` oder `ssh -Q kex` zeigt nicht direkt an, aber `ssh -I /path/to/lib` mit "ssh: PKCS#11 support disabled at compile time" tut.

## Eigenexperiment

- Tausche im Lab-Setup den SSHD-Port auf einen anderen Wert (z.B. 2200) und beobachte, wie die ENV `PKCS11_SSHD_PORT` durchgereicht wird.
- Schalte im sshd_config auf `AuthorizedKeysCommand` um — der Server fragt ein Skript nach den Pubkeys (ueblich bei LDAP/Vault-Integration). Wirf dort statt `cat` ein curl gegen einen Mock-Endpoint.
- Setze deinen lokalen ssh-Client auf `IdentitiesOnly=yes` und `IdentityFile=` (leer) — ohne `-o PKCS11Provider` muss der Login fehlschlagen. Nimm es wieder rein und alles funktioniert.

Strukturierte Aufgaben in [`exercises/13-ssh-mit-hsm.md`](../exercises/13-ssh-mit-hsm.md).
