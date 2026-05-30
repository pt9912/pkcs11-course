# Loesung 12 - HSM-gestuetzte TLS-Terminierung

## TLS-Cert

```bash
make gen-tls-cert
openssl x509 -in lab/work/tls-cert.pem -noout -text | head -20
```

Felder: `Subject: CN = localhost`, `Issuer: CN = localhost` (self-signed), `Subject Alternative Name: DNS:localhost, IP Address:127.0.0.1`, `Signature Algorithm: sha256WithRSAEncryption`.

## nginx-Roundtrip

```text
--- nginx Konfiguration validieren ---
nginx: the configuration file /tmp/nginx-pkcs11-XXX.conf syntax is ok
nginx: configuration file /tmp/nginx-pkcs11-XXX.conf test is successful

--- nginx im Hintergrund starten ---

--- curl gegen https://localhost:8443 (mit --cacert) ---
* Connected to localhost (127.0.0.1) port 8443
* TLS 1.3 (OUT), Server hello (2):
* TLS 1.3 handshake (...)
* Server certificate: subject: CN=localhost; ...
< HTTP/1.1 200 OK
Hello from nginx via HSM-Key
TLS-Demo OK
```

## Cipher Suite

```text
Protocol  : TLSv1.3
Cipher    : TLS_AES_256_GCM_SHA384
subject=CN = localhost
```

## PIN entfernt

```text
nginx: [emerg] could not load PEM client certificate engine: ENGINE_load_private_key: ENGINE function failure
```

Genau: ohne PIN kann die Engine keinen Login machen, der Privkey ist nicht erreichbar.

## pkcs11-spy Trace

Snippet aus dem Spy-Log waehrend des Handshakes:

```text
C_Initialize
C_GetSlotList
C_OpenSession (RW)
C_Login (CKU_USER)
C_FindObjectsInit (CKA_CLASS=CKO_PRIVATE_KEY, CKA_LABEL=signing-key)
C_FindObjects -> 1 object
C_SignInit (CKM_RSA_PKCS_PSS)
C_Sign -> 256 bytes (TLS handshake CertificateVerify-Signatur)
```

Genau ein `C_Sign` pro Handshake. Bei TLS-Session-Resumption (Tickets) entfaellt der `C_Sign` ueberhaupt — die Session wird ohne Server-Key wiederhergestellt.

## Antworten zu den Reflexionsfragen

**TLS-1.3 ohne CKA_DECRYPT:** TLS 1.3 hat den RSA-Kex (Pre-Master-Secret mit Server-Pubkey verschluesseln) komplett gestrichen. Stattdessen wird der Session-Key per ECDHE (oder DHE) ausgehandelt; der Server-Key signiert nur die Handshake-Transcript-Hash. Decrypt-Funktion wird nie aufgerufen.

**ssl_ciphers-Reihenfolge:** OpenSSL waehlt die erste Cipher Suite, die beide Seiten unterstuetzen. Wenn `AES256-SHA` als erstes steht, kommt entweder RSA-Kex (ohne PFS) oder kein ECDHE — abhaengig von der Suite-Definition. Modern: ECDHE+AEAD-Suiten zuerst, alles andere optional als Fallback. Bei TLS 1.3 ist die Suite-Auswahl entkoppelt (die Cipher Suite definiert nur AEAD+Hash, Key-Exchange ist immer ECDHE).

**PIN im Git-Repo:** klassischer Leak. Wer auf das Repo zugreift, hat trivialen HSM-Zugriff. Mitigation: `pin-source` auf Script, das die PIN aus dem Secret-Backend (Vault, AWS Secrets Manager, Sealed-Secrets) holt. Oder `ssl_password_file` mit Permissions 0600 nur fuer den nginx-User.

**Worker-Fork ohne Fork-Safety:** Klassisches Symptom — der erste Request klappt, der zweite haengt oder bricht mit `CKR_DEVICE_ERROR`. Workaround: `worker_processes 1` (nur ein Worker, kein fork-after-init), oder ein separater Sidecar-Proxy (z.B. `socat`/`stunnel`) macht TLS-Termination, nginx selbst ist HTTP. Echte HSM-Hersteller liefern oft ein eigenes `nginx-pkcs11-helper`-Daemon mit, das den fork-Issue umgeht.
