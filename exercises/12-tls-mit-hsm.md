# Uebung 12 - HSM-gestuetzte TLS-Terminierung

## Ziel

Du laesst nginx mit dem HSM-Signing-Key als TLS-Server-Key laufen, verbindest dich mit curl, beobachtest den Handshake und untersuchst die Cipher Suite.

## Vorbereitung

```bash
make init-token gen-rsa import-cert
```

## Aufgabe 1 — TLS-Cert ausstellen

```bash
make gen-tls-cert
```

Erwartete Ausgabe:

```text
TLS-Cert: lab/work/tls-cert.pem (subject=CN = localhost)
         X509v3 Subject Alternative Name: DNS:localhost, IP Address:127.0.0.1
```

Self-signed durch den HSM-Key — der Cert ist sein eigener Issuer.

## Aufgabe 2 — nginx + curl-Roundtrip

```bash
make tls-serve
```

Erwartet:
- nginx loggt `nginx: the configuration file ... syntax is ok`
- curl baut eine TLS-1.3-Verbindung auf, zeigt im verbose-Output `Server certificate: subject: CN=localhost`
- Response: `Hello from nginx via HSM-Key`

## Aufgabe 3 — Cipher-Suite-Beobachtung

```bash
docker compose -f lab/docker-compose.yml run --rm pkcs11-lab bash -lc '
  lab/scripts/51-tls-serve-and-test.sh & sleep 1
  echo Q | openssl s_client -connect localhost:8443 -servername localhost \
    -CAfile lab/work/tls-cert.pem 2>&1 | grep -E "Cipher|Protocol|subject="
  wait
'
```

Erwartet:
- `Protocol  : TLSv1.3`
- `Cipher    : TLS_AES_256_GCM_SHA384` (oder TLS_CHACHA20_POLY1305_SHA256)
- `subject=CN = localhost`

## Aufgabe 4 — PIN aus dem Config-File entfernen

Loesche aus `lab/nginx/nginx-pkcs11.conf.template` den Teil `;pin-value=987654` und starte erneut. Erwartet: nginx-Startup-Fehler `(SSL: error:... could not load PEM client certificate engine:pkcs11`).

In Produktion ersetzt man die hardcoded PIN durch `pin-source=|/path/to/pin-fetcher.sh` (Script druckt PIN nach stdout) oder durch das `ssl_password_file`-Feature von nginx (Datei mit 0600).

## Aufgabe 5 — Welche PKCS#11-Calls beim Handshake?

Lege eine `OPENSSL_CONF`-Variante an, in der `MODULE_PATH` auf `pkcs11-spy.so` zeigt (siehe Kapitel 8 fuer das Pattern), starte nginx, mach einen curl-Roundtrip, und schau in `/tmp/spy.log`, was passiert ist.

## Reflexionsfragen

- Warum braucht ein **TLS-1.3-Server-Key** nur `CKA_SIGN`, nicht `CKA_DECRYPT`?
- Wann ist die Reihenfolge `ssl_ciphers ECDHE+AESGCM:CHACHA20` wichtig — was wuerde passieren, wenn statt `ECDHE+AESGCM` `AES256-SHA` als erstes stuende?
- Welches Risiko bringt es, wenn `pin-value=` in der nginx-Config steht und die Config in einem Git-Repo liegt?
- Was passiert, wenn dein nginx mit 4 Worker-Prozessen laeuft und die HSM-Library nicht fork-safe ist?

## Musterloesung

Siehe `solutions/12-tls-mit-hsm.md`.
