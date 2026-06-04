# 18 — HSM-gestuetzte TLS-Terminierung

## Lernziele

Nach diesem Kapitel kannst du:

- erklaeren, **welche** TLS-Operationen den Server-Privkey nutzen (Handshake-Signatur in TLS 1.3, optional Key-Decrypt in TLS 1.2 RSA-Kex).
- ein TLS-Cert vom HSM-Signing-Key ausstellen lassen (self-signed mit SAN).
- nginx (und HAProxy, Apache analog) so konfigurieren, dass der Privkey via OpenSSL-Engine im HSM bleibt.
- mit `curl` und `openssl s_client` nachweisen, dass der Handshake tatsaechlich ueber den HSM-Key gelaufen ist.
- **(Bloom 5 — evaluate)** entscheiden, ob `pkcs11-engine` (Engine-Modell) oder `pkcs11-provider` (Provider-Modell) fuer ein neues Deployment der richtige Pfad ist — und welche Distro-Realitaeten (OpenSSL-3-Migration, Engine-Deprecation) die Wahl in zwei Jahren erzwingen werden.

## Lab-Bezug

```bash
make import-cert            # Voraussetzung: signing-key + cert.pem
make gen-tls-cert           # Neues self-signed Cert CN=localhost (signiert vom HSM)
make tls-serve              # nginx im Container starten + curl-Test
```

Nachschlag zu TLS, Engine, Provider, PKCS#11-URI und Mechanism-Praefixen: [Glossar](../docs/glossar.md).

## Was macht der Server-Key im TLS-Handshake?

Drei mögliche Rollen, abhaengig von Cipher Suite:

| Mode | Rolle des Server-Privkey | Anforderung an HSM-Key |
|---|---|---|
| **TLS 1.3 (alle Suites)** | Signiert `CertificateVerify`-Nachricht ueber Handshake-Transcript | `CKA_SIGN=TRUE`, RSA-PSS oder ECDSA |
| **TLS 1.2 ECDHE-RSA** | Signiert `ServerKeyExchange`-Parameter | `CKA_SIGN=TRUE`, RSA-PKCS oder PSS |
| **TLS 1.2 RSA-Kex** (deprecated) | Entschluesselt Pre-Master-Secret | `CKA_DECRYPT=TRUE`, RSA-OAEP/PKCS1 |
| **TLS 1.2 DHE-RSA** | Wie ECDHE-RSA, RSA signiert DH-Params | `CKA_SIGN=TRUE` |

Praktisch: ein **CKA_SIGN-only**-Key (unser `signing-key`) reicht fuer alle modernen Cipher Suites. Den letzten Fall (RSA-Kex) sollte man ohnehin nicht mehr verwenden — kein Perfect Forward Secrecy. Der Lab-`signing-key` ist seit 0.16.0 strikt sortenrein und antwortet auf jeden Decrypt-Versuch mit `CKR_KEY_FUNCTION_NOT_PERMITTED` — `make validate-key-usage` haengt das aus.

In `nginx-pkcs11.conf` deshalb explizit:

```nginx
ssl_protocols TLSv1.2 TLSv1.3;
ssl_ciphers ECDHE+AESGCM:CHACHA20;
```

Das schliesst RSA-Kex aus und vermeidet `CKR_KEY_FUNCTION_NOT_PERMITTED` beim Handshake.

## OpenSSL-Engine-Bruecke

nginx hat keinen eingebauten PKCS#11-Support — es nutzt OpenSSL. Die Bruecke ist die **`libengine-pkcs11-openssl`** (Paket `libengine-pkcs11-openssl`, Engine `pkcs11`, Lib-Maintainer "OpenSC"). Konfiguration in zwei Schritten:

**1. `OPENSSL_CONF`** zeigt auf eine Datei, die die Engine lädt:

```ini
openssl_conf = openssl_init

[openssl_init]
engines = engine_section

[engine_section]
pkcs11 = pkcs11_section

[pkcs11_section]
engine_id = pkcs11
dynamic_path = /usr/lib/x86_64-linux-gnu/engines-3/pkcs11.so
MODULE_PATH = /usr/lib/softhsm/libsofthsm2.so
init = 0
```

**2. `ssl_certificate_key` in der nginx-Config** referenziert den Engine-Key:

```nginx
ssl_certificate_key engine:pkcs11:pkcs11:token=dev-token;object=signing-key;type=private;pin-value=987654;
```

Format-Erklaerung: `engine:<engine-name>:<key-uri>`. Erst kommt `engine:`, dann der Engine-Name (`pkcs11`), dann die PKCS#11-URI (selbst mit `pkcs11:` als Schema). Die doppelte `pkcs11:` ist also kein Tippfehler.

## Nicht-pkcs11-engine: pkcs11-provider

OpenSSL 3+ deprecated den klassischen Engine-Mechanism zugunsten von **Providers**. Der modernere Weg ist `pkcs11-provider` (Paket `pkcs11-provider`, neueres Projekt von Simo Sorce). Mit Provider sieht die nginx-Config etwas anders aus (URI direkt ohne `engine:`-Praefix), die Funktion bleibt identisch.

In diesem Lab nutzen wir die Engine, weil sie in Debian 13 standardmaessig verfuegbar ist und mit jedem nginx ≥ 1.7.9 funktioniert.

## HAProxy- und Apache-Analoga (Cookbook)

**HAProxy** (>= 2.2 mit OpenSSL-Engine-Support):

```haproxy
global
    ssl-engine pkcs11

frontend tls
    bind *:8443 ssl crt /path/to/tls-cert.pem \
        crt-list /path/to/crt-list.txt
# In der crt-list:
# /path/to/tls-cert.pem [bind-options] keyid=pkcs11:token=dev-token;object=signing-key;type=private
```

**Apache mod_ssl**:

```apache
SSLEngine on
SSLCertificateFile /path/to/tls-cert.pem
SSLCertificateKeyFile "pkcs11:token=dev-token;object=signing-key;type=private;pin-value=987654"
SSLOpenSSLConfCmd Options PKCS11
```

Voraussetzung: `mod_ssl` ist gegen ein OpenSSL mit Engine-Support gebaut, `OPENSSL_CONF` zeigt auf die Engine-Config (analog nginx).

## Den Handshake nachweisen

`curl -v` zeigt das TLS-Subject, die Cipher Suite und ob der Hostname zur SAN passt. Wer noch tiefer rein will, nutzt:

```bash
openssl s_client -connect localhost:8443 -servername localhost -showcerts -CAfile lab/work/tls-cert.pem </dev/null
```

Ausgabe enthaelt:
- `Server certificate: ... CN = localhost`
- `subject=CN = localhost`
- `Cipher    : TLS_AES_256_GCM_SHA384` (oder ECDHE-RSA-AES...)
- `Verify return code: 0 (ok)`

Wer sehen will, dass tatsaechlich der HSM signiert hat (und nicht etwa ein gecachter Key irgendwo), kann `nginx` parallel mit `strace -f -e openat` starten — der `nginx`-Prozess oeffnet `libsofthsm2.so` und ruft `softhsm2.conf` auf. Oder `pkcs11-spy` zwischenhaengen (siehe Kapitel 8).

## Stolperfallen

- **PIN im Config-File**: `pin-value=987654` in der nginx-Config landet auf der Platte. Produktion: PIN ueber `ssl_password_file` (nginx-Mechanism, Datei kann mit 0600 + root geschuetzt sein) oder ueber `pin-source=|/path/to/pin-script` in der PKCS#11-URI.
- **SELinux/AppArmor**: nginx-User braucht Lesezugriff auf `libsofthsm2.so` und ggf. `/var/lib/softhsm/tokens/`. Auf Distros mit aktivem MAC laufen sonst stille Permission-Denials.
- **PKCS#11-Lib im Worker-Prozess**: nginx forked Worker. Die Library muss fork-safe sein oder nach `fork()` re-initialisiert werden. SoftHSM ist fork-safe, manche Hardware-HSM-Libs nicht — dann mit `worker_processes 1` arbeiten oder ein dediziertes TLS-Termination-Frontend nutzen.
- **Engine-Path haengt von der Distro ab**: Debian/Ubuntu hat `engines-3/pkcs11.so` unter `/usr/lib/<triplet>/`, Fedora/Suse unter `/usr/lib64/engines-3/`. Das `find`-Snippet in `51-tls-serve-and-test.sh` haendelt das.

## Eigenexperiment

- Aendere im nginx-Config `ssl_protocols` auf nur `TLSv1.2` und teste mit `curl --tls-max 1.2`. Verifiziere via `-v`, dass die Cipher Suite jetzt eine ECDHE-RSA-Variante ist.
- Nimm das `pin-value=` aus der URI raus und probiere — nginx scheitert mit `Cannot load private key` (der Engine-Login findet keine PIN).
- Bau den nginx-Container mit `pkcs11-spy` als `MODULE_PATH` (siehe Kapitel 8) und beobachte den Handshake-RPC-Trail: `C_OpenSession`, `C_Login`, `C_FindObjects`, `C_SignInit` (CKM_RSA_PKCS oder PSS), `C_Sign`, ...

Strukturierte Aufgaben in [`exercises/12-tls-mit-hsm.md`](../exercises/12-tls-mit-hsm.md).

## Selbsttest

<details>
<summary>1. Welche Cipher Suites schliessen <code>CKR_KEY_FUNCTION_NOT_PERMITTED</code> beim Handshake aus, wenn der Server-Key strikt CKA_SIGN-only ist?</summary>

Alle modernen ECDHE-RSA-/ECDHE-ECDSA-Suiten und alle TLS-1.3-Suiten — sie nutzen den Server-Privkey nur fuer **Signaturen**, nicht fuer Decrypt. Auszuschliessen ist die TLS-1.2-**RSA-Kex**-Variante, die den Privkey zum Decrypt des Pre-Master-Secret braucht. Praktisch: `ssl_protocols TLSv1.2 TLSv1.3` plus `ssl_ciphers ECDHE+AESGCM:CHACHA20` reicht.
</details>

<details>
<summary>2. Was bedeutet das doppelte <code>pkcs11:</code> in <code>ssl_certificate_key engine:pkcs11:pkcs11:token=...</code> ?</summary>

`engine:<engine-name>:<key-uri>`. Erst der Engine-Schalter (`engine:`), dann der Engine-Name (`pkcs11`), dann die PKCS#11-URI (selbst mit `pkcs11:` als Schema nach RFC 7512). Kein Tippfehler, sondern eine geschachtelte Adressierung — Engine kapselt das Protokoll, das Protokoll hat sein eigenes URI-Schema.
</details>

<details>
<summary>3. Warum ist die PIN im <code>pin-value=</code>-Teil der URI in Production problematisch — und welcher nginx-Mechanism loest es?</summary>

`pin-value=987654` steht im Klartext in der nginx-Config, also auf der Platte und in jedem Backup. Loesungen: `ssl_password_file` (nginx-Mechanism, Datei mit 0600 + root als Owner), oder `pin-source=|/path/to/pin-script` in der PKCS#11-URI — das Skript holt die PIN aus Vault/SSM und gibt sie auf stdout aus. Beide schaffen ein Indirekt-Modell, in dem die PIN nicht in der Service-Config landet.
</details>

<details>
<summary>4. <strong>(evaluate)</strong> Ein Team will den HSM-TLS-Pfad auf Edge-Pods skalieren: 200 Pods, einer pro Region, ein zentraler HSM-Cluster. Worker-Latenz beim HSM-Roundtrip liegt bei ~8 ms. Welcher Aspekt limitiert zuerst — und welche zwei Architektur-Knoepfe drehst du, bevor du mehr HSM-Hardware kaufst?</summary>

Limit ist die **HSM-Session-Quote** (Pro-Partition-Limits sind oft zweistellig), nicht der RSA-Rechen-Durchsatz. Knopf 1: **TLS-1.3 + Session-Resumption** — der HSM-Sign-Call faellt nur beim initialen Handshake an; Resumption ueber PSK braucht keinen HSM. Knopf 2: **OCSP-Stapling und Session-Cache** statt jeder Verbindung neu — beide reduzieren die Anzahl HSM-Operationen pro Geschaeftsvorgang. Erst wenn beides sichtbar genutzt ist und Session-Quote nachweisbar saturiert, lohnt sich HSM-Cluster-Ausbau. Die Falle: die Naturreaktion "mehr Pods → mehr nginx-Worker → mehr Sessions" pumpt Last in den HSM, die durch TLS-Protokollwahl gar nicht haette entstehen muessen.
</details>
