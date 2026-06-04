# Uebung 19 — RFC-3161-Timestamps fuer CMS

## Ziel

Du baust eine CMS-Signatur mit HSM-Key, holst einen RFC-3161-Timestamp von der Lab-TSA, betten ihn in vier Sprachen als `signatureTimeStampToken` ein und vergleichst die Artefakte.

## Vorbereitung

```bash
make tsa-setup            # erzeugt TSA-Key + CA-signiertes TSA-Cert
make import-cert          # signing-key + Cert im Token
```

## Aufgabe 1 — Bash-TSA-Roundtrip

```bash
make cms-tsa-sign
make cms-tsa-verify
```

Erwartet:
- `lab/work/tsa-document.p7s` (CMS-Signatur, ~1.4 KB)
- `lab/work/tsa-document.tsr` (TSToken, ~700 Byte)
- `openssl ts -verify` mit `Verification: OK`
- TSToken zeigt `Policy OID: 1.3.6.1.4.1.99999.1` und `Hash Algorithm: sha256`

## Aufgabe 2 — Vier Sprach-Demos mit Embedding

```bash
make java-cms-tsa-demo
make kotlin-cms-tsa-demo
make csharp-cms-tsa-demo
make go-cms-tsa-demo
```

Erwartete Output-Muster:
- Java/Kotlin/C#: `CMS+TSA-Signatur geschrieben` als **eine** Datei (`*-cms-tsa.p7s`), Plain-CMS ~1.3 KB, mit eingebettetem TSToken ~3.8 KB.
- Go: `CMS und TSR separat` als zwei Dateien — Go-Library hat kein Embedding-API.
- Alle vier: `CMS-Signatur: OK`, `Timestamp-Hash: OK`, `Timestamp-Zeit: <heute, UTC>`.

## Aufgabe 3 — Reuse zwischen den Sprachen

Verifiziere die Java-erzeugte Datei mit openssl:

```bash
openssl cms -verify -binary -inform DER -in lab/work/java-cms-tsa.p7s \
  -content lab/work/tsa-document.txt \
  -CAfile lab/work/cert.pem -out /dev/null
```

Erwartet: `CMS Verification successful`. CMS-Format ist standardisiert — die Java-Signatur ist ohne Aenderung von OpenSSL lesbar, auch mit eingebettetem TSToken (UnsignedAttributes stoeren den Verifier nicht).

## Aufgabe 4 — Bonus: TSA ohne extKU-timeStamping

Aendere in `lab/scripts/85-tsa-setup.sh` die Zeile

```text
-addext "extendedKeyUsage=critical,timeStamping"
```

zu

```text
-addext "extendedKeyUsage=critical,serverAuth"
```

und lade frisch (`rm -f lab/work/tsa-*.{pem,der,csr} && make tsa-setup`). Starte den TSA-Daemon, lasse Java-Demo laufen.

Erwartet: BouncyCastles `TimeStampResponse.validate` wirft eine Exception mit `TSP-Verfication: ...` oder `eku` — das TSA-Cert wird abgelehnt, weil `timeStamping` nicht (kritisch) gesetzt ist. Aenderung zurueckdrehen.

## Aufgabe 5 — Reflexion zur Lab-Realitaet

Schau dir `85-tsa-setup.sh` an und beantworte:

- Warum ist der TSA-Key im Lab Software statt HSM-resident, obwohl der Doc-Signing-Key im HSM bleibt?
- Welcher Punkt am `openssl ts -reply`-CLI verhindert die Engine-Nutzung?
- Welche realen TSA-Implementierungen wuerden das anders machen?

## Reflexionsfragen

Vier Stufen — eine Recall-, zwei Analyse- und eine Evaluate-Frage:

1. **(Recall)** Wer in der Cert-Hierarchie (CA, TSA-Cert, Doc-Signer-Cert) muss `extendedKeyUsage=timeStamping` tragen, und wer **darf** das auf keinen Fall?
2. **(Analyse)** Warum gilt `signingTime` aus PKCS#9 fuer eIDAS-qualifizierte Signaturen nicht als ausreichende Zeitquelle? Welche zwei Eigenschaften (externer Zeuge, auditierte Uhr) fehlen `signingTime`, die ein RFC-3161-TSToken mitbringt?
3. **(Analyse)** Welche Eigenschaft eines RFC-3161-TSToken stellt sicher, dass er nicht von einem Angreifer "wiederverwendet" werden kann? Verfolge den Nonce-Pfad durch TSReq → TSResp und denke ueber Replay-Schutz nach.
4. **(Evaluate)** Du sollst fuer einen Vertragsdienst eine TSA waehlen: (A) Free-Tier, (B) Commercial, (C) qualifizierter eIDAS-TSA. Eingangs-Bedingung "eIDAS-Geltung, 7 Jahre Aufbewahrung". Welche gewinnt — und welcher Faktor (Aufbewahrungsdauer, Krypto-Bruch-Sicherheit, Revocation-Validierbarkeit) verschiebt die Antwort auf (C), wenn die Aufbewahrungspflicht auf 35 Jahre steigt? Welches CAdES-Profil (-T, -LT, -LTA) gehoert dann dazu?

## Musterloesung

Siehe `solutions/19-rfc3161-timestamps.md`.
