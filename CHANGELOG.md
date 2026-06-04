# Changelog

## 0.21.0 - 2026-06-04

### Hinzugefuegt
- Conceptual-Change-Hooks ("Bevor du anfaengst — was vermutest du?") in `course/04-signieren-und-verifizieren.md` (DigestInfo-Falle), `course/08-debugging.md` (Object-Handle-Lebenszeit), `course/20-key-wrap.md` (Wrap-ist-nicht-Backup) und `course/25-rfc3161-timestamps.md` (signingTime-ist-nicht-Beweis). Schliesst F4 aus dem 0.20-Folge-Review — vier neuralgische Misconceptions, die der Fliesstext schon implizit aufloest, sind jetzt metakognitiv explizit aktiviert.
- Vierte Selbsttest-Frage mit **Evaluate**-Charakter (Bloom 5) in `course/13-verschluesselung.md`, `course/14-cms-signatur.md`, `course/18-tls-mit-hsm.md`, `course/19-ssh-mit-hsm.md`, `course/20-key-wrap.md`, `course/21-pin-management.md`, `course/22-csr-und-ca-workflow.md`, `course/24-ecdh-hkdf.md`, `course/25-rfc3161-timestamps.md`. Schliesst F3: bisher war das einzige Vehikel fuer Bloom 5/6 die Uebungs-Reflexion; ein Lerner, der nur Kapitel liest, blieb auf Stufe 1-3. Jetzt enthaelt jedes Vertiefungskapitel ohne klares Eval-Item im Selbsttest ein Szenario-basiertes Mini-Eval-Item im selben `<details>`/`<summary>`-Pattern wie die anderen Fragen.
- Vorwissen-Selbstcheck in `course/00-kursuebersicht.md` mit vier Crypto-Basisbegriffen plus Ressourcen-Pointer (F9). Bisher war das Vorwissen implizit; jetzt expliziert ein 5-Zeilen-Block, was vor Kap. 01 sitzen muss.
- Curriculare Reduktionen als eigener Abschnitt in `course/00-kursuebersicht.md` (F10). Die vierte Spalte "Bewusst weggelassen" der 0.20-Lernpfad-Tabelle wird zu einer separaten Liste — Tabelle bleibt dreispaltig (Schritt/Kapitel/Begruendung), Reduktionen kommen darunter als kompakte Bullet-Liste. Gleicher Informationsgehalt, bessere Lesbarkeit auf schmalen Markdown-Renderern.
- `## Lab-Bezug`-Block in `course/10-abschlussprojekt.md` und `course/26-abschlussprojekt-advanced.md` (F2). Beide Capstones hatten den Block bisher nicht — er macht jetzt den Ausgangszustand explizit ("`make init-token gen-rsa import-cert`") und verweist auf die internen Strukturabschnitte. Pattern-Konsistenz zu den 24 anderen Inhaltskapiteln.
- `## Eigenexperiment`-Block in `course/03-token-und-objekte.md` (`CKA_EXTRACTABLE`-Einbahnstrasse selbst klicken) und `course/11-ec-und-pss.md` (ECDSA-DER-Format-Falle, PSS-Salt-Mismatch). Schliesst F7 — die letzten beiden Lab-Kapitel ohne Eigenexperiment-Pattern.
- `## Fehlerfaelle direkt ausfuehren — Devcontainer vs. Docker Compose` als neuer Referenzabschnitt in `course/02-lab-setup.md` mit Pattern A (Devcontainer) und Pattern B (Compose). Schliesst F8: die Wiederholung des Devcontainer-vs-Compose-Aufrufmusters in jeder ENV-basierten Fehlerfall-Uebung (`exercises/03`–`06`) ist extraneous load. Jetzt eine zentrale Referenz, die Uebungen verweisen drauf und reduzieren ihren Fehlerfall-Block auf "Compose-Service X, ENV Y, Befehl Z".
- `### Cookbook: fork-Falle ohne neue Demo reproduzieren` in `course/17-session-pooling.md`: Zwei-Terminal-Bash-Pattern, das den fork-Effekt mit dem bestehenden Lab-Code sichtbar macht. Ersetzt F6 — Uebung 11 Aufgabe 5 war als "mach es als Cookbook-Notiz" formuliert und damit nicht ausfuehrbar; jetzt liegt das Cookbook im Kapitel, die Uebung verweist darauf.

### Geaendert
- `course/12-sprachbindings.md`: Lernziel 4 traegt jetzt den expliziten `(Bloom 5 — evaluate)`-Marker (F5). Inhaltlich war es schon ein Evaluate-Item, aber asymmetrisch zur 0.20-Konvention "Marker in jedem Vertiefungskapitel 13–25". Jetzt einheitlich.
- `exercises/03-java.md`, `exercises/04-go.md`, `exercises/05-kotlin.md`, `exercises/06-csharp.md`: Fehlerfall-Bloecke verweisen auf den neuen `02-lab-setup.md`-Referenzabschnitt, statt Devcontainer-/Compose-Aufrufmuster jeweils einzeln zu wiederholen.
- `exercises/11-session-pooling.md` Aufgabe 5: umstrukturiert von "mach es als Cookbook-Notiz" auf "fuehre das Cookbook aus `course/17-session-pooling.md` aus". Reproduzierbar geworden ohne neue Lab-Binary.
- `solutions/README.md`: Tabelle um die seit 0.16 nachgereichten Vertiefungs-Loesungen (`17-random`, `18-ecdh-hkdf`, `19-rfc3161-timestamps`, `20-ec-und-pss`) und die beiden 0.20-Rahmen-Selbsttests (`00-glossar`, `21-production-audit`) ergaenzt (F1 — der einzige harte Nav-Bruch des Review).

### Didaktischer Hintergrund
- Diese Iteration setzt das eigene Folge-Review zur 0.20.0 um (Bezugsrahmen unveraendert: Biggs' konstruktives Alignment, Anderson/Krathwohl-Taxonomie, Cognitive Load Theory, Modell der Didaktischen Rekonstruktion).
- Adressiert werden konkret: (F1) Nav-Bruch in `solutions/README.md`, (F2) `## Lab-Bezug`-Pattern in den Capstones, (F3) Bloom-Coverage fuer reine Leser ueber alle Vertiefungskapitel, (F4) vier weitere Conceptual-Change-Stellen mit dokumentierter Misconception, (F5) Bloom-Marker-Konsistenz in Kap. 12, (F6) reproduzierbare fork-Falle ohne neue Demo-Binary, (F7) Eigenexperiment-Pattern in Kap. 03 und 11, (F8) Reduktion extraneous load in den vier Sprach-Uebungs-Fehlerfaellen, (F9) Vorwissen-Selbstcheck vor Kap. 01, (F10) bessere Lesbarkeit der Lernpfad-Tabelle.
- Die zehn Findings sind dem Bericht "Didaktik-Review PKCS#11-Kurs (Stand 0.20.0)" entnommen. Nichts an der Substanz des Kurses wurde umgebaut; alle Aenderungen sind additiv (neue Hooks, neue Eval-Fragen) oder Pattern-Vereinheitlichungen (Capstone-`Lab-Bezug`, Fehlerfall-Referenzpattern, solutions/README-Pflege).

## 0.20.0 - 2026-06-04

### Hinzugefuegt

- `exercises/00-glossar.md` + `solutions/00-glossar.md`: Vokabel-Selbsttest fuer die acht PKCS#11-Praefix-Familien (`CKR_`, `CKM_`, `CKA_`, `CKO_`, `CKK_`, `CKF_`, `CKU_`, `CKZ_`) plus Diagnose der fuenf haeufigsten `CKR_*`-Fehler. Schliesst das Kursziel "PKCS#11-Begriffe sauber erklaeren" (`course/00-kursuebersicht.md` Z. 11) als pruefbares Outcome.
- `exercises/21-production-audit.md` + `solutions/21-production-audit.md`: Production-Readiness-Audit gegen die zwoelf Produktionsfragen aus Kap. 09. Drei Aufgaben — Audit-Report (Befund/Soll/Aufwand), Risiko-Ranking (Showstopper/Operational Debt/Optimization), Schreibtisch-Migration einer Showstopper-Komponente. Schliesst das Outcome "abschaetzen, was sich bei echten HSMs aendert" (`course/00-kursuebersicht.md` Z. 20) als Artefakt. Musterloesung gegen die Micronaut-Skizze aus Kap. 07 durchgefuehrt.
- Konfrontations-Hooks ("Bevor du anfaengst — was vermutest du?") am Anfang von `course/01-grundlagen.md`, `course/13-verschluesselung.md`, `course/17-session-pooling.md`, `course/21-pin-management.md`: aktivieren die typischen Lerner-Vorstellungen (Schluessel=Datei, RSA-direkt verschluesselt grosse Dateien, Session=TCP-Socket, PIN-Counter wie ein Passwort-Lockout) und konfrontieren sie, bevor die korrekte Sicht aufgebaut wird. Conceptual-Change-Pattern nach Posner et al.
- Selbsttest-Bloecke (jeweils drei Closed-Form-Fragen mit Spoiler-Antworten via `<details>`/`<summary>`) am Ende **aller** Kapitel 01–26. Retrieval-Practice-Anker zwischen Kapiteln (Roediger/Karpicke); kein Mehraufwand zur Laufzeit, hoher Behaltensgewinn.
- Zusaetzliches Bloom-5/6-Lernziel ("entscheiden", "bewerten", "entwerfen") in jedem Vertiefungskapitel 13–25. Bisher dominierten Verben der Bloom-Stufen 2-3; die tatsaechliche kognitive Anforderung war damit unter Wert verkauft. Jetzt sichtbar als zusaetzliche Bullet-Zeile mit "(Bloom 5 — evaluate)"-/"(Bloom 6 — create)"-Praefix.
- Eigenexperimente in `course/05-zertifikate.md` und `course/06-java-sunpkcs11.md` (CKA_ID-Mismatch reproduzieren, Cert ohne Privkey, Default-Provider-Verify ueber nicht-extractable Pubkey). Schliesst die letzte Luecke im "Eigenexperiment in jedem Kapitel mit Lab-Bezug"-Pattern.

### Geaendert

- `course/00-kursuebersicht.md`: Lernpfad-Tabelle bekommt eine vierte Spalte "Bewusst weggelassen" — macht die curricularen Reduktionsentscheidungen pro Schritt explizit (z.B. CAdES-LT/LTA in Kap. 25, X25519 in Kap. 24, EdDSA-Hands-on in Kap. 11). Primaer fuer Adaptierende, hilft Erstlesern bei der Erwartungsbildung. Neue Schritte 0 (Glossar-Selbsttest) und 27 (Production-Audit) rahmen den Pfad. `Uebungs- und Loesungsstruktur` erwaehnt die zwei Rahmen-Uebungen und das Selbsttest-Block-Muster.
- `course/10-abschlussprojekt.md` und `course/26-abschlussprojekt-advanced.md`: impressionistische `## Bewertung` ersetzt durch eine **3-Stufen-Rubric** (Akzeptanz erfuellt / + Erweiterungen / Production-ready). Stufe 3 verlinkt jeweils auf `exercises/21-production-audit.md`. Kap. 26 schliesst mit einer Vergleichstabelle Track-1 vs. Track-2 entlang der drei Stufen.
- `course/13-verschluesselung.md` §"Drei Stolperfallen, ein Lab-Lauf": refaktoriert zum **Worked-Example-Pattern**. Der Bash-Pfad wird jetzt vollstaendig in sechs Schritten durchgearbeitet (mit Begruendung der `CKM_RSA_X_509`-Fallback-Entscheidung der Engine). Go, C# und Java/Kotlin folgen als **faded examples** mit je drei Leitfragen, die das Schema des Bash-Pfads gegen die jeweilige Sprach-Implementierung pruefen. Reduziert die element interactivity der vorherigen 4-Zeilen-Vergleichstabelle.
- `exercises/02-key-signature.md` bis `exercises/06-csharp.md` plus die zugehoerigen `solutions/`: Reflexionsfragen auf einen Bloom-Mindest-Mix gehoben (Recall, Analyse, Evaluate). Bisher waren die fruehen Uebungen Recall-/Understand-lastig (Bloom 1-2); jetzt enthaelt jede Uebung mindestens eine Evaluate-Frage mit Architektur-/Entscheidungs-Charakter (z.B. globale `Security.addProvider`-Falle, miekg-Schicht-Wahl, Linux-vs-Windows-Pkcs11Interop-Entscheidung). Loesungen entsprechend ergaenzt.
- `README.md`: zwei rahmende Selbsttest-Uebungen (00, 21) verlinkt; Hinweis auf Selbsttest-Bloecke pro Kapitel.

### Didaktischer Hintergrund

- Diese Iteration setzt das eigene Folge-Review zur 0.19.0 um (Bezugsrahmen unveraendert: Biggs' konstruktives Alignment, Anderson/Krathwohl-Taxonomie, Cognitive Load Theory, Modell der Didaktischen Rekonstruktion).
- Adressiert werden konkret: (a) zwei offene Outcomes aus `course/00-kursuebersicht.md` ohne Assessment-Vehikel (Begriffe sauber erklaeren, abschaetzen was bei echten HSMs anders ist), (b) verkaufte Bloom-Stufe der Vertiefungskapitel unter Wert (jetzt Bloom 5/6 explizit), (c) impressionistische Bewertung der Capstones (jetzt 3-Stufen-Rubric mit Cross-Track-Vergleichbarkeit), (d) Worked-Example-Effekt in Kap. 13 ungenutzt (jetzt 1+3-Pattern), (e) Bloom-uneven Reflexionsfragen in den fruehen Uebungen (jetzt Mindest-Mix), (f) keine retrieval practice zwischen Kapiteln (jetzt Selbsttest-Bloecke), (g) Lerner-Vorstellungen nicht explizit konfrontiert (jetzt Conceptual-Change-Hooks an den vier neuralgischen Stellen), (h) Eigenexperimente nicht systematisch (Luecken in Kap. 05, 06 geschlossen), (i) Reduktions-Entscheidungen unsichtbar (jetzt vierte Spalte im Lernpfad).

## 0.19.0 - 2026-06-04

### Hinzugefuegt
- `course/26-abschlussprojekt-advanced.md`: Track-2-Abschlussprojekt fuer Kap. 13–25. CMS-Signaturservice mit Session-Pool, JSON-Lines-Audit-Log, RFC-3161-Timestamp-Integration (CAdES-T). Architekturskizze, Akzeptanzkriterien, Erweiterungsideen (CAdES-LT, mTLS, OTel, Multi-Tenant, PIN-Rotation). Schliesst die Outcome-Luecke fuer die fortgeschrittenen Module (Kap. 10 deckt nur Kap. 01–11 ab).
- `exercises/20-ec-und-pss.md` + `solutions/20-ec-und-pss.md`: neue Uebung fuer Kap. 11. ECDSA-DER-Encoding-Falle reproduzieren (rohe `r||s` gegen OpenSSL), RSA-PSS-Spiegelparameter (Salt-Laenge, MGF-Hash), schriftliche Mechanism-Entscheidung fuer drei Anforderungs-Szenarien. Kap. 11 war bisher als "optionale Erweiterung" ohne eigene Uebung gefuehrt — Outcome "entscheiden, welcher Mechanism fuer neue Systeme sinnvoll ist" wird jetzt assessiert.
- `course/03-token-und-objekte.md` §`CKA_SENSITIVE` und `CKA_EXTRACTABLE`: neuer Abschnitt, der das Sicherheitsmodell der beiden Attribute frueh erklaert und auf Kap. 06, 13, 20 verweist. Bisher tauchten beide Attribute en passant in spaeteren Kapiteln auf, ohne dass das Einbahn-Verhalten von `CKA_EXTRACTABLE` (PKCS#11 §10.2.6) systematisch eingefuehrt war.
- `course/10-abschlussprojekt.md` §Cross-Language-Akzeptanz: neuer Abschnitt mit Cross-Stack-Verify-Anforderung und neuem Akzeptanzkriterium. Schliesst den Alignment-Gap zum deklarierten Multi-Sprach-Outcome (`course/00-kursuebersicht.md`).
- `docs/api.md` §1.1 Werkzeug-Pfad im Detail: neue Tabelle mit allen sechs Zugriffspfaden (`pkcs11-tool`, `engine_pkcs11`, `pkcs11-provider`, SunPKCS11, miekg/pkcs11, Pkcs11Interop), Abstraktionsebene, Lebensdauer pro Operation und Querverweisen in die Kurskapitel. Erklaert die "wann welcher Pfad gleich falsch ist"-Faelle (SunPKCS11 fuer AES-Wrap/OAEP/PIN, openssl ts fuer HSM-resident Signer).
- Antwortbloecke "Antworten zur Selbstkontrolle" in `solutions/01-token.md`, `solutions/02-key-signature.md`, `solutions/03-java.md`, `solutions/04-go.md`, `solutions/05-kotlin.md`, `solutions/06-csharp.md`. Hebt die Selbstevaluierbarkeit der Reflexionsfragen auf das Niveau der technischen `make verify`-Tests; 07–19 hatten die Bloecke bereits.

### Geaendert
- `course/00-kursuebersicht.md`: Lernpfad-Tabelle bekommt Begruendungs-Spalte fuer jede Sprung-Stelle (z.B. warum Kap. 08 vor Kap. 09/10, warum Kap. 11 vor Kap. 13/14, warum Kap. 17 nach Kap. 16). Schritt 26 ergaenzt fuer das Track-2-Abschlussprojekt.
- `course/01-grundlagen.md`: Mechanism-Distinktionen (`CKM_RSA_PKCS`, `CKM_SHA256_RSA_PKCS`, `CKM_RSA_PKCS_PSS`, `CKM_ECDSA_SHA256`) entschlackt — Kap. 01 fuehrt nur noch die Namen und Familien ein, die operative Semantik (DigestInfo-Falle, Wer-hasht-wer-paddet) wandert nach Kap. 04. Reduziert die Begriffsdichte des ersten Kapitels.
- `course/04-signieren-und-verifizieren.md`: neuer Abschnitt "Wer hasht, wer paddet?" mit RSA-Mechanism-Familien-Tabelle und DigestInfo-Falle. Sammelt das, was vorher in Kap. 01 stand, an der operativen Stelle.
- `course/06-java-sunpkcs11.md`: Verweis auf den neuen `CKA_SENSITIVE`/`CKA_EXTRACTABLE`-Abschnitt in Kap. 03; expliziter Hinweis, warum der Default-Provider-Pfad bei `CKA_EXTRACTABLE=false` fehlschlaegt.
- `course/11-ec-und-pss.md`: Verweis auf die neue Uebung 20 am Kapitelende.
- `docs/glossar.md`: Eintraege fuer `Sensitive` und `Extractable` geschaerft (PKCS#11-Spec-Referenz und Modul-Querverweise).
- `README.md`: Kursstruktur-Tabelle um Kap. 26 erweitert; Uebung 20 fuer Kap. 11 verlinkt (war "optionale Erweiterung").

### Didaktischer Hintergrund
- Diese Aenderungen setzen die Findings aus dem Didaktik-Review um (Bezugsrahmen: Biggs' konstruktives Alignment, Anderson/Krathwohl-Taxonomie, Cognitive Load Theory, Modell der Didaktischen Rekonstruktion). Geschlossen werden Lueken bei (a) Outcome-Assessment fuer Kap. 13–25 (Track-2-Projekt), (b) Multi-Sprach-Outcome (Cross-Language-Akzeptanz), (c) frueher Konzept-Einfuehrung von `CKA_EXTRACTABLE` (Schemaaufbau), (d) Begriffsdichte in Kap. 01 (intrinsic load), (e) Selbst-Evaluierbarkeit der Reflexionsfragen, (f) Transparenz der curricularen Reihenfolge.

## 0.18.0 - 2026-05-31

### Hinzugefügt
- Kapitel 25 `course/25-rfc3161-timestamps.md`: RFC-3161-Timestamps fuer CMS (CAdES-T-aequivalent). Erklaert, warum `signingTime` aus PKCS#9 fuer rechtsverbindliche Signaturen nicht reicht, TSA-Protokoll mit TSReq/TSToken/Nonce, `extendedKeyUsage=critical,timeStamping` (RFC 3161 §2.3), CAdES-BES/T/LT/A-Profile. Dokumentiert den Lab-Kompromiss: TSA-Signing-Key ist Software (PEM-Datei mode 0600), weil `openssl ts -reply` per `fopen()` laedt und keine pkcs11-engine-URIs versteht. CA-Key und Document-Signer-Key bleiben HSM-resident.
- Uebung 19 `exercises/19-rfc3161-timestamps.md` + Loesung `solutions/19-rfc3161-timestamps.md`: Bash-Roundtrip, vier Sprach-Demos mit Embedding, Cross-Sprach-Verify-Test, extKU-Manipulation als Negativ-Beweis, Reflexion zur Lab-Realitaet.
- Lab-TSA: `85-tsa-setup.sh` (Software-TSA-Key + CA-signiertes Cert mit kritischer `extendedKeyUsage=timeStamping`), `86-tsa-serve.sh` (Bash-Wrapper fuer Python-HTTP-Daemon), `_tsa_server.py` (minimaler `http.server`-basierter Wrapper um `openssl ts -reply`), `87-cms-tsa-sign.sh` + `88-cms-tsa-verify.sh` (Bash-Demo mit TSA-Daemon im Hintergrund), `89-92-{java,kotlin,csharp,go}-cms-tsa-demo.sh` (Sprach-Demo-Wrapper).
- Java-Demo `lab/java/pkcs11-cms-tsa-demo/`: SunPKCS11 fuer Doc-Signer, BouncyCastle bcpkix fuer CMSSignedDataGenerator + TimeStampRequestGenerator + Embedding via `SignerInformation.replaceUnsignedAttributes` + Verifier mit Hash-Check.
- Kotlin-Demo `lab/kotlin/pkcs11-cms-tsa-demo/`: idiomatischer Spiegel der Java-Variante.
- C#-Demo `lab/csharp/Pkcs11CmsTsaDemo/`: Pkcs11Interop fuer HSM-Sign + BouncyCastle.Cryptography 2.5.1 fuer CMS, TSP-Library aus dem gleichen Package, identisches Embedding-Pattern wie Java.
- Go-Demo `lab/go/pkcs11-cms-tsa-demo/`: miekg/pkcs11 fuer HSM-Sign + digitorus/pkcs7 (wie Modul 14) + digitorus/timestamp fuer TSReq/TSResp. **Bewusst kein Embedding** — digitorus/pkcs7 hat keine UnsignedAttributes-API. Demo gibt CMS und TSR als zwei Dateien aus, dokumentiert die Library-Limitierung.
- Make-Targets: `tsa-setup`, `tsa-serve`, `cms-tsa-sign`, `cms-tsa-verify`, `java-cms-tsa-demo`, `kotlin-cms-tsa-demo`, `csharp-cms-tsa-demo`, `go-cms-tsa-demo`.

### Geändert
- `README.md`: Modul 25 in der Kursstruktur-Tabelle und in den "Erweiterte Module"-Targets erfasst; "Kapitel 13-25" im Header; Roadmap-Hinweis im Materialien-Block auf "alle urspruenglichen Themen erledigt" geaendert.
- `course/00-kursuebersicht.md`: Lernpfad um Kapitel 25 erweitert.
- `docs/api.md`: RFC-3161-Verweis in der Funktionsgruppen-Tabelle ergaenzt.
- `roadmap.md`: alle Roadmap-Eintraege auf Erledigt-Hinweis verkuerzt; neuer Abschnitt "Moegliche Folgethemen" mit CAdES-LT/A, PKCS#11 v3-Mechanismen, Pyhanko/PDF-Signaturen, HSM-Migration als Anregungen.

## 0.17.1 - 2026-05-31

### Hinzugefügt
- `docs/cloud-hsm-vergleich.md`: neues Hintergrund-Dokument fuer Cloud-HSM- und HSM-backed-KMS-Angebote. Drei Angebotsklassen (Single-Tenant Cloud-HSM, HSM-backed KMS, Multi-Tenant Managed HSM) mit Trust-Modell-Diff, Anbieter-Vergleichstabelle ueber sieben Anbieter (AWS CloudHSM, AWS KMS Custom Key Store, Azure Dedicated HSM, Azure Key Vault Managed HSM, GCP Cloud HSM, GCP KMS HSM-backed, OCI Vault, Thales DPoD) und sechs Achsen (PKCS#11, FIPS-Level, Tenancy, Backup, Latenz, Pricing). PKCS#11-API-Verfuegbarkeit im Detail mit Limitierungen der KMS-Bruecken (`libkmsp11`, `azure-keyvault-pkcs11`). FIPS-Compliance-Tabelle fuer FIPS 140-3, eIDAS, BSI TR-03116, PCI-DSS, SOX/FedRAMP. Drei Migrations-Klassen (Library-Pfad-Wechsel, Backup-Restore via Vendor-Format, paralleler Key-Neuaufbau). Use-Case-Empfehlungstabelle (Datenverschluesselung, TLS-Termination, eigene CA, eIDAS, TDE, Cross-Cloud). Begruendung, warum kein Hands-on-Lab im Kurs.

### Geändert
- `course/09-production-checkliste.md`: Cloud-HSM-Abschnitt verweist auf die ausfuehrliche Vergleichs-Tabelle.
- `README.md`: Materialien-Block um `docs/cloud-hsm-vergleich.md` ergaenzt; Roadmap-Hinweis auf den verbleibenden RFC-3161-Punkt verkuerzt.
- `docs/api.md`: Querverweis-Block um Cloud-HSM-Vergleich erweitert.
- `roadmap.md`: Eintrag "Cloud-HSM-Provider-Vergleich" auf Erledigt-Hinweis verkuerzt; Priorisierungs-Paragraf jetzt RFC-3161-fokussiert.

## 0.17.0 - 2026-05-31

### Hinzugefügt
- Kapitel 24 `course/24-ecdh-hkdf.md`: ECDH-Schluesselableitung ueber `C_DeriveKey(CKM_ECDH1_DERIVE)`, Alice+Bob-Setup mit Shared-Secret-Match-Beweis (P-256 x-Koordinate, byte-identisch auf beiden Seiten), zwei KDF-Pfade (host-side HKDF-SHA256 RFC 5869 als Default plus `--kdf=raw` als Vergleich). Erklaert SoftHSM-Limitierung rund um `CKM_HKDF_DERIVE` (fehlt vor PKCS#11 v3.0) und die SunPKCS11-Eigenheit, dass `KeyAgreement.generateSecret()` ein `CKA_SENSITIVE=false`-Attribut-Override braucht.
- Uebung 18 `exercises/18-ecdh-hkdf.md` + Loesung `solutions/18-ecdh-hkdf.md`: Bash/Go-Pfad, KDF-Pfad-Vergleich, Vier-Sprachen-Konsistenz-Beweis (alle vier Demos produzieren `8e8922dcb79a3dcf...` als AES-Key), Info-Sensitivitaet, `CKR_ATTRIBUTE_SENSITIVE`-Fehler aus dem leeren `attributes(...)`-Block.
- Go-Demo `lab/go/pkcs11-ecdh-demo/`: Standalone-Programm mit miekg/pkcs11 + golang.org/x/crypto/hkdf, fuer Bash- und Sprach-Demo-Pfad gleichermassen.
- C#-Demo `lab/csharp/Pkcs11EcdhDemo/`: Pkcs11Interop 5.3.0 `session.DeriveKey` + `System.Security.Cryptography.HKDF.DeriveKey`.
- Java-Demo `lab/java/pkcs11-ecdh-demo/`: SunPKCS11 + JCA `KeyAgreement("ECDH")` + selbstgeschriebene RFC-5869-HKDF (Extract+Expand mit `Mac`). `softhsm.cfg` mit `attributes(generate, CKO_SECRET_KEY, CKK_GENERIC_SECRET) = { CKA_SENSITIVE=false, CKA_EXTRACTABLE=true }`-Override.
- Kotlin-Demo `lab/kotlin/pkcs11-ecdh-demo/`: identisch zur Java-Variante, idiomatisches Kotlin.
- Lab-Skripte `78-generate-ecdh-keys.sh` (Alice+Bob EC-P256 mit `--sign --derive` ueber `pkcs11-keygen`), `79-ecdh-derive.sh` (Bash-Wrapper um Go-Demo, KDF via `PKCS11_ECDH_KDF`), `80-83-{go,csharp,java,kotlin}-ecdh-demo.sh` (Sprach-Demo-Wrapper), `84-import-ecdh-certs.sh` (Self-signed Certs fuer Alice und Bob als SunPKCS11-Alias-Plumbing — Certs werden im ECDH-Protokoll nicht benutzt).
- Make-Targets: `gen-ecdh-keys`, `ecdh-derive`, `go-ecdh-demo`, `csharp-ecdh-demo`, `java-ecdh-demo`, `kotlin-ecdh-demo`, `issue-ecdh-certs`.

### Geändert
- `README.md`: Modul 24 in der Kursstruktur-Tabelle und in den "Erweiterte Module"-Targets erfasst; Roadmap-Hinweis im Materialien-Block auf zwei verbleibende Themen verkuerzt; "Kapitel 13-24" im Header der erweiterten Module.
- `course/00-kursuebersicht.md`: Lernpfad um Kapitel 24 erweitert.
- `docs/api.md`: `C_DeriveKey`-Zeile referenziert das neue Kapitel.
- `roadmap.md`: Eintrag "Key Derivation (ECDH und HKDF)" auf Erledigt-Hinweis verkuerzt; Priorisierungs-Paragraf auf zwei verbleibende Themen aktualisiert.
- `Makefile`: `PKCS11_VARS` um `PKCS11_ECDH_*`-Variablen erweitert, neue Targets und Dependencies dokumentiert.

### Migration
- Wer bestehende ECDH-Keys aus einer halben Test-Session ohne `--derive` hat (`alice-ec-key`/`bob-ec-key` mit `Usage: sign,verify`), bekommt beim `make ecdh-derive` ein `CKR_KEY_FUNCTION_NOT_PERMITTED`. Loesung: `make clean-tokens && make init-token && make gen-ecdh-keys`. Neue Anwender brauchen nichts.

## 0.16.1 - 2026-05-31

### Hinzugefügt
- `docs/hsm-kategorien.md`: neues Hintergrund-Dokument zur Einordnung der HSM-Geraeteklassen (TPM, Smartcard/USB-Token, Cloud-Smartcard, PCIe-/Netzwerk-HSM, HLSM, Cloud-HSM, Cloud-KMS). Vergleichstabelle mit Formfaktor/Use-Case/PKCS#11-Verfuegbarkeit/FIPS-Niveau, Abgrenzung zu PKCS#11 (welche Klassen sprechen es nativ, welche nur ueber Bruecken), Entscheidungsmatrix fuer vier typische Szenarien (Notebook-Disk-Encryption, SSH-Token, eIDAS-Bank-Fernsignatur, interne CA), externer Verweis auf Pohlmanns Lehrbuch-Uebungen.

### Geändert
- `course/01-grundlagen.md`: kurzer Absatz nach "Was ist PKCS#11?" mit Verweis auf die HSM-Kategorien-Einordnung — wer PKCS#11 lernt, sollte fruehzeitig wissen, dass "Token" und "HSM" sehr unterschiedliche Geraeteklassen meinen.
- `course/09-production-checkliste.md`: Cloud-HSM-Abschnitt verweist auf die vollstaendige Kategorien-Tabelle.
- `README.md`: Materialien-Block um `docs/hsm-kategorien.md` ergaenzt.
- `docs/api.md`: Querverweis-Block um HSM-Kategorien erweitert.
- `docs/glossar.md`: HLSM-, HSM- und TPM-Eintraege verlinken jetzt auf `hsm-kategorien.md` fuer die ausfuehrliche Einordnung.
- `roadmap.md`: Eintrag "HSM-Kategorien didaktisch schaerfen" auf Erledigt-Hinweis verkuerzt.

## 0.16.0 - 2026-05-31

### Hinzugefügt
- `lab/go/pkcs11-keygen/`: neuer Go-Helper, der `C_GenerateKey` / `C_GenerateKeyPair` ueber miekg/pkcs11 mit **explizitem, vollstaendigem** CKA-Template aufruft. Loest die 0.15.1 dokumentierte SoftHSM-Default-Profil-Falle: `pkcs11-tool --usage-*` markiert nur Intent, das Token defaultet alle nicht erwaehnten Flags auf TRUE. Der Helper setzt jeden CKA-Usage-Wert explizit, plus `CKA_TOKEN=TRUE`, `CKA_PRIVATE=TRUE`, `CKA_SENSITIVE=TRUE`, `CKA_EXTRACTABLE=FALSE` (Defaults), `--extractable`-Override fuer Backup-Szenarien. CLI: `--type rsa|ec|aes|generic`, `--bits`, `--curve`, `--label`, `--id`, `--sign/--encrypt/--wrap/--derive` (High-Level: bei Keypairs auf die jeweilige Haelfte gemappt).
- Neues Make-Target `validate-key-usage` mit Skript `lab/scripts/77-validate-key-usage.sh`: parst `pkcs11-tool --list-objects`, normalisiert die `Usage:`-Zeile (`signRecover`/`verifyRecover` ignoriert — SoftHSM-Implizit-Flags), vergleicht gegen ein hartcodiertes Soll-Profil pro Lab-Key, exit 1 bei Drift. Haengt von allen sieben gen-Targets ab.

### Geändert
- Sieben Generate-Skripte auf den Go-Helper umgestellt: `04-generate-rsa.sh`, `09-generate-ec.sh`, `16-generate-rsa-wrap.sh`, `30-generate-aes-stream-key.sh`, `39-generate-hmac-key.sh`, `54-generate-kek.sh`, `64-generate-ca-key.sh`. Bash-Wrapper bleiben thin; ENV-Variablen fuer Label/ID/Curve unveraendert.
- Makefile: `gen-rsa`, `gen-ec`, `gen-rsa-wrap`, `gen-aes-stream`, `gen-hmac`, `gen-kek`, `gen-ca-key` rufen jetzt `$(RUN_GO)` (Service `pkcs11-go`) statt `$(RUN_LAB)`, weil der Go-Helper im Go-Container laeuft. Nachgelagerte Targets (sign, encrypt, cms, wrap-backup, CA-Chain) bleiben in `pkcs11-lab`; Token-Volume ist geteilt.
- Doku-Rollback nach Sortenreinheit-Fix:
  - `course/13-verschluesselung.md`: zentraler Disclaimer-Abschnitt umformuliert — Sortenreinheit ist jetzt durchgesetzt, nicht nur Soll. Historischer Kontext bleibt als "Historisch"-Block fuer Verstaendnis der pkcs11-tool-Falle.
  - `course/18-tls-mit-hsm.md`: CKA_SIGN-only-Disclaimer entfernt, durch `validate-key-usage`-Hinweis ersetzt.
  - `course/20-key-wrap.md`: KEK-Policy als beobachtbar markiert; Eigenexperiment auf `CKR_KEY_FUNCTION_NOT_PERMITTED`-Pfad umgestellt.
  - `course/22-csr-und-ca-workflow.md`: gen-ca-key-Comment auf "strikt CKA_SIGN-only" zurueckgezogen.
  - `docs/cheatsheet.md`: Lab-Pfad (go run) und klassisch (pkcs11-tool) zeigt jetzt beide; Hinweis-Block auf Validierung umgeschrieben.

### Migration
- Wer einen bestehenden Token aus 0.15.x weiternutzt: alte Keys haben noch das breite SoftHSM-Default-Profil. `make clean-tokens && make init-token && make validate-key-usage` zieht alles frisch ueber den Helper hoch. Vorher `make distclean` raeumt auch Build-Artefakte mit ab.
- Die Roadmap-Aufgabe "Strikte CKA-Templates" aus 0.15.1 ist damit abgeschlossen.

### Parallele Doku-Ergaenzungen vom Kurs-Autor
- `docs/post-quantum.md`: neue Einfuehrung in Post-Quantum-Kryptografie (PQC) mit NIST-Stand 2026, Migrations-Hinweisen und Mapping zu PKCS#11.
- `docs/glossar.md`: API, CA, CSR, ECDH, EdDSA, FIPS, FN-DSA, ML-KEM, ML-DSA, SLH-DSA und weitere PQC-relevante Abkuerzungen ergaenzt.
- `docs/elliptische-kurven.md`: Verweis "keine Post-Quantum-Sicherheit" auf das neue PQC-Dokument verlinkt.
- `docs/api.md`: Querverweis-Block um PQC-Dokument erweitert.

## 0.15.1 - 2026-05-31

### Geändert
- `course/13-verschluesselung.md`: Abschnitt "Sortenreiner Wrap-Key" honest gemacht — `pkcs11-tool --usage-*` ist Intent-Marker, kein Constraint. SoftHSM 2.6 setzt aus `--usage-sign`-RSA `decrypt, sign, signRecover, unwrap`, aus `--usage-wrap`-KEK sogar `encrypt, decrypt, sign, verify, wrap, unwrap`. Neuer Disclaimer-Abschnitt verweist auf 0.16.0-Roadmap-Aufgabe (native CKA-Templates).
- `course/20-key-wrap.md`: KEK-Policy-Tabelle bleibt als Soll, Disclaimer-Block ergaenzt; Experiment "Setze CKA_ENCRYPT=true" auf Lab-Realitaet umgeschrieben (geht im Lab ohnehin schon ohne Override).
- `course/22-csr-und-ca-workflow.md`: Comment am `gen-ca-key`-Target von "(CKA_SIGN only)" auf "(Intent ..., SoftHSM gibt weitere Flags dazu)" praezisiert.
- `course/18-tls-mit-hsm.md`: "ein CKA_SIGN-only-Key" mit Lab-Disclaimer ergaenzt.
- `docs/cheatsheet.md`: Hinweis-Block unter den RSA/EC-Keypair-Beispielen, dass `--usage-*` nur Intent setzt.
- `lab/java/pkcs11-demo/.../Pkcs11Demo.java`, `lab/kotlin/pkcs11-demo/.../KotlinPkcs11Demo.kt`: `PKCS11_KEY_ALIAS`-Default auf `signing-key` statt `null` — vorher zog die Suche nach Modulen mit weiteren Privkeys (wrap-key, hmac-key, ca-key) einen falschen Alias, Verifikation lieferte trotzdem `true`. Java-Demo ausserdem auf `nonEmpty`-Helper umgestellt, weil `System.getenv().getOrDefault` leere ENV-Strings nicht als "use default" behandelt — relevant, sobald das Makefile `export PKCS11_JAVA_CONFIG` durchreicht.
- `lab/java/pkcs11-cms-demo/.../Pkcs11CmsDemo.java`, `lab/java/pkcs11-encrypt-demo/.../Pkcs11EncryptDemo.java`, `lab/java/pkcs11-stream-demo/.../Pkcs11StreamDemo.java`: gleicher `getOrDefault`-zu-`nonEmpty`-Fix; `nonEmpty`-Helper bei den Demos ergaenzt, die ihn noch nicht hatten.
- `exercises/03-java.md` + `solutions/03-java.md`: erwarteter Output korrigiert (`key=true cert=false`, weil `isCertificateEntry` fuer `PrivateKeyEntry` mit Cert-Kette `false` zurueckgibt — Erklaerung ergaenzt). Reflexionsfragen umformuliert: Verify-Pfad nutzt seit 0.4.0 bewusst den `SunPKCS11`-Provider, nicht den Default-Provider.
- `Makefile`: `DOCKER_ENV` ueber neue `PKCS11_VARS`-Liste auf alle PKCS11_*-Variablen erweitert, die Lab-Skripte tatsaechlich lesen (`PKCS11_LEAF_SUBJECT`, `PKCS11_KEY_LABEL`, `PKCS11_CA_KEY_*`, `PKCS11_HMAC_*`, `PKCS11_RANDOM_*` etc.). Vorher kamen die Overrides im Docker-Compose-Pfad nicht im Container an. `addprefix` baut die `-e VAR ...`-Sequenz aus der Liste.
- `lab/scripts/55-wrap-key-backup.sh`: Hinweis-Zeile auf `go-wrap-demo` / `csharp-wrap-demo` reduziert (Java/Kotlin gibt's nicht, war Doku-Drift).
- `roadmap.md`: neuer Eintrag "Strikte CKA-Templates fuer Key-Generate (0.16.0)" mit Helper-Skizze, `validate-key-usage`-Target und Plan zum Disclaimer-Rollback; ausserdem orphaned ECDH-Body wieder unter "## Key Derivation (ECDH und HKDF)" eingehaengt.
- `docs/glossar.md`: HLSM- und TPM-Eintraege ergaenzt; gesamte Abkuerzungs-Tabelle alphabetisch sortiert; EC- und ECDSA-Zeilen verlinken auf das neue EC-Grundlagen-Dokument.
- `README.md`, `docs/api.md`: Materialien-/Querverweis-Block um `docs/elliptische-kurven.md` ergaenzt.

### Hinzugefügt
- `docs/elliptische-kurven.md`: Einfuehrung in Kryptografie auf elliptischen Kurven (Punktmultiplikation, ECDLP, EC vs RSA, Kurvenwahl) als Lese-Background zu Kapitel 11. Verlinkt aus Glossar (`EC`, `ECDSA`) und README-Materialien-Block.

## 0.15.0 - 2026-05-31

### Hinzugefügt
- Kapitel 23 `course/23-random.md`: `C_GenerateRandom`/`C_SeedRandom`, Token-Flag `CKF_RNG`, TRNG vs CSPRNG vs RDRAND vs HSM-RNG, NIST SP 800-90A/B/C-Einordnung, Performance-Realitaet realer HSMs (YubiKey/Thales/Utimaco/AWS CloudHSM), `SecureRandom.getInstance("PKCS11", provider)` als JCA-Pfad. Erklaert, warum SoftHSM-Zahlen nicht repraesentativ sind und warum `C_SeedRandom` als Sicherheits-Feature von vielen HSMs abgelehnt wird.
- Uebung 17 `exercises/17-random.md` + Loesung `solutions/17-random.md`: pkcs11-tool-Generierung, Throughput/Verteilung, Sprach-Demo-Vergleich, CKF_RNG-Inversion als Fehlerpfad, Chunk-Size-Variation.
- Lab-Skripte `lab/scripts/71-random-gen.sh` (pkcs11-tool --generate-random in drei Groessen, CKF_RNG-Check via list-token-slots), `72-random-bench.sh` (HSM vs /dev/urandom vs /dev/zero als Anti-Test, Shannon-Entropie + Chi^2 via Python-Helper), `_random_stats.py` (Verteilungs-Check ohne externe Tools).
- Sprach-Demos `lab/{go,csharp,java,kotlin}/pkcs11-random-demo/`: 32 Byte Proof-of-Life + 1 MB Throughput-Vergleich (persistente Session/Provider) + Shannon-Entropie ueber 64 KB.
  - Go: `p11.GenerateRandom(session, n)` mit miekg/pkcs11, Vergleich vs `crypto/rand`.
  - C#: `session.GenerateRandom(n)` mit Pkcs11Interop, Vergleich vs `RandomNumberGenerator.Fill`.
  - Java/Kotlin: `SecureRandom.getInstance("PKCS11", provider)` ueber SunPKCS11, Vergleich vs Default-NativePRNG. Demonstriert SoftHSM-Spezialfall (HSM-Pfad in-Process schneller als syscall-RNG — auf Hardware umgekehrt).
- Wrapper-Skripte `73-go-random-demo.sh`, `74-csharp-random-demo.sh`, `75-java-random-demo.sh`, `76-kotlin-random-demo.sh`.
- Makefile-Targets: `random-gen`, `random-bench`, `go-random-demo`, `csharp-random-demo`, `java-random-demo`, `kotlin-random-demo`.

### Geändert
- `README.md`: Modul 23 in der Kursstruktur-Tabelle und in den "Erweiterte Module"-Targets erfasst; Roadmap-Hinweis im Materialien-Block auf drei verbleibende Themen verkuerzt.
- `course/00-kursuebersicht.md`: Lernpfad um Kapitel 23 erweitert.
- `Makefile clean`: neue Random-Demo-Build-Verzeichnisse aufgenommen.
- `docs/api.md`: RNG-Zeile referenziert jetzt das neue Kapitel.
- `docs/glossar.md`: RNG/TRNG/PRNG/CSPRNG/DRBG/FIPS 140-2/3/SP 800-90A/B/C als Abkuerzungen ergaenzt — vom Random-Kapitel kreuzreferenziert.
- `roadmap.md`: `C_GenerateRandom`-Eintrag entfernt, Priorisierungs- und Schlussabsatz auf drei verbleibende Themen aktualisiert.
- `.gitignore`: `pkcs11-random-demo`-Build-Pfade fuer Java/Kotlin und `Pkcs11RandomDemo/{bin,obj}` fuer C# ergaenzt.

## 0.14.0 - 2026-05-30

### Hinzugefügt
- Kapitel 22 `course/22-csr-und-ca-workflow.md`: CSR-Mechanik (Proof-of-Possession), Mini-CA-Aufbau mit HSM-residentem CA-Key, vollstaendiger Workflow Generate-CSR → CA-Sign → Cert-Import. Erklaert, warum `08-import-cert.sh` ein didaktischer Hack war und wie der echte Workflow aussieht. Vergleichstabelle Bridge-Pattern pro Sprache (reused aus CMS-Modul). Hinweis zu RFC 6125 / SAN-Pflicht moderner Browser.
- Uebung 16 `exercises/16-csr-und-ca-workflow.md` + Loesung `solutions/16-csr-und-ca-workflow.md`: CA-Setup, Leaf-Cert-Workflow, Sprach-Demo, Cross-Lib-Signing (Go-CSR + Bash-CA), Bonus mit broken CSR.
- Lab-Skripte `lab/scripts/64-generate-ca-key.sh` (CA-Key RSA-2048 auf ID=08, sortenrein CKA_SIGN), `65-issue-ca-cert.sh` (Self-signed Root CA mit `basicConstraints CA:TRUE`, `keyUsage keyCertSign,cRLSign`, SKI), `66-issue-leaf-cert.sh` (CSR via openssl + pkcs11-engine, CA signiert via CAkey=engine, Chain-Verify, Import auf separater ID=09/Label `leaf-cert` — beruehrt das Self-signed Cert auf ID=01 nicht, damit existierende CMS/TLS-Demos weiterhin funktionieren).
- Sprach-Demos `lab/{go,csharp,java,kotlin}/pkcs11-csr-demo/`: CSR-Generierung mit dem signing-key auf ID=01 ueber jeweils sprachspezifische Bridge:
  - Go: `crypto.Signer`-Adapter (DigestInfo + CKM_RSA_PKCS) + `x509.CreateCertificateRequest`. CKA_MODULUS/CKA_PUBLIC_EXPONENT manuell lesen, daraus `*rsa.PublicKey` bauen.
  - C#: `ExternalRsaSha256SignatureFactory` aus dem CMS-Modul + `Pkcs10CertificationRequest` von BouncyCastle.Cryptography. Pubkey aus CKA_MODULUS/CKA_PUBLIC_EXPONENT → `RsaKeyParameters`. SAN/KeyUsage via `X509ExtensionsGenerator` im extensionRequest-Attribut.
  - Java/Kotlin: `JcaContentSignerBuilder("SHA256withRSA").setProvider(sunPkcs11)` + `JcaPKCS10CertificationRequestBuilder` von BouncyCastle bcpkix. Pubkey aus `keyStore.getCertificate(alias).getPublicKey()` (braucht das `import-cert`-Plumbing).
- Alle CSR-Wrapper cross-verifizieren am Ende mit `openssl req -verify` — Standard-Interop beweisen.
- Makefile-Targets: `gen-ca-key`, `issue-ca-cert`, `issue-leaf-cert`, `go-csr-demo`, `csharp-csr-demo`, `java-csr-demo`, `kotlin-csr-demo`.

### Geändert
- `course/00-kursuebersicht.md`: Lernpfad um Kapitel 22 erweitert.
- `Makefile clean`: neue CSR-Demo-Build-Verzeichnisse aufgenommen.

## 0.13.1 - 2026-05-30

### Geändert
- `course/21-pin-management.md`: Abschnitt "BouncyHsm als Alternative" ergaenzt — der C#-PKCS#11-Software-HSM hat das Datenmodell fuer `CKF_USER_PIN_LOCKED` (operator-driven via Web-UI/REST), erhoeht aber im LoginHandler ebenfalls keinen Retry-Counter. Vergleichstabelle SoftHSM vs BouncyHsm vs reale HSMs ergaenzt. Quellcode-Analyse: `LoginHandler.cs` → `ValidatePin` macht nur `FixedTimeEquals`, `IsUserPinLocked` wird ausschliesslich in `SlotFacade.SetTokenLockState`-aehnlichen Pfaden ueber das Web-UI gesetzt.

## 0.13.0 - 2026-05-30

### Hinzugefügt
- Kapitel 21 `course/21-pin-management.md`: CKU_USER vs CKU_SO, C_Login/C_SetPIN/C_InitPIN, vollstaendige CKF_USER_PIN_*-/CKF_SO_PIN_*-Flag-Geometrie, Anwendungs-Pflicht beim Flag-Check vor jedem Login, Vergleichstabelle SoftHSM (kein Lockout) vs Smartcards (3) vs Cloud-HSMs (konfigurierbar), JCA-Limit dokumentiert.
- Uebung 15 `exercises/15-pin-management.md` + Loesung `solutions/15-pin-management.md`: PIN-Info-Read, Change-und-zurueck, Flag-Transition beobachten, SO-Recovery, Sprach-Demo, Bonus zur PIN-Laenge.
- Lab-Skripte `lab/scripts/59-pin-info.sh` (parst Roh-Flags und dekodiert PIN-Sub-Flags), `60-pin-change.sh` (User-PIN-Roundtrip mit garantiertem State-Restore), `61-pin-recovery-by-so.sh` (3 Fehlversuche → CKF_USER_PIN_COUNT_LOW, dann SO-InitPIN-Recovery, Cleanup).
- Sprach-Demos `lab/go/pkcs11-pin-demo/` und `lab/csharp/Pkcs11PinDemo/`: vollstaendiger Lifecycle (GetTokenInfo → SetPIN → 3 Fehlversuche → Flag-Beobachtung → InitPIN-Recovery → Cleanup). Jede Demo stellt den Ausgangs-PIN wieder her.
- Wrapper-Skripte `62-go-pin-demo.sh`, `63-csharp-pin-demo.sh`.
- Makefile-Targets: `pin-info`, `pin-change`, `pin-recovery`, `go-pin-demo`, `csharp-pin-demo`.

### Bekannte Limits
- **Java/Kotlin-Demo entfaellt**: JCA bietet `KeyStore.PasswordProtection`, exponiert aber `C_SetPIN`/`C_InitPIN` nicht ueber das `Provider`-API. SunPKCS11 hat interne Hooks (`sun.security.pkcs11.*`), die aber JDK-Internalcode sind. Workaround in der Praxis: PIN-Lifecycle ueber Bash/Go/C# verwalten, Java-Anwendung wartet auf gueltige PIN.

### Geändert
- `course/00-kursuebersicht.md`: Lernpfad um Kapitel 21 erweitert.
- `Makefile clean`: neues Pkcs11PinDemo bin/obj aufgenommen.

## 0.12.0 - 2026-05-30

### Hinzugefügt
- Kapitel 20 `course/20-key-wrap.md`: C_WrapKey vs C_Encrypt, CKA_EXTRACTABLE als one-way-Gate, KEK-Policy (Use-Case-Trennung), Mechanism-Vergleich RFC 3394 vs 5649, Audit-Aspekte, dokumentierte JCA-Luecke in OpenJDK 21.
- Uebung 14 `exercises/14-key-wrap.md` + Loesung `solutions/14-key-wrap.md`: Backup-Erzeugung, Anti-Pattern ohne --extractable, Sprach-Roundtrip, KEK-Verlust-Szenario, Reflexion zu CKA_WRAP_TEMPLATE und Backup-Strategien.
- Lab-Skripte `lab/scripts/54-generate-kek.sh` (AES-256 KEK auf ID=06 mit CKA_WRAP/UNWRAP, KEINE CKA_ENCRYPT — sortenrein wrap-only) und `55-wrap-key-backup.sh` (extractable payload-key generieren, Test-Daten encrypten, wrap unter KEK → opaque Blob).
- Sprach-Demos `lab/go/pkcs11-wrap-demo/` und `lab/csharp/Pkcs11WrapDemo/`: vollstaendiger Round-Trip Generate → Encrypt → Wrap → Destroy → Unwrap → Decrypt → Verify. Minimal-Template beim Unwrap (kein CKA_VALUE_LEN), damit SoftHSM nicht mit CKR_ATTRIBUTE_READ_ONLY ablehnt.
- Wrapper-Skripte `57-go-wrap-demo.sh`, `58-csharp-wrap-demo.sh`.
- Makefile-Targets: `gen-kek`, `wrap-backup`, `go-wrap-demo`, `csharp-wrap-demo`.

### Bekannte Limits
- **Java/Kotlin-Demo entfaellt**: SunPKCS11 in OpenJDK 21.0.11 (Debian 13) registriert weder `AESWrap` noch `AES/KW/NoPadding`/`AES/KWP/NoPadding` als Cipher-Service, obwohl SoftHSM die Mechanismen advertised. JCA-`Cipher.wrap()` ist damit nicht erreichbar. Course-Modul dokumentiert die Luecke und nennt OpenJDK ≥ 23 sowie IAIK-Wrapper als Workarounds.
- **pkcs11-tool kann nicht unwrappen mit SoftHSM**: setzt im Template CKA_VALUE_LEN, was SoftHSM mit CKR_ATTRIBUTE_READ_ONLY ablehnt. Bash-Pfad endet beim Backup-Export; Restore liegt in den Sprach-Demos.

### Geändert
- `course/00-kursuebersicht.md`: Lernpfad um Kapitel 20 erweitert.
- `Makefile clean`: neues Pkcs11WrapDemo bin/obj aufgenommen.

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
