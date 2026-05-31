# Loesung 18 — ECDH + HKDF

## Bash/Go-Demo

```text
=== 1) Setup ===
  Alice priv-handle=2   pub-point=0441043706e242c1...
  Bob   priv-handle=3   pub-point=0441040fcde6cbd5...

=== 2) ECDH-Derive (CKM_ECDH1_DERIVE, kdf=CKD_NULL) ===
  Alice-Secret: e90e368d95f68725... (32 Byte)
  Bob-Secret:   e90e368d95f68725... (32 Byte)
  Match: ja  (P-256 x-Koordinate, byte-identisch)

=== 3) KDF=hkdf — HKDF-SHA256 host-side (RFC 5869) ===
  info="ECDH-Lab-V1"  salt=zero  ckm_hkdf_derive nicht in SoftHSM 2.6
  AES-Key (gekuerzt): 8e8922dcb79a3dcf...

=== 4) AES-256-GCM Roundtrip ===
  Alice -> 62 Byte Ciphertext + 12 Byte Nonce + 16 Byte Tag
  Bob   <- entschluesselt: "Hallo Bob — diese Nachricht kommt von Alice ueber ECDH+HKDF."
```

## KDF=raw

```text
=== 3) KDF=raw — Shared Secret direkt als AES-256-Key ===
  Funktional ok (TLS 1.2 ECDHE machte das aehnlich), aber kein RFC-5869-Standard.
  AES-Key (gekuerzt): e90e368d95f68725...
```

Der AES-Key sind hier die ersten 32 Byte des rohen Shared Secret. Praktisch funktional fuer AES-256, aber kein Standard-Pattern fuer 2026er-Protokolle.

## Vier-Sprachen-Konsistenz

Alle vier Demos drucken `AES-Key (gekuerzt): 8e8922dcb79a3dcf...`. Das ist der Beweis, dass:

- die ECDH-Berechnung in PKCS#11 (`C_DeriveKey(CKM_ECDH1_DERIVE)`) byte-deterministisch ist, sobald die Keys gleich sind,
- die HKDF-Standard-Implementierung in `golang.org/x/crypto/hkdf`, `System.Security.Cryptography.HKDF`, sowie die selbstgeschriebenen Java/Kotlin-Varianten alle dieselbe RFC-5869-Konvention einhalten (Salt-Default = HashLen Nullbytes).

Wer im Java/Kotlin-Demo nur Expand statt Extract+Expand implementiert, bekommt einen anderen Wert — typischer Fehler bei Eigen-HKDF-Implementierungen.

## Info-Sensitivitaet

```text
Mit info="ECDH-Lab-V2":
  AES-Key (gekuerzt): 4a3f72f1a98cdbe6...
```

Voellig anders als `8e8922dcb79a3dcf...`. Roundtrip funktioniert trotzdem, weil beide Seiten den gleichen Info-String nutzen. In Produktion wird `info` mit Kontext-Daten gefuettert (Protokoll-Version, Session-ID, Cipher-Suite-Name), damit dasselbe Shared Secret nie zwei unterschiedliche Keys produziert.

## CKR_ATTRIBUTE_SENSITIVE provozieren

Mit geloeschtem `attributes(...)`-Block:

```text
Fehler beim PKCS#11-Lauf:
  ProviderException: Could not derive key
  PKCS11Exception: CKR_ATTRIBUTE_SENSITIVE 0x11
```

SunPKCS11 versucht `C_GetAttributeValue(CKA_VALUE)` auf dem abgeleiteten Generic-Secret-Key, das aber `CKA_SENSITIVE=true` traegt (SunPKCS11-Default). PKCS#11-Spec §10.4.1: `CKR_ATTRIBUTE_SENSITIVE` ist der korrekte Rueckgabe-Code in dem Fall.

## Antworten zu den Reflexionsfragen

**ECDH vs RSA-Wrap fuer TLS 1.3:**
- **Forward Secrecy**: Ephemeral-ECDH (jedes Handshake neuer Privkey) macht das Aufzeichnen-und-spaeter-knacken-Modell technisch sinnlos.
- **Performance**: ECDH ist deutlich schneller als RSA-Decrypt bei vergleichbarem Sicherheitsniveau.
- **Migrations-Pfad**: KEMs (`ML-KEM`, `X25519MLKEM768`-Hybrid) haben dieselbe API-Form wie ECDH. RSA-Wrap muss komplett ausgetauscht werden, ECDH-Code wird zu KEM-Code mit minimalen Aenderungen.

**HKDF info-Rolle und salt=null:**
- `info` bindet das abgeleitete Material an einen Kontext. Selber IKM, anderer Kontext → anderer Output. So vermeidet man Kollisionen, wenn das gleiche Shared Secret in mehreren Protokoll-Phasen genutzt wird (z.B. fuer Encryption-Key und Authentication-Key).
- `salt=null` ist RFC 5869 explizit erlaubt, sobald das IKM bereits hochentropisch ist. Salt erfuellt zwei Aufgaben: Domain-Separation (kann auch `info` machen) und Whitening von schwachen IKMs (relevant bei Passwoertern, irrelevant bei ECDH-Shared-Secrets).

**API-Pfad ohne byte[]-Extraktion:**
- `KeyAgreement.generateSecret("AES")` in JCA gibt einen `SecretKey` zurueck. Mit dem laesst sich `Cipher.init(...)` aufrufen, ohne dass die Anwendung jemals die rohen Bytes sieht. SunPKCS11 kann den Key dann mit `CKA_EXTRACTABLE=false` auf dem Token halten und nur `C_Encrypt`/`C_Decrypt`-Operationen freigeben.

**ECDH und HKDF in HSM-Kategorien:**
- TPM/Smartcard: ECDH meist verfuegbar, HKDF on-Token selten.
- PCIe-/Netzwerk-HSM und Cloud-HSM (z.B. AWS CloudHSM v3): typisch beides on-Token (`CKM_ECDH1_DERIVE` + `CKM_HKDF_DERIVE`), seit PKCS#11 v3.0.
- HLSM: alles, plus oft FIPS-Mode-Configurable.
- Cloud-KMS: spricht meist kein PKCS#11; die ECDH/HKDF-Aequivalente sind Provider-spezifische SDK-Calls (AWS KMS GenerateDataKeyPair / Sign-via-ECDH-API).
