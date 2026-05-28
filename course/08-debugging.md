# 08 — Debugging

## Typische Fehler

| Fehler | Wahrscheinliche Ursache |
|---|---|
| `CKR_PIN_INCORRECT` | User-PIN falsch |
| `CKR_USER_NOT_LOGGED_IN` | Operation braucht Login |
| `CKR_SLOT_ID_INVALID` | falscher Slot oder Token verschoben |
| `CKR_KEY_HANDLE_INVALID` | Objekt-Handle aus alter Session benutzt |
| `CKR_MECHANISM_INVALID` | Mechanism nicht unterstützt |
| `CKR_MECHANISM_PARAM_INVALID` | Parameter, z. B. PSS Salt, falsch |
| `CKR_KEY_TYPE_INCONSISTENT` | Algorithmus passt nicht zum Key |
| `CKR_ATTRIBUTE_VALUE_INVALID` | Objektattribute nicht erlaubt |
| `CKR_OBJECT_HANDLE_INVALID` | Objekt nicht in dieser Session gültig |
| `CKR_SIGNATURE_INVALID` | Signaturprüfung im Token gescheitert |
| `CKR_TEMPLATE_INCONSISTENT` | Attribut-Kombination ist ungültig (z. B. `CKA_SIGN` und `CKA_DECRYPT`) |

## Mechanism-Namen über Stacks hinweg

Dieselbe Operation hat in jedem Tool einen anderen Namen. Diese Tabelle spart viel Zeit:

| PKCS#11 (`CKM_*`) | `pkcs11-tool --mechanism` | OpenSSL `dgst` | JCA `Signature` |
|---|---|---|---|
| `CKM_RSA_PKCS` | `RSA-PKCS` | `-sigopt rsa_padding_mode:pkcs1` | `NONEwithRSA` (raw) |
| `CKM_SHA256_RSA_PKCS` | `SHA256-RSA-PKCS` | `-sha256` (default Padding) | `SHA256withRSA` |
| `CKM_RSA_PKCS_PSS` | `RSA-PKCS-PSS` + `--hash-algorithm SHA256 --mgf MGF1-SHA256` | `-sha256 -sigopt rsa_padding_mode:pss -sigopt rsa_pss_saltlen:-1` | `RSASSA-PSS` mit `PSSParameterSpec` |
| `CKM_ECDSA_SHA256` | `ECDSA-SHA256` | `-sha256` (auf EC-Key) | `SHA256withECDSA` |

## Debugging-Reihenfolge

1. Modulpfad prüfen.
2. Slots anzeigen.
3. Token-Label prüfen.
4. Login testen.
5. Mechanisms anzeigen.
6. Objekte anzeigen.
7. Attribute prüfen.
8. Minimaloperation mit `pkcs11-tool` ausführen.
9. Erst danach Anwendung debuggen.

## OpenSC Spy

OpenSC bietet `pkcs11-spy`, um PKCS#11-Aufrufe zu protokollieren:

```bash
PKCS11SPY=/usr/lib/softhsm/libsofthsm2.so \
PKCS11SPY_OUTPUT=/tmp/spy.log \
  java -Dsun.security.pkcs11.allowSingleThreadedModules=true ...
```

Mächtig, aber gefährlich: Logs können sensitive Metadaten enthalten. Nicht in Produktion anschalten, außer du weißt genau, was du tust.

## Gute Debug-Frage

Kann ich dieselbe Operation mit `pkcs11-tool` ausführen? Wenn nein, ist dein Anwendungs-Code nicht das Hauptproblem.

## Self-Test des Moduls

```bash
pkcs11-tool --module /usr/lib/softhsm/libsofthsm2.so --test --login --pin 987654 --token-label dev-token
```

Bei SoftHSM laufen damit Sign/Verify und Encrypt/Decrypt gegen jeden gefundenen Key. Bei echten HSMs oft langsam und mit Auswirkungen auf Audit-Logs.
