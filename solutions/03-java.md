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
| Cert gelöscht (`--delete-object --type cert --id 01`) | Exit-Code `2`, Meldung „Kein Private-Key-Alias … sichtbar" |
| `PKCS11_USER_PIN=000000` | `KeyStore.load` wirft `LoginException`, Ursache `CKR_PIN_INCORRECT` |

## Bonus (EC)

Java-Demo um EC zu erweitern:

1. `make gen-ec` + Cert mit derselben `CKA_ID` wie der EC-Key importieren (Skript anpassen oder zweites Cert mit ID `02`).
2. In `Pkcs11Demo` einen zweiten Durchlauf hinzufügen, der den EC-Alias sucht und `Signature.getInstance("SHA256withECDSA", provider)` benutzt.
3. Verifizieren mit Default-Provider funktioniert auch hier, weil der EC-Public-Key über X.509 extrahierbar ist.
