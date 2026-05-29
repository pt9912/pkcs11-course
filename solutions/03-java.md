# Loesung 03 - Java ueber SunPKCS11

```bash
make init-token
make gen-rsa
make import-cert
make java-demo
```

`make java-demo` haengt von `import-cert` ab und stellt sicher, dass das Zertifikat vor dem Java-Lauf existiert.

## Erwarteter Output

Gekuerzt:

```text
Provider: SunPKCS11-SoftHSM
Mechanismus: SHA256withRSA
Aliase im PKCS#11-KeyStore:
- signing-key key=true cert=true
Alias: signing-key
Verifikation: true
```

## Erzwungene Fehler

| Manipulation | Erwarteter Fehler |
|---|---|
| `library = /nicht/da` in `softhsm.cfg` | Provider-Load scheitert |
| Zertifikat fehlt | kein Private-Key-Alias sichtbar |
| `PKCS11_USER_PIN=000000` | `CKR_PIN_INCORRECT` beim KeyStore-Load |

Zertifikat direkt loeschen:

```bash
pkcs11-tool --module "$PKCS11_MODULE" \
  --login --pin "$PKCS11_USER_PIN" \
  --token-label "$PKCS11_TOKEN_LABEL" \
  --delete-object --type cert --id 01
```

Java danach bewusst direkt starten, damit `make java-demo` das Zertifikat nicht automatisch wieder importiert:

```bash
cd lab/java/pkcs11-demo
gradle --quiet run
```

Ausserhalb des Devcontainers kannst du denselben direkten Lauf ueber Compose starten:

```bash
docker compose -f lab/docker-compose.yml run --rm pkcs11-lab \
  bash -lc 'cd lab/java/pkcs11-demo && gradle --quiet run'
```
