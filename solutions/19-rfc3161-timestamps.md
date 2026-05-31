# Loesung 19 — RFC-3161-Timestamps

## Bash-Roundtrip

```text
=== 1) CMS-Signatur ueber lab/work/tsa-document.txt (detached, SHA-256, HSM-signed) ===
  CMS-Signatur: lab/work/tsa-document.p7s (1434 Byte)

=== 3) TimeStampReq ueber die CMS-Signatur bauen + POST an Lab-TSA ===
  TimeStampResp: lab/work/tsa-document.tsr (703 Byte)

=== 4) TimeStampResp inhaltlich anzeigen ===
  Time stamp: May 31 12:00:48 2026 GMT
  Hash Algorithm: sha256
  Policy OID: 1.3.6.1.4.1.99999.1
  TSA: unspecified
```

`make cms-tsa-verify`:

```text
=== 1) CMS-Signatur ueber das Dokument verifizieren ===
  CMS Verification successful

=== 2) TimeStamp-Token verifizieren ===
  Verification: OK
```

## Sprach-Demos

Alle vier Demos kommen bei einem frischen Lab-Run zum selben Funktionsbild:

```text
=== 4) Verifikation ===
  CMS-Signatur:    OK
  Timestamp-Hash:  OK
  Timestamp-Zeit:  <aktuelle UTC-Zeit>
  TSA-Subject:     CN=Lab TSA,O=PKCS11 Lab,OU=Time Stamping
```

Java/Kotlin/C# schreiben ein einziges `*.p7s` mit eingebettetem `signatureTimeStampToken` (~3.8 KB). Go schreibt `.p7s` und `.tsr` getrennt — `digitorus/pkcs7` hat keine API fuer nachtraegliche UnsignedAttributes.

Konkrete Hex-Werte/Zeitstempel sind run-spezifisch; die didaktisch relevante Eigenschaft ist die Klassen-Konsistenz (CMS+TS valid auf allen vier Pfaden).

## Cross-Sprache: Java-CMS mit OpenSSL verifizieren

```text
CMS Verification successful
```

Das ist der Beweis: das eingebettete `signatureTimeStampToken` ist als UnsignedAttribute korrekt platziert und stoert openssl-cms nicht. Die TSToken-Bytes liegen ueber dem `digest`-Input, aber der TSToken selbst hat seine eigene Signaturverkettung; openssl-cms ignoriert UnsignedAttributes beim CMS-Verify.

## Aufgabe 4 — extKU-Manipulation

Mit `extendedKeyUsage=critical,serverAuth` im TSA-Cert:

```text
Fehler beim CMS+TSA-Lauf:
  TSPException: TSP Verification:
  CertPathValidatorException: certificate not allowed for timeStamping
```

BouncyCastle ruft intern `org.bouncycastle.tsp.TimeStampResponse.validate(...)`, das auf das `extKeyUsage` des TSA-Certs schaut. Ohne `timeStamping` (kritisch) wird das Cert abgelehnt. Damit kann ein kompromittiertes Server-TLS-Cert nicht plötzlich als Time-Stamping-Cert missbraucht werden — genau die Schutzwirkung, die RFC 3161 §2.3 mit der Kritikalitaet erzwingt.

## Aufgabe 5 — Lab-Realitaet

**Warum Software-TSA-Key:**
`openssl ts -reply` ist ein CLI-Werkzeug aus der OpenSSL-Suite, das eine Konfigurationsdatei einliest und den Signer-Key per `BIO_new_file()` laedt. Es hat keine Anbindung an die OpenSSL-Engine-API fuer `signer_key`; jeder Wert in der Config wird als Filesystem-Pfad interpretiert. Eine PKCS#11-URI fuehrt zu `fopen("pkcs11:...")` und schlaegt mit `No such file or directory` fehl. Loesung im Lab: TSA-Key liegt im Filesystem (`lab/work/tsa-key.pem`, mode 0600). Der CA-Key, der das TSA-Cert signiert, ist weiterhin HSM-resident — der HSM-Aspekt bleibt am Trust-Root erhalten.

**Was reale TSAs anders machen:**
Reale TSAs sind keine `openssl ts -reply`-Wrapper, sondern eigenstaendige TSP-Daemons. Beispiele: Linagora's `freetsa`, Sphinx's Java-basierte TSPv2-Server, OpenTimestamps mit eigener Bindung. Diese Implementierungen nutzen direkt PKCS#11 oder eine HSM-Vendor-Library, ohne `openssl ts -reply` als CLI-Stueck zu durchlaufen.

**Pragmatischer Lab-Workaround:**
Wer im Lab den Sprach-Demo-Pfad mit echtem HSM-TSA-Key sehen will, kann den Python-Wrapper `_tsa_server.py` durch einen eigenen Daemon ersetzen, der per `pyca/cryptography` + `python-pkcs11` einen TSToken direkt zusammenbaut. Aufwand: ~150 Zeilen Python.

## Antworten zu den Reflexionsfragen

**`signingTime` reicht nicht:**
Das `signingTime`-Attribut (PKCS#9, OID 1.2.840.113549.1.9.5) traegt einen vom Signer selbst gesetzten Zeitstempel — kein externer Trust-Anchor garantiert, dass die Uhr des Signers stimmt. eIDAS Art. 41 verlangt fuer qualifizierte elektronische Signaturen explizit eine "qualifizierte elektronische Zeitstempelung" durch einen TSP. Ein selbst gesetzter Zeitstempel kann nach Cert-Ablauf zurueckdatiert werden, um eine "war damals noch gueltig"-Behauptung zu konstruieren — das schliesst RFC 3161 aus.

**CAdES-T vs CAdES-LT:**
- CAdES-T: Signatur + eine TSA-Bestaetigung des Signaturzeitpunkts. Reicht, solange das Signer-Cert noch gueltig ist und Trust-Chain online verifizierbar ist.
- CAdES-LT: zusaetzlich eingebettete Revocation-Informationen (CRL/OCSP-Response). Erlaubt Validierung auch, **nachdem** das Signer-Cert abgelaufen ist oder die CA-Infrastruktur nicht mehr erreichbar ist.
- CAdES-A laeuft eine Stufe weiter: periodische Archive-Timestamps stellen sicher, dass die Beweiskraft auch nach Kryptographie-Bruch (z.B. SHA-1 unsicher) erhalten bleibt.

Typische Verwendung:
- Vertraege mit kurzer Validierungsfenster (Cloud-Signature-Services): CAdES-T reicht.
- Vertraege mit Aufbewahrungspflicht von 10+ Jahren (notarielle Dokumente, Steuerunterlagen): CAdES-LT oder CAdES-A.

**Replay-Schutz:**
Die `nonce` im TSReq. Der TSA muss sie unveraendert in den TSToken ueberhnehmen (RFC 3161 §2.4.2). Wer einen alten TSToken kopieren wuerde, wuerde durch den Nonce-Mismatch beim Verify auffliegen. Zusaetzlich enthaelt das TSToken den Hash der Daten — Manipulation der Daten brueche das ebenfalls.

**extKU=timeStamping — wer darf, wer nicht:**
- **TSA-Cert:** MUSS `extendedKeyUsage=critical,id-kp-timeStamping (1.3.6.1.5.5.7.3.8)` tragen. Sonst lehnt der RFC-3161-Verifier das TSToken ab.
- **Doc-Signer-Cert:** soll **kein** `timeStamping`-extKU tragen — der Signer ist nicht die Zeitquelle.
- **CA-Cert:** keine direkten extKU-Anforderungen fuer die TSA-Funktion; die CA signiert das TSA-Cert, nicht den Timestamp selbst.
- Faustregel: die kritisch markierte extKU `timeStamping` ist ein **einzelner Rollen-Stempel**. Ein Cert, das diese trägt, darf NICHT zusätzlich für TLS oder S/MIME genutzt werden — Verifier dort lehnen das implizit ab, weil sie ihre eigene erwartete extKU nicht finden.
