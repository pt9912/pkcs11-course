# 11 — ECDSA und RSA-PSS

## Lernziele

Nach diesem Kapitel kannst du:

- RSA-PKCS#1-v1.5, RSA-PSS und ECDSA unterscheiden.
- PSS-Parameter wie Hash, MGF und Salt-Laenge konsistent setzen.
- ECDSA-Signaturencoding fuer OpenSSL korrekt behandeln.
- entscheiden, welcher Mechanism fuer neue Systeme sinnvoll ist.

## Lab-Bezug

Passende Targets:

```bash
make gen-ec
make sign-ec
make verify-ec
make sign-pss
```

## Warum nicht nur RSA-PKCS#1-v1.5?

`SHA256-RSA-PKCS` ist weit verbreitet, aber für neue Systeme empfehlen NIST und BSI in der Regel:

- **RSA-PSS** statt RSA-PKCS#1-v1.5, weil PSS einen Sicherheitsbeweis und randomisiertes Padding hat.
- **ECDSA** mit Kurven wie `secp256r1` (NIST P-256) oder `secp384r1`, weil EC-Keys deutlich kleiner und Operationen schneller sind.

Ein HSM-Kurs ist unvollständig, ohne beide Varianten praktisch zu zeigen.

## ECDSA im Lab

Key erzeugen:

```bash
make gen-ec
```

Das Skript legt `ec-signing-key` mit `secp256r1` an. Signieren:

```bash
make sign-ec
```

Wichtig: `pkcs11-tool` gibt ECDSA-Signaturen standardmäßig als rohe `r || s`-Konkatenation aus. OpenSSL erwartet DER-codiertes `SEQUENCE { r, s }`. Deshalb steht im Skript `--signature-format openssl`. Wer das vergisst, bekommt eine korrekte Signatur, die OpenSSL trotzdem ablehnt — ein klassischer Stolperer.

Eine weitere Falle bei `CKM_ECDSA` (ohne `_SHA*`-Suffix): der Mechanismus erwartet einen bereits gehashten Input genau in Curve-Order-Laenge. Wer SHA-512 auf P-256 anwendet, muss den Hash selbst linksbuendig kuerzen — sonst antwortet das Token mit `CKR_DATA_LEN_RANGE`. `CKM_ECDSA_SHA256` umgeht das, weil dort das Token hasht.

### Warum das Lab `CKM_ECDSA` benutzt

`lab/scripts/10-sign-ec.sh` ruft `pkcs11-tool --mechanism ECDSA` (= `CKM_ECDSA`) auf und uebergibt einen vorab applikationsseitig erzeugten SHA-256-Hash. Hintergrund: SoftHSM v2 meldet in `--list-mechanisms` ausschliesslich `CKM_ECDSA`, nicht die Token-Side-Hash-Variante `CKM_ECDSA_SHA256`. Auf produktiven HSMs ist `CKM_ECDSA_SHA256` ueblicherweise verfuegbar — dann waere folgender Aufruf einfacher und unmittelbar zu `openssl dgst -sha256 -verify` kompatibel:

```bash
pkcs11-tool --module $MODULE --login --pin $PIN --token-label $TOKEN \
  --sign --mechanism ECDSA-SHA256 --signature-format openssl --id 02 \
  --input-file data.txt --output-file data.sig
```

Praxis-Workflow: vor dem Mechanismus-Festschreiben immer `pkcs11-tool --list-mechanisms` oder `C_GetMechanismList` lesen. Was SoftHSM kann, sagt nichts darueber, was das Ziel-HSM kann — und umgekehrt.

Verifizieren:

```bash
make verify-ec
```

## RSA-PSS im Lab

Voraussetzung: RSA-Key existiert (`make gen-rsa`). Das Skript exportiert den Public Key bei Bedarf selbst aus dem Token und konvertiert ihn nach PEM für die OpenSSL-Verifikation.

```bash
make sign-pss
```

Wichtige PSS-Parameter:

| Parameter | Bedeutung |
|---|---|
| `--hash-algorithm SHA256` | Hash für die Nachricht |
| `--mgf MGF1-SHA256` | Mask Generation Function für PSS |
| `rsa_pss_saltlen:-1` (OpenSSL) | Salt-Länge gleich Hashlänge |

Wenn HSM und Anwendung unterschiedliche Salt-Längen oder unterschiedliche MGF-Hashes verwenden, schlägt die Verifikation fehl, obwohl Key und Daten korrekt sind. Das ist die häufigste PSS-Falle.

## JCA-Namen

| `pkcs11-tool` Mechanism | PKCS#11 (`CKM_*`) | JCA `Signature` |
|---|---|---|
| `SHA256-RSA-PKCS` | `CKM_SHA256_RSA_PKCS` | `SHA256withRSA` |
| `RSA-PKCS-PSS` (SHA256/MGF1-SHA256/SaltLen=32) — Input ist Hash | `CKM_RSA_PKCS_PSS` | `RSASSA-PSS` mit `PSSParameterSpec` |
| `SHA256-RSA-PKCS-PSS` (MGF1-SHA256/SaltLen=32) — Token hasht | `CKM_SHA256_RSA_PKCS_PSS` | `RSASSA-PSS` mit `PSSParameterSpec` |
| `ECDSA-SHA256` | `CKM_ECDSA_SHA256` | `SHA256withECDSA` |
| `ECDSA-SHA384` | `CKM_ECDSA_SHA384` | `SHA384withECDSA` |

Im Lab nutzen wir `SHA256-RSA-PKCS-PSS`/`CKM_SHA256_RSA_PKCS_PSS` (Token hasht), weil SoftHSM v2 das anbietet und der Test gegen `openssl dgst -sha256` direkt funktioniert. Auf produktiven HSMs ist die Pre-Hash-Variante `RSA-PKCS-PSS`/`CKM_RSA_PKCS_PSS` haeufiger relevant, weil sie unterschiedliche Hash-Laengen ohne neue Mechanismen erlaubt. JCA-seitig ist die Wahl transparent: `RSASSA-PSS` mit `PSSParameterSpec` deckt beide ab.

In JCA muss die `PSSParameterSpec` explizit gesetzt werden, sonst greifen Defaults, die nicht zu den `CK_RSA_PKCS_PSS_PARAMS` auf der Token-Seite passen und `CKR_MECHANISM_PARAM_INVALID` ausloesen:

```java
Signature sig = Signature.getInstance("RSASSA-PSS", provider);
sig.setParameter(new PSSParameterSpec(
        "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1));
sig.initSign(privateKey);
```

Die Lab-Demo (`lab/java/pkcs11-demo`) setzt diese Parameter automatisch, sobald `PKCS11_MECHANISM=RSASSA-PSS` gesetzt ist.

## EC-Kurven im Vergleich

| Kurve | Sicherheitsniveau | Signaturalgorithmus | PKCS#11-Mechanismus | Typische HSM-Verfuegbarkeit |
|---|---|---|---|---|
| `secp256r1` (P-256, NIST) | ~128 bit | ECDSA | `CKM_ECDSA`, `CKM_ECDSA_SHA256` | praktisch ueberall, FIPS-zugelassen |
| `secp384r1` (P-384, NIST) | ~192 bit | ECDSA | `CKM_ECDSA`, `CKM_ECDSA_SHA384` | weit verbreitet, FIPS-zugelassen |
| `secp521r1` (P-521, NIST) | ~256 bit | ECDSA | `CKM_ECDSA`, `CKM_ECDSA_SHA512` | meist vorhanden, FIPS-zugelassen |
| `secp256k1` | ~128 bit | ECDSA | `CKM_ECDSA` | Bitcoin/Ethereum-Kontext, viele Enterprise-HSMs sperren das per Default |
| `brainpoolP256r1` / `P384r1` | ~128/192 bit | ECDSA | `CKM_ECDSA_*` | europaeische HSMs (eIDAS-Kontext), in US-Cloud-HSMs oft nicht aktiviert |
| `Ed25519` | ~128 bit | EdDSA | `CKM_EDDSA` (PKCS#11 v3.0+) | neuere HSMs/SoftHSM v2.6+; FIPS 186-5 zugelassen, in FIPS-Mode aber haeufig noch nicht freigeschaltet |
| `Ed448` | ~224 bit | EdDSA | `CKM_EDDSA` | seltener, neue HSMs |

Praktische Hinweise:

- **PKCS#11 v3.0** hat EdDSA standardisiert; SoftHSM v2.6+ kann es, aeltere Builds nicht. Vor Festlegung `--list-mechanisms` lesen.
- **EdDSA** signiert nicht ueber Hashes wie ECDSA, sondern hat einen festen internen Hash. Die Signatur ist deterministisch — kein Salt, kein Mismatch wie bei PSS.
- **brainpool-Kurven** kommen oft in eIDAS-Kontexten vor; nicht jedes HSM hat sie freigeschaltet.
- **secp256k1** ist die Bitcoin-Kurve, fuer klassische PKI selten relevant und in vielen Enterprise-HSMs aus Compliance-Gruenden deaktiviert.

## Wann was?

- Neuer Code, frei wählbar, FIPS-Kontext: ECDSA P-256 oder P-384.
- Neuer Code, frei wählbar, kein FIPS-Zwang: Ed25519 (kleiner, schneller, kein Salt-/Encoding-Theater), wenn das Ziel-HSM den Mechanismus unterstuetzt.
- Bestehender PKI-Stack mit RSA-CA: RSA-PSS.
- Legacy-Kompatibilität: RSA-PKCS#1-v1.5.

## Harte Wahrheit

Viele HSMs unterstützen PSS, aber mit Einschränkungen bei MGF-Hash und Salt-Länge. Vor dem produktiven Einsatz: `pkcs11-tool --list-mechanisms` lesen, im Zweifel beim Hersteller nachfragen.
