# Changelog

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
