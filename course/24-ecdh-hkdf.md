# 24 — ECDH + HKDF (Key Derivation ueber das HSM)

> **Didaktischer Pfad:** Vorher → [`23-random.md`](23-random.md) · Nachher → [`25-rfc3161-timestamps.md`](25-rfc3161-timestamps.md)

## Lernziele

Nach diesem Kapitel kannst du:

- den ECDH-Schluesselaustausch erklaeren und ihn vom RSA-Wrap aus Kapitel 13 abgrenzen.
- `C_DeriveKey(CKM_ECDH1_DERIVE)` einsetzen — Privkey bleibt im HSM, das Peer-Material ist nur das Public-Key-Point-Encoding.
- den Shared-Secret-Match-Beweis ueber beide Seiten nachvollziehen (Alice und Bob erhalten dieselben Bytes).
- HKDF (RFC 5869) als Extract+Expand verstehen und sehen, warum Salt-Default und Info-String byte-identisch sind, sobald die Spezifikation eingehalten wird.
- die SoftHSM-Limitierung rund um `CKM_HKDF_DERIVE` einordnen und die SunPKCS11-Eigenheit zu CKA_SENSITIVE-Override kennen.
- **(Bloom 5 — evaluate)** entscheiden, ob ECDH+HKDF, RSA-OAEP-Wrap (Kap. 13) oder ein KEM-Hybrid-Schema (Post-Quantum-Migration) fuer ein neues Protokoll-Design die richtige Wahl ist — und welche zwei Forward-Secrecy-/Performance-Eigenschaften die Entscheidung tragen.

> **Geschaetzte Bearbeitungszeit:** ~75 min (Lesen + Bash-Worked-Example mit Alice/Bob 30 min + Sprach-Faded 25 min + Reflexion 20 min). Vier-Sprach-Konsistenz auf Byte-Ebene ist die Garantie, an der man HKDF-Korrektheit empirisch festmacht.

## Lab-Bezug

```bash
make gen-ecdh-keys       # Alice + Bob EC-P256 ueber den pkcs11-keygen-Helper (--sign --derive)
make ecdh-derive         # Bash-Pfad: Go-Demo, KDF=hkdf default; PKCS11_ECDH_KDF=raw fuer Vergleich
make go-ecdh-demo        # identisch zum Bash-Pfad, separates Target zur Konvention
make csharp-ecdh-demo    # Pkcs11Interop + System.Security.Cryptography.HKDF
make java-ecdh-demo      # SunPKCS11 KeyAgreement("ECDH") + HKDF host-side
make kotlin-ecdh-demo    # Kotlin-Spiegel der Java-Variante
make issue-ecdh-certs    # Plumbing nur fuer Java/Kotlin (SunPKCS11-Alias-Sichtbarkeit)
```

Begriffe (ECDH, KDF, KEM, HKDF, X25519) und Praefixe (`CKM_`, `CKA_`): [Glossar](../docs/glossar.md), [EC-Grundlagen](../docs/elliptische-kurven.md).

## Warum ECDH statt RSA-Wrap?

Modul 13 hat einen AES-Session-Key per RSA-OAEP gewrappt: Sender hat den Pubkey, verschluesselt damit. Funktional ok, aber:

- **Forward Secrecy** fehlt. Wer RSA-Privkey kompromittiert, dekryptiert alle bisherigen Sessions.
- **Asymmetrisch in den Performance-Kosten**: RSA-Decrypt ist langsam; bei vielen Sessions wird das Bottleneck.
- **Hybrid-Migration**: Post-Quantum-Verfahren laufen praktisch alle ueber KEMs (Encapsulate/Decapsulate). Wer schon ECDH spricht, hat die "Shared-Secret-aus-Public-Material"-Architektur und tauscht spaeter nur den Mechanismus.

ECDH ist die Form, in der TLS 1.3 alle Schluessel ableitet. Das hat einen Grund.

## ECDH in einem Satz

```text
priv_A * pub_B == priv_B * pub_A  (auf der gleichen Kurve)
```

Beide Parteien berechnen denselben Kurvenpunkt, ohne ihre Privkeys preiszugeben. Die x-Koordinate dieses Punktes ist der Shared Secret (32 Byte bei P-256). Mathematischer Hintergrund: [Kryptografie auf elliptischen Kurven](../docs/elliptische-kurven.md).

## Im PKCS#11-Aufruf

Eine `C_DeriveKey`-Operation pro Seite:

```text
priv_handle = alice-ec-key (CKO_PRIVATE_KEY, CKK_EC, CKA_DERIVE=true)
peer_data   = Bobs EC_POINT-Bytes (CKA_EC_POINT, DER-OCTET-STRING)

mechanism   = CKM_ECDH1_DERIVE
parameter   = CK_ECDH1_DERIVE_PARAMS {
                kdf = CKD_NULL,                  # roh, ohne Token-KDF
                pSharedData = NULL,
                pPublicData = peer_data
              }

template    = { CKA_CLASS=SECRET_KEY, CKA_KEY_TYPE=GENERIC_SECRET,
                CKA_VALUE_LEN=32, CKA_EXTRACTABLE=true (Lab) }

derived_handle = C_DeriveKey(session, mechanism, priv_handle, template)
```

Reale Anwendungen lassen `CKA_EXTRACTABLE=false` und nutzen den abgeleiteten Schluessel direkt fuer weitere PKCS#11-Operationen. Das Lab extrahiert die Bytes (`C_GetAttributeValue(CKA_VALUE)`), um den Match-Beweis explizit zu zeigen.

**Portabilitaets-Hinweis zu `pPublicData`:** PKCS#11 v2.40 §2.3.7 ist hier historisch zweideutig — SoftHSM, Utimaco und AWS CloudHSM akzeptieren das DER-OCTET-STRING-Wrapping wie es `CKA_EC_POINT` direkt liefert (bei P-256: `04 41 04 || X || Y`, 67 Byte). **Aeltere Thales-Luna-Firmwares** wollen das nackte EC-Point-Encoding (`04 || X || Y`, 65 Byte) und lehnen den DER-Prefix ab. Wer die Demo auf einer Luna laufen laesst und `CKR_DATA_INVALID` bekommt, muss die ersten zwei Bytes (`04 41`) abschneiden. Im SoftHSM-Lab unauffaellig, in der Cloud-Migration gut zu wissen.

## KDF: zwei Pfade im Lab

Das rohe Shared Secret hat zwar 256 Bit Entropie, ist aber **strukturiert** (die x-Koordinate eines bestimmten Punktes, keine uniforme Verteilung). Fuer kryptographische Verwendung gehoert noch ein KDF dazwischen.

**Pfad `--kdf=hkdf` (Default, RFC 5869):** Host-side HKDF-SHA256 mit `info="ECDH-Lab-V1"`, `salt=null` (= HashLen Nullbytes per Konvention). Funktional korrekt; das Lab implementiert HKDF in jeder Sprache so, dass alle vier Demos byte-identische AES-Keys produzieren (`8e8922dcb79a3dcf...`).

**Pfad `--kdf=raw`:** Shared Secret direkt als AES-256-Key. Funktional gueltige AES-Bytes, aber kein Standard-Protokoll-Pattern — TLS 1.2 ECDHE laeuft anders, dort wird der Premaster Secret durch TLS-PRF (P_SHA256) zu Master Secret expandiert. Eine fairere Analogie waere ECIES mit `KDF=identity`. Im Lab steht der `raw`-Pfad nur als Anschauung, um den Unterschied zwischen ECDH-Output und KDF-Output sichtbar zu machen.

**Was SoftHSM nicht hat:** `CKM_HKDF_DERIVE` ist eine PKCS#11 v3.0-Ergaenzung; SoftHSM 2.x zielt auf v2.40 und implementiert das nicht. Auf realen HSMs (PCIe-HSM, Cloud-HSM, HLSM) laeuft HKDF on-Token; Anwendung schickt nur Info/Salt-Bytes und bekommt einen neuen Key-Handle zurueck. Im Lab kompensieren wir host-side.

Der Lab-Sourcecode dokumentiert das in der HKDF-Stelle.

## SunPKCS11-Eigenheit: CKA_SENSITIVE-Override

`KeyAgreement.getInstance("ECDH", sunPkcs11).generateSecret()` ruft intern `C_DeriveKey` mit einem Default-Template auf, das `CKA_SENSITIVE=true` setzt. Anschliessend versucht SunPKCS11, `CKA_VALUE` zu lesen — und scheitert mit `CKR_ATTRIBUTE_SENSITIVE`.

Workaround in der `softhsm.cfg` der Java/Kotlin-Demos:

```text
attributes(generate, CKO_SECRET_KEY, CKK_GENERIC_SECRET) = {
  CKA_SENSITIVE = false
  CKA_EXTRACTABLE = true
}
```

Das ueberschreibt das Derive-Template fuer alle Generic-Secret-Keys auf der Session. In Produktion **nicht** machen — dort nutzt man das abgeleitete Material direkt ueber Folge-Operationen (z.B. `Cipher.init` mit dem SecretKey aus `generateSecret("AES")`), ohne `byte[]`-Extraktion.

## Java/Kotlin und der Cert-Plumbing-Hack

Wie in Kapitel 6 und 22: SunPKCS11s `KeyStore.aliases()` zeigt nur Private-Key-Aliase, fuer die ein Zertifikat mit gleicher `CKA_ID` existiert. ECDH selbst braucht keine Zertifikate, aber `keyStore.getKey("alice-ec-key")` braucht den sichtbaren Alias.

`make issue-ecdh-certs` (Skript `84-import-ecdh-certs.sh`) loest das ueber zwei self-signed X.509-Certs ueber die `pkcs11-engine`. Go/C#/Bash-Demos brauchen die Certs nicht, sie lesen `CKA_EC_POINT` direkt.

## Sprach-Demos im Vergleich: Ueberblick

Vier Demos, identische byte-Output auf demselben EC-Keypaar. Wir arbeiten den Go-Pfad vollstaendig durch und lassen die drei anderen als Faded Examples folgen.

| Sprache | ECDH-API (kurz) | HKDF |
|---|---|---|
| Go (miekg/pkcs11) | `p.DeriveKey(session, CKM_ECDH1_DERIVE_params, priv, tmpl)` | `golang.org/x/crypto/hkdf` |
| C# (Pkcs11Interop) | `session.DeriveKey(mech, priv, tmpl)` mit `CkEcdh1DeriveParams` | `System.Security.Cryptography.HKDF.DeriveKey(...)` |
| Java (SunPKCS11/JCA) | `KeyAgreement.getInstance("ECDH", provider).generateSecret()` | Eigene Mac-basierte Implementierung von Extract+Expand |
| Kotlin | identisch zu Java | identisch zu Java |

### Worked Example: der Go-Pfad (vollstaendig)

```bash
make gen-ecdh-keys      # Alice + Bob EC-P256 (--sign --derive)
make go-ecdh-demo       # ECDH-Derive + HKDF + AES-GCM-Roundtrip
```

**Schritt 1 — Voraussetzungen.** `make gen-ecdh-keys` legt zwei sortenreine EC-Keypaare an: `alice-ec-key` (ID=10) und `bob-ec-key` (ID=11), beide P-256. `CKA_DERIVE=true` ist Pflicht — sonst `CKR_KEY_FUNCTION_NOT_PERMITTED` beim Derive. Sortenreinheit aus 0.16.0 garantiert das.

**Schritt 2 — Bob's EC-Point holen.** Auf der Alice-Seite brauchen wir Bob's Public Key. `pubBob, _ := p.GetAttributeValue(session, bobPubHandle, []*pkcs11.Attribute{pkcs11.NewAttribute(pkcs11.CKA_EC_POINT, nil)})`. Das liefert ~67 Byte DER-OCTET-STRING (`04 41 04 || X || Y` bei P-256). Kein Login noetig, Pubkeys sind `CKA_PRIVATE=false`.

**Schritt 3 — Derive: `C_DeriveKey(CKM_ECDH1_DERIVE)`.** Mit Alice's Privkey als Input und Bob's EC-Point als Parameter:

```go
params := []byte{}                          // kein SharedData
ecdh := pkcs11.NewECDH1DeriveParams(pkcs11.CKD_NULL, params, pubBob)
mech := pkcs11.NewMechanism(pkcs11.CKM_ECDH1_DERIVE, ecdh)
template := []*pkcs11.Attribute{
    pkcs11.NewAttribute(pkcs11.CKA_CLASS, pkcs11.CKO_SECRET_KEY),
    pkcs11.NewAttribute(pkcs11.CKA_KEY_TYPE, pkcs11.CKK_GENERIC_SECRET),
    pkcs11.NewAttribute(pkcs11.CKA_VALUE_LEN, 32),
    pkcs11.NewAttribute(pkcs11.CKA_EXTRACTABLE, true),   // nur Lab!
}
sharedHandle, _ := p.DeriveKey(session, []*pkcs11.Mechanism{mech}, alicePrivHandle, template)
```

Das HSM rechnet `priv_A * pub_B` und schreibt das 32-Byte-Shared-Secret in ein neues SECRET_KEY-Objekt. Der Alice-Privkey hat den HSM nicht verlassen.

**Schritt 4 — Shared Secret extrahieren (Lab-only).** `value, _ := p.GetAttributeValue(session, sharedHandle, []*pkcs11.Attribute{pkcs11.NewAttribute(pkcs11.CKA_VALUE, nil)})`. Wir bekommen 32 Byte. **Diese Extraktion ist nur fuer den Match-Beweis** — in Produktion bleibt `CKA_EXTRACTABLE=false` und wir nutzen `sharedHandle` direkt fuer Folge-Operationen.

**Schritt 5 — HKDF-Expand auf Host-Seite.** `hkdf.New(sha256.New, sharedSecret, nil, []byte("ECDH-Lab-V1"))` plus `io.ReadFull(reader, aesKey[:])`. SoftHSM kann `CKM_HKDF_DERIVE` (PKCS#11 v3.0) nicht — wir simulieren host-side mit byte-identischem Output.

**Schritt 6 — AES-GCM-Roundtrip als Cross-Check.** Alice verschluesselt `Hello Bob`, Bob (mit demselben Code, andere Seite) leitet das **identische** Shared Secret ab und entschluesselt. Beweis: Alice's Shared Secret == Bob's Shared Secret == HKDF-Input == AES-Key.

Der gewonnene Schema-Kern: **ECDH liefert einen 32-Byte-Shared-Secret-Handle im HSM. Was die Anwendung damit macht (extrahieren, weiter-deriven, direkt nutzen) ist eine Konfigurations-Frage — der eigentliche Crypto-Boundary wurde im `C_DeriveKey` ueberwunden.**

### Faded Examples — die drei Sprach-Pfade

Pro Pfad: kurze Tabellen-Beschreibung und **drei Leitfragen**.

#### C# (`Pkcs11EcdhDemo`)

| ECDH-Schritt | HKDF |
|---|---|
| `session.DeriveKey(mech, alicePriv, template)` mit `CkEcdh1DeriveParams(CKD_NULL, sharedData=null, publicData=bobPoint)`. Symmetrisch zum Go-Pfad, andere API-Wrapper. | `System.Security.Cryptography.HKDF.DeriveKey(HashAlgorithmName.SHA256, ikm, len, salt: null, info: "ECDH-Lab-V1")` — eine Zeile. |

Leitfragen:

1. **Worin unterscheidet sich `CkEcdh1DeriveParams` strukturell von Go's `NewECDH1DeriveParams`?** Beide kapseln dieselbe `CK_ECDH1_DERIVE_PARAMS`-C-Struktur — verfolge, was auf den C-API-Aufruf hinaufgeht und was die Wrapper-Sprache draufpackt.
2. **Warum brauchst du in C# **kein** `CKA_VALUE_LEN`-Attribut im Template?** (Tipp: Pkcs11Interop setzt einen sinnvollen Default; Go zwingt dich, alles explizit anzugeben.)
3. **`HKDF.DeriveKey` in .NET hat einen anderen Parameter-Namen (`info` vs Go's `info`) — aber der Output ist byte-identisch. Welche RFC-Stelle garantiert das?**

#### Java (`pkcs11-ecdh-demo`)

| ECDH-Schritt | HKDF |
|---|---|
| `KeyAgreement ka = KeyAgreement.getInstance("ECDH", sunPkcs11Provider); ka.init(alicePriv); ka.doPhase(bobPubKey, true); SecretKey shared = ka.generateSecret("Generic")`. JCA versteckt `C_DeriveKey` komplett. | Java hat keine eingebaute HKDF — der Lab-Code implementiert Extract+Expand selbst via `Mac.getInstance("HmacSHA256")`. |

Leitfragen:

1. **Warum braucht der Java-Pfad den `attributes(...)`-Override in der `softhsm.cfg`, der Go-Pfad aber nicht?** Verbinde mit der SunPKCS11-Defaults-Diskussion oben.
2. **Bob's Pubkey kommt im Java-Pfad aus dem KeyStore-Cert, nicht direkt aus dem Token. Wie wuerde es ohne `make issue-ecdh-certs` aussehen?** (Tipp: leerer Alias, kein Pubkey.)
3. **Die HKDF-Implementierung ist ~30 Zeilen Code im Lab. Welche zwei Schritte (Extract, Expand) entsprechen welchen `Mac.doFinal`-Aufrufen?** Zaehl die HMAC-Operationen pro Schritt.

#### Kotlin (`pkcs11-ecdh-demo`)

| ECDH-Schritt | HKDF |
|---|---|
| Identisch zu Java, syntaktisch Kotlin-idiomatisch. | Identisch zu Java. |

Leitfragen:

1. **Welche Stelle im Java-Code muesste die Kotlin-Demo am wenigsten anpassen — und welche am meisten?** (Antwort: KeyAgreement-API ist gleich; Stream-/Buffer-Handling oft kotlin-idiomatisch geschrieben.)
2. **Wuerde ein Coroutine-Pattern hier etwas bringen — und wenn ja, an welcher Stelle?** (Tipp: Alice und Bob koennten parallel auf demselben Token deriven; siehe Kap. 17.)
3. **Wo siehst du, dass HKDF deterministisch ist — Lauf der Kotlin-Demo, dann Lauf der Java-Demo: identische Bytes? Erklaere warum.**

### Wenn alle vier Pfade im Kopf zusammenkommen

Vier-Sprach-Determinismus auf Byte-Ebene ist der Lakmustest fuer HKDF-Korrektheit. Wer in einer Sprache `e90e368d95f68725...` als Shared Secret und `8e8922dcb79a3dcf...` als AES-Key sieht, muss in allen anderen dieselben Bytes sehen — sonst hat irgendwo eine Implementierung `salt=null` als "leer-string" statt als "HashLen Nullbytes" interpretiert. Genau das ist die Falle, die RFC 5869 §2.2 als "salt may be null" erlaubt, aber selten korrekt umgesetzt wird.

## Eigenexperiment

- Setze `PKCS11_ECDH_KDF=raw` und lass `make ecdh-derive` laufen. Beobachte: AES-Key entspricht den ersten 32 Byte des Shared Secret (`e90e368d95f68725...`).
- Ersetze im Go-Demo den `info`-String `"ECDH-Lab-V1"` durch `"ECDH-Lab-V2"`. AES-Key aendert sich vollstaendig — HKDF ist info-sensitive.
- Versuche, einen der ECDH-Keys ohne `--derive` neu zu erzeugen (`make clean-tokens` + manuelle pkcs11-keygen-Aufrufe ohne `--derive`). `make ecdh-derive` scheitert mit `CKR_KEY_FUNCTION_NOT_PERMITTED` — die strikte CKA-Trennung aus 0.16.0 wirkt auch hier.

Strukturierte Aufgaben in [`exercises/18-ecdh-hkdf.md`](../exercises/18-ecdh-hkdf.md).

## Selbsttest

<details>
<summary>1. Warum hat ECDH Forward Secrecy, RSA-Wrap (Kap. 13) aber nicht?</summary>

Bei RSA-Wrap nutzt der Sender den langlebigen RSA-Pubkey des Empfaengers. Wer den RSA-Privkey spaeter kompromittiert, kann jede aufgezeichnete Vergangenheitsoperation entschluesseln. Bei ECDH-Ephemeral (TLS 1.3-Stil) wird pro Session ein neues EC-Keypair generiert; nach der Session werden die ephemeren Keys verworfen — Vergangenheitsoperationen bleiben damit auch nach Privkey-Kompromittierung sicher. Im Lab nutzen wir aus didaktischen Gruenden statische ECDH-Keys (Alice/Bob), die Mechanik des ephemer-Patterns waere dieselbe.
</details>

<details>
<summary>2. Was ist der Unterschied zwischen "Shared Secret" und "AES-Key" — und warum brauchst du HKDF dazwischen?</summary>

Das ECDH-Shared-Secret ist die x-Koordinate eines Kurvenpunkts — 256 Bit Entropie, aber **strukturiert**, nicht uniform verteilt. Direkt als AES-Key zu nutzen waere mathematisch gueltig, aber kein Standard-Pattern. HKDF (Extract+Expand) macht aus dem strukturierten Input einen uniform verteilten, kontext-getaggten Key. Der `info`-String bindet den Key an einen Verwendungskontext — `info="ECDH-Lab-V1"` produziert einen anderen Key als `info="ECDH-Lab-V2"`, das gleiche Shared Secret vorausgesetzt.
</details>

<details>
<summary>3. SunPKCS11 macht beim ECDH-Derive ein <code>CKR_ATTRIBUTE_SENSITIVE</code>. Was ist der Workaround, und warum nur fuer das Lab?</summary>

`KeyAgreement.getInstance("ECDH", sunPkcs11).generateSecret()` ruft `C_DeriveKey` mit `CKA_SENSITIVE=true` und versucht dann `CKA_VALUE` zu lesen. Lab-Workaround: `attributes(generate, CKO_SECRET_KEY, CKK_GENERIC_SECRET) = { CKA_SENSITIVE=false, CKA_EXTRACTABLE=true }` in der `softhsm.cfg`. **Nur fuer das Lab**, weil die Demo den Match-Beweis ueber Byte-Vergleich braucht. In Produktion nutzt man den abgeleiteten Schluessel direkt ueber `Cipher.init` mit dem SecretKey-Handle aus `generateSecret("AES")`, ohne Byte-Extraktion — dann bleibt `CKA_SENSITIVE=true` und alles ist sauber.
</details>

<details>
<summary>4. <strong>(evaluate)</strong> Du sollst zwischen drei Key-Establishment-Pfaden fuer ein neues Document-Sharing-System waehlen: (A) RSA-OAEP-Wrap (Kap. 13), (B) statisches ECDH + HKDF (dieses Kapitel), (C) ephemerales ECDH + HKDF (TLS-1.3-Stil). Welcher gewinnt fuer "asynchrones Document-Sharing zwischen Org-Boundaries" — und welcher gewinnt fuer "interaktive Session zwischen zwei Endpoints"? Welcher Faktor ist in beiden Faellen der ausschlaggebende?</summary>

Async-Cross-Org: **(A)** RSA-OAEP-Wrap. Ephemerales ECDH braucht beide Endpunkte gleichzeitig online; bei einer Document-Drop-Architektur (Sender wrappt jetzt, Empfaenger entschluesselt naechste Woche) ist das nicht gegeben. Interaktive Session: **(C)** ephemerales ECDH, weil Forward Secrecy (siehe Frage 1) — wer eine alte Session-Mitschrift hat und spaeter den Server-Privkey kompromittiert, kann nichts entschluesseln. (B) statisches ECDH ist der schlechteste Kompromiss: keine Forward Secrecy, aber doppelte Public-Key-Infrastruktur (beide Seiten brauchen langlebige EC-Keys mit Identitaetsbindung). Der gemeinsame Faktor in beiden Wahlen ist **Liveness-Erfordernis** des Protokolls — RSA-Wrap kommt mit Sender-allein-aktiv aus, ECDH braucht zwei Punkte. Performance ist nebensaechlich (alle drei sind im Millisekunden-Bereich), Forward Secrecy ist der eigentliche Differenzierungs-Anker.
</details>
