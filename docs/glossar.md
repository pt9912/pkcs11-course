# Glossar und Abkuerzungen

Dieses Glossar ist ein schneller Nachschlag fuer Begriffe, die im Kurs immer wieder auftauchen. Die detaillierte Einordnung steht in den jeweiligen Kapiteln, besonders in [course/01-grundlagen.md](../course/01-grundlagen.md) und [docs/api.md](api.md).

## Abkuerzungen

| Abkuerzung | Bedeutung | Einordnung |
|---|---|---|
| `AES` | Advanced Encryption Standard | Symmetrische Blockchiffre. |
| `API` | Application Programming Interface | Programmierschnittstelle; PKCS#11 ist eine API fuer kryptografische Tokens und HSMs. |
| `ASN.1` | Abstract Syntax Notation One | Datenbeschreibungssprache hinter Zertifikaten, OIDs und vielen Krypto-Formaten. |
| `CA` | Certificate Authority | Zertifizierungsstelle, die Zertifikate oder CSRs prueft und signiert. |
| `CMS` | Cryptographic Message Syntax | Standard fuer signierte/verschluesselte Nachrichten, Nachfolger von PKCS#7. |
| `CSR` | Certificate Signing Request | Zertifikatsantrag mit Public Key und Subject-Daten, der von einer CA signiert wird. |
| `Cryptoki` | Cryptographic Token Interface | Alternativer Name fuer PKCS#11. |
| `CSPRNG` | Cryptographically Secure PRNG | PRNG mit zusaetzlichen Vorwaerts- und Rueckwaerts-Sicherheits-Eigenschaften. |
| `DER` | Distinguished Encoding Rules | Binaere ASN.1-Codierung, z. B. fuer Zertifikate, Public Keys oder ECDSA-Signaturen. |
| `DRBG` | Deterministic Random Bit Generator | NIST-Begriff fuer CSPRNG aus SP 800-90A (CTR_DRBG, HMAC_DRBG, Hash_DRBG). |
| `EC` | Elliptic Curve | Oberbegriff fuer [Kryptografie auf elliptischen Kurven](elliptische-kurven.md). |
| `ECDH` | Elliptic Curve Diffie-Hellman | Schluesselaustausch auf elliptischen Kurven; nicht post-quantum-sicher, aber relevant fuer Hybrid-Migration. |
| `ECDSA` | Elliptic Curve Digital Signature Algorithm | Signaturverfahren auf elliptischen Kurven. Siehe [EC-Grundlagen](elliptische-kurven.md). |
| `EdDSA` | Edwards-curve Digital Signature Algorithm | Moderne EC-Signaturfamilie, z. B. Ed25519; nicht post-quantum-sicher. |
| `FIPS` | Federal Information Processing Standard | US-Standardreihe fuer Kryptografie und IT-Sicherheit, z. B. FIPS 203 fuer ML-KEM. |
| `FIPS 140-2/3` | Federal Information Processing Standard | US-Zertifizierung fuer kryptografische Module; relevant fuer HSM-Auswahl. |
| `FN-DSA` | FFT over NTRU-Lattice-Based Digital Signature Algorithm | Falcon-basierter Post-Quantum-Signaturalgorithmus; von NIST ausgewaehlt, FIPS noch nicht final. |
| `GCM` | Galois/Counter Mode | AEAD-Modus fuer AES mit Authentifizierung. |
| `HLSM` | High-Level Security Module | Besonders abgesichertes, meist zentrales HSM fuer kritische Anwendungen wie CA-Schluessel, Bank-Schluessel oder Fernsignaturen. Einordnung: [HSM-Kategorien](hsm-kategorien.md). |
| `HMAC` | Hash-based Message Authentication Code | MAC-Verfahren auf Basis einer Hashfunktion und eines symmetrischen Geheimnisses. |
| `HQC` | Hamming Quasi-Cyclic | Von NIST als Backup-KEM zu ML-KEM ausgewaehltes Post-Quantum-Verfahren. |
| `HSM` | Hardware Security Module | Geraet oder Service, der Schluesselmaterial geschuetzt erzeugt, speichert und benutzt. Siehe [HSM-Kategorien](hsm-kategorien.md) fuer die Geraeteklassen-Einordnung. |
| `JCA` | Java Cryptography Architecture | Java-Abstraktion fuer Signaturen, KeyStores und Provider. |
| `JCE` | Java Cryptography Extension | Java-Abstraktion fuer Cipher, MACs und weitere Krypto-Primitive. |
| `JNI` | Java Native Interface | Bruecke von Java zu nativen Bibliotheken, relevant fuer manche PKCS#11-Wrapper. |
| `JWT` | JSON Web Token | Token-Format, im Kurs bei HMAC/HS256 relevant. |
| `KDF` | Key Derivation Function | Funktion, die aus einem gemeinsamen Geheimnis oder Seed konkrete Schluessel ableitet. |
| `KEK` | Key Encryption Key | Schluessel, der andere Schluessel wrapped oder unwrapped. |
| `KEM` | Key Encapsulation Mechanism | Verfahren zum Aufbau eines gemeinsamen Geheimnisses ueber einen Public Key, zentral fuer [Post-Quantum-Kryptografie](post-quantum.md). |
| `LMS` | Leighton-Micali Signature | Stateful hashbasiertes Signaturverfahren aus NIST SP 800-208. |
| `MGF` | Mask Generation Function | Bestandteil von RSA-PSS-Parametern, meistens `MGF1` mit passendem Hash. |
| `ML-DSA` | Module-Lattice-Based Digital Signature Algorithm | NIST-standardisiertes Post-Quantum-Signaturverfahren, frueher CRYSTALS-Dilithium. |
| `ML-KEM` | Module-Lattice-Based Key-Encapsulation Mechanism | NIST-standardisiertes Post-Quantum-KEM, frueher CRYSTALS-Kyber. |
| `NIST` | National Institute of Standards and Technology | US-Standardisierungsbehoerde, u. a. fuer FIPS- und Post-Quantum-Standards. |
| `OAEP` | Optimal Asymmetric Encryption Padding | RSA-Padding fuer Verschluesselung. |
| `OID` | Object Identifier | Eindeutige numerische Kennung, z. B. fuer Kurven oder Algorithmen. |
| `PEM` | Privacy-Enhanced Mail | Base64-Textverpackung fuer DER-Daten mit Header/Footer. |
| `PIN` | Personal Identification Number | Geheimnis fuer Login am Token, im Lab z. B. User-PIN `987654`. |
| `PKCS#11` | Public-Key Cryptography Standards #11 | Standardisierte API fuer kryptografische Tokens und HSMs. |
| `PKCS#7` | Public-Key Cryptography Standards #7 | Aeltere Bezeichnung im Umfeld von CMS und S/MIME. |
| `PQC` | Post-Quantum Cryptography | Klassische Kryptografie, die nach heutigem Kenntnisstand gegen grosse Quantencomputer widerstandsfaehig sein soll. |
| `PRNG` | Pseudo-Random Number Generator | Deterministischer Algorithmus, der einen Seed in eine lange Output-Sequenz expandiert. |
| `PSS` | Probabilistic Signature Scheme | Moderne RSA-Signatur-Padding-Variante. |
| `RNG` | Random Number Generator | Oberbegriff fuer alle Zufallsquellen, TRNG und PRNG eingeschlossen. |
| `RSA` | Rivest-Shamir-Adleman | Asymmetrisches Verfahren fuer Signaturen und historische Verschluesselungsfaelle. |
| `SHA` | Secure Hash Algorithm | Hashalgorithmus-Familie, z. B. SHA-256, SHA-384 und SHA-512. |
| `SLH-DSA` | Stateless Hash-Based Digital Signature Algorithm | NIST-standardisiertes hashbasiertes Post-Quantum-Signaturverfahren, frueher SPHINCS+. |
| `SO` | Security Officer | Administrativer Token-Benutzer, z. B. fuer Initialisierung und User-PIN-Reset. |
| `SP` | Special Publication | NIST-Publikationsreihe fuer Empfehlungen und Richtlinien, z. B. SP 800-208. |
| `SP 800-90A/B/C` | NIST Special Publication 800-90 | Standards fuer DRBG-Konstruktion (A), Entropy Sources (B) und RBG-Konstruktion (C). |
| `SSH` | Secure Shell | Protokoll fuer Remote-Login und Git-Zugriff. |
| `TLS` | Transport Layer Security | Protokoll fuer gesicherte Verbindungen, z. B. HTTPS. |
| `TPM` | Trusted Platform Module | Geraetegebundener Sicherheitschip fuer Plattform-Identitaet, gemessenen Boot und lokale Schluessel; kein allgemeiner HSM-Ersatz und im Kurs nicht der typische PKCS#11-Zieltyp. Einordnung: [HSM-Kategorien](hsm-kategorien.md). |
| `TRNG` | True Random Number Generator | Physikalische Quelle (Ringoszillator, Quantenrauschen, Zener-Diode). |
| `URI` | Uniform Resource Identifier | Adressformat, z. B. `pkcs11:token=...;object=...`. |
| `VPN` | Virtual Private Network | Geschuetzte Netzverbindung; relevant fuer hybride Post-Quantum-Schluesselaustauschverfahren. |
| `X25519` | X25519 Diffie-Hellman function | Weit verbreiteter ECDH-Schluesselaustausch auf Curve25519; nicht post-quantum-sicher, aber oft Hybrid-Partner fuer ML-KEM. |
| `XMSS` | eXtended Merkle Signature Scheme | Stateful hashbasiertes Signaturverfahren aus NIST SP 800-208. |

## PKCS#11-Praefixe

| Praefix | Bedeutung | Beispiele |
|---|---|---|
| `C_` | PKCS#11-Funktion | `C_Initialize`, `C_OpenSession`, `C_Sign` |
| `CKA_` | Attribut eines Objekts | `CKA_ID`, `CKA_LABEL`, `CKA_SIGN` |
| `CKK_` | Key-Typ | `CKK_RSA`, `CKK_EC`, `CKK_AES` |
| `CKO_` | Objektklasse | `CKO_PRIVATE_KEY`, `CKO_PUBLIC_KEY`, `CKO_CERTIFICATE` |
| `CKM_` | Mechanism | `CKM_SHA256_RSA_PKCS`, `CKM_ECDSA_SHA256`, `CKM_AES_GCM` |
| `CKR_` | Rueckgabecode | `CKR_OK`, `CKR_PIN_INCORRECT`, `CKR_MECHANISM_INVALID` |
| `CKF_` | Flag | `CKF_RW_SESSION`, `CKF_SERIAL_SESSION` |
| `CKU_` | Login-Benutzertyp | `CKU_USER`, `CKU_SO` |

## PKCS#11-Begriffe

| Begriff | Bedeutung |
|---|---|
| Module | Native PKCS#11-Bibliothek, die eine Anwendung laedt, z. B. `libsofthsm2.so`. |
| Slot | Logischer Steckplatz fuer ein Token. Slot-IDs koennen sich aendern; im Lab deshalb bevorzugt mit Token-Label arbeiten. |
| Token | Logische oder physische Einheit mit Objekten, PINs und Mechanismen. SoftHSM simuliert solche Tokens lokal. |
| Session | Verbindung einer Anwendung zu einem Token. Viele Operationen laufen ueber Session-Handles. |
| Object | PKCS#11-Objekt im Token, z. B. Private Key, Public Key, Zertifikat oder Secret Key. |
| Handle | Laufzeitkennung fuer Session oder Objekt. Handles sind nicht stabil ueber Prozesse oder neue Sessions hinweg. |
| Mechanism | Algorithmus plus Betriebsart aus Sicht des Tokens, z. B. `CKM_SHA256_RSA_PKCS`. |
| Attribute | Eigenschaften eines Objekts, z. B. `CKA_ID`, `CKA_LABEL`, `CKA_EXTRACTABLE` oder `CKA_SIGN`. |
| Template | Attributliste fuer Suchen, Erzeugen oder Importieren von Objekten. |
| Label | Menschenlesbarer Objekt- oder Token-Name, z. B. `signing-key` oder `dev-token`. |
| `CKA_ID` | Bytefolge zur technischen Kopplung zusammengehoeriger Objekte, z. B. Private Key und Zertifikat. |
| Object class | Art eines Objekts, z. B. Private Key, Public Key, Zertifikat oder Secret Key. |
| Key type | Kryptografischer Typ eines Schluessels, z. B. RSA, EC oder AES. |
| Private Key | Privater asymmetrischer Schluessel. In HSM-Setups ist er normalerweise nicht extrahierbar. |
| Public Key | Oeffentlicher asymmetrischer Schluessel. Er kann meist gelesen und extern verifiziert werden. |
| Secret Key | Symmetrischer Schluessel, z. B. AES- oder HMAC-Key. |
| Certificate object | Zertifikat im Token. Java-SunPKCS11 braucht es mit passender `CKA_ID`, damit ein Private-Key-Alias sichtbar wird. |
| Login-State | Anmeldestatus einer Anwendung gegenueber einem Token. Er wirkt in vielen Implementierungen tokenweit, nicht nur fuer eine einzelne Session. |
| SO-PIN | Administrative PIN des Security Officers. Im Lab hauptsaechlich fuer Token-Initialisierung relevant. |
| User-PIN | PIN fuer normale kryptografische Nutzung, z. B. Signieren oder Entschluesseln. |
| Sensitive | Attributzustand, bei dem der Wert eines Schluessels nicht direkt gelesen werden darf. |
| Extractable | Attributzustand, der entscheidet, ob Schluesselmaterial exportiert oder gewrapped werden darf. |
| Wrap | Verschluesselter Export eines Schluessels durch einen anderen Schluessel, meistens einen KEK. |
| Unwrap | Import eines gewrappten Schluessels zurueck in ein Token. |
| Multi-Part-Operation | Operation mit Init/Update/Final-Aufrufen, z. B. fuer grosse Datenstroeme. |
| Provider | Sprach- oder Framework-Integration, z. B. SunPKCS11 als Java-Provider. |
| Engine | OpenSSL-Integrationsschicht fuer PKCS#11 in aelteren OpenSSL-Pfaden. |
| PKCS#11-URI | Standardisiertes Adressformat fuer Token-Objekte, z. B. `pkcs11:token=dev-token;object=signing-key;type=private`. |

## Typische Verwechslungen

| Verwechslung | Korrektur |
|---|---|
| Slot-ID als stabile Konfiguration | Slot-IDs koennen wandern. Token-Label oder PKCS#11-URI ist robuster. |
| `CKA_ID=01` als Text `"01"` | Im Lab ist `01` eine Bytefolge, nicht zwingend ASCII-Zeichen `0x30 0x31`. |
| Mechanism gleich Algorithmusname | Mechanisms enthalten oft auch Hashing, Padding oder Modus. |
| Zertifikat enthaelt privaten Schluessel | Das Zertifikat enthaelt nur den Public Key und Metadaten. Der Private Key bleibt ein eigenes Token-Objekt. |
| Signatur prueft automatisch Zertifikatsvertrauen | Signaturverifikation und Zertifikatspfad-/Trust-Pruefung sind getrennte Schritte. |
| Private Key lesen, um damit zu signieren | Bei PKCS#11 wird der Key nicht gelesen; die Anwendung fordert eine Operation am Token an. |
