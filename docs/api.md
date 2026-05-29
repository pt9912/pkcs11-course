# PKCS#11 API — Leitfaden

Dieses Dokument beschreibt die PKCS#11-API auf allen relevanten Ebenen, mit denen du im Kurs in Berührung kommst:

1. **Native C-API (Cryptoki)** — die eigentliche Spezifikation
2. **`pkcs11-tool`** — CLI-Wrapper aus OpenSC
3. **OpenSSL** — `engine_pkcs11` und der neuere `pkcs11-provider`
4. **Java SunPKCS11** — JCA/JCE über PKCS#11

Ziel: Du verstehst, was unter der Haube passiert, wenn du im Lab `make sign` oder `make java-demo` aufrufst, und kannst zwischen den Ebenen wechseln, ohne den roten Faden zu verlieren.

---

## 1. Architekturüberblick

```text
+----------------------------------------------------+
|  Anwendung (Java, Python, C, Shell, ...)            |
+----------------------------------------------------+
            |              |              |
            |              |              |
   +--------+----+   +-----+------+  +----+------+
   | pkcs11-tool |   | OpenSSL    |  | SunPKCS11 |
   | (CLI)       |   | engine/    |  | (Java)    |
   |             |   | provider   |  |           |
   +-------------+   +------------+  +-----------+
            \             |              /
             \            |             /
              v           v            v
       +----------------------------------+
       |  PKCS#11 Cryptoki C-API (.so)    |
       |  z. B. libsofthsm2.so            |
       +----------------------------------+
                       |
                       v
              +-----------------+
              |  Token / HSM    |
              +-----------------+
```

Jeder Wrapper landet am Ende beim selben Set von `C_*`-Funktionen. Wer die C-API kennt, kann das Verhalten aller höheren Ebenen erklären — auch deren Fehler.

---

## 2. Native C-API (Cryptoki)

Header: `pkcs11.h`, `pkcs11t.h`, `pkcs11f.h` (OASIS-Spec v2.40 oder v3.2).

Alle Funktionen geben `CK_RV` zurück. `CKR_OK = 0` bedeutet Erfolg. Jede andere Konstante ist ein Fehler, siehe Abschnitt 2.6.

### 2.1 Funktionsgruppen

| Gruppe | Wichtige Funktionen | Zweck |
|---|---|---|
| Bibliothek | `C_Initialize`, `C_Finalize`, `C_GetInfo`, `C_GetFunctionList` | Modul laden/entladen |
| Slot/Token | `C_GetSlotList`, `C_GetSlotInfo`, `C_GetTokenInfo`, `C_InitToken`, `C_InitPIN`, `C_SetPIN` | Slots/Tokens entdecken und verwalten |
| Session | `C_OpenSession`, `C_CloseSession`, `C_CloseAllSessions`, `C_GetSessionInfo` | Verbindung zum Token |
| Login | `C_Login`, `C_Logout` | Authentifizierung (User/SO) |
| Objekte | `C_CreateObject`, `C_DestroyObject`, `C_CopyObject`, `C_GetAttributeValue`, `C_SetAttributeValue`, `C_FindObjectsInit`, `C_FindObjects`, `C_FindObjectsFinal` | Keys, Zertifikate, Daten verwalten |
| Mechanismen | `C_GetMechanismList`, `C_GetMechanismInfo` | Welche Algorithmen kann das Token? |
| Signatur | `C_SignInit`, `C_Sign`, `C_SignUpdate`, `C_SignFinal` | Signieren |
| Verifikation | `C_VerifyInit`, `C_Verify`, `C_VerifyUpdate`, `C_VerifyFinal` | Verifizieren (oft im Token, aber Public-Key-Operation) |
| Verschlüsselung | `C_EncryptInit`, `C_Encrypt`, `C_DecryptInit`, `C_Decrypt` | Symmetrisch oder asymmetrisch |
| Digest | `C_DigestInit`, `C_Digest`, `C_DigestUpdate`, `C_DigestFinal` | Hashing |
| Key-Mgmt | `C_GenerateKey`, `C_GenerateKeyPair`, `C_WrapKey`, `C_UnwrapKey`, `C_DeriveKey` | Schlüsselerzeugung und -transport |
| RNG | `C_GenerateRandom`, `C_SeedRandom` | Zufallszahlen |

### 2.2 Wichtige Datentypen

| Typ | Bedeutung |
|---|---|
| `CK_SLOT_ID` | Identifiziert einen Slot |
| `CK_SESSION_HANDLE` | Identifiziert eine offene Session |
| `CK_OBJECT_HANDLE` | Identifiziert ein Objekt innerhalb einer Session |
| `CK_MECHANISM` | `{ mechanism, pParameter, ulParameterLen }` |
| `CK_MECHANISM_TYPE` | Konstante wie `CKM_SHA256_RSA_PKCS` |
| `CK_ATTRIBUTE` | `{ type, pValue, ulValueLen }` für Get/Set/Find |
| `CK_USER_TYPE` | `CKU_USER`, `CKU_SO`, `CKU_CONTEXT_SPECIFIC` |
| `CK_RV` | Return Code |

### 2.3 Typische Aufrufsequenz: Signieren

```c
#define CHECK(call) do {                         \
    CK_RV rv_ = (call);                          \
    if (rv_ != CKR_OK) {                         \
        /* Fehler behandeln, z. B. loggen/cleanup */ \
        goto cleanup;                            \
    }                                            \
} while (0)

CK_FUNCTION_LIST_PTR p11 = NULL_PTR;
CK_SESSION_HANDLE s = CK_INVALID_HANDLE;
CK_BYTE *sig = NULL_PTR;
CK_BBOOL initialized = CK_FALSE;
CK_BBOOL sessionOpen = CK_FALSE;
CK_BBOOL loggedIn = CK_FALSE;

/* Zu signierende Daten — von der Anwendung bereitgestellt */
CK_BYTE *data;
CK_ULONG dataLen;

CHECK(C_GetFunctionList(&p11));
CHECK(p11->C_Initialize(NULL));
initialized = CK_TRUE;

CK_SLOT_ID slot = 0;            /* echter Wert via C_GetSlotList ermitteln */
/* CKF_SERIAL_SESSION ist von der Spec vorgeschrieben (Legacy-Bit, muss immer gesetzt sein),
 * CKF_RW_SESSION nur, wenn schreibende Operationen wie Login als USER + Objekterzeugung kommen. */
CHECK(p11->C_OpenSession(slot, CKF_SERIAL_SESSION | CKF_RW_SESSION, NULL, NULL, &s));
sessionOpen = CK_TRUE;
CHECK(p11->C_Login(s, CKU_USER, (CK_UTF8CHAR*)"987654", 6));
loggedIn = CK_TRUE;

/* Privaten Key per ID suchen */
CK_OBJECT_CLASS cls = CKO_PRIVATE_KEY;
CK_BYTE id[] = { 0x01 };
CK_ATTRIBUTE tmpl[] = {
    { CKA_CLASS, &cls, sizeof(cls) },
    { CKA_ID,    id,  sizeof(id)  }
};
CK_OBJECT_HANDLE key;
CK_ULONG n;
CHECK(p11->C_FindObjectsInit(s, tmpl, 2));
CHECK(p11->C_FindObjects(s, &key, 1, &n));
CHECK(p11->C_FindObjectsFinal(s));
if (n != 1) {
    /* kein eindeutiger Key gefunden */
    goto cleanup;
}

/* Mechanismus: SHA256 + RSA-PKCS#1 v1.5, vom Token gehasht */
CK_MECHANISM mech = { CKM_SHA256_RSA_PKCS, NULL, 0 };
CHECK(p11->C_SignInit(s, &mech, key));

/* Zweistufiges Sign-Idiom: erst Länge ermitteln (Operation bleibt aktiv),
 * dann mit echtem Buffer signieren (Operation wird terminiert). */
CK_ULONG sigLen = 0;
CHECK(p11->C_Sign(s, data, dataLen, NULL_PTR, &sigLen));
sig = malloc(sigLen);
if (sig == NULL) {
    goto cleanup;
}
CHECK(p11->C_Sign(s, data, dataLen, sig, &sigLen));

cleanup:
free(sig);
if (p11 != NULL_PTR && loggedIn) {
    p11->C_Logout(s);
}
if (p11 != NULL_PTR && sessionOpen) {
    p11->C_CloseSession(s);
}
if (p11 != NULL_PTR && initialized) {
    p11->C_Finalize(NULL);
}
```

Für RSA-PSS braucht `mech.pParameter` zusätzlich eine `CK_RSA_PKCS_PSS_PARAMS`-Struktur mit Hash, MGF und Salt-Länge. Für ECDSA gibt es keine Parameter, das Encoding ist aber roh `r||s` — die DER-Verpackung übernimmt die jeweils darüberliegende Schicht (`pkcs11-tool --signature-format openssl`, OpenSSL-Engine/Provider, Java-Provider).

### 2.4 Objektattribute (Auszug)

| Attribut | Bedeutung |
|---|---|
| `CKA_CLASS` | `CKO_PRIVATE_KEY`, `CKO_PUBLIC_KEY`, `CKO_CERTIFICATE`, `CKO_SECRET_KEY`, `CKO_DATA` |
| `CKA_KEY_TYPE` | `CKK_RSA`, `CKK_EC`, `CKK_AES`, … |
| `CKA_ID` | Bytes, koppelt Private/Public/Cert (entscheidend für Java-Alias) |
| `CKA_LABEL` | UTF-8-String, menschenlesbarer Name |
| `CKA_TOKEN` | `TRUE` = persistent im Token, `FALSE` = nur Session |
| `CKA_PRIVATE` | `TRUE` = Login erforderlich, um das Objekt zu sehen |
| `CKA_SENSITIVE` | `TRUE` = Wert kann nicht ausgelesen werden |
| `CKA_EXTRACTABLE` | `FALSE` = Key verlässt das Token nie |
| `CKA_SIGN`, `CKA_VERIFY` | Nutzungsflags |
| `CKA_ENCRYPT`, `CKA_DECRYPT`, `CKA_WRAP`, `CKA_UNWRAP`, `CKA_DERIVE` | weitere Nutzungsflags |
| `CKA_MODULUS`, `CKA_PUBLIC_EXPONENT` | RSA-Parameter |
| `CKA_EC_PARAMS`, `CKA_EC_POINT` | EC-Parameter (DER-encoded `OID`/`OCTET STRING`) |

### 2.5 Wichtige Mechanismen

| `CK_MECHANISM_TYPE` | Bedeutung | Hinweise |
|---|---|---|
| `CKM_RSA_PKCS_KEY_PAIR_GEN` | RSA-Keypair erzeugen | `CKA_MODULUS_BITS`, `CKA_PUBLIC_EXPONENT` setzen |
| `CKM_EC_KEY_PAIR_GEN` | EC-Keypair erzeugen | `CKA_EC_PARAMS` = DER-OID der Kurve |
| `CKM_RSA_PKCS` | RSA-PKCS#1 v1.5, **ohne Hashing** | Input <= Modulus-Laenge minus 11 Bytes; fuer "SHA-x-with-RSA" baut die Anwendung die DigestInfo selbst |
| `CKM_SHA256_RSA_PKCS` | RSA-PKCS#1 v1.5, Token hasht | Bevorzugen |
| `CKM_RSA_PKCS_PSS` | RSA-PSS auf vorgehashtem Input | Hash mit Anwendung; `CK_RSA_PKCS_PSS_PARAMS` ist Pflicht |
| `CKM_SHA256_RSA_PKCS_PSS` | RSA-PSS, Token hasht | bevorzugen; `CK_RSA_PKCS_PSS_PARAMS` ist Pflicht |
| `CKM_ECDSA` | ECDSA über vorgehashte Daten | Input-Laenge = Curve-Order-Laenge (links truncated/zero-padded liegt in der Verantwortung der Anwendung); Ergebnis ist `r\|\|s`, **nicht** DER |
| `CKM_ECDSA_SHA256` | ECDSA inkl. Hashing | Ergebnis ist `r\|\|s` |
| `CKM_AES_GCM` | AES-GCM | Parameter `CK_GCM_PARAMS` |
| `CKM_SHA256` | reines Hashing | für Digest-Operationen |

Tipp: `C_GetMechanismList` plus `C_GetMechanismInfo` ist der ehrliche Weg, herauszufinden, was ein konkretes Token wirklich kann. `pkcs11-tool --list-mechanisms` ist dafür nur ein Wrapper.

### 2.6 Fehlercodes (`CKR_*`)

| Code | Häufige Ursache |
|---|---|
| `CKR_PIN_INCORRECT` | Falsche User-PIN |
| `CKR_PIN_LOCKED` | Zu viele Fehlversuche |
| `CKR_USER_NOT_LOGGED_IN` | Operation braucht Login, es gab keins |
| `CKR_SESSION_HANDLE_INVALID` | Session geschlossen oder fremde Session |
| `CKR_OBJECT_HANDLE_INVALID` | Objekt existiert nicht (mehr) in dieser Session |
| `CKR_MECHANISM_INVALID` | Token unterstützt diesen Algorithmus nicht |
| `CKR_MECHANISM_PARAM_INVALID` | PSS-Parameter falsch, MGF/Salt passt nicht |
| `CKR_KEY_TYPE_INCONSISTENT` | Key-Typ passt nicht zum Mechanismus |
| `CKR_ATTRIBUTE_VALUE_INVALID` | z. B. `CKA_EC_PARAMS` ist keine valide DER-OID |
| `CKR_TEMPLATE_INCONSISTENT` | Attribute widersprechen sich (z. B. `CKA_KEY_TYPE=CKK_RSA` zusammen mit `CKA_EC_PARAMS`, oder Nutzungsflags, die der Mechanismus nicht erlaubt) |
| `CKR_FUNCTION_NOT_SUPPORTED` | Funktion existiert in dieser Modulversion nicht |
| `CKR_DEVICE_ERROR` | Generisches HSM-Problem, ins Log des HSM schauen |

---

## 3. CLI: `pkcs11-tool` (OpenSC)

`pkcs11-tool` ist der pragmatischste Einstieg. Jeder Aufruf öffnet eine eigene Session, loggt ggf. ein, führt **eine** Operation aus und schließt wieder. Gut für Lab und Debugging — ungeeignet für Hochlast.

### Wichtige Optionen

| Option | Bedeutung |
|---|---|
| `--module <path>` | Pfad zur `.so` |
| `--list-slots` | Slots zeigen |
| `--list-mechanisms` | Mechanismen des Tokens |
| `--list-objects` | Objekte (Login je nach Sichtbarkeit nötig) |
| `--login --pin <pin>` | User-Login |
| `--token-label <label>` | Token per Label wählen — **stabiler als `--slot`** |
| `--id <hex>` / `--label <name>` | Objekt-Selektor |
| `--keypairgen --key-type rsa:2048` | RSA-Keypair |
| `--keypairgen --key-type EC:secp256r1` | EC-Keypair |
| `--sign --mechanism <name>` | Signieren |
| `--signature-format openssl` | ECDSA-DER statt rohem `r\|\|s` |
| `--hash-algorithm SHA256 --mgf MGF1-SHA256` | PSS-Parameter |
| `--read-object --type pubkey` | Public Key extrahieren |
| `--write-object … --type cert` | Zertifikat importieren |
| `--delete-object --type privkey --id 01` | Objekt löschen |
| `--test --login` | Schnelltest der wichtigsten Mechanismen |

Beispiele und typische Stolpersteine: siehe [cheatsheet.md](cheatsheet.md).

---

## 4. OpenSSL: Engine und Provider

OpenSSL kennt zwei Wege zu PKCS#11:

### 4.1 `engine_pkcs11` (OpenSSL 1.1.1 und OpenSSL 3.x legacy)

- Engine-Mechanismus, paketiert als `libengine-pkcs11-openssl` (Debian/Ubuntu).
- In OpenSSL 3.x funktioniert das weiterhin über die Legacy-Engine-Schnittstelle, ist aber nicht mehr der moderne Pfad.
- Konfiguration via `/etc/ssl/openssl.cnf` oder per `-engine pkcs11 -keyform engine`.
- Keys werden per **PKCS#11-URI** angesprochen. libp11 akzeptiert in der Praxis auch die Kurzform mit `pin-value` im Pfad.

```bash
# libp11-Kurzform mit pin-value im Pfad — bequem, aber nicht streng RFC 7512.
# Portable Variante siehe Abschnitt 4.3.
KEY_URI="pkcs11:token=$TOKEN;object=signing-key;type=private;pin-value=$PIN"

openssl req -new -x509 -engine pkcs11 -keyform engine \
  -key "$KEY_URI" -sha256 -days 365 \
  -subj "/CN=signing-key" -out cert.pem
```

### 4.2 `pkcs11-provider` (OpenSSL ≥ 3.0)

- Modernerer Ersatz, kein Engine-API mehr.
- Nicht Teil des Kurscontainers; das Lab nutzt `engine_pkcs11`. Für Provider-Tests brauchst du ein System/Image, in dem das OpenSSL-3-Provider-Modul installiert ist.
- Konfig in `openssl.cnf`:

```ini
openssl_conf = openssl_init

[openssl_init]
providers = provider_sect

[provider_sect]
default = default_sect
pkcs11 = pkcs11_sect

[default_sect]
activate = 1

[pkcs11_sect]
module = /usr/lib/x86_64-linux-gnu/ossl-modules/pkcs11.so
pkcs11-module-path = /usr/lib/softhsm/libsofthsm2.so
activate = 1
```

- Aufruf praktisch wie gewohnt, Key kommt wieder per URI. Für normale Signaturen ist `openssl dgst` meist der bessere Einstieg, weil Hashing und Signatursemantik explizit sind:

```bash
openssl dgst -sha256 \
  -sign "pkcs11:token=$TOKEN;object=signing-key;type=private;pin-value=$PIN" \
  -out data.sig data.txt
```

`openssl pkeyutl` ist dagegen ein Low-Level-Werkzeug. Nutze es nur, wenn du bewusst rohe oder exakt parametrisierte Public-Key-Operationen brauchst und Padding/Hashing selbst festlegst.

### 4.3 PKCS#11-URI nach RFC 7512

```
pkcs11:token=<label>;object=<label>;type=private;id=%01?pin-source=file:/run/secrets/pkcs11-pin
```

- `object=`/`id=` selektiert das Objekt
- `id=` ist percent-encoded Binary nach RFC 7512, also z. B. `%01` für die Byte-ID `01`
- `pin-value=`/`pin-source=` sind Query-Attribute und stehen nach `?`
- `pin-value=` ist für Demos okay, in Produktion besser `pin-source=file:...`, Secret-Manager oder eine interaktive PIN-Abfrage nutzen
- URI ist auch das stabile Interface gegenüber CI/CD-Pipelines

Viele libp11/OpenSSL-Beispiele, auch im Lab, nutzen aus Kompatibilitätsgründen die verbreitete Kurzform:

```bash
pkcs11:token=$TOKEN;object=signing-key;type=private;pin-value=$PIN
```

Das ist praktisch, aber nicht die portable RFC-7512-Schreibweise.

---

## 5. Java: SunPKCS11

Java spricht nicht direkt mit dem nativen Modul, sondern registriert einen JCA-Provider, der das übernimmt.

### Konfigurationsdatei

```text
name = SoftHSM
library = /usr/lib/softhsm/libsofthsm2.so
slotListIndex = 0
```

Das Format ist die NSS-aehnliche SunPKCS11-Config (Oracle PKCS#11 Reference Guide §Configuration File), nicht das Java-`.properties`-Format — `attributes = { ... }`-Bloecke sind erlaubt, Escaping ist anders.

Der finale Providername wird `SunPKCS11-SoftHSM`. `slotListIndex` ist für das Lab einfach, in echten Setups aber fragil, weil sich Slot-Reihenfolgen ändern können.

Wichtig: Die Standard-SunPKCS11-Config in OpenJDK kennt `slot` und `slotListIndex`, aber kein portables `tokenLabel`-Property. Wenn du Token-Labels nutzen willst, brauchst du entweder eine vorgelagerte Slot-Ermittlung (per `C_GetSlotList`/`C_GetTokenInfo` und passendem Label-Match) oder einen Stack mit eigener Label-Auswahl wie den IAIK-PKCS11-Provider.

### Initialisierung (Java 9+)

```java
Provider base = Security.getProvider("SunPKCS11");
Provider sun  = base.configure("softhsm.cfg");
Security.addProvider(sun);

KeyStore ks = KeyStore.getInstance("PKCS11", sun);
ks.load(null, "987654".toCharArray());           // Login

PrivateKey pk = (PrivateKey) ks.getKey("signing-key", null);

Signature sig = Signature.getInstance("SHA256withRSA", sun);
sig.initSign(pk);
sig.update(data);
byte[] signature = sig.sign();
```

### JCA-Algorithmusnamen vs. PKCS#11-Mechanismen

| JCA-Name | PKCS#11-Mechanismus |
|---|---|
| `SHA256withRSA` | `CKM_SHA256_RSA_PKCS` |
| `RSASSA-PSS` (+ `PSSParameterSpec`) | `CKM_RSA_PKCS_PSS` / `CKM_SHA256_RSA_PKCS_PSS` |
| `SHA256withECDSA` | `CKM_ECDSA_SHA256` (DER-Output durch Java) |
| `AES/GCM/NoPadding` | `CKM_AES_GCM` |

### Aliasing-Regel

`KeyStore.getInstance("PKCS11", provider)` zeigt einen privaten Key als Alias **nur**, wenn ein Zertifikat mit derselben `CKA_ID` existiert. Im Lab: erst `make import-cert`, dann `make java-demo`.

Weitere Details: [course/06-java-sunpkcs11.md](../course/06-java-sunpkcs11.md).

---

## 6. Best Practices und Querverweise

- **Token-Label statt Slot-Index** überall, wo der Stack es zulässt. Slot-Indizes verschieben sich, wenn ein zweites Token auftaucht.
- **PIN nie im Quellcode** — Env, Secret-Manager, oder `pin-source=` in der URI.
- **Mechanismus erst prüfen** (`C_GetMechanismInfo` oder `--list-mechanisms`), bevor du dich auf einen Algorithmus festlegst. Echte HSMs unterstützen nicht alles, was SoftHSM kann.
- **ECDSA-Encoding klären**: `r\|\|s` (PKCS#11 nativ) vs. DER (OpenSSL, Java). Falscher Wrapper → kaputte Signatur ohne sichtbaren Grund.
- **PSS-Parameter müssen synchron sein**: Hash, MGF-Hash, Salt-Länge bei Signer und Verifier identisch. Inkonsistente Parameter beim Signer liefern `CKR_MECHANISM_PARAM_INVALID`, beim Verifier `CKR_SIGNATURE_INVALID` bzw. `BadPaddingException`.
- **`CKA_ID` als Bindeglied**: Private Key, Public Key und Zertifikat sollten dieselbe ID tragen. Sonst sehen weder Java noch viele OpenSSL-Pfade die volle Kette.
- **Sessions kosten**: in Servern Session-Pooling implementieren, nicht pro Request `C_OpenSession`/`C_Login`/`C_CloseSession`.
- **Login-State ist tokenweit innerhalb derselben Anwendung**, nicht an genau eine Session gebunden — weitere Sessions dieses Prozesses zum selben Token sehen den Login ebenfalls. Andere Prozesse sind dadurch nicht automatisch eingeloggt.

Querverweise:

- [cheatsheet.md](cheatsheet.md) — kompakter Spickzettel für CLI-Befehle
- [course/01-grundlagen.md](../course/01-grundlagen.md) — Begriffe und Modell
- [course/04-signieren-und-verifizieren.md](../course/04-signieren-und-verifizieren.md) — Signaturen in der Praxis
- [course/06-java-sunpkcs11.md](../course/06-java-sunpkcs11.md) — Java-Details
- [course/08-debugging.md](../course/08-debugging.md) — Fehleranalyse
- [course/11-ec-und-pss.md](../course/11-ec-und-pss.md) — ECDSA und RSA-PSS

---

## 7. Referenzen

- OASIS PKCS#11 Base Specification v2.40 — https://docs.oasis-open.org/pkcs11/pkcs11-base/v2.40/pkcs11-base-v2.40.html
- OASIS PKCS#11 Specification v3.2 — https://docs.oasis-open.org/pkcs11/pkcs11-spec/v3.2/pkcs11-spec-v3.2.html
- OpenSC `pkcs11-tool` — https://github.com/OpenSC/OpenSC/wiki/Using-pkcs11-tool-and-OpenSSL
- OpenSSL `pkcs11-provider` — https://github.com/latchset/pkcs11-provider
- libp11 / `engine_pkcs11` — https://github.com/OpenSC/libp11
- Oracle PKCS#11 Reference Guide (SunPKCS11) — https://docs.oracle.com/en/java/javase/17/security/pkcs11-reference-guide1.html
- RFC 7512 — PKCS#11 URI — https://www.rfc-editor.org/rfc/rfc7512
