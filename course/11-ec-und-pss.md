# 11 — ECDSA und RSA-PSS

> **Didaktischer Pfad:** Vorher → [`08-debugging.md`](08-debugging.md) · Nachher → [`07-service-integration.md`](07-service-integration.md) (Architektur-Skizze nach Debugging — sonst happy-path-Trugschluss)

## Bevor du anfaengst — was vermutest du?

> Du nimmst `CKM_RSA_PKCS_PSS`, signierst, der OpenSSL-Verifier akzeptiert die Signatur — mit `-sigopt rsa_padding_mode:pss`. Du wechselst die Salt-Laenge im Verifier-Aufruf von 32 auf 20. Geht der Verify trotzdem durch?

Wahrscheinliche Vermutung: ja. PSS ist eine moderne Sicherheits-Verbesserung, die "richtigen" Parameter sind irgendwo im Algorithmus eingebrannt — Salt-Laenge ist ein Detail, das die Bibliothek schon richtig macht. Mentale Karte: **PSS = ein Padding-Mode wie PKCS#1, mit etwas mehr Schutz**.

Diese Karte uebersieht, dass PSS ein **parametrierter** Modus ist: Hash der Nachricht, MGF-Hash und Salt-Laenge muessen Signer und Verifier **byte-identisch** teilen, sonst schlaegt der Verify ohne klare Fehlermeldung fehl. Bei ECDSA wiederholt sich das Muster auf der Encoding-Achse: `pkcs11-tool` gibt rohe `r||s`-Bytes aus, OpenSSL erwartet DER-codiertes `SEQUENCE { r, s }`. Mathematik korrekt, Encoding falsch — selbe Folge: `Verification Failure` ohne Begruendung. Halte die "PSS/ECDSA ist eingebrannt"-Karte fest. Dieses Kapitel zeigt, dass die *Parameter-Achse* (Salt, MGF, DER vs raw) den Unterschied zwischen "funktioniert" und "Verifier sagt Nein" macht — und dass die HSM-Capabilities (`--list-mechanisms`) entscheiden, bevor die Theorie ueberhaupt anfaengt.

## Lernziele

Nach diesem Kapitel kannst du:

- RSA-PKCS#1-v1.5, RSA-PSS und ECDSA unterscheiden.
- PSS-Parameter wie Hash, MGF und Salt-Laenge konsistent setzen.
- ECDSA-Signaturencoding fuer OpenSSL korrekt behandeln.
- entscheiden, welcher Mechanism fuer neue Systeme sinnvoll ist.
- **(Bloom 5 — evaluate)** fuer ein gegebenes System-Szenario (Bestand, FIPS, Cloud) **begruenden**, welcher Mechanism die richtige Wahl ist — und welche zwei HSM-Eigenschaften die Entscheidung tatsaechlich tragen, nicht nur die kryptographische Theorie.

> **Geschaetzte Bearbeitungszeit:** ~75 min (Lesen 30 min + Lab 15 min + ECDSA-/PSS-Eigenexperimente 30 min). PSS-Salt-Mismatch und ECDSA-DER-Encoding sind die zwei Stolperer, die in fast jedem realen Projekt einmal auftauchen.

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
| `Ed25519` | ~128 bit | EdDSA | `CKM_EDDSA` (PKCS#11 v3.0+) | neuere HSMs/SoftHSM v2.6+; in FIPS 186-5 (Signaturstandard) spezifiziert, in FIPS-140-2-zertifizierten Modulen aber **nicht** erlaubt, in FIPS-140-3-Modulen uneinheitlich aktiviert |
| `Ed448` | ~224 bit | EdDSA | `CKM_EDDSA` | seltener, neue HSMs |

Praktische Hinweise:

- **PKCS#11 v3.0** hat EdDSA standardisiert; SoftHSM v2.6+ kann es, aeltere Builds nicht. Vor Festlegung `--list-mechanisms` lesen.
- **EdDSA** signiert nicht ueber Hashes wie ECDSA, sondern hat einen festen internen Hash. Die Signatur ist deterministisch — kein Salt, kein Mismatch wie bei PSS.
- **Im Kurs-Lab nicht ausgefuehrt**: das im Image gebaute SoftHSM v2 (Debian-Default) listet kein `CKM_EDDSA`. Ein eigener Build oder ein produktives HSM mit `CKM_EDDSA` ist Voraussetzung, um die Demo zu reproduzieren — deshalb gibt es kein `make sign-eddsa`-Target.
- **brainpool-Kurven** kommen oft in eIDAS-Kontexten vor; nicht jedes HSM hat sie freigeschaltet.
- **secp256k1** ist die Bitcoin-Kurve, fuer klassische PKI selten relevant und in vielen Enterprise-HSMs aus Compliance-Gruenden deaktiviert.

## Wann was?

- Neuer Code, frei wählbar, FIPS-Kontext: ECDSA P-256 oder P-384.
- Neuer Code, frei wählbar, kein FIPS-Zwang: Ed25519 (kleiner, schneller, kein Salt-/Encoding-Theater), wenn das Ziel-HSM den Mechanismus unterstuetzt.
- Bestehender PKI-Stack mit RSA-CA: RSA-PSS.
- Legacy-Kompatibilität: RSA-PKCS#1-v1.5.

## Harte Wahrheit

Viele HSMs unterstützen PSS, aber mit Einschränkungen bei MGF-Hash und Salt-Länge. Vor dem produktiven Einsatz: `pkcs11-tool --list-mechanisms` lesen, im Zweifel beim Hersteller nachfragen.

## Eigenexperiment

- **ECDSA-Format-Falle reproduzieren.** Signiere mit `pkcs11-tool --sign --mechanism ECDSA` **ohne** `--signature-format openssl`. Das Token liefert rohe `r||s`-Bytes (64 Byte bei P-256). Gib die Datei an `openssl dgst -sha256 -verify` — Erwartet: `Verification Failure`, obwohl Krypto-Mathematik korrekt. Reparatur: denselben Sign-Aufruf mit `--signature-format openssl` wiederholen, jetzt produziert `pkcs11-tool` DER-codiertes `SEQUENCE { r, s }`, OpenSSL akzeptiert.

- **PSS-Salt-Mismatch.** Signiere mit `--mechanism SHA256-RSA-PKCS-PSS` und `--mgf MGF1-SHA256` (Lab-Default, Salt = 32 Byte = Hashlaenge). Verifiziere mit `openssl dgst -sha256 -sigopt rsa_padding_mode:pss -sigopt rsa_pss_saltlen:20`. Erwartet: `Verification Failure` mit identischem Key und identischer Datei — der einzige Unterschied ist die Salt-Laenge. Reparatur: `rsa_pss_saltlen:-1` (= Hashlaenge automatisch) oder explizit `:32`.

Strukturierte Aufgaben (DER-Falle, PSS-Spiegelparameter, Mechanism-Entscheidung) in [`exercises/20-ec-und-pss.md`](../exercises/20-ec-und-pss.md).

## Selbsttest

<details>
<summary>1. <code>pkcs11-tool</code> liefert ECDSA-Signaturen standardmaessig als <code>r||s</code>. OpenSSL erwartet DER. Welcher Schalter loest das auf der Sign-Seite?</summary>

`--signature-format openssl`. Setzt der Schalter, gibt `pkcs11-tool` die Signatur als `SEQUENCE { INTEGER r, INTEGER s }` DER-codiert aus. Ohne diesen Schalter sind die Bytes mathematisch korrekt, OpenSSL-Verify lehnt aber mit `Verification Failure` ab.
</details>

<details>
<summary>2. Welche zwei PSS-Parameter muessen Signer und Verifier zwingend uebereinstimmen, damit die Verifikation klappt?</summary>

**Salt-Laenge** und **MGF-Hash**. Hash-Algorithmus der Nachricht ist offensichtlich; die beiden anderen sind die haeufige Falle, weil JCA-Defaults (`MGF1ParameterSpec.SHA1`, Salt 20 Byte) typisch nicht mit `CKM_SHA256_RSA_PKCS_PSS`-HSM-Defaults uebereinstimmen — Resultat: `CKR_MECHANISM_PARAM_INVALID` oder `Verification Failure` ohne klare Begruendung.
</details>

<details>
<summary>3. Du sollst fuer ein neues System eine Mechanism-Empfehlung abgeben. Welche Frage stellst du zuerst — Krypto-Theorie oder HSM-Capabilities?</summary>

HSM-Capabilities. `pkcs11-tool --list-mechanisms` zuerst. Was der Token nicht kann, hilft theoretisch nicht. EdDSA waere kryptographisch oft die beste Wahl — auf SoftHSM v2 (Debian-Default) gibt es kein `CKM_EDDSA`, also nicht moeglich. Brainpool ist in vielen US-Cloud-HSMs nicht aktiviert. Erst nachdem die Mechanism-Liste bekannt ist, kommt die Theorie-Diskussion (PSS vs ECDSA vs Ed25519).
</details>
