# Uebung 18 — ECDH + HKDF (Key Derivation)

## Ziel

Du leitest ein gemeinsames Geheimnis zwischen Alice und Bob ueber den HSM ab (Privkeys bleiben sortenrein im Token), siehst zwei KDF-Pfade und beweist den Shared-Secret-Match ueber AES-Roundtrip.

## Vorbereitung

```bash
make init-token
make gen-ecdh-keys
```

Falls Java oder Kotlin folgen:

```bash
make issue-ecdh-certs
```

## Aufgabe 1 — Bash/Go-Demo, KDF=hkdf

```bash
make ecdh-derive
```

Erwartet:

- `CKM_ECDH1_DERIVE` auf beiden Seiten, gleicher 32-Byte-Shared-Secret (`e90e368d95f68725...` bei deterministisch gleichen Keys; bei `clean-tokens` + Neu-Generieren aendert sich der Wert, beide Seiten muessen aber matchen)
- HKDF-SHA256 mit `info="ECDH-Lab-V1"` liefert AES-Key `8e8922dcb79a3dcf...`
- AES-256-GCM Roundtrip: Bob entschluesselt Alices Nachricht

## Aufgabe 2 — KDF=raw als Vergleich

```bash
PKCS11_ECDH_KDF=raw make ecdh-derive
```

Erwartet: AES-Key entspricht den ersten 32 Byte des rohen Shared Secret (`e90e368d95f68725...`), nicht dem HKDF-Output. Nachricht entschluesselt trotzdem korrekt — das Protokoll funktioniert, der KDF ist nur ein zusaetzlicher Sicherheits-/Standardisierungs-Schritt.

## Aufgabe 3 — Vier-Sprachen-Konsistenz

```bash
make go-ecdh-demo
make csharp-ecdh-demo
make java-ecdh-demo
make kotlin-ecdh-demo
```

Erwartet: alle vier Demos drucken denselben AES-Key (`8e8922dcb79a3dcf...`). Das ist die HKDF-RFC-5869-Interop-Garantie — Salt-Default und Info-String byte-identisch interpretiert.

## Aufgabe 4 — Info-Sensitivitaet

Aendere in `lab/go/pkcs11-ecdh-demo/main.go` die Konstante `hkdfInfo` von `"ECDH-Lab-V1"` auf `"ECDH-Lab-V2"`. Lass `make go-ecdh-demo` erneut laufen.

Erwartet: voellig anderer AES-Key, Roundtrip funktioniert weiterhin (weil Alice und Bob beide den gleichen neuen Info-String nutzen). Aenderung wieder zuruecksetzen.

## Aufgabe 5 — Validate-Key-Usage

```bash
make validate-key-usage
```

Erwartet: 11/11 OK wie bisher. Die ECDH-Keys (alice-ec-key, bob-ec-key) sind in der Validator-Tabelle **nicht** enthalten — bewusst, weil das Soll-Profil dort `sign,derive` (priv) bzw. `verify,derive` (pub) waere. Wer den Validator erweitern will: lab/scripts/77-validate-key-usage.sh, EXPECTED-Array um vier Eintraege erweitern. Reflexion: Warum ist es vertretbar, dass der Default-Validator die ECDH-Keys nicht abdeckt?

## Aufgabe 6 — Bonus: Sensitive-Flag wirklich verstehen

Loesche aus `lab/java/pkcs11-ecdh-demo/src/main/resources/softhsm.cfg` den `attributes(...)`-Block. Lass `make java-ecdh-demo` laufen.

Erwartet: `ProviderException: Could not derive key` mit Ursache `PKCS11Exception: CKR_ATTRIBUTE_SENSITIVE 0x11`. Begruendung: SunPKCS11 setzt im Derive-Template per Default `CKA_SENSITIVE=true` und will den Wert anschliessend extrahieren. Block wieder einfuegen.

## Reflexionsfragen

Vier Stufen — eine Recall-, zwei Analyse- und eine Evaluate-Frage:

1. **(Recall)** Welche Rolle spielt der `info`-Parameter in HKDF — und warum ist `salt=null` kein Sicherheitsproblem, wenn das IKM bereits hochentropisch ist?
2. **(Analyse)** Warum ist ECDH die Grundform jeder modernen TLS-1.3-Cipher-Suite, und nicht RSA-Wrap? Welche Eigenschaft (Forward Secrecy, KEM-Vorlage fuer PQ-Migration) macht den Unterschied?
3. **(Analyse)** SunPKCS11 verlangt den `attributes(...)`-Override fuer Generic-Secret-Keys, um den Derive-Output zu sehen. In Produktion will man das NICHT. Welcher API-Pfad (`generateSecret("AES")` ueber den SunPKCS11-Provider) umgeht das Problem, ohne `byte[]` zu extrahieren — und welche Eigenschaft des abgeleiteten Keys bleibt damit `CKA_SENSITIVE=true`?
4. **(Evaluate)** Du sollst zwischen drei Key-Establishment-Pfaden fuer einen neuen Service waehlen: (A) RSA-OAEP-Wrap (Kap. 13), (B) statisches ECDH+HKDF, (C) ephemerales ECDH+HKDF (TLS-1.3-Stil). Welcher gewinnt fuer "asynchrones Document-Sharing zwischen Org-Boundaries", welcher fuer "interaktive Session zwischen zwei Endpoints"? Welcher Faktor (Liveness-Erfordernis, Forward Secrecy) entscheidet?

## Musterloesung

Siehe `solutions/18-ecdh-hkdf.md`.
