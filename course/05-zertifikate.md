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

## Eigenexperiment

- **CKA_ID bewusst auf Mismatch setzen.** Importiere das Cert mit `--id 99` statt `--id 01` und starte danach `make java-demo`. Erwartet: der Alias `signing-key` ist im KeyStore **nicht** als Private-Key-Eintrag sichtbar, weil SunPKCS11 kein Cert mit der `CKA_ID` des Privkeys findet. Repariere die ID mit einem zweiten `pkcs11-tool --write-object`-Aufruf und beobachte, dass der Alias zurueckkommt.

  ```bash
  docker compose -f lab/docker-compose.yml run --rm pkcs11-lab bash -lc '
    openssl x509 -outform der -in lab/work/cert.pem -out /tmp/cert.der &&
    pkcs11-tool --module $PKCS11_MODULE --login --pin $PKCS11_USER_PIN \
      --token-label $PKCS11_TOKEN_LABEL --delete-object --type cert --id 01 &&
    pkcs11-tool --module $PKCS11_MODULE --login --pin $PKCS11_USER_PIN \
      --token-label $PKCS11_TOKEN_LABEL --write-object /tmp/cert.der --type cert \
      --id 99 --label mismatched-cert
  ' && make java-demo
  ```

  (Im Devcontainer: ohne `docker compose run --rm pkcs11-lab bash -lc`.) Erwartet: die Demo bricht mit `key=null` ab. Reparatur: das Cert nochmal mit `--id 01` importieren oder `make import-cert` aufrufen (loescht und re-importiert).

- **Zertifikat als PEM statt DER schreiben.** `pkcs11-tool --write-object` erwartet DER. Ein versehentliches `cert.pem` als Input fuehrt zu `Unable to write certificate`. Lehrreich, weil real verbreitet — viele Vendor-CAs liefern PEM und nicht DER, der Konvertierungsschritt ist eigene Pflicht.

- **Cert ohne Privkey importieren.** Lege das Cert in das Token, ohne dass der Privkey mit gleicher ID existiert. `make java-demo` zeigt dann den Alias, aber `isKeyEntry()` ist `false` und `isCertificateEntry()` ist `true` — der Gegen-Test zur Erklaerung in Kap. 06.

## Selbsttest

<details>
<summary>1. Du importierst ein DER-Cert mit <code>--id 01</code>, aber im Token liegt der Privkey mit <code>--id 02</code>. Was passiert, wenn Java mit <code>KeyStore.aliases()</code> sucht?</summary>

Der Alias wird gar nicht erst sichtbar. SunPKCS11 baut Aliase aus Cert/Privkey-Paaren, die ueber gleiche `CKA_ID` verknuepft sind — eine Cert-ID `01` ohne passenden Privkey wird als Trust-Cert behandelt, aber nicht als Private-Key-Alias. Go/C# koennen den Privkey trotzdem direkt ueber `CKA_ID=02` ansprechen.
</details>

<details>
<summary>2. Warum erzeugt das Lab im Schritt <code>make import-cert</code> ein self-signed Cert, obwohl in Produktion immer CA-signiert?</summary>

Reproduzierbarkeit ohne CA-Setup. Self-signed Certs liefern fuer das Lab den Alias-Plumbing-Effekt (Java findet den Privkey), ohne dass eine PKI gebaut werden muss. Kap. 22 ersetzt diesen Hack durch den Production-aequivalenten CSR-+CA-Workflow.
</details>

<details>
<summary>3. Welche Konsequenz hat die <code>pin-value=...</code>-Form in der PKCS#11-URI gegenueber dem RFC-7512-konformen Query-Format <code>?pin-value=</code>?</summary>

Funktional gleich, aber **nicht streng portabel**. RFC 7512 trennt Pfad- und Query-Attribute; libp11 akzeptiert beide Formen, andere Tools nur eine. Die `pin-value=` im Pfad ist gaengig, aber in Cloud-HSM-Stacks (`libkmsp11`, `azure-keyvault-pkcs11`) bisweilen abgelehnt — in dem Fall Query-Form nutzen.
</details>
