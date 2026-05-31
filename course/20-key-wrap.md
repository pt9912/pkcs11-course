## 20 — Key Wrap und Unwrap (Backup, Escrow, Migration)

## Lernziele

Nach diesem Kapitel kannst du:

- den Unterschied zwischen "etwas mit einer Key verschluesseln" (Modul 13) und "einen Key wrappen" (`C_WrapKey`) erklaeren.
- ein `CKK_AES`-Key mit `CKA_EXTRACTABLE=true` anlegen — und sagen, warum man das in Produktion sehr bewusst entscheidet.
- ein backup-faehiges Blob ueber `CKM_AES_KEY_WRAP_PAD` erzeugen und mit demselben KEK wieder ins Token unwrappen.
- die HSM-Library- und Tool-Quirks rund um Unwrap-Templates (`CKA_VALUE_LEN`, fehlende SunPKCS11-Registrierung) einordnen.

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

**Lab-Realitaet:** die obige Tabelle beschreibt die didaktische Soll-Policy. SoftHSM 2.6 setzt aus `pkcs11-tool --keygen --usage-wrap` aber ein breites Default-Profil; der KEK kommt mit `encrypt, decrypt, sign, verify, wrap, unwrap` raus, also alles, nicht nur Wrap. Die Use-Case-Trennung wird im Lab dadurch nicht erzwungen — die Aussagen "KEK darf KEINE Daten ver-/entschluesseln" gelten als Policy-Statement, nicht als beobachtbarer Lab-Effekt. Hintergrund und Roadmap-Plan fuer native CKA-Templates: siehe [Kapitel 13](13-verschluesselung.md#softhsm-realitaet--usage--ist-intent-kein-constraint).

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
- Verschluessele eine Datei direkt mit dem KEK (z.B. `pkcs11-tool --encrypt --mechanism AES-CBC-PAD --id 06 --iv ...`). Im Lab funktioniert das ohne weitere Aenderungen, weil SoftHSM den KEK breit ausstattet (siehe Disclaimer oben). Das Experiment zeigt die Konsequenz fehlender Use-Case-Trennung: ein Key, der wrappen UND verschluesseln darf, leakt bei Kompromittierung beides.

Strukturierte Aufgaben in [`exercises/14-key-wrap.md`](../exercises/14-key-wrap.md).
