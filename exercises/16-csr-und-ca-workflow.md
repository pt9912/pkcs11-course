# Uebung 16 - CSR und CA-Workflow

## Ziel

Du baust eine kleine HSM-residente CA, generierst eine CSR ueber den signing-key, laesst die CA das Cert ausstellen und importierst es zurueck ins Token. Dann reproduzierst du die CSR-Generierung in einer Sprache deiner Wahl und beobachtest, wie die Bridge-Patterns aus Modul 14 (CMS) sich hier wiederverwenden lassen.

## Vorbereitung

```bash
make init-token gen-rsa import-cert
```

## Aufgabe 1 — Bash-CA aufsetzen

```bash
make issue-ca-cert
```

Das macht hintereinander `gen-ca-key` (CA-Key auf ID=08) und `issue-ca-cert` (Self-signed CA-Cert mit `basicConstraints CA:TRUE` und `keyUsage keyCertSign,cRLSign`).

Erwartet:

```text
Root-CA-Cert: lab/work/ca-cert.pem
Subject: CN=Lab Root CA, O=PKCS11 Lab, OU=Course
Importiert ins Token: id=08 label=ca-key
```

## Aufgabe 2 — Leaf-Cert ueber die HSM-CA

```bash
make issue-leaf-cert
```

Erwartet:

```text
=== 1) CSR fuer signing-key bauen (HSM-signed) ===
    Certificate request self-signature verify OK
    subject=CN=app.example.org, O=PKCS11 Lab
=== 2) CA signiert die CSR (CA-Key auch im HSM) ===
  Leaf-Cert: lab/work/leaf-cert.pem
  Serial:    1001
  Issuer:    CN=Lab Root CA, O=PKCS11 Lab, OU=Course
  Subject:   CN=app.example.org, O=PKCS11 Lab
=== 3) Chain-Verify ===
lab/work/leaf-cert.pem: OK
```

`openssl verify` baut die Chain Leaf → Root CA und akzeptiert sie.

## Aufgabe 3 — Sprach-Demo

```bash
make go-csr-demo
make csharp-csr-demo
make java-csr-demo
make kotlin-csr-demo
```

Jede produziert `lab/work/<sprache>-app.csr` und cross-verifiziert mit `openssl req -verify`. Vergleiche die PEM-Dateien — Subject und Bytes unterscheiden sich, alle aber valide CSRs.

## Aufgabe 4 — Cross-Lib-Signing

Nimm eine fremd-erzeugte CSR (z.B. die Go-Variante) und lass die Bash-CA sie signieren:

```bash
cp lab/work/go-app.csr lab/work/leaf.csr
make issue-leaf-cert
```

Erwartet: die CA akzeptiert die Go-erzeugte CSR (Pubkey kommt aus der CSR, Signatur ueber CSR-Bytes ist gueltig). Das ist der reale Workflow zwischen Anwendung (CSR-Erzeuger) und PKI (CA): zwei Bibliotheken, zwei Sprachen, gleiches Standard-Format.

## Aufgabe 5 — Bonus: Cross-Verify mit ungueltiger Signatur

Korrumpiere die CSR und beobachte, dass der Verify-Check anschlaegt:

```bash
cp lab/work/go-app.csr lab/work/broken.csr
# zweites Byte des CSR-Inhalts kippen — bricht Signatur OR Subject-Hash
sed -i 's/A/B/' lab/work/broken.csr
openssl req -in lab/work/broken.csr -noout -verify 2>&1 || echo "wie erwartet: ungueltig"
```

## Reflexionsfragen

- Was beweist die CSR-Signatur — und was beweist sie **nicht**?
- Warum bekommt die CA-CSR vom Antragsteller statt direkt einen vorgefertigten Cert zur Unterschrift?
- Wer haftet, wenn der `SubjectKeyIdentifier` und `AuthorityKeyIdentifier` nicht zueinander passen?
- Wenn dein Subject `CN=app.example.org` ist, aber SAN-Extension fehlt: warum lehnt ein moderner Browser das ab?

## Musterloesung

Siehe `solutions/16-csr-und-ca-workflow.md`.
