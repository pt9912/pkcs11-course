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

## Antworten zu den Reflexionsfragen

**Java sieht den Key ohne Zertifikat nicht "sauber":** `KeyStore.getInstance("PKCS11", provider)` exponiert Private Keys nur ueber Aliase, und ein Alias entsteht in SunPKCS11 erst, wenn das Token ein Cert-Objekt mit derselben `CKA_ID` wie der Privkey hat. Ohne Cert ist der Privkey zwar via Find-Operations sichtbar (Go/C# nutzen das direkt), aber `keyStore.getKey("signing-key", null)` liefert `null`, weil SunPKCS11 den Alias gar nicht erst eintraegt. Das ist eine reine Konvention der Java-KeyStore-Abstraktion, kein PKCS#11-Zwang.

**Demo verifiziert ueber denselben Provider:** Public Keys aus dem Token koennen `CKA_EXTRACTABLE=false` tragen (typisch fuer EC-Keys auf restriktiven HSMs) — der Default-Provider kommt dann an deren Material nicht heran, weil die Pubkey-Instanz intern ein PKCS#11-Handle ist. Verifikation ueber denselben Provider bleibt damit funktional, auch wenn man den Wrap-Pfad spaeter haerter macht. In Produktion verifiziert man hingegen typisch auf der Gegenseite mit einem Default-Provider, weil dort der Pubkey als rohe `SubjectPublicKeyInfo`-Bytes bekannt ist.

**`isCertificateEntry` ist false trotz Cert im Token:** `KeyStore.isCertificateEntry(alias)` meldet `true` nur fuer reine Trusted-Cert-Eintraege ohne Privkey (`TrustedCertificateEntry`). Sobald der Alias einen Privkey hat — also ein `PrivateKeyEntry` mit Zertifikatskette ist — gilt `isKeyEntry() == true` und `isCertificateEntry() == false`. Das Cert ist trotzdem da, abrufbar via `keyStore.getCertificate(alias)`, und die Demo nutzt es genau dafuer.
