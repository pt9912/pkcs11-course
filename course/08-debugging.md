# 08 — Debugging

## Lernziele

Nach diesem Kapitel kannst du:

- haeufige `CKR_*`-Fehler systematisch eingrenzen.
- Mechanism-Namen zwischen PKCS#11, `pkcs11-tool`, OpenSSL und JCA uebersetzen.
- Slot-, Token-, PIN-, Objekt- und Mechanism-Probleme voneinander trennen.
- `pkcs11-spy` gezielt einsetzen.

## Lab-Bezug

Passende Targets:

```bash
make list-slots
make list-mechanisms
make list-objects
make sign
make verify
```

Nachschlag fuer `CKR_*`, `CKA_*`, `CKM_*` und weitere Praefixe: [Glossar](../docs/glossar.md).

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
| `CKR_SIGNATURE_INVALID` | Signaturprüfung gescheitert — laut Spec ein Rueckgabewert von `C_Verify`/`C_VerifyFinal`, also typischerweise auf der Verifier-Seite sichtbar |
| `CKR_TEMPLATE_INCONSISTENT` | Attribute widersprechen sich (z. B. `CKA_KEY_TYPE=CKK_RSA` zusammen mit `CKA_EC_PARAMS`, oder Nutzungsflags, die der Mechanismus nicht erlaubt) |

## Praxisfaelle

| Symptom | Erst pruefen | Naechster Schritt |
|---|---|---|
| Kein Token sichtbar | `make list-slots` | Token initialisieren oder falsches `SOFTHSM2_CONF` suchen |
| Token sichtbar, Login scheitert | `PKCS11_USER_PIN` | PIN gegen Lab-Doku pruefen, nicht SO-PIN verwenden |
| Key nicht gefunden | `make list-objects` | `CKA_ID`, `CKA_LABEL` und Objektklasse vergleichen |
| Java-KeyStore leer | Zertifikat im Token | `make import-cert`, gleiche `CKA_ID` sicherstellen |
| OpenSSL Verify scheitert | Daten, Mechanism, Encoding | Datei nach Signatur geaendert? PSS/ECDSA-Parameter korrekt? |
| Devcontainer warnt ueber Locale | Image-Stand | Devcontainer rebuilden, damit generierte Locale im Image aktiv ist |
| NuGet/Gradle schreibt nach `/root` | Cache-Env | `.devcontainer` rebuilden und `.nuget`, `.gradle`, `.dotnet` pruefen |

## Mechanism-Namen über Stacks hinweg

Dieselbe Operation hat in jedem Tool einen anderen Namen. Diese Tabelle spart viel Zeit:

| PKCS#11 (`CKM_*`) | `pkcs11-tool --mechanism` | OpenSSL `dgst` | JCA `Signature` |
|---|---|---|---|
| `CKM_RSA_PKCS` | `RSA-PKCS` | `-sigopt rsa_padding_mode:pkcs1` | `NONEwithRSA` (raw) |
| `CKM_SHA256_RSA_PKCS` | `SHA256-RSA-PKCS` | `-sha256` (default Padding) | `SHA256withRSA` |
| `CKM_RSA_PKCS_PSS` | `RSA-PKCS-PSS` + `--hash-algorithm SHA256 --mgf MGF1-SHA256` (Input ist Hash) | `-sha256 -sigopt rsa_padding_mode:pss -sigopt rsa_pss_saltlen:-1` | `RSASSA-PSS` mit `PSSParameterSpec` |
| `CKM_SHA256_RSA_PKCS_PSS` | `SHA256-RSA-PKCS-PSS` + `--mgf MGF1-SHA256` (Token hasht) | `-sha256 -sigopt rsa_padding_mode:pss -sigopt rsa_pss_saltlen:-1` | `RSASSA-PSS` mit `PSSParameterSpec` |
| `CKM_ECDSA` | `ECDSA` + `--signature-format openssl` (Input ist Hash der Curve-Order-Laenge) | `-sha256` (auf EC-Key) + manuelle Hash-Vorstufe | `NONEwithECDSA` (raw, selten genutzt) |
| `CKM_ECDSA_SHA256` | `ECDSA-SHA256` + `--signature-format openssl` | `-sha256` (auf EC-Key) | `SHA256withECDSA` |

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

OpenSC bietet `pkcs11-spy`, um PKCS#11-Aufrufe zu protokollieren. Die Mechanik: Die Anwendung lädt `pkcs11-spy.so` **als Modul**, `PKCS11SPY` zeigt auf das echte Backend, das der Spy dann durchreicht und mitprotokolliert.

```bash
# CLI-Beispiel:
PKCS11SPY=/usr/lib/softhsm/libsofthsm2.so \
PKCS11SPY_OUTPUT=/tmp/spy.log \
  pkcs11-tool --module /usr/lib/x86_64-linux-gnu/pkcs11/pkcs11-spy.so --list-slots
```

Für Java entsprechend in der SunPKCS11-Config:

```text
library = /usr/lib/x86_64-linux-gnu/pkcs11/pkcs11-spy.so
```

und `PKCS11SPY` zeigt aus dem Java-Prozess auf das echte Modul. `PKCS11SPY_OUTPUT` ist optional; ohne Setzung schreibt der Spy nach stderr, nicht in eine Datei.

Mächtig, aber gefährlich: Logs können sensitive Metadaten enthalten. Nicht in Produktion anschalten, außer du weißt genau, was du tust.

### Beispiel-Ausgabe

Ein typischer Mini-Trace fuer eine Signatur sieht etwa so aus:

```text
*************** OpenSC PKCS#11 spy *****************
Loaded: "/usr/lib/softhsm/libsofthsm2.so"

0: C_GetFunctionList
Returned: 0 CKR_OK

1: C_Initialize
[in] pInitArgs = (nil)
Returned: 0 CKR_OK

2: C_GetSlotList
[in] tokenPresent = 0x1
[out] pSlotList[1]: 0x5f6c2d11
[out] *pulCount = 0x1
Returned: 0 CKR_OK

3: C_OpenSession
[in] slotID = 0x5f6c2d11
[in] flags = 0x6 ( CKF_RW_SESSION | CKF_SERIAL_SESSION )
[out] *phSession = 0x1
Returned: 0 CKR_OK

4: C_Login
[in] hSession = 0x1
[in] userType = CKU_USER
[in] pPin[ulPinLen=6] = [REDACTED — PIN]
Returned: 0 CKR_OK

5: C_FindObjectsInit
[in] hSession = 0x1
[in] pTemplate[2]:
    CKA_CLASS    type=00000000 ulValueLen=8 = CKO_PRIVATE_KEY
    CKA_ID       type=00000102 ulValueLen=1 = 01
Returned: 0 CKR_OK

6: C_FindObjects
[in] ulMaxObjectCount = 0x1
[out] phObject[0] = 0x2
[out] *pulObjectCount = 0x1
Returned: 0 CKR_OK

7: C_SignInit
[in] hSession = 0x1
[in] pMechanism->type = CKM_SHA256_RSA_PKCS
[in] hKey = 0x2
Returned: 0 CKR_OK

8: C_Sign
[in] hSession = 0x1
[in] pData[ulDataLen=13] = "hello pkcs11\n"
[out] pSignature[*pulSignatureLen=256] = [256-byte RSA signature]
Returned: 0 CKR_OK

9: C_Logout / C_CloseSession / C_Finalize
Returned: 0 CKR_OK
```

Was man daraus lernt:

- `CKA_ID` zeigt die *Bytefolge*, nicht die hex-String-Darstellung — `01` ist hier ein 1-Byte-Wert mit Zahl 0x01, nicht der ASCII-String "01" (`0x30 0x31`).
- Der PIN-Wert wird unzensiert geloggt — Grund Nr. 1, `pkcs11-spy` niemals dauerhaft in Produktion aktiv zu haben.
- Wenn ein `C_*`-Call ein Non-Zero-`CKR_*` zurueckgibt, ist der Trace **die** schnellste Diagnose. Bei `CKR_KEY_HANDLE_INVALID` siehst du z. B. exakt, mit welchem Handle das `C_SignInit` lief und ob es zur Session passt.

## Gute Debug-Frage

Kann ich dieselbe Operation mit `pkcs11-tool` ausführen? Wenn nein, ist dein Anwendungs-Code nicht das Hauptproblem.

## Self-Test des Moduls

```bash
pkcs11-tool --module /usr/lib/softhsm/libsofthsm2.so --test --login --pin 987654 --token-label dev-token
```

Bei SoftHSM laufen damit Sign/Verify und Encrypt/Decrypt gegen jeden gefundenen Key. Bei echten HSMs oft langsam und mit Auswirkungen auf Audit-Logs.
