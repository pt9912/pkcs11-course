# 13 — Hybride Verschluesselung mit RSA-OAEP und AES-GCM

## Lernziele

Nach diesem Kapitel kannst du:

- erklaeren, warum reines RSA-OAEP fuer Dokumente nicht reicht.
- einen Wrap-Key im Token erzeugen, der **nicht** signieren kann.
- ein Dokument hybrid verschluesseln (AES-Session-Key wird per RSA-OAEP gewrappt, Dokument per AES-GCM).
- den Empfaengerpfad ueber den HSM ausfuehren.
- die typischen Stolperfallen bei OAEP-Parametern und SoftHSM einordnen.

## Lab-Bezug

```bash
make gen-rsa-wrap           # Wrap-Keypair (ID=03, Label wrap-key)
make encrypt                # Bash-Encrypt (RSA-OAEP via openssl pkcs11-engine)
make decrypt                # Bash-Decrypt + Round-Trip-Check
make issue-wrap-cert        # Plumbing-Cert fuer SunPKCS11 (nur Java/Kotlin)
make java-encrypt-demo      # SunPKCS11 + javax.crypto
make go-encrypt-demo        # miekg/pkcs11 + crypto/aes
make kotlin-encrypt-demo    # SunPKCS11 + javax.crypto
make csharp-encrypt-demo    # Pkcs11Interop + System.Security.Cryptography
```

## Warum nicht einfach RSA?

RSA-OAEP verschluesselt nur Bloecke kleiner als der Modulus. Bei RSA-2048 mit SHA-256 OAEP bleiben **rund 190 Byte** Klartext pro Operation — fuer eine Mail oder PDF zu wenig. Eine Streckung durch viele RSA-Aufrufe waere zudem dramatisch langsam: RSA ist 100- bis 1000-mal langsamer als AES.

Die Standardloesung ist **hybride Verschluesselung**:

1. Eine zufaellige AES-Session-Key + IV werden auf dem Sender-Host erzeugt.
2. Das Dokument wird symmetrisch (AES-256-GCM) verschluesselt — schnell und beliebig gross.
3. Der AES-Key wird mit RSA-OAEP unter dem Public Key des Empfaengers **gewrappt**.
4. Sender sendet `wrapped-key`, `iv`, `ciphertext` an den Empfaenger.
5. Empfaenger gibt `wrapped-key` an seinen HSM, bekommt den AES-Key zurueck, entschluesselt das Dokument.

Genau diesen Aufbau verwenden S/MIME, age, TLS-Resumption und viele HSM-gestuetzte Document-Stores.

```
Sender (kein HSM)                     Empfaenger (HSM)
-----------------                     ----------------
random AES, IV
AES-GCM(doc) ───────► ciphertext+IV ───►
RSA-OAEP(pub, AES) ─► wrapped       ───► RSA-OAEP-Decrypt(priv) ─► AES
                                        AES-GCM-Decrypt(ciphertext) ─► doc
```

## Sortenreiner Wrap-Key (Soll vs Lab-Realitaet)

Die didaktische Zielpolicy: der Signing-Key auf `ID=01` ist intendiert als reiner Signier-Key (`CKA_SIGN=true`, `CKA_DECRYPT=false`), der Wrap-Key auf `ID=03` als reiner Wrap/Decrypt-Key ohne `CKA_SIGN`. Ein HSM, das diese Policy strikt erzwingt, antwortet auf jeden Cross-Use mit `CKR_KEY_FUNCTION_NOT_PERMITTED`.

Wir legen den Wrap-Key entsprechend an:

```bash
pkcs11-tool --keypairgen --key-type rsa:2048 \
  --id 03 --label wrap-key \
  --usage-decrypt --usage-wrap
```

`--usage-decrypt` markiert `CKA_DECRYPT`/`CKA_ENCRYPT`, `--usage-wrap` zusaetzlich `CKA_WRAP/CKA_UNWRAP`. **Kein** `--usage-sign` — die explizite Intent ist "darf nicht signieren".

Hintergrund zur Wahl von `ID=03`: `ID=02` ist bereits durch den EC-Key aus `09-generate-ec.sh` belegt; ein Konflikt waere fuer Suchen ueber `CKA_ID` unerkennbar.

### SoftHSM-Realitaet: `--usage-*` ist Intent, kein Constraint

`pkcs11-tool --usage-*` markiert die Intent, setzt aber unter SoftHSM 2.6 / OpenSC kein vollstaendiges sortenrein-Template. Frisch erzeugt sieht der `signing-key` so aus:

```text
Private Key Object; RSA
  label:      signing-key
  Usage:      decrypt, sign, signRecover, unwrap
```

Der `wrap-key` aus `--usage-decrypt --usage-wrap` hat zusaetzlich auch `CKA_SIGN/CKA_VERIFY`; der KEK aus Modul 20 (`--usage-wrap`) kommt sogar mit `encrypt, decrypt, sign, verify, wrap, unwrap` raus. SoftHSM setzt ein breites Default-Profil und behandelt `--usage-*` als Filter, der das Profil nur ueberschreibt, wenn die Flag explizit ausgeschlossen ist.

Praktisch heisst das fuer's Lab: wenn du den `signing-key` versehentlich an einen Decrypt-Pfad reichst, antwortet SoftHSM mit Erfolg, nicht mit `CKR_KEY_FUNCTION_NOT_PERMITTED`. Die Lektion "Use-Case-Trennung schuetzt Keys vor Missbrauch" stimmt — sie wird im Lab aber nicht durch das HSM erzwungen.

Reale HSMs mit FIPS-/CC-Policy (Thales Luna, Utimaco, AWS CloudHSM) verhalten sich strenger und liefern den erwarteten Fehler. Strikt-sortenreine Keys im Lab sind das Thema einer eigenen Roadmap-Aufgabe (siehe `roadmap.md`, 0.16.0): ein Generate-Helper, der `C_GenerateKey`/`C_GenerateKeyPair` mit vollstaendigem CKA-Template direkt aufruft (statt ueber `pkcs11-tool --usage-*`), plus ein `--validate-usage`-Check-Target. Damit kommt das HSM-Verhalten im Lab dem realer Hardware gleich.

Bis dahin gilt fuer alle Module, die Use-Case-Trennung lehren (13, 20, 22): die Tabellen beschreiben das Soll, der Lab-Token ist breiter. Die Tests, die im Kurs `CKR_KEY_FUNCTION_NOT_PERMITTED` provozieren wollen, brauchen entweder native Templates oder echte HSM-Hardware.

## OAEP-Parameter — die unterschaetzte Falle

`CKM_RSA_PKCS_OAEP` ist eine **familie** von Mechanismen. Jeder Aufruf braucht drei Parameter:

| Parameter | Werte (typisch) |
|---|---|
| `hashAlg` | `CKM_SHA_1`, `CKM_SHA256`, `CKM_SHA384`, `CKM_SHA512` |
| `mgf` | `CKG_MGF1_SHA1`, `CKG_MGF1_SHA256`, … |
| `source` | `CKZ_DATA_SPECIFIED` (Label, in der Regel leer) |

`hashAlg` und `mgf` **muessen zur selben Hash-Familie** gehoeren — Sender und Empfaenger genauso. Mischen erzeugt `CKR_MECHANISM_PARAM_INVALID` oder die Entschluesselung schlaegt stumm fehl.

## Drei Stolperfallen, ein Lab-Lauf

Dieses Lab traegt zwei reale Quirks offen:

1. **SoftHSM 2.6.x lehnt `CKM_RSA_PKCS_OAEP` mit `hashAlg=CKM_SHA256` direkt ab** (`CKR_ARGUMENTS_BAD`). SHA-1 OAEP funktioniert.
2. **SunPKCS11 registriert keinen OAEP-Cipher** — nur `RSA/ECB/PKCS1Padding` und `RSA/ECB/NoPadding` stehen zur Verfuegung.

Daraus ergeben sich drei Wege durch dasselbe Ziel:

| Demo | Wrap-Pfad | Decrypt-Pfad |
|---|---|---|
| **Bash** (`17/18-*`) | `openssl pkeyutl -encrypt -pubin` (host) | `openssl pkeyutl -decrypt -engine pkcs11` → die Engine faellt intern auf `CKM_RSA_X_509` zurueck und macht OAEP in Software. **SHA-256 OAEP**. |
| **Go** (`pkcs11-encrypt-demo`) | `miekg/pkcs11` mit `CKM_RSA_PKCS_OAEP` | `miekg/pkcs11` mit `CKM_RSA_PKCS_OAEP`. **SHA-1 OAEP** wegen SoftHSM-Quirk. |
| **C#** (`Pkcs11EncryptDemo`) | `Pkcs11Interop` mit `CKM_RSA_PKCS_OAEP` | wie Go. **SHA-1 OAEP**. |
| **Java / Kotlin** | SunJCE mit Pubkey aus dem Cert (kein HSM-Call) | SunPKCS11 mit `RSA/ECB/NoPadding` + **Software-OAEP-Unpadding** im Anwendungscode. **SHA-1 OAEP** wegen SoftHSM. |

Reale HSMs (Thales, Utimaco, AWS CloudHSM, YubiHSM 2) akzeptieren SHA-256 OAEP problemlos. Die Werkstatt-HSM-Erfahrung "der Mechanism ist da, aber die Parameter sind irgendwo zickig" gehoert allerdings dazu.

## Was bleibt im HSM, was nicht

| Material | Lebenszyklus |
|---|---|
| Privater RSA-Wrap-Key | bleibt **immer** im HSM. `CKA_SENSITIVE=true`, `CKA_EXTRACTABLE=false`. |
| AES-Session-Key | wird auf dem Host erzeugt, sofort nach Verbrauch ueberschrieben. Existiert dauerhaft nur als gewrappte Kopie. |
| OAEP-padded RSA-Output (Decrypt-Variante Java/Kotlin) | landet kurzzeitig im Anwendungsspeicher. Wer Schutz vor Memory-Dumps braucht, nutzt einen HSM mit OAEP-Decrypt-Support und vermeidet diesen Pfad. |

## Eigenexperiment

- Aendere im Encrypt-Schritt den `IV`-Wert nach dem Schreiben um ein Byte und starte den Decrypt — der Helper meldet `InvalidTag` (Bash) oder `AEADBadTagException` (Java). Genau dafuer ist GCM da: erkennen, **dass** etwas geaendert wurde.
- Versuche `pkcs11-tool --decrypt --mechanism RSA-PKCS-OAEP --hash-algorithm SHA256` direkt — beobachte den `CKR_ARGUMENTS_BAD`-Fehler. Vergleiche mit `openssl pkeyutl … -engine pkcs11`, das die `CKM_RSA_X_509`-Fallback-Strategie nutzt.
- Setze in `19-issue-wrap-cert.sh` den Subject auf einen anderen Namen und beobachte, was SunPKCS11 dann als Alias zurueckgibt — der KeyStore liest den Cert-Subject als Alias-Hinweis.

Strukturierte Aufgaben dazu findest du in [`exercises/07-encrypt.md`](../exercises/07-encrypt.md).
