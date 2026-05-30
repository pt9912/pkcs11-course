# Loesung 16 - CSR und CA-Workflow

## CA-Setup

`make issue-ca-cert` produziert:
- `lab/work/ca-cert.pem` (Subject = "CN=Lab Root CA, O=PKCS11 Lab, OU=Course")
- Cert im Token auf CKA_ID=08, Label `ca-key`

Pruefen:

```bash
openssl x509 -in lab/work/ca-cert.pem -noout -text | head -30
```

Erwartete Extensions:
- `Basic Constraints: critical, CA:TRUE`
- `Key Usage: critical, Certificate Sign, CRL Sign`
- `Subject Key Identifier`

## Leaf-Cert

`make issue-leaf-cert`:
- Generiert CSR via openssl req mit pkcs11-engine (HSM signiert die CSR-Bytes)
- CA signiert die CSR via openssl x509 -req mit pkcs11-engine (HSM signiert das Cert)
- Chain-Verify gegen `lab/work/ca-cert.pem` → OK
- Leaf-Cert auf CKA_ID=09, Label `leaf-cert`

Beobachte: jeder Aufruf vergibt eine inkrementelle Serialnummer (1001, 1002, ...). Das ist Standard-CA-Pattern.

## Sprach-Demos

Output-Schema gleich, Subjects unterschiedlich:

```text
Subject:      CN=go-app.example.org,O=PKCS11 Lab
Subject:      CN=csharp-app.example.org,O=PKCS11 Lab
Subject:      CN=java-app.example.org,O=PKCS11 Lab
Subject:      CN=kotlin-app.example.org,O=PKCS11 Lab
```

Cross-Verify am Ende jeder Demo:

```text
Certificate request self-signature verify OK
```

## Cross-Lib-Signing

```bash
cp lab/work/go-app.csr lab/work/leaf.csr
make issue-leaf-cert
```

Erwartet: das Cert enthaelt jetzt den Go-Subject (`CN=go-app.example.org`), signiert von der Bash-CA. Verify ok. **Das ist** der echte Workflow: CSR aus Anwendung, Cert aus PKI.

## Broken-CSR

```text
Certificate request self-signature verify failure
139785214412096:error:04091077:rsa routines:int_rsa_verify:wrong signature length:...
wie erwartet: ungueltig
```

Selbst kleinste Aenderungen am DER-codierten CSR-Body brechen die Signatur. ASN.1 + RSA-Signatur erlauben kein "fast korrekt".

## Antworten zu den Reflexionsfragen

**Was die CSR beweist:** Der Antragsteller besitzt den **privaten Schluessel** zum in der CSR enthaltenen Public Key. Mehr nicht. Insbesondere beweist eine CSR **nicht**:
- Dass der Antragsteller der rechtmaessige Inhaber des Subject-Namens ist (das macht die CA durch ihren Validation-Process — DNS-Challenge, manuelles Vetting, Domain-Ownership-Check).
- Dass der Antragsteller berechtigt ist, ein Cert mit den gewuenschten Extensions (z.B. CA-Bit, EKU code-signing) zu bekommen — das entscheidet die CA-Policy.

Eine CA, die jede CSR ohne Validierung signiert, ist nutzlos.

**Warum CSR statt Cert vorlegen:** Wenn der Antragsteller ein fertiges Cert vorlegen wuerde, koennte die CA seinen Inhalt nicht kontrollieren — Subject, Extensions, Validity-Period waeren Antragsteller-bestimmt. Mit der CSR sieht die CA nur den Pubkey + gewuenschtes Subject + gewuenschte Extensions (im extensionRequest-Attribut); sie entscheidet selbst, **welche** davon sie in den Cert uebernimmt und welche Validity sie vergibt.

**SKI/AKI-Mismatch:** Praktisch: der Verifier kann die Chain nicht aufbauen, weil er nicht weiss, welches der vorliegenden CA-Certs das richtige fuer den vorliegenden Leaf-Cert ist. Symptom: `openssl verify` meldet `unable to get local issuer certificate`. SKI in Leaf == AKI in CA-Cert ist die kanonische Verknuepfung in modernen Cert-Chains; ohne kann ein Verifier zwischen mehreren in Frage kommenden CA-Certs nicht eindeutig waehlen.

**SAN-Pflicht im Browser:** RFC 6125 (2011) sagt: Verifier MUSS SAN nutzen, wenn vorhanden, MUSS dann CN ignorieren. RFC 2818 erlaubte noch CN als Hostname-Quelle. Moderne Browser folgen 6125 strikt: kein SAN → kein Hostname-Match → Cert-Error. Chrome hat das ab 2017 (Cert-Validation v2) hart durchgesetzt, Safari/Firefox folgten. Die historische CN-Bedeutung "menschenlesbarer Subject-Name" steht jetzt frei; Hostname-Binding gehoert ausschliesslich in SAN.
