## 20 — Key Wrap und Unwrap (Backup, Escrow, Migration)

> **Didaktischer Pfad:** Vorher → [`19-ssh-mit-hsm.md`](19-ssh-mit-hsm.md) · Nachher → [`21-pin-management.md`](21-pin-management.md)

## Bevor du anfaengst — was vermutest du?

> Du sollst einen produktiven HSM-Key sichern. Du rufst `C_WrapKey` auf, bekommst ein Backup-Blob, legst es auf S3 mit Object Lock. Ist der Key damit safe gesichert?

Wahrscheinliche Vermutung: ja — Wrap heisst, der Key ist verschluesselt rausgekommen, ich habe ein Blob, das ich jederzeit unwrappen kann. Mentale Karte: **Wrap = Backup. Der Key ist jetzt eine Datei wie jede andere — sicher genug, weil verschluesselt**.

Diese Karte ueberspringt zwei Realitaeten. Erstens: was sichert das *Wrapping-Key* (KEK)? Wenn du den verlierst, ist das Blob mathematisch zwar verschluesselt, praktisch aber unwiederherstellbar — derselbe Single-Point-of-Failure, den du eigentlich vermeiden wolltest, nur eine Ebene tiefer. Zweitens: PKCS#11 §10.2.6 macht `CKA_EXTRACTABLE` zur **Einbahnstrasse**. Wer den Backup-Bedarf nicht *bei der Key-Erzeugung* miteinplant, kann spaeter nicht mehr wrappen — auch nicht mit einem KEK, der einen Tag spaeter eingespielt wird. Halte die "Wrap = Backup-erledigt"-Karte fest. Dieses Kapitel zeigt, dass Backup eine **Policy** ist und ein paar Bytes Krypto, nicht ein einzelner API-Call.

## Lernziele

Nach diesem Kapitel kannst du:

- den Unterschied zwischen "etwas mit einer Key verschluesseln" (Modul 13) und "einen Key wrappen" (`C_WrapKey`) erklaeren.
- ein `CKK_AES`-Key mit `CKA_EXTRACTABLE=true` anlegen — und sagen, warum man das in Produktion sehr bewusst entscheidet.
- ein backup-faehiges Blob ueber `CKM_AES_KEY_WRAP_PAD` erzeugen und mit demselben KEK wieder ins Token unwrappen.
- die HSM-Library- und Tool-Quirks rund um Unwrap-Templates (`CKA_VALUE_LEN`, fehlende SunPKCS11-Registrierung) einordnen.
- **(Bloom 6 — create)** eine KEK-Policy fuer ein gegebenes Setup **entwerfen**: welche Attribute, welche `CKA_WRAP_TEMPLATE`-Constraints, welcher Restore-Workflow, welche Mehraugen-Anforderung beim Unwrap — und gegen welche zwei realistischen Angriffsszenarien dieses Design verteidigt.

> **Geschaetzte Bearbeitungszeit:** ~75 min (Lesen 30 min + Bash + Go-Wrap-Demo 25 min + KEK-Policy-Skizze auf Papier 20 min). Die "Backup ist Policy, nicht ein API-Call"-Erkenntnis ist die schwerste mentale Aenderung in den Vertiefungsmodulen.

## Lab-Bezug

```bash
make gen-kek          # AES-256 KEK auf ID=06 mit CKA_WRAP/UNWRAP
make wrap-backup      # Bash: AES-Payload-Key erzeugen, verschluesseln, wrappen
make go-wrap-demo     # Vollstaendiger Wrap+Unwrap+Restore-Roundtrip in Go
make csharp-wrap-demo # Selbes in C#/Pkcs11Interop
```

Nachschlag zu KEK, Wrap/Unwrap, `CKA_*`, `CKK_*` und `CKM_*`: [Glossar](../docs/glossar.md).

## "Wrap einen Key" ≠ "Encrypt mit dem Key"

Modul 13 hat einen AES-Session-Key (Host-Speicher) per RSA-OAEP verschluesselt — das war pure **Daten**-Verschluesselung. Der "AES-Key" war fuer das Token nur ein Byte-Array.

`C_WrapKey` ist anders: der zu sichernde Key lebt **als PKCS#11-Objekt im Token**, mit `CKA_SENSITIVE=true`. Sein Plaintext-Wert ist via `C_GetAttributeValue(CKA_VALUE)` nicht abrufbar (`CKR_ATTRIBUTE_SENSITIVE`). `C_WrapKey` ist der **einzige** Weg, dieses Schluesselmaterial — verschluesselt — aus dem Token zu bekommen. Und nur, wenn `CKA_EXTRACTABLE=true` ist.

Use-Cases:
- **Backup**: KEK wrappt produktive Keys, Blobs liegen offline → Disaster Recovery.
- **Escrow / Key Recovery**: dieselbe Mechanik, anderes Risiko-Modell (regulatorischer Zugriff).
- **Cross-HSM-Migration**: HSM-A wrappt unter dem Pubkey von HSM-B, Blob wird transportiert, HSM-B unwrappt.
- **KMS-zu-Client-Delivery**: KMS generiert eine Data-Encryption-Key, wrappt unter Customer-Master-Key, schickt das gewrappte Blob.

## `CKA_EXTRACTABLE`: das Backup-Gate

Defaults sind je nach Tool unterschiedlich:

| Tool | Default `CKA_EXTRACTABLE` bei AES-Keygen |
|---|---|
| `pkcs11-tool --keygen` | **FALSE** (security-default) |
| miekg/pkcs11 + manuelle Attribute | konfigurierbar |
| Pkcs11Interop + manuelle Attribute | konfigurierbar |
| SunPKCS11 `KeyGenerator.generateKey()` | **FALSE** (security-default, ueberschreibbar via `attributes`-Block in der Provider-Config) |

Ein Key, der mit `CKA_EXTRACTABLE=false` erzeugt wurde, ist **fuer immer** nicht backup-faehig. PKCS#11 §10.2.6: das Attribut darf nur in eine Richtung wechseln (true → false), nie zurueck. Wer einen produktiven HSM-Schluessel ohne Backup-Strategie generiert, sitzt im Recovery-Fall in der Falle.

Im Bash-Demo legen wir den Payload-Key deshalb explizit mit `--extractable` an. Im Go/C#-Demo setzen wir `CKA_EXTRACTABLE=true` im Generate-Template. Im Java/Kotlin-Demo bekommt SunPKCS11 eine Provider-Config mit `attributes(generate, CKO_SECRET_KEY, CKK_AES) = { CKA_EXTRACTABLE = true; ... }`.

## KEK-Policy

Der KEK ist der **kritischste** Key in einer Backup-Strategie — alle gewrappten Blobs entfalten sich, wenn er kompromittiert wird. Empfohlene Attribute:

| Attribut | Wert | Begruendung |
|---|---|---|
| `CKA_TOKEN` | true | persistent, ueberlebt Sessions |
| `CKA_SENSITIVE` | true | Wert nicht via `C_GetAttributeValue` lesbar |
| `CKA_EXTRACTABLE` | **false** | KEK selbst wird nie gewrappt (sonst hat man dasselbe Problem rekursiv); muss separat per HSM-Backup gesichert werden |
| `CKA_WRAP` | true | darf andere Keys wrappen |
| `CKA_UNWRAP` | true | darf gewrappte Keys reimportieren |
| `CKA_ENCRYPT` / `CKA_DECRYPT` | **false** | KEK darf KEINE Daten ver-/entschluesseln — Use-Case-Trennung |
| `CKA_WRAP_TEMPLATE` | (optional) | begrenzt, WELCHE Attribute der unwrappte Key haben darf (z.B. nur `CKA_EXTRACTABLE=false`) |

`CKA_WRAP_TEMPLATE` ist die HSM-Variante von "type-safety": man kann erzwingen, dass aus einem gewrappten Blob nur Keys mit bestimmten Eigenschaften reimportiert werden duerfen. So verhindert man, dass ein Angreifer das Blob unwrappt UND gleich `CKA_EXTRACTABLE=true` mitliefert. SoftHSM unterstuetzt die Constraint nicht voll — produktive HSMs (Thales/AWS CloudHSM) tun das.

Die KEK-Policy aus der Tabelle ist seit 0.16.0 **auch im Lab erzwungen**: `make gen-kek` legt den KEK ueber `lab/go/pkcs11-keygen` mit explizitem CKA-Template an, `CKA_WRAP/UNWRAP=true` und `CKA_ENCRYPT/DECRYPT/SIGN/VERIFY=false`. `make validate-key-usage` prueft das. Versucht man den KEK fuer `C_Encrypt` zu nutzen, antwortet SoftHSM jetzt mit `CKR_KEY_FUNCTION_NOT_PERMITTED` — vorher (bis 0.15.x) ging das stillschweigend durch.

## Mechanism-Wahl: AES-KEY-WRAP-PAD vs AES-KEY-WRAP

| Mechanism | RFC | Erlaubte Key-Laengen | Output-Overhead |
|---|---|---|---|
| `CKM_AES_KEY_WRAP` | 3394 | nur Vielfache von 8 Byte (also AES-128/192/256 ok) | +8 Byte |
| `CKM_AES_KEY_WRAP_PAD` | 5649 | beliebig (1+ Byte) | +8 bis +15 Byte |
| `CKM_AES_KEY_WRAP_KWP` | 5649 (alias) | wie WRAP_PAD | — |

Fuer AES-256 (32 Byte) sind beide moeglich. Fuer GENERIC_SECRET-Keys mit nicht-8-Byte-Vielfacher Laenge braucht es WRAP_PAD oder WRAP_KWP.

## SoftHSM-Quirk: pkcs11-tool kann nicht unwrappen

`pkcs11-tool --unwrap` setzt im Unwrap-Template **immer** `CKA_VALUE_LEN`. SoftHSM 2.6 lehnt das bei AES-Key-Wrap mit `CKR_ATTRIBUTE_READ_ONLY` ab, weil die Laenge bereits im Blob enthalten ist. Die Sprach-Demos (Go, C#) setzen das Template selbst und lassen `CKA_VALUE_LEN` weg — funktioniert sauber.

**Konsequenz fuers Lab**: Der Bash-Pfad endet beim Erzeugen des Backup-Blobs (`make wrap-backup`). Restore-Roundtrip ist nur ueber die Sprach-Demos zu sehen.

## SunPKCS11-Quirk: keine Key-Wrap-Cipher-Services

OpenJDK 21.0.11 (Debian 13) registriert ueber SunPKCS11 **keine** `AESWrap`/`AES/KW/*`/`AES/KWP/*`-Cipher-Transformationen — obwohl SoftHSM `CKM_AES_KEY_WRAP` und `CKM_AES_KEY_WRAP_PAD` advertised. JCA-`Cipher.wrap()`/`unwrap()` faellt deshalb mit `NoSuchAlgorithmException` aus.

Workarounds in Produktion:
- Neuerer OpenJDK (≥ 23 hat AES/KW/NoPadding fix registriert)
- BouncyCastle-JCE-Provider — supportet AESWrap, kann aber nicht auf HSM-residente Keys zugreifen (braucht Key-Material)
- Direkter Zugriff via `sun.security.pkcs11`-Internals (nicht portabel, openjdk-spezifisch)

Daraus folgt fuer dieses Lab: **kein Java/Kotlin-Wrap-Demo**. Das Modul deckt Bash + Go + C# ab und dokumentiert die JCA-Luecke. Wer einen produktiven JVM-Backup-Workflow bauen muss, geht ueber den IAIK-PKCS#11-Wrapper oder ein eigenes JNI-Binding.

## Praxis-Tipp: Wrap-Operationen auditen

Jede `C_WrapKey`-Operation auf einem produktiven KEK ist ein potenzieller Daten-Leak (gewrapptes Blob = Backup einer Identity). HSM-Audit-Logs sollten:

- Zeitstempel + Caller (PIN/User)
- Welcher KEK (CKA_LABEL/CKA_ID)
- Welcher Source-Key (CKA_LABEL/CKA_ID)
- Mechanism + Parameter
- Output-Hash (NICHT das Blob selbst, sonst doppelte Exposure)

Bei Cloud-HSMs (AWS CloudHSM, GCP Cloud HSM, Azure Dedicated HSM) loggt der Service jede `C_WrapKey`-Operation automatisch. Bei On-Prem-HSMs muss man den Audit-Tail explizit aktivieren.

## Eigenexperiment

- Generiere im Bash-Skript den payload-key ohne `--extractable` und beobachte `CKR_KEY_UNEXTRACTABLE` beim Wrap-Versuch.
- Aendere im Go-Demo den Unwrap-Mechanism auf `CKM_AES_KEY_WRAP` (ohne PAD). Es funktioniert mit AES-256 (Vielfaches von 8), schlaegt aber bei GENERIC_SECRET 17 Byte fehl.
- Verschluessele eine Datei direkt mit dem KEK (z.B. `pkcs11-tool --encrypt --mechanism AES-CBC-PAD --id 06 --iv ...`). Seit 0.16.0 antwortet das Token mit `CKR_KEY_FUNCTION_NOT_PERMITTED`, weil der KEK strikt `CKA_ENCRYPT=false` hat — genau die Use-Case-Trennung, um die es geht. Zum Gegentest: temporaer `--encrypt` im Generate-Helper hinzufuegen, neu generieren, Versuch wiederholen.

Strukturierte Aufgaben in [`exercises/14-key-wrap.md`](../exercises/14-key-wrap.md).

## Selbsttest

<details>
<summary>1. Warum ist <code>CKA_EXTRACTABLE</code> die wichtigste Backup-Strategie-Entscheidung — und wie wirkt PKCS#11 §10.2.6?</summary>

`CKA_EXTRACTABLE=true` ist Voraussetzung fuer `C_WrapKey`. PKCS#11 §10.2.6 erlaubt nur den Uebergang `true → false`, nie zurueck. Ein produktiver Key, der ohne Backup-Strategie als `CKA_EXTRACTABLE=false` erzeugt wurde, ist permanent nicht backupbar. Die Entscheidung "extractable oder nicht" faellt bei der Key-Generierung und ist endgueltig.
</details>

<details>
<summary>2. Welche zwei <code>CKA_*</code>-Attribute muss der KEK selbst NICHT haben, damit Use-Case-Trennung greift?</summary>

`CKA_ENCRYPT=false` und `CKA_DECRYPT=false`. Der KEK darf nur `CKA_WRAP=true`/`CKA_UNWRAP=true`. Wer Daten direkt mit dem KEK verschluesseln liesse, mischt Use-Cases — der KEK ist dann gleichzeitig Backup-Tool und Daten-Cipher, und Audit-Logs werden unscharf. SoftHSM ab 0.16.0 erzwingt das im Lab via `pkcs11-keygen`-Template.
</details>

<details>
<summary>3. <code>pkcs11-tool --unwrap</code> bricht auf SoftHSM mit <code>CKR_ATTRIBUTE_READ_ONLY</code>. Warum, und welcher Pfad funktioniert?</summary>

`pkcs11-tool --unwrap` setzt im Template **immer** `CKA_VALUE_LEN`. SoftHSM 2.6 lehnt das bei AES-Key-Wrap ab — die Laenge ist bereits im Blob enthalten, redundante Angabe ist Spec-Verletzung. Die Sprach-Demos (Go, C#) bauen das Template selbst und lassen `CKA_VALUE_LEN` weg, deshalb funktioniert der Restore-Roundtrip dort.
</details>

<details>
<summary>4. <strong>(evaluate)</strong> Du sollst die KEK-Policy fuer ein eIDAS-Signing-HSM entwerfen. Zwei Vorschlaege liegen vor: (A) ein KEK mit <code>CKA_WRAP=true</code>, <code>CKA_UNWRAP=true</code> auf dem Produktiv-HSM, gewrappte Backups im S3-Bucket; (B) zwei separate Keys: ein Wrap-Only-KEK im Produktiv-HSM und ein Unwrap-Only-KEK im Restore-HSM. Welche zwei Compliance-/Operations-Achsen entscheiden, und welcher Entwurf gewinnt — und warum ist die naheliegende Antwort "A spart Hardware" hier irrefuehrend?</summary>

Entwurf **B** gewinnt. Achse 1 — **Mehraugen/Separation of Duty**: ein KEK, der gleichzeitig wrappen und unwrappen kann, erlaubt es einem einzelnen kompromittierten Operator, ein Backup *und* dessen Restore durchzufuehren — die gesamte Backup-Kette bricht ohne externe Pruefung. Wrap-Only/Unwrap-Only erzwingt physischen Wechsel der HSM-Partition fuer Restore. Achse 2 — **Audit-Trail-Klarheit**: getrennte Keys schreiben unterschiedliche Operationen in den Audit-Log, ein Restore-Vorgang ist als solcher erkennbar; bei (A) ist `C_UnwrapKey` mit demselben KEK ein normaler "der Operator wollte einen Backup-Test machen"-Pattern und faellt nicht auf. Die "A spart Hardware"-Antwort uebersieht, dass ein Restore-HSM in einer eIDAS-Umgebung ohnehin vorhanden sein muss (Disaster Recovery Site) — die "Hardware-Ersparnis" ist eine Phantom-Ersparnis. Der echte Cost-Treiber ist nicht das zweite HSM, sondern die Operator-Trainings und das Mehraugen-Ceremony-Skript. Beides aber ist Compliance-Pflicht, kein Spar-Knopf.
</details>
