# Lösung 03 — Java über SunPKCS11

```bash
make init-token
make gen-rsa
make import-cert
make java-demo
```

`make java-demo` ist als `java-demo: import-cert` definiert und stellt sicher, dass das Zertifikat vor dem Java-Lauf existiert.

## Erwarteter Output (gekürzt)

```
Provider: SunPKCS11-SoftHSM
Mechanismus: SHA256withRSA
Aliase im PKCS#11-KeyStore:
- signing-key key=true cert=true
Alias: signing-key
Signatur (Base64): ...
Verifikation: true
```

## Erzwungene Fehler

| Manipulation | Erwarteter Fehler |
|---|---|
| `library = /nicht/da` in `softhsm.cfg` | `Provider configure` wirft `ProviderException` mit `UnsatisfiedLinkError`-Hinweis |
| Cert gelöscht und Java direkt gestartet, nicht über `make java-demo` | Exit-Code `2`, Meldung „Kein Private-Key-Alias … sichtbar" |
| Java direkt mit `PKCS11_USER_PIN=000000` gestartet | `KeyStore.load` wirft `LoginException`, Ursache `CKR_PIN_INCORRECT` |

Direkter Java-Aufruf für die Fehlertests:

```bash
docker compose -f lab/docker-compose.yml run --rm pkcs11-lab \
  bash -lc 'pkcs11-tool --module "$PKCS11_MODULE" --login --pin "$PKCS11_USER_PIN" --token-label "$PKCS11_TOKEN_LABEL" --delete-object --type cert --id 01'

docker compose -f lab/docker-compose.yml run --rm pkcs11-lab \
  bash -lc 'cd lab/java/pkcs11-demo && mvn -q package && java -jar target/pkcs11-demo-1.0.0.jar'

docker compose -f lab/docker-compose.yml run --rm -e PKCS11_USER_PIN=000000 pkcs11-lab \
  bash -lc 'cd lab/java/pkcs11-demo && mvn -q package && java -jar target/pkcs11-demo-1.0.0.jar'
```

## Bonus (EC)

`Pkcs11Demo` nimmt Mechanismus und Alias inzwischen über Env-Variablen. Damit reicht für den EC-Lauf:

1. EC-Key erzeugen und Cert mit derselben `CKA_ID` wie der EC-Key importieren (Skript `08-import-cert.sh` anpassen oder ein zweites Cert mit ID `02` von Hand erzeugen).
2. Demo mit den passenden Env-Variablen starten:
   ```bash
   docker compose -f lab/docker-compose.yml run --rm \
     -e PKCS11_MECHANISM=SHA256withECDSA \
     -e PKCS11_KEY_ALIAS=ec-signing-key \
     pkcs11-lab \
     bash -lc 'cd lab/java/pkcs11-demo && mvn -q package && java -jar target/pkcs11-demo-1.0.0.jar'
   ```

Verifizieren mit Default-Provider funktioniert auch hier, weil der EC-Public-Key über X.509 extrahierbar ist.
