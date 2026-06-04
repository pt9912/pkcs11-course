# 24 — ECDH + HKDF (Key Derivation ueber das HSM)

## Lernziele

Nach diesem Kapitel kannst du:

- den ECDH-Schluesselaustausch erklaeren und ihn vom RSA-Wrap aus Kapitel 13 abgrenzen.
- `C_DeriveKey(CKM_ECDH1_DERIVE)` einsetzen — Privkey bleibt im HSM, das Peer-Material ist nur das Public-Key-Point-Encoding.
- den Shared-Secret-Match-Beweis ueber beide Seiten nachvollziehen (Alice und Bob erhalten dieselben Bytes).
- HKDF (RFC 5869) als Extract+Expand verstehen und sehen, warum Salt-Default und Info-String byte-identisch sind, sobald die Spezifikation eingehalten wird.
- die SoftHSM-Limitierung rund um `CKM_HKDF_DERIVE` einordnen und die SunPKCS11-Eigenheit zu CKA_SENSITIVE-Override kennen.
- **(Bloom 5 — evaluate)** entscheiden, ob ECDH+HKDF, RSA-OAEP-Wrap (Kap. 13) oder ein KEM-Hybrid-Schema (Post-Quantum-Migration) fuer ein neues Protokoll-Design die richtige Wahl ist — und welche zwei Forward-Secrecy-/Performance-Eigenschaften die Entscheidung tragen.

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

## Sprach-Demos im Vergleich

| Sprache | ECDH-API | HKDF |
|---|---|---|
| Go (miekg/pkcs11) | `p.DeriveKey(session, CKM_ECDH1_DERIVE_params, priv, tmpl)` | `golang.org/x/crypto/hkdf` |
| C# (Pkcs11Interop) | `session.DeriveKey(mech, priv, tmpl)` mit `CkEcdh1DeriveParams` | `System.Security.Cryptography.HKDF.DeriveKey(...)` |
| Java (SunPKCS11/JCA) | `KeyAgreement.getInstance("ECDH", provider).generateSecret()` | Eigene Mac-basierte Implementierung von Extract+Expand |
| Kotlin | identisch zu Java | identisch zu Java |

Alle vier produzieren byte-identische AES-Keys aus denselben EC-Keys. Das ist eine harte Interop-Garantie: HKDF-RFC einhalten heisst Cross-Sprach-Determinismus.

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
