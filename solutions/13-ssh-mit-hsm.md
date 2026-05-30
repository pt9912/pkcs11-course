# Loesung 13 - HSM-gestuetzte SSH-Authentifizierung

## Pubkeys

```bash
make ssh-pubkey
```

Erwartete Zeilen pro RSA-Key (signing, wrap, eventuell stream-key falls erfasst):

```text
ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQ... pkcs11:id=%01;type=public
ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQ... pkcs11:id=%03;type=public
```

EC-Key (ec-signing-key auf ID=02) zusaetzlich als `ecdsa-sha2-nistp256 ...`.

## SSH-Roundtrip

Erwartete Ausgabe:

```text
--- ssh-Login via HSM-Key ---
Eingeloggt als course auf <hostname>. Authentication = HSM-Pubkey.
SSH-Demo OK
```

## Ohne PKCS11Provider

```text
Warning: Permanently added '[127.0.0.1]:2222' (ED25519) to the list of known hosts.
course@127.0.0.1: Permission denied (publickey).
```

Genau wie gewollt: kein Passwort-Auth erlaubt, kein File-basierter Key vorhanden, kein HSM-Key bereitgestellt → Login-Verweigerung.

## Falsche PIN

```text
sign_and_send_pubkey: signing failed for RSA "pkcs11:id=%01;type=public": agent refused operation
course@127.0.0.1: Permission denied (publickey).
```

Die PKCS#11-Lib gibt `CKR_PIN_INCORRECT` zurueck; ssh bekommt einen Sign-Fehler, behandelt den Key als unbenutzbar, scheitert.

## ssh-agent

```text
Identity added: pkcs11:id=%01;type=public
... weitere ...
ssh-rsa AAAAB3NzaC1yc2EA... pkcs11:id=%01;type=public
```

`ssh-add -L` listet die im Agent geladenen Identities — der Login-Subprozess via ssh fragt den Agent statt direkt das HSM, deshalb fragt das Token nicht erneut nach PIN.

## Antworten zu den Reflexionsfragen

**Wo wird signiert?** SSH-User-Auth-Sign passiert in der Phase nach dem Pubkey-Probe. Inhalt der Signatur: ein "Pubkey-User-Auth-Request"-Block mit `session_id + SSH_MSG_USERAUTH_REQUEST + user + service + "publickey" + 1 + algo + pubkey`. Genau ein `C_Sign(CKM_RSA_PKCS oder CKM_SHA512_RSA_PKCS)` pro erfolgreicher Authentifikation. Bei TLS-Session-Reuse (ControlMaster im SSH) entfaellt das fuer Folgeverbindungen.

**Pubkey im Klartext senden:** Kein Leak. Der Pubkey ist per Definition oeffentlich; sein Sicherheitswert liegt allein in der mathematischen Bindung an den geheimen Privkey. Ein Lauscher kann den Pubkey kopieren, aber nicht damit signieren — und ohne Signatur kein Login. Trotzdem ist das ein Privacy-Hinweis: der Server lernt, welche Identitaeten ein Client probiert. Wer das vermeiden will, nutzt `IdentitiesOnly=yes` mit nur dem benoetigten Key.

**5 Pubkeys, 1 in authorized_keys:** ssh probiert die Keys in der Reihenfolge, die die PKCS#11-Library liefert (in der Regel CKA_ID-aufsteigend). Im schlimmsten Fall vier "publickey rejected" bevor der fuenfte akzeptiert wird. Folgekosten: 4 zusaetzliche Roundtrips zum Server plus 4 unnoetige PKCS#11-`C_FindObjects`-Aufrufe. Bei kostspieligem PIN-Prompt (Smartcard mit physischem PIN-Pad) wuerde ssh dazwischen aber pro Key fragen — sehr nervig. Workaround: `IdentitiesOnly=yes` + explizit gewuenschter Pubkey via `IdentityFile`.

**Agent-Forwarding-Risiko:** Bei `ssh -A user@jump-host` ist auf `jump-host` ein Unix-Socket des lokalen ssh-Agents sichtbar. Wer Root auf `jump-host` hat (Admin oder Eindringling) kann den Socket benutzen, um beliebige ssh-Logins **mit deinem HSM** auszuloesen — solange deine Session laeuft. Mitigation: Forwarding nur auf eigenen, vertrauenswuerdigen Hops; bei externen Hops `ProxyJump` statt Agent-Forwarding nutzen (lokaler ssh-Prozess macht die Auth, der Jump tunnelt nur das TCP).
