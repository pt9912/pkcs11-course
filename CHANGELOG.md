# Changelog

## 0.11.0 - 2026-05-30

### Hinzugefügt
- Kapitel 19 `course/19-ssh-mit-hsm.md`: SSH-Pubkey-Authentifizierung Schritt fuer Schritt, `ssh-keygen -D` zum Pubkey-Export, drei PIN-Varianten (Prompt/ASKPASS/ssh-agent), Smartcard- und YubiKey-Aequivalenz, Agent-Forwarding-Risiko, Stolperfallen rund um StrictModes und Distros ohne PKCS#11-Compile-Option.
- Uebung 13 `exercises/13-ssh-mit-hsm.md` + Loesung `solutions/13-ssh-mit-hsm.md`: Pubkey-Extract, SSH-Roundtrip, Ohne-Provider-Test, Falsche-PIN-Beobachtung, ssh-agent-Bonus.
- Lab-Skripte `lab/scripts/52-ssh-extract-pubkey.sh` (kein PIN noetig, Pubkey-Read passiert ohne Login) und `lab/scripts/53-ssh-start-and-test.sh` (sshd als unprivilegierter User auf Port 2222, authorized_keys aus HSM-Pubkeys, ssh-Login via SSH_ASKPASS-Helfer fuer die PIN, Cleanup im trap).
- Makefile-Targets: `ssh-pubkey`, `ssh-test`.
- `lab/Dockerfile`: `openssh-client` + `openssh-server`.

### Geändert
- `course/00-kursuebersicht.md`: Lernpfad um Kapitel 19 erweitert.

## 0.10.0 - 2026-05-30

### Hinzugefügt
- Kapitel 18 `course/18-tls-mit-hsm.md`: TLS-Handshake-Rolle des Server-Keys (TLS 1.3 CertificateVerify-Signature, TLS 1.2 Varianten), `libengine-pkcs11-openssl` Bruecke, nginx+HAProxy+Apache-Configs, pkcs11-provider als modernere Alternative.
- Uebung 12 `exercises/12-tls-mit-hsm.md` + Loesung `solutions/12-tls-mit-hsm.md`: TLS-Cert ausstellen, nginx-Roundtrip, Cipher-Suite ueber openssl s_client, PIN-im-Config-Risiko, pkcs11-spy-Trace beim Handshake.
- Lab-Skripte `lab/scripts/50-issue-tls-cert.sh` (self-signed CN=localhost+SAN, signiert vom HSM-Signing-Key) und `lab/scripts/51-tls-serve-and-test.sh` (nginx mit pkcs11-Engine starten, curl-Verify, Cleanup).
- `lab/nginx/nginx-pkcs11.conf.template`: vollstaendige nginx-Config mit `ssl_certificate_key "engine:pkcs11:..."` (Quotes Pflicht wegen Semikolons in der URI), Runtime-Pfade nach /tmp umgeleitet fuer rootless-User.
- Makefile-Targets: `gen-tls-cert`, `tls-serve`.
- `lab/Dockerfile`: `curl`, `nginx-light`, `procps` ergaenzt.

### Geändert
- `course/00-kursuebersicht.md`: Lernpfad um Kapitel 18 erweitert.

## 0.9.0 - 2026-05-30

### Hinzugefügt
- Kapitel 17 `course/17-session-pooling.md`: Thread-Safety pro Binding (SunPKCS11/miekg/pkcs11/Pkcs11Interop), Pool-Patterns, `C_Login`-Lebensdauer (anwendungsweit, nicht session-weit), fork()-Falle, realistischer Speedup-Vergleich SoftHSM vs reale HSMs.
- Uebung 11 `exercises/11-session-pooling.md` + Loesung `solutions/11-session-pooling.md`: Baseline-Benchmark, `CKR_OPERATION_ACTIVE`-Anti-Pattern provozieren, Pool-Groesse variieren, Login-Doppelfehler, fork-Diskussion.
- Sprach-Demos `lab/{go,csharp,java,kotlin}/pkcs11-pool-demo/`: sequenziell vs parallel mit Pool-Size 8 und 10000 HMAC-SHA256-Operationen, Speedup-Report.
  - Go: `chan pkcs11.SessionHandle` als Pool, `atomic.Int64`-Counter.
  - C#: `BlockingCollection<ISession>` + `Task.WhenAll`, `AppType.MultiThreaded`.
  - Java/Kotlin: `BlockingQueue<Mac>` + `ExecutorService` (Mac-Pool statt Session-Pool, weil SunPKCS11 Sessions intern selbst poolt).
- Wrapper-Skripte `46-49-*-pool-demo.sh`.
- Makefile-Targets: `go-pool-demo`, `csharp-pool-demo`, `java-pool-demo`, `kotlin-pool-demo`.

### Geändert
- `course/00-kursuebersicht.md`: Lernpfad um Kapitel 17 erweitert.
- `Makefile clean`: neue Pool-Demo-Build-Verzeichnisse aufgenommen.

## 0.8.0 - 2026-05-30

### Hinzugefügt
- Kapitel 16 `course/16-hmac.md`: HMAC, `CKK_GENERIC_SECRET`-Keys, MAC-vs-Signatur, drei Verify-Pfade (`C_Verify` / recompute+compare / non-CT-anti-pattern), HS256-JWT als Praxis-Use-Case.
- Uebung 10 `exercises/10-hmac.md` + Loesung `solutions/10-hmac.md`: Bash-Roundtrip, Tamper-Erkennung, Sprach-Demo + JWT, Cross-Sprach-Verifikation via pkcs11-tool, Bonus mit Hash-Familie-Wechsel.
- Lab-Skripte `lab/scripts/39-generate-hmac-key.sh` (GENERIC:32 auf ID=05), `40-hmac-sign.sh` (`SHA256-HMAC`, 32-Byte-MAC), `41-hmac-verify.sh` (`C_Verify`-Pfad via pkcs11-tool).
- Sprach-Demos `lab/{go,csharp,java,kotlin}/pkcs11-hmac-demo/`: Raw HMAC + HS256-JWT-Roundtrip in einem Programm.
  - Go: `SignInit`/`Sign` + `VerifyInit`/`Verify`, JWT-Encoder mit `encoding/base64.RawURLEncoding`.
  - C#: `session.Sign` + `session.Verify(...out bool)`, JWT-Encoder mit manuellem Base64URL (statt Microsoft.IdentityModel-Dep).
  - Java/Kotlin: JCA `Mac.getInstance("HmacSHA256", sunPkcs11Provider)`, Verify als recompute + `MessageDigest.isEqual` (JCA-Mac hat kein eingebautes verify), minimaler JSON-Encoder ohne Lib-Dep.
- Wrapper-Skripte `42-45-*-hmac-demo.sh`.
- Makefile-Targets: `gen-hmac`, `hmac-sign`, `hmac-verify`, `go-hmac-demo`, `csharp-hmac-demo`, `java-hmac-demo`, `kotlin-hmac-demo`.

### Geändert
- `course/00-kursuebersicht.md`: Lernpfad-Tabelle um Kapitel 16 erweitert.
- `Makefile clean`: neue HMAC-Demo-Build-Verzeichnisse aufgenommen.

## 0.7.0 - 2026-05-30

### Hinzugefügt
- Kapitel 15 `course/15-streaming.md`: PKCS#11 Multi-Part Ops (`C_*Init`/`C_*Update`/`C_*Final`), Mechanism-Eignungstabelle, Speicherbeweis fuer 100MB-Files, AES-CBC-PAD vs AES-GCM Streaming-Eigenschaften.
- Uebung 09 `exercises/09-streaming.md` + Loesung `solutions/09-streaming.md`: Bash-Round-Trip, pkcs11-spy-Beweis (Update-Calls zaehlen), Sprach-Demo, Speicher-Messung, Chunk-Size-Experiment.
- Lab-Skripte `lab/scripts/30-generate-aes-stream-key.sh` (AES-256 als CKO_SECRET_KEY mit `CKA_ENCRYPT`/`CKA_DECRYPT`, ID=04 zur Vermeidung von Konflikten), `31-stream-sign.sh` (`SHA256-RSA-PKCS`, Token-side Hash), `32-stream-verify.sh`, `33-stream-encrypt.sh` (`AES-CBC-PAD` mit zufaelligem IV, persistiert als Hex), `34-stream-decrypt.sh` (Round-Trip-Check via `diff`).
- Sprach-Demos `lab/{go,csharp,java,kotlin}/pkcs11-stream-demo/`: 100MB-Sign + 100MB-Encrypt + Decrypt in einem Programm.
  - Go: expliziter `SignUpdate`-Loop und gemeinsame `streamUpdateFinal`-Abstraktion fuer Encrypt/Decrypt.
  - C#: nutzt `ISession.Sign(mech, key, Stream)` und `ISession.Encrypt(mech, key, in, out)` Stream-Ueberladungen.
  - Java/Kotlin: `Signature.update(buf, off, len)` und `Cipher.update` via `CipherInputStream`-Pattern; SunPKCS11 exponiert den AES-Secret-Key direkt ueber CKA_LABEL als KeyStore-Alias.
- Wrapper-Skripte `35-38-*-stream-demo.sh`.
- Makefile-Targets: `gen-aes-stream`, `stream-sign`, `stream-verify`, `stream-encrypt`, `stream-decrypt`, `go-stream-demo`, `csharp-stream-demo`, `java-stream-demo`, `kotlin-stream-demo`.

### Geändert
- `course/00-kursuebersicht.md`: Lernpfad-Tabelle um Kapitel 15 erweitert.
- `Makefile clean`: neue Stream-Demo-Build-Verzeichnisse aufgenommen.

## 0.6.0 - 2026-05-30

### Hinzugefügt
- Kapitel 14 `course/14-cms-signatur.md`: CMS/PKCS#7-Dokumentsignatur (RFC 5652), detached SignedData, signed attributes (contentType/signingTime/messageDigest), attached-vs-detached, die zwei wiederkehrenden HSM-zu-CMS-Bruecken-Probleme.
- Uebung 08 `exercises/08-cms.md` + Loesung `solutions/08-cms.md`: vier Aufgaben (Bash-Sign/Verify, Tamper-Erkennung, Sprach-Demo, ASN.1-Lesen).
- Lab-Skripte `lab/scripts/24-cms-sign.sh` (openssl cms -sign via pkcs11-engine, detached, signing-key ID=01) + `25-cms-verify.sh` (mit CAfile=signer-cert).
- Sprach-Demos:
  - `lab/go/pkcs11-cms-demo/` — digitorus/pkcs7 + crypto.Signer-Adapter, der DigestInfo wrappt und CKM_RSA_PKCS aufruft.
  - `lab/csharp/Pkcs11CmsDemo/` — BouncyCastle.Cryptography mit eigener ISignatureFactory; .NET-SignedCms ist auf Linux nicht HSM-tauglich (OpenSSL prueft n=p*q).
  - `lab/java/pkcs11-cms-demo/` und `lab/kotlin/pkcs11-cms-demo/` — BouncyCastle bcpkix-jdk18on, JcaContentSignerBuilder mit SunPKCS11-Provider.
  - Wrapper-Skripte `26-29-*-cms-demo.sh` mit OpenSSL-Cross-Verify nach jeder Sprach-Demo.
- Makefile-Targets: `cms-sign`, `cms-verify`, `go-cms-demo`, `csharp-cms-demo`, `java-cms-demo`, `kotlin-cms-demo`.

### Geändert
- `course/00-kursuebersicht.md`: Lernpfad-Tabelle um Kapitel 14 erweitert.
- `Makefile clean`: neue CMS-Demo-Build-Verzeichnisse aufgenommen.

## 0.5.0 - 2026-05-30

### Hinzugefügt
- Kapitel 13 `course/13-verschluesselung.md`: hybride Verschluesselung mit RSA-OAEP-Wrap + AES-256-GCM, inkl. Wrap-Key-Policy, OAEP-Parameter-Falle und der SoftHSM/SunPKCS11-Quirks.
- Uebung 07 `exercises/07-encrypt.md` + Loesung `solutions/07-encrypt.md`: vier Aufgaben (Keygen, Bash-Encrypt/Decrypt, Tampering-Erkennung, Sprach-Demo).
- Lab-Skripte `lab/scripts/16-generate-rsa-wrap.sh` (RSA-2048 mit `CKA_DECRYPT/CKA_UNWRAP/CKA_WRAP`, sortenrein ohne `CKA_SIGN`, ID=03 zur Vermeidung des Konflikts mit dem EC-Key auf ID=02), `17-encrypt-hybrid.sh`, `18-decrypt-hybrid.sh`, `19-issue-wrap-cert.sh` (Plumbing-Cert fuer den SunPKCS11-KeyStore-Alias).
- AES-GCM-Helper `lab/scripts/_aes_gcm.py` (python3-cryptography).
- Sprach-Demos: `lab/java/pkcs11-encrypt-demo/`, `lab/go/pkcs11-encrypt-demo/`, `lab/kotlin/pkcs11-encrypt-demo/`, `lab/csharp/Pkcs11EncryptDemo/`. Wrapper-Skripte `20-23-*-encrypt-demo.sh`. Java/Kotlin implementieren OAEP-Unpadding in Software, weil SunPKCS11 keinen OAEP-Cipher exponiert.
- Makefile-Targets: `gen-rsa-wrap`, `encrypt`, `decrypt`, `issue-wrap-cert`, `java-encrypt-demo`, `go-encrypt-demo`, `kotlin-encrypt-demo`, `csharp-encrypt-demo`.
- `lab/Dockerfile`: `python3` + `python3-cryptography` fuer den AES-GCM-Helper.

### Geändert
- `course/00-kursuebersicht.md`: Lernpfad-Tabelle um Kapitel 13 erweitert.
- `Makefile clean`: neue Demo-Projekt-Build-Verzeichnisse mit aufgenommen.

## 0.4.0 - 2026-05-29

### Geändert
- `docs/api.md`: `CKM_ECDSA`-Beschreibung korrigiert — Hash-Truncation auf Curve-Order-Laenge folgt FIPS 186-4 §6.4 und wird von SoftHSM/OpenSC implizit gemacht; manche HSMs erwarten das vom Aufrufer.
- `docs/api.md`: `CKF_RW_SESSION`-Begruendung praezisiert — wird nur fuer Objekt-/PIN-Aenderungen verlangt, nicht fuer `C_Login(CKU_USER)` oder Sign/Verify (PKCS#11 v2.40 §11.2 / §11.6).
- `docs/api.md`: `Security.addProvider(...)` als optional markiert (nur fuer globalen JCA-Lookup ohne Provider-Argument noetig).
- `course/11-ec-und-pss.md`: EdDSA-Tabellenzeile entkoppelt FIPS 186-5 (Signaturstandard) und FIPS 140-2/3 (Modulzertifizierung); EdDSA ist in FIPS-140-2-zertifizierten Modulen nicht erlaubt.
- `course/11-ec-und-pss.md`: expliziter Lab-Disclaimer, dass `CKM_EDDSA` im Kurs-Image nicht verfuegbar ist und es deshalb kein `make sign-eddsa`-Target gibt.
- `course/06-java-sunpkcs11.md`: SunPKCS11-Override-Regel ergaenzt — `slot = <ID>` hat Vorrang vor `slotListIndex`; die Demo haengt `slot =` deshalb hinten an.
- `course/05-zertifikate.md` / `lab/scripts/08-import-cert.sh`: Hinweis ergaenzt, dass die `pin-value`-im-Pfad-Form libp11-Kurzform ist und nicht streng RFC 7512 entspricht.
- `course/10-abschlussprojekt.md`: Audit-Log-Hinweis zur CKR-Code-Extraktion verlangt jetzt Stack-Walk durch die Cause-Kette statt nur `getCause().getMessage()`.
- `course/12-sprachbindings.md`: Fussnote ergaenzt, dass "Zertifikat noetig?" sich auf den Java-`KeyStore`-Alias bezieht, nicht auf PKCS#11 selbst.
- `course/04-signieren-und-verifizieren.md`: PSS-Halbsatz ergaenzt, dass Anwendung-vs.-Token-Hashing davon abhaengt, ob `CKM_RSA_PKCS_PSS` oder `CKM_SHA256_RSA_PKCS_PSS` gewaehlt wird.
- `lab/kotlin/.../KotlinPkcs11Demo.kt`: PIN-Wipe nutzt jetzt das explizite `'\u0000'`-Escape statt eines im Source-File versteckten rohen NUL-Bytes.
- `lab/csharp/Pkcs11Demo/Program.cs`: Kommentar ergaenzt, dass der `pin`-String wegen CLR-String-Immutabilitaet nicht zuverlaessig getilgt werden kann; in Produktion `SecureString` oder direkt `byte[]` aus dem Secret-Backend.
- `lab/scripts/04-generate-rsa.sh`: nutzt jetzt `PKCS11_KEY_LABEL`/`PKCS11_KEY_ID` analog zu `08-import-cert.sh` und vergleicht das Label per `awk`-Field-Match (robust gegen Regex-Meta im Label).
- `lab/scripts/01-init-token.sh`, `lab/scripts/08-import-cert.sh`, `lab/scripts/09-generate-ec.sh`: Label-Existenz-Checks vereinheitlicht auf `awk`-Field-Match (robust gegen Regex-Meta im Label).
- `docs/cheatsheet.md`: Mechanismus-Namen-Hinweis bei der "Anwendung hasht"-Variante geschaerft (`pkcs11-tool`-Mechanismus heisst `RSA-PKCS-PSS`).
- `exercises/05-kotlin.md`: Vorbereitung entschlackt — `make kotlin-demo` haengt die `import-cert -> gen-rsa -> init-token`-Kette selbst ein, einzelne Targets nur fuer isoliertes Testen.

## 0.3.0 - 2026-05-29

### Hinzugefügt
- Kapitel 09: Session-Pooling und Threading-Modelle pro Stack (SunPKCS11, miekg/pkcs11, Pkcs11Interop) mit Java- und Go-Skizzen.
- Kapitel 08: `pkcs11-spy`-Beispiel-Log mit Mini-Trace einer Signatur, plus Lehrnotizen zu `CKA_ID`-Encoding und PIN-Leak.
- Kapitel 11: EC-Kurven-Vergleichstabelle (P-256/384/521, secp256k1, Brainpool, Ed25519/448) inkl. HSM-Verfuegbarkeit.
- Kapitel 10: Audit-Log-Schema (JSON Lines) inkl. Felder, Regeln und Beispielen fuer Erfolgs- und Fehler-Events.
- Kapitel 07: Konkretes Java/Micronaut-Skelett (Configuration/Factory/Service/Controller) statt nur Architektur-Bullets.
- Kapitel 05: CSR-Workflow als Code-Block fuer die Produktionsalternative.
- README: Hinweis auf `apply.sh` und auf die Reihenfolgen-Diskrepanz zwischen Dateinummern und Lernpfad.

### Geändert
- PSS-Mechanismen vereinheitlicht: `08-debugging.md` und `11-ec-und-pss.md` listen jetzt `CKM_RSA_PKCS_PSS` (Anwendung hasht) **und** `CKM_SHA256_RSA_PKCS_PSS` (Token hasht); Cheatsheet zeigt die im Lab tatsaechlich benutzte Variante.
- ECDSA: `11-ec-und-pss.md` erklaert, warum das Lab `CKM_ECDSA` mit applikationsseitigem SHA-256 benutzt (SoftHSM v2 listet nur `CKM_ECDSA`), und zeigt die `ECDSA-SHA256`-Variante fuer produktive HSMs.
- `05-zertifikate.md`: Schritt 1 explizit als "Self-Signed-Erzeugung" formuliert (Skript ist self-signed, nicht CSR).
- CHANGELOG-Eintrag der 0.2.0 von "JDK-17-Referenz" auf "JDK-21-Referenz" korrigiert (Drift gegen Dockerfile und README).
- `lab/csharp/Pkcs11Demo/Program.cs`: PIN-Bytes werden nach `Login` per `Array.Clear` getilgt, analog zu Java/Kotlin.
- `lab/Dockerfile`: `chmod 0777` als Lab-only annotiert.
- `lab/scripts/06-sign.sh`: Hinweis, dass Pubkey-Read ohne Login auf produktiven HSMs nicht garantiert ist.
- `course/06-java-sunpkcs11.md`: Verweist explizit auf den Lab-Disclaimer in `softhsm.cfg`.

## 0.2.0 - 2026-05-28

### Hinzugefügt
- Kapitel 11: ECDSA und RSA-PSS mit JCA-Mapping.
- Skript `08-import-cert.sh`: Self-Signed-Cert via OpenSSL `pkcs11`-Engine, Import als Token-Objekt.
- Skripte `09-generate-ec.sh`, `10-sign-ec.sh`, `11-verify-ec.sh`, `12-sign-pss.sh`.
- Mechanism-Mapping-Tabelle in `08-debugging.md` (`CKM_*` ↔ `pkcs11-tool` ↔ OpenSSL ↔ JCA).
- FIPS- und Cloud-HSM-Abschnitt in der Produktionscheckliste.
- `.gitignore`, `.dockerignore`.

### Geändert
- `Pkcs11Demo.java` schlägt jetzt hart fehl, wenn kein Zertifikat im Token liegt. Der vorherige Wegwerf-KeyPair-Fallback umging das eigentliche Lernziel.
- `make java-demo` ruft `import-cert` als Voraussetzung auf.
- `lab/Dockerfile`: `libengine-pkcs11-openssl` ergänzt.
- `lab/docker-compose.yml`: ENV-Duplikate entfernt, Dockerfile ist Single Source of Truth.
- `04-generate-rsa.sh`: `--usage-decrypt` entfernt, Key ist jetzt sortenrein Signier-Key.
- `06-sign.sh`: kaputter mehrzeiliger `printf` korrigiert.
- `02-lab-setup.md`: JDK-Version auf 17 korrigiert (war Doku-Drift).
- README: `apply.sh`-Hinweis geklärt, v2.40-Spec-Link ergänzt, JDK-21-Referenz.
- Cheatsheet komplett überarbeitet: PSS, EC, Cert-Import, häufige Stolperer.
- Übung 03 mit konkreten Erfolgskriterien.

## 0.1.0 - 2026-05-28

- Initialer PKCS#11-Kurs.
- Docker-Lab mit SoftHSM, OpenSC, OpenSSL und JDK.
- Java-Signaturdemo über SunPKCS11.
- Übungen und Musterlösungen.
- Debugging- und Produktionscheckliste.
