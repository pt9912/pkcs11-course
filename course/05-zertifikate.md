# 05 — Zertifikate

## Lernziele

Nach diesem Kapitel kannst du:

- erklaeren, warum Zertifikate im Token fuer manche Stacks wichtig sind.
- Private Key und Zertifikat ueber `CKA_ID` koppeln.
- ein selbstsigniertes Zertifikat ueber den Token-Key erzeugen.
- Zertifikate mit `pkcs11-tool` importieren und pruefen.

## Lab-Bezug

Passende Targets:

```bash
make gen-rsa
make import-cert
make list-objects
```

## Warum Zertifikate im Token liegen

Ein Token kann private Schlüssel und zugehörige Zertifikate enthalten. Das Zertifikat ist öffentlich, aber praktisch, weil Anwendungen darüber den passenden Schlüssel finden können. Insbesondere Java-`KeyStore` macht ohne Zertifikat keinen Private-Key-Alias sichtbar.

## Wichtige Regel

Private Key und Zertifikat müssen dieselbe `CKA_ID` verwenden. Beispiel:

- Private Key ID: `01`
- Certificate ID: `01`

Sonst findet die Anwendung den Key nicht, obwohl beide Objekte existieren.

## Selbstsigniertes Zertifikat erzeugen — Lab-Weg

Das Skript `lab/scripts/08-import-cert.sh` erzeugt im Lab bewusst ein **selbstsigniertes** Zertifikat (`openssl req -new -x509`), damit der Schritt ohne CA reproduzierbar ist. Der Ablauf:

1. Self-Signed-Zertifikatserzeugung mit OpenSSL über die PKCS#11-Engine — Signatur wird intern vom Token erstellt (`openssl req -new -x509 -engine pkcs11 -keyform engine`).
2. Der private Schlüssel bleibt im Token. OpenSSL signiert das Zertifikat über die Engine, nicht außerhalb.
3. Das DER-Zertifikat wird per `pkcs11-tool --write-object --type cert` in das Token geschrieben.

```bash
make import-cert
```

Wichtige Bausteine im Skript:

- PKCS#11-URI: `pkcs11:token=dev-token;object=signing-key;type=private;pin-value=...` — libp11-Kurzform. `pin-value` ist nach RFC 7512 ein gueltiges Attribut, gehoert dort aber in den Query-Teil hinter `?`. Diese Form schreibt es in den Pfad und ist deshalb nicht streng portabel. Siehe [docs/api.md §4.3](../docs/api.md#43-pkcs11-uri-nach-rfc-7512).
- OpenSSL 3 lädt die Engine `pkcs11` über eine `openssl.cnf`-Section, die das Skript temporär anlegt.

In Produktion erzeugst du normalerweise keinen Self-Signed-Cert, sondern einen CSR im HSM, lässt ihn von einer CA signieren und importierst dann das CA-signierte Zertifikat. Der CSR-Pfad sieht im Wesentlichen so aus (Key bleibt im Token, CSR landet auf der CA):

```bash
KEY_URI="pkcs11:token=$PKCS11_TOKEN_LABEL;object=signing-key;type=private;pin-value=$PKCS11_USER_PIN"
openssl req -new -engine pkcs11 -keyform engine \
  -key "$KEY_URI" -sha256 \
  -subj "/CN=signing-key/O=PKCS11 Lab" \
  -out csr.pem
# csr.pem an die CA geben, signiertes Zertifikat anschliessend ueber
# pkcs11-tool --write-object cert.der --type cert --id 01 in das Token schreiben.
```

## Import-Grundform mit `pkcs11-tool`

```bash
pkcs11-tool \
  --module /usr/lib/softhsm/libsofthsm2.so \
  --login \
  --pin 987654 \
  --write-object cert.der \
  --type cert \
  --id 01 \
  --label signing-key
```

## Praxiswarnung

Zertifikatsimport ist herstellerabhängig oft zickig. Manche HSMs erwarten DER, andere Tools akzeptieren PEM, manche setzen Attribute anders. Immer mit `--list-objects` prüfen.
