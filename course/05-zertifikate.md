# 05 — Zertifikate

## Warum Zertifikate im Token liegen

Ein Token kann private Schlüssel und zugehörige Zertifikate enthalten. Das Zertifikat ist öffentlich, aber praktisch, weil Anwendungen darüber den passenden Schlüssel finden können. Insbesondere Java-`KeyStore` macht ohne Zertifikat keinen Private-Key-Alias sichtbar.

## Wichtige Regel

Private Key und Zertifikat müssen dieselbe `CKA_ID` verwenden. Beispiel:

- Private Key ID: `01`
- Certificate ID: `01`

Sonst findet die Anwendung den Key nicht, obwohl beide Objekte existieren.

## Selbstsigniertes Zertifikat erzeugen — Lab-Weg

Das Skript `lab/scripts/08-import-cert.sh` zeigt den realistischen Flow:

1. CSR/Certificate-Erzeugung mit OpenSSL über die PKCS#11-Engine.
2. Der private Schlüssel bleibt im Token. OpenSSL signiert das Zertifikat über die Engine, nicht außerhalb.
3. Das DER-Zertifikat wird per `pkcs11-tool --write-object --type cert` in das Token geschrieben.

```bash
make import-cert
```

Wichtige Bausteine im Skript:

- PKCS#11-URI nach RFC 7512: `pkcs11:token=dev-token;object=signing-key;type=private;pin-value=...`
- OpenSSL 3 lädt die Engine `pkcs11` über eine `openssl.cnf`-Section, die das Skript temporär anlegt.

In Produktion erzeugst du normalerweise keinen Self-Signed-Cert, sondern einen CSR im HSM, lässt ihn von einer CA signieren und importierst dann das CA-signierte Zertifikat.

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
