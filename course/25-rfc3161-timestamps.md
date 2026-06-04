# 25 — RFC-3161-Timestamps fuer CMS (CAdES-T)

## Bevor du anfaengst — was vermutest du?

> Deine CMS-Signatur enthaelt schon ein `signingTime`-Attribut (`signedAttrs.signingTime`, RFC 5652). Reicht das fuer den Beweis "der Vertrag wurde am 14.03.2026 unterschrieben"?

Wahrscheinliche Vermutung: ja — `signingTime` ist Teil der `signedAttrs`, also kryptographisch in die Signatur eingebunden. Wer das veraendert, bricht die Signatur. Mentale Karte: **Signiert = nicht manipulierbar = beweisbar**.

Diese Karte verwechselt *kryptographische Integritaet* mit *Beweisbarkeit gegen einen Dritten*. `signingTime` ist die Zeit, die der **Signer** behauptet — aus seiner Uhr, ohne externen Zeugen. Wer rueckdatieren will, stellt einfach die Systemuhr und signiert. Die Signatur bleibt mathematisch gueltig. Vor Gericht oder im eIDAS-Audit fragt der Pruefer aber: *wer bezeugt, dass die Uhr richtig ging?* — und genau die Antwort fehlt. RFC 3161 trennt das, indem eine vertrauenswuerdige Time-Stamping-Authority mit auditierter Uhr **ihre Sicht** auf den Signatur-Hash signiert. Halte die "signingTime = Beweis"-Karte fest. Dieses Kapitel zeigt, wo sie reisst — und wie ein TSA-Token, eingebettet als `signatureTimeStampToken`, das CAdES-T-Profil rechtsverwertbar macht.

## Lernziele

Nach diesem Kapitel kannst du:

- erklaeren, warum `signingTime` als CMS-Attribut fuer rechtsverbindliche Signaturen nicht reicht.
- den RFC-3161-Workflow durchspielen: TimeStampReq, TSA-Service, TimeStampResp, TSToken.
- einen Lab-TSA-Server aufsetzen, der via `openssl ts -reply` signiert.
- den TSToken als `unsignedAttribute.signatureTimeStampToken` in eine CMS-Signatur einbauen (CAdES-T).
- die Cert-Anforderung an die TSA verstehen (`extendedKeyUsage=critical,timeStamping`).
- den Lab-Pfad von realen TSAs (DigiCert, Sectigo) und qualifizierten eIDAS-TSAs abgrenzen.
- **(Bloom 5 — evaluate)** fuer ein konkretes Compliance-Niveau (interne Beweisbarkeit, eIDAS-T, eIDAS-LT, eIDAS-A) entscheiden, welcher CAdES-Profil-Aufbau und welche TSA-Vertragsklasse (Free-Tier, Commercial, Qualified) erforderlich sind — und welcher Faktor (Aufbewahrungsdauer, Krypto-Bruch-Sicherheit, Revocation-Validierbarkeit) die Wahl bestimmt.

## Lab-Bezug

```bash
make tsa-setup           # TSA-Key (Software, Lab-Kompromiss) + CA-signiertes TSA-Cert
make tsa-serve           # Foreground-TSA-Daemon auf 127.0.0.1:8088
make cms-tsa-sign        # Bash: CMS-Signatur + TimeStampReq + Response (separat)
make cms-tsa-verify      # Bash: openssl ts -verify + openssl cms -verify
make java-cms-tsa-demo   # Java (BouncyCastle): voller CAdES-T-Flow mit Embedding
make kotlin-cms-tsa-demo # Kotlin-Pendant
make csharp-cms-tsa-demo # C# (Pkcs11Interop + BouncyCastle.Cryptography)
make go-cms-tsa-demo     # Go (digitorus/pkcs7 + digitorus/timestamp), CMS + TSR separat
```

Begriffe (CMS, eIDAS, CC, EAL, KMIP): [Glossar](../docs/glossar.md). HSM-Klassen-Einordnung: [HSM-Kategorien](../docs/hsm-kategorien.md).

## Warum reicht `signingTime` nicht?

Eine CMS-`SignerInfo` enthaelt als signed attribute optional ein `signingTime` (PKCS#9, OID 1.2.840.113549.1.9.5). Das ist die Zeit, **die der Signer behauptet**. Beweis-rechtlich ist das schwach:

- Der Signer kann jeden beliebigen Wert eintragen — die Uhr des Signers ist nicht vertrauenswuerdig.
- Eine Anwendung kann nach Cert-Ablauf rueckdatieren und ein Cert nutzen, das im Original-Zeitfenster gueltig gewesen waere.
- eIDAS und CAdES-T verlangen explizit eine **vertrauenswuerdige** Zeitquelle.

RFC 3161 loest das: ein externer TSA-Service (Time Stamping Authority) signiert mit einer audit-zertifizierten Uhr einen Hash der zu stempelnden Daten und gibt einen `TSToken` zurueck. Der TSToken wird **als unsignierte Eigenschaft** in die CMS-Signatur eingebaut — er aendert die Signatur nicht und kann nach dem Sign-Zeitpunkt eingefuegt werden.

## Der Protokoll-Ablauf

```text
Signer                                             TSA
------                                             ---
1. CMS-Sign(content) → SignedData
2. h = SHA-256(SignerInfo.signature)
3. TSReq(h, nonce, certReq=true)        ─────►   4. validate
                                                  5. TSToken(time, h, nonce,
                                                             SignerInfo[TSA-Cert])
                                                  6. TimeStampResp.PKIStatus.success
                                        ◄─────
7. validate TSResp gegen TSReq
8. TSToken einbauen als
   unsignedAttribute.signatureTimeStampToken
   (OID 1.2.840.113549.1.9.16.2.14)
```

Wichtige Eigenschaften:

- **Der TSA hashed die Signature, nicht das Dokument.** So entsteht ein Beweis "diese konkrete Signatur existierte zum Zeitpunkt X". Wer das Dokument aendert, invalidiert die Signature; der Timestamp bleibt formal korrekt.
- **Unsigned Attribute:** der TSToken wird in die SignerInfo eingebaut, ohne dass die Signatur neu berechnet werden muss. RFC 5652 §11.2: UnsignedAttributes sind explizit nicht Teil des `digest`-Inputs.
- **Nonce verhindert Replay**, der TSA gibt sie im TSToken zurueck.
- **`certReq=true`** in der TSReq weist die TSA an, ihr Signing-Cert im TSToken mitzuliefern — der Verifier braucht es spaeter.

## TSA-Cert: `extendedKeyUsage=critical,timeStamping`

RFC 3161 §2.3 verlangt fuer den Signing-Key der TSA ein Zertifikat mit:

- `extendedKeyUsage = critical, id-kp-timeStamping (1.3.6.1.5.5.7.3.8)`
- Optional `keyUsage = digitalSignature, nonRepudiation`

Das `critical`-Flag bedeutet: Verifier, die `timeStamping` nicht kennen, **muessen** das Cert ablehnen. So verhindert man, dass ein normales TLS- oder Code-Signing-Cert als TSA missbraucht wird.

Im Lab erzeugt `make tsa-setup` ein passendes Cert ueber den HSM-CA-Key aus Modul 22.

## Lab-Architektur

```text
                ┌─────────────┐
                │  ca-key     │  HSM-resident, signt das TSA-Cert
                │  (CKA_SIGN) │
                └──────┬──────┘
                       │ x509 -CA
                       ▼
                ┌─────────────┐     ┌──────────────────┐
                │  tsa-cert   │     │  signing-key     │
                │  extKU=ts   │     │  (CKA_SIGN, HSM) │
                └──────┬──────┘     └─────────┬────────┘
                       │ certs              │ CMS-Sign
                       ▼                    ▼
   curl POST     ┌──────────────────────────────────┐
   tsq ──────►   │  TSA-Daemon (openssl ts -reply)  │
                 │  via Python HTTP-Wrapper         │
                 │  Port 8088                       │
   tsr ◄──────   └──────────────────────────────────┘
                       ▲
                       │
                ┌─────────────┐
                │  tsa-key    │  Software-Key, Filesystem
                │  (Lab-      │  (Lab-Kompromiss, siehe unten)
                │   Software) │
                └─────────────┘
```

### Lab-Kompromiss: TSA-Key ist Software

`openssl ts -reply` laedt den Signer-Key per `fopen()`, nicht ueber die OpenSSL-Engine-API. PKCS#11-URIs als `signer_key` werden ignoriert/abgelehnt. Damit der Lab-TSA mit Standard-OpenSSL laeuft, liegt der TSA-Signing-Key in `lab/work/tsa-key.pem` (PEM-Datei, mode 0600). Der **Document-Signer-Key** (`signing-key`) und der **CA-Key** bleiben HSM-resident — am eigentlichen Lab-Ziel "Keys bleiben im HSM" aendert das nichts.

Reale TSAs haben ihre Signing-Keys natuerlich im eigenen HSM. Wer das im Lab spielen will, braucht einen TSA-Daemon, der die OpenSSL-Engine-API nutzt (z.B. eigenen RFC-3161-Server in Go/Java/Python mit PKCS#11-Bindung). Sieh die Sprach-Demos: dort laeuft das Sign-bezogene HSM-Sign ueber miekg/pkcs11 / Pkcs11Interop / SunPKCS11.

## Embedding pro Sprache

Java, Kotlin, C# und Bash schreiben unterschiedlich detaillierte Ergebnisse:

| Sprache | TSToken-Embedding | Begruendung |
|---|---|---|
| Bash (openssl) | nein — `.p7s` + `.tsr` separat | openssl-CLI hat keinen Operator, der einer CMS-Signatur einen TSToken nachreicht. |
| Java/Kotlin (BouncyCastle) | ja — `CMSSignedData.replaceSigners` + `SignerInformation.replaceUnsignedAttributes` | bcpkix-Standardpfad. |
| C# (BouncyCastle.Cryptography) | ja — identisches Pattern wie Java | gleiches API. |
| Go (digitorus/pkcs7) | nein — `.p7s` + `.tsr` separat | `pkcs7.SignerInfoConfig` hat kein `ExtraUnsignedAttributes`. Nachtraegliche ASN.1-Manipulation moeglich, aber fragil. Dokumentierte Limitation. |

Wer fuer Go den vollen Embedding-Pfad braucht: `github.com/digitorus/pdfsigner` oder `pyhanko` (Python) zeigen, wie es geht — meist auf Kosten eigener ASN.1-Routinen.

## Reale TSAs

Im Lab fuettern wir den TSA-Daemon mit der Lab-CA. Im Produktivbetrieb nimmt man eine zertifizierte TSA:

| Anbieter | Free-Tier? | Anwendungsfall |
|---|---|---|
| DigiCert | nein | Code-Signing (Microsoft Authenticode), CAdES-T |
| Sectigo | nein | Code-Signing, S/MIME-LT |
| GlobalSign | nein | Code-Signing |
| FreeTSA (freetsa.org) | ja | Lab/Experimentieren, **keine** eIDAS-Konformitaet |
| eIDAS Qualified TSAs (D-Trust, A-Trust, etc.) | nein | qualifizierte elektronische Signaturen |

URL-Konvention: alle akzeptieren `application/timestamp-query` per HTTP-POST nach RFC 3161 §3.4.

## CAdES-T vs CAdES-LT vs CAdES-A

CAdES (CMS Advanced Electronic Signatures) baut Profile auf CMS auf, die langfristige Beweiskraft sichern:

| Profil | Was kommt dazu? |
|---|---|
| CAdES-BES | Basis-CMS, mehr `signed attributes` (z.B. `signing-certificate-v2`) |
| **CAdES-T** | + `signatureTimeStampToken` (was wir hier bauen) |
| CAdES-LT | + `certificate-values` und `revocation-values` als unsigniert (CRLs/OCSP-Responses werden eingebettet — auch nach Cert-Revocation verifizierbar) |
| CAdES-LTA | + zusaetzliche `archive-time-stamp`s, die periodisch erneuert werden (Beweis bleibt auch nach Kryptographie-Bruch zukunftssicher) |

Das Lab bleibt bei **CAdES-T**. CAdES-LT und CAdES-A brauchen CRL/OCSP-Logik und Wiederholungs-Scheduler — eigene Domaene.

## Eigenexperiment

- Stoppe `make tsa-serve` und versuche `make java-cms-tsa-demo`. Die Demo blockiert beim TSA-POST, scheitert nach ~10 Sekunden mit Connection-Refused. Im Lab-Schritt 89-script wird der Daemon vor dem Java-Lauf automatisch gestartet.
- Loesche die `extendedKeyUsage`-Zeile aus `85-tsa-setup.sh`, regeneriere TSA-Cert (`make clean-tokens && make tsa-setup`), lasse den Lab-TSA laufen. `openssl ts -verify` lehnt mit `unable to find tsa certificate` ab — `critical,timeStamping` ist Pflicht.
- Vergleiche Plain-CMS-Groesse (`make cms-sign` aus Modul 14) mit CMS+TSA-Groesse (`make java-cms-tsa-demo`). Differenz ~2.4 KB — die Groesse des eingebetteten TSToken inkl. TSA-Cert.

Strukturierte Aufgaben in [`exercises/19-rfc3161-timestamps.md`](../exercises/19-rfc3161-timestamps.md).

## Selbsttest

<details>
<summary>1. Was hasht die TSA — das Dokument oder die Signatur des Dokuments?</summary>

Die Signatur (genauer: `SHA-256(SignerInfo.signature)`). Damit entsteht der Beweis "diese konkrete Signatur existierte zum Zeitpunkt X". Vorteil: wer das Dokument aendert, invalidiert die Signature; der Timestamp bleibt formal korrekt, das Dokument bleibt aber unverifizierbar. So bindet der Timestamp die Existenz der Signatur an die Zeit, nicht den Inhalt direkt.
</details>

<details>
<summary>2. Warum muss <code>extendedKeyUsage=timeStamping</code> als <code>critical</code> markiert sein?</summary>

`critical` zwingt Verifier, die Extension zu verstehen. Verifier, die `timeStamping` nicht kennen, muessen das Cert ablehnen. So verhindert man, dass ein normales TLS- oder Code-Signing-Cert versehentlich als TSA-Cert akzeptiert wird. Ohne `critical` koennte ein Verifier die Extension uebersehen und ein falsches Cert akzeptieren.
</details>

<details>
<summary>3. Welches CAdES-Profil baust du im Lab, und was fehlt fuer Langzeit-Beweiskraft (10+ Jahre)?</summary>

CAdES-T. Es fehlen **CAdES-LT** (Embedding von CRL/OCSP-Material — auch nach Cert-Revocation noch verifizierbar) und **CAdES-LTA** (periodische `archive-time-stamp`s, die den Beweis auch nach kryptographischem Bruch — z.B. SHA-256 nicht mehr sicher — zukunftssicher machen). Beide brauchen Revocation-Logik und einen periodischen Re-Timestamp-Scheduler — eigene Domaene und nicht im Kurs-Lab.
</details>

<details>
<summary>4. <strong>(evaluate)</strong> Du sollst fuer einen Vertragsdienst eine TSA waehlen: (A) Free-Tier-TSA (FreeTSA, DigiCert-Public), (B) Commercial-TSA (DigiCert Enterprise, GlobalSign), (C) qualifizierter eIDAS-TSA (D-TRUST, A-Trust). Eingangs-Bedingung sei "Vertraege mit eIDAS-Geltungsbereich, 7 Jahre Aufbewahrung, kein expliziter QES-Bedarf". Welche TSA gewinnt — und welcher Faktor verschiebt die Antwort, wenn die Aufbewahrungspflicht auf 35 Jahre (qualifizierte eIDAS) steigt?</summary>

7 Jahre + kein QES-Bedarf: **(B) Commercial-TSA** ist die richtige Antwort. (A) reicht aus Krypto-Sicht, aber Free-Tier-TSAs liefern keine Verfuegbarkeits-SLA und keine garantierte Cert-Aufbewahrung — wer in 5 Jahren die TSA-Signatur verifizieren will, braucht den TSA-Pubkey *plus* den Cert-Pfad zur damaligen Zeit. (C) ist Overkill ohne QES-Anforderung und kostet Faktor 10-50. Die 35-Jahre-Verschiebung kippt das auf **(C)**: qualifizierte eIDAS-Aufbewahrung verlangt zusaetzlich (a) kontinuierliche Re-Timestamping (CAdES-LTA), das nur eIDAS-Qualified-TSAs aufrechterhalten, und (b) eine TSA mit gesicherter Existenz-Garantie ueber den Zeitraum — eine Commercial-TSA, die in 12 Jahren von einem Wettbewerber uebernommen und der TSA-Pubkey aus dem Trust-Anchor genommen wird, macht den gesamten Bestand unverifizierbar. eIDAS-Qualified-TSA traegt regulatorisch die Pflicht zur Schluessel-Archivierung. Der eigentliche Differenzierer ist also nicht das Krypto-Niveau (alle drei machen SHA-256-Timestamps), sondern die **rechtlich-organisationale Existenz-Garantie ueber die Aufbewahrungsdauer**.
</details>
