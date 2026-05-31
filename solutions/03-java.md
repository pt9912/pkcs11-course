# Loesung 03 - Java ueber SunPKCS11

```bash
make init-token
make gen-rsa
make import-cert
make java-demo
```

`make java-demo` haengt von `import-cert` ab und stellt sicher, dass das Zertifikat vor dem Java-Lauf existiert.

## Erwarteter Output

Gekuerzt (der Demo druckt ausserdem `Signatur (Base64): ...`):

```text
Provider: SunPKCS11-SoftHSM
Mechanismus: SHA256withRSA
Aliase im PKCS#11-KeyStore:
- signing-key key=true cert=false
Alias: signing-key
Verifikation: true
```

`cert=false` ist die korrekte Ausgabe, obwohl `make import-cert` ein Zertifikat reingelegt hat: `KeyStore.isCertificateEntry(alias)` meldet `true` nur fuer reine Trusted-Cert-Eintraege ohne Privkey. Bei einem `PrivateKeyEntry` mit Zertifikatskette — was wir hier haben — bleibt `isCertificateEntry` `false`, waehrend `isKeyEntry` `true` ist. Das Zertifikat ist trotzdem ueber `keyStore.getCertificate(alias)` abrufbar, und genau das tut die Demo, um den Pubkey fuer den Verify zu bekommen.

Die Reflexionsfrage zum Verify-Pfad: die Demo verifiziert bewusst mit demselben `SunPKCS11`-Provider, nicht mit dem Default-Provider. Begruendung: Public Keys, die aus einem PKCS#11-Token kommen, koennen `CKA_EXTRACTABLE=false` tragen (typisch fuer EC-Keys auf restriktiven HSMs) — der Default-Provider wuerde an deren Material nicht herankommen, weil die Pubkey-Instanz intern ein PKCS#11-Handle ist. Der Sign-Pfad ist ohnehin HSM-gebunden; den Verify-Pfad auf denselben Provider zu legen, vermeidet Edge-Cases ohne Sicherheitsverlust. In Produktion verifiziert man fast immer auf der Gegenseite mit einem Default-Provider — dort ist der Pubkey als rohe Bytes (`SubjectPublicKeyInfo`) bekannt.

## Erzwungene Fehler

Die Tabelle zeigt jeweils, wie der Lauf zuverlaessig direkt in die Java-Demo platzt — `make`-Aufrufe wuerden je nach Variante schon in der `init-token`/`gen-rsa`/`import-cert`-Kette abbrechen.

| Manipulation | Aufruf | Erwarteter Fehler |
|---|---|---|
| `PKCS11_LIBRARY=/nicht/da` | siehe Block unten | Provider-Load wirft `ProviderException`; `reportFailure` druckt die Cause-Kette |
| Zertifikat manuell loeschen | siehe Block unten | kein Private-Key-Alias sichtbar, Exit-Code 2 |
| `PKCS11_USER_PIN=000000` direkt an die Java-Demo | siehe Block unten | `CKR_PIN_INCORRECT` beim `KeyStore.load` |

Falsche Library — Demo direkt ueber Compose starten, damit nichts neu initialisiert wird:

```bash
docker compose -f lab/docker-compose.yml run --rm \
  -e PKCS11_LIBRARY=/nicht/da \
  pkcs11-lab bash -lc 'cd lab/java/pkcs11-demo && ./gradlew --quiet --no-daemon run'
```

Zertifikat loeschen und anschliessend direkt starten (ohne `make java-demo`, sonst wird das Zertifikat ueber `import-cert` sofort neu erzeugt):

```bash
docker compose -f lab/docker-compose.yml run --rm pkcs11-lab bash -lc '
  pkcs11-tool --module "$PKCS11_MODULE" \
    --login --pin "$PKCS11_USER_PIN" \
    --token-label "$PKCS11_TOKEN_LABEL" \
    --delete-object --type cert --id 01 &&
  cd lab/java/pkcs11-demo && ./gradlew --quiet --no-daemon run
'
```

Falsche PIN nur an die Java-Demo geben, damit die `init-token`-Vorstufe noch mit der echten PIN laeuft:

```bash
make init-token gen-rsa import-cert
docker compose -f lab/docker-compose.yml run --rm \
  -e PKCS11_USER_PIN=000000 \
  pkcs11-lab bash -lc 'cd lab/java/pkcs11-demo && ./gradlew --quiet --no-daemon run'
```

Im Devcontainer ersetzt du das `docker compose ... run --rm ... bash -lc '...'` jeweils durch ein direktes `(cd lab/java/pkcs11-demo && ./gradlew --quiet --no-daemon run)` mit vorangestellten ENV-Variablen.
