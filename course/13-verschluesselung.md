# 13 — Hybride Verschluesselung mit RSA-OAEP und AES-GCM

## Bevor du anfaengst — was vermutest du?

> Du sollst eine 50 MB grosse Datei mit RSA verschluesseln. Welche API rufst du auf?

Wahrscheinliche Vermutung: irgendwo gibt es `RSA.encrypt(publicKey, data)` mit beliebiger Eingabelaenge. Vielleicht muss man die Datei in Stuecke schneiden, aber das macht die Library schon.

Diese Vermutung ist falsch — und genau hier setzt das Kapitel an. RSA verschluesselt nur **einen** Block kleiner als der Modulus (rund 190 Byte bei RSA-2048 mit SHA-256 OAEP). Wer 50 MB direkt mit RSA verschluesselt, bekommt entweder einen Fehler oder versucht, die Datei in Hunderttausende RSA-Operationen zu zerlegen — beides praktisch unbrauchbar. Die Loesung ist **hybrid**: AES verschluesselt die Daten, RSA verschluesselt nur den AES-Schluessel.

Halte die "RSA-direkt"-Karte fest. Sie wird durch das hybride Schema ersetzt — und das ist genau die Architektur, die S/MIME, age und TLS-Resumption nutzen.

## Lernziele

Nach diesem Kapitel kannst du:

- erklaeren, warum reines RSA-OAEP fuer Dokumente nicht reicht.
- einen Wrap-Key im Token erzeugen, der **nicht** signieren kann.
- ein Dokument hybrid verschluesseln (AES-Session-Key wird per RSA-OAEP gewrappt, Dokument per AES-GCM).
- den Empfaengerpfad ueber den HSM ausfuehren.
- die typischen Stolperfallen bei OAEP-Parametern und SoftHSM einordnen.
- **(Bloom 5 — evaluate)** entscheiden, wann ein HSM-residenter OAEP-Decrypt-Pfad gegenueber dem JCA-Software-OAEP-Pfad (`RSA/ECB/NoPadding` plus manuelles Unpadding) der richtige Weg ist — und welche Compliance-/Performance-Achsen die Wahl tragen.

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

## Sortenreiner Wrap-Key

Der Signing-Key auf `ID=01` ist absichtlich **nur** zum Signieren angelegt (`CKA_SIGN=true`, alle anderen Usage-Flags `false`). Versucht man ihn zum Decrypt zu nutzen, antwortet das Token mit `CKR_KEY_FUNCTION_NOT_PERMITTED`.

Wir legen deshalb einen zweiten Key an:

```bash
make gen-rsa-wrap
```

Hinter dem Target steht seit 0.16.0 der Go-Helper `lab/go/pkcs11-keygen`, der `C_GenerateKeyPair` mit einem **vollstaendigen** CKA-Template aufruft. Fuer den Wrap-Key sieht das Ergebnis so aus (sichtbar via `make list-objects`):

```text
Private Key Object; RSA
  label:      wrap-key
  Usage:      decrypt, unwrap
Public Key Object; RSA 2048 bits
  label:      wrap-key
  Usage:      encrypt, wrap
```

`CKA_DECRYPT=true`, `CKA_UNWRAP=true` (privater Teil) bzw. `CKA_ENCRYPT=true`, `CKA_WRAP=true` (oeffentlicher Teil). `CKA_SIGN/CKA_VERIFY` und alle weiteren Usage-Flags sind explizit `false` — das Token weist Sign-Versuche mit `CKR_KEY_FUNCTION_NOT_PERMITTED` ab.

Hintergrund zur Wahl von `ID=03`: `ID=02` ist bereits durch den EC-Key aus `09-generate-ec.sh` belegt; ein Konflikt waere fuer Suchen ueber `CKA_ID` unerkennbar.

### Historisch: `pkcs11-tool --usage-*` ist Intent, kein Constraint

Bis 0.16.0 lief das Lab ueber `pkcs11-tool --keypairgen --usage-*`. Diese Flag markiert die Intent (in OpenSC-Quellcode-Begriffen: setzt die genannten CKA_*-Bits TRUE), setzt die anderen Bits aber **nicht explizit auf FALSE**. SoftHSM 2.6 / OpenSC interpretieren das nicht als "user said FALSE" sondern als "no preference", und der Default-Wert ist TRUE. Der Lab-`signing-key` kam so mit `decrypt, sign, signRecover, unwrap` raus, der KEK aus Modul 20 sogar mit `encrypt, decrypt, sign, verify, wrap, unwrap`.

Reale HSMs mit FIPS-/CC-Policy (Thales Luna, Utimaco, AWS CloudHSM) verhalten sich anders: sie defaulten die nicht-gesetzten Flags auf FALSE, und Cross-Use schlaegt mit `CKR_KEY_FUNCTION_NOT_PERMITTED` fehl. Das war die Diskrepanz, die der Go-Helper aus dem Weg raeumt.

Wer das nachpruefen will: `make validate-key-usage` ruft `pkcs11-tool --list-objects` auf und vergleicht die `Usage:`-Zeile jedes Keys gegen ein hartcodiertes Soll-Profil. Drift → exit 1.

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

Daraus ergeben sich vier Wege durch dasselbe Ziel. Statt sie alle parallel zu listen, arbeiten wir **einen Pfad vollstaendig durch** (Bash) und lassen dich an den anderen drei pruefen, ob du das Schema verstanden hast.

### Worked Example: der Bash-Pfad (vollstaendig)

```bash
make encrypt   # Schritt-fuer-Schritt unten
make decrypt   # symmetrisch
```

**Schritt 1 — Pubkey vom Token holen.** `lab/scripts/17-encrypt.sh` ruft `pkcs11-tool --read-object --type pubkey --id 03` und konvertiert den DER-Pubkey nach PEM. Kein HSM-Login, weil Pubkeys `CKA_PRIVATE=FALSE` sind.

**Schritt 2 — AES-Session-Key auf dem Host wuerfeln.** `openssl rand -hex 32` → 32 Byte AES-256-Key, plus 12 Byte IV. Bei jeder Encrypt-Operation neu. **Diese Bytes existieren nur auf dem Host**, das Token sieht sie nie als Klartext.

**Schritt 3 — Dokument mit AES-256-GCM verschluesseln.** `openssl enc -aes-256-gcm -K $AES_HEX -iv $IV_HEX -in document.txt -out document.enc`. AES-GCM ist authenticated — eine Manipulation des Ciphertexts wird beim Decrypt als `InvalidTag` sichtbar.

**Schritt 4 — AES-Key per RSA-OAEP wrappen (Host-Seite).** `openssl pkeyutl -encrypt -pubin -inkey pub.pem -pkeyopt rsa_padding_mode:oaep -pkeyopt rsa_oaep_md:sha256 -pkeyopt rsa_mgf1_md:sha256 -in aes-key.bin -out wrapped.bin`. SHA-256 OAEP. Achtung — der naechste Schritt ist die eigentliche Falle.

**Schritt 5 — Decrypt-Seite: `openssl pkeyutl -decrypt -engine pkcs11`.** Die OpenSSL-Engine erkennt, dass der Privkey hinter PKCS#11 sitzt. Sie *koennte* `CKM_RSA_PKCS_OAEP` mit SHA-256 anfordern — SoftHSM 2.6 lehnt das aber mit `CKR_ARGUMENTS_BAD` ab. Die Engine erkennt den Fehler intern und faellt auf **`CKM_RSA_X_509`** zurueck: sie laesst das Token nur die rohe RSA-Operation machen, das OAEP-Padding wird **in Software auf dem Host** entfernt. Aus Anwendungssicht (`openssl pkeyutl -decrypt`) ist das transparent — die Schicht-Trennung "Token rechnet RSA, Engine paddet" passiert tief unten in der Engine. Der gewrappte AES-Key kommt korrekt zurueck.

**Schritt 6 — Mit dem AES-Key entschluesseln.** Symmetrisch zu Schritt 3. `openssl enc -d -aes-256-gcm` — bei verfaelschtem Ciphertext kommt `bad decrypt`/`gcm decryption failed`.

Der gewonnene Schema-Kern: **Wrap auf dem Host (Pubkey ist nicht sensitiv), Unwrap am HSM (Privkey bleibt im Token), OAEP-Padding kann je nach Pfad entweder vom HSM oder von der Anwendung gemacht werden — der Punkt ist nur, dass beide Seiten dieselbe Wahl treffen.**

### Faded Examples — die drei Sprach-Pfade

Du hast jetzt das Schema. Pruefe an den drei anderen Pfaden, ob du die jeweils relevante Variante des Schemas erkennst. Pro Pfad: eine kurze Tabellen-Beschreibung und **drei Leitfragen**, die du beantworten koennen solltest, bevor du `make ...-encrypt-demo` aufrufst.

#### Go (`pkcs11-encrypt-demo`)

| Wrap-Pfad | Decrypt-Pfad |
|---|---|
| `miekg/pkcs11` direkt mit `CKM_RSA_PKCS_OAEP`. **SHA-1 OAEP** statt SHA-256 wegen SoftHSM-Quirk. | `miekg/pkcs11` direkt mit `CKM_RSA_PKCS_OAEP`, SHA-1. |

Leitfragen:

1. **Was unterscheidet diesen Pfad vom Bash-Pfad in Schritt 4?** (Stichworte: wer macht das Padding, welche Hashfunktion.)
2. **Warum ist hier kein Fallback auf `CKM_RSA_X_509` noetig?** (Tipp: die Engine bei Bash entscheidet selbst; Go ruft direkt auf, mit welcher Hashfunktion?)
3. **Was passiert, wenn du im Go-Code `sha256.New()` statt `sha1.New()` in den `CK_RSA_PKCS_OAEP_PARAMS` setzt?** Beobachte den Fehler und vergleiche mit dem `CKR_ARGUMENTS_BAD`-Quirk oben.

#### C# (`Pkcs11EncryptDemo`)

| Wrap-Pfad | Decrypt-Pfad |
|---|---|
| `Pkcs11Interop` direkt mit `CKM_RSA_PKCS_OAEP`. **SHA-1**. | wie Go. |

Leitfragen:

1. **Was unterscheidet den C#-Pfad strukturell vom Go-Pfad?** (Antwort: fast nichts — beides ist eine duenne Library-Schicht ueber die C-API. Die OAEP-Parameter sind in `CkRsaPkcsOaepParams`.)
2. **Warum benutzt C# auch SHA-1, obwohl `System.Security.Cryptography` SHA-256-OAEP problemlos kann?** (Stichwort: HSM-Seite, nicht Host-Seite, entscheidet.)
3. **Was musst du auf der Encrypt-Seite tun?** Schau in den Code — der Pubkey wird hier ueber `CKA_MODULUS`/`CKA_PUBLIC_EXPONENT` aus dem Token gelesen und in eine `RSACryptoServiceProvider`-Instanz gebaut, dann macht .NET den Encrypt mit SHA-1 OAEP auf dem Host. Warum nicht ueber den HSM-Wrap-Pfad?

#### Java / Kotlin

| Wrap-Pfad | Decrypt-Pfad |
|---|---|
| SunJCE mit Pubkey aus dem Cert (kein HSM-Call). Encrypt-Seite ist *kein* HSM-Pfad. | SunPKCS11 mit `RSA/ECB/NoPadding` + **Software-OAEP-Unpadding** im Anwendungscode. **SHA-1**. |

Leitfragen:

1. **Warum geht SunPKCS11 hier NICHT den `Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")`-Weg, der scheinbar naheliegend waere?** (Quirk Nr. 2 oben — SunPKCS11 registriert die OAEP-Cipher gar nicht.)
2. **Welche zwei Stufen werden im Anwendungscode zusammengesetzt, weil die Library das nicht tut?** (Stichwort: roh RSA + manuelles OAEP-Unpadding ueber Bouncy oder eigenen Code.)
3. **Wieso ist das in der Demo nicht so schlimm, wie es klingt?** Vergleiche mit dem Bash-Engine-Fallback: dort wird auch in Software gepaddet. Der einzige Unterschied: bei Java siehst du das im *Anwendungscode*, bei Bash versteckt es die Engine.

### Wenn alle vier Pfade im Kopf zusammenkommen

Reale HSMs (Thales, Utimaco, AWS CloudHSM, YubiHSM 2) akzeptieren SHA-256 OAEP problemlos. Die Werkstatt-HSM-Erfahrung "der Mechanism ist da, aber die Parameter sind irgendwo zickig" gehoert allerdings dazu — wer eine der vier Implementierungen verstanden hat, hat den Reflex: **vor Annahme der Mechanism-Wahl `pkcs11-tool --list-mechanisms` lesen, dann das HSM-Vendor-Handbuch, dann erst die Sprach-Lib.**

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

## Selbsttest

<details>
<summary>1. Warum nutzt das hybride Schema RSA <em>nur</em> fuer den AES-Key und nicht direkt fuer das Dokument?</summary>

Drei Gruende: RSA-OAEP verschluesselt nur ~190 Byte pro Aufruf (bei RSA-2048 SHA-256 OAEP), nicht beliebige Datenmengen; RSA ist 100-1000x langsamer als AES; und der Stream-Aspekt — AES-GCM kann groessere Dokumente streamen, RSA kann das per Spec nicht.
</details>

<details>
<summary>2. Welcher der vier Sprach-Pfade macht den OAEP-Decrypt komplett im HSM, und welcher macht ihn in Software?</summary>

**Komplett im HSM:** Go und C# (`miekg/pkcs11` und Pkcs11Interop rufen `CKM_RSA_PKCS_OAEP` mit SHA-1 direkt am Token auf). **In Software auf dem Host:** Bash (Engine faellt auf `CKM_RSA_X_509` zurueck) und Java/Kotlin (SunPKCS11 mit `RSA/ECB/NoPadding` + manuelles OAEP-Unpadding im Anwendungscode). Das gewrappte AES-Key-Byte-Muster ist in allen Faellen dasselbe.
</details>

<details>
<summary>3. Was passiert beim Decrypt, wenn du <em>nur</em> ein Byte des Ciphertexts veraenderst?</summary>

AES-GCM hat ein Authenticated-Tag am Ende. Die Verifikation des Tags scheitert, Bash meldet `bad decrypt`/`gcm decryption failed`, Java wirft `AEADBadTagException`. Genau dafuer ist GCM da: Modifikation wird mit hoher Wahrscheinlichkeit erkannt, nicht nur stillschweigend mitentschluesselt.
</details>
