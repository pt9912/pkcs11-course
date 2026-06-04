# Loesung 00 - Vokabular-Selbsttest

## Aufgabe 1 — Praefix-Familien

| Praefix | Steht fuer | Typisches Beispiel |
|---|---|---|
| `CKR_` | **R**eturn value — Fehler-/OK-Code von `C_*`-Calls | `CKR_OK`, `CKR_PIN_INCORRECT` |
| `CKM_` | **M**echanism — kryptographischer Algorithmus/Modus | `CKM_SHA256_RSA_PKCS`, `CKM_AES_GCM` |
| `CKA_` | **A**ttribute — Eigenschaft eines Objekts | `CKA_ID`, `CKA_SIGN`, `CKA_SENSITIVE` |
| `CKO_` | **O**bject class — Art des PKCS#11-Objekts | `CKO_PRIVATE_KEY`, `CKO_CERTIFICATE` |
| `CKK_` | **K**ey type — Algorithmus-Familie eines Keys | `CKK_RSA`, `CKK_EC`, `CKK_AES`, `CKK_GENERIC_SECRET` |
| `CKF_` | **F**lag — Bit-Flag in Token-/Session-/Mechanism-Info | `CKF_RW_SESSION`, `CKF_USER_PIN_LOCKED` |
| `CKU_` | **U**ser type — Rolle beim Login | `CKU_USER`, `CKU_SO` |
| `CKZ_` | OAE**Z** (Source-Parameter) — fuer OAEP-Source-Attribut | `CKZ_DATA_SPECIFIED` |

## Aufgabe 2 — Sechs Begriffe

1. **Module** — die native PKCS#11-Bibliothek (z.B. `libsofthsm2.so`), die eine Anwendung dynamisch laedt.
2. **Slot** — ein logischer Steckplatz im Modul, in dem ein Token vorhanden sein **kann**.
3. **Token** — der kryptographische Container im Slot, der persistente Objekte (Keys, Certs) traegt und PINs verwaltet.
4. **Session** — die laufzeitgebundene Verbindung einer Anwendung zu einem Slot/Token, ueber die `C_*`-Calls laufen.
5. **Object** — ein Eintrag im Token (Key, Cert, Datenobjekt), beschrieben durch eine Menge von Attributen.
6. **Mechanism** — der konkrete Algorithmus inkl. Padding/Modus, mit dem eine Operation ausgefuehrt wird.

**Persistent:** Module (als Datei am Filesystem), Token (auf dem HSM), Object (im Token). **Laufzeit-only:** Slot (Liste wird neu aufgebaut), Session, Mechanism (waehlt man pro Operation; Token meldet nur die unterstuetzten via `C_GetMechanismList`).

## Aufgabe 3 — Begriffspaare

1. **`CKA_SENSITIVE` vs `CKA_EXTRACTABLE`**
   `CKA_SENSITIVE=true` verbietet das **Lesen** des Schluesselwerts ueber `C_GetAttributeValue(CKA_VALUE)`. `CKA_EXTRACTABLE=false` verbietet zusaetzlich den **verschluesselten Export** ueber `C_WrapKey`. Beide zusammen sind das HSM-Sicherheitsmodell. `CKA_EXTRACTABLE` darf laut Spec nur in eine Richtung wechseln (`true → false`).

2. **`CKM_RSA_PKCS` vs `CKM_SHA256_RSA_PKCS`**
   Bei `CKM_RSA_PKCS` paddet das Token PKCS#1-v1.5, **hasht aber nicht** — der Aufrufer muss die `DigestInfo` selbst bilden. Bei `CKM_SHA256_RSA_PKCS` hasht das Token zusaetzlich. Cross-Verify mit OpenSSL/JCA klappt nur, wenn beide Seiten dieselbe Variante meinen.

3. **`CKU_USER` vs `CKU_SO`**
   `CKU_USER` ist die normale Anwendungs-Rolle — darf Crypto-Operationen und die eigene User-PIN aendern. `CKU_SO` (Security Officer) initialisiert das Token (`C_InitToken`), setzt die User-PIN nach einem Lockout zurueck (`C_InitPIN`) und aendert die SO-PIN. Crypto-Operationen sind dem SO typisch **nicht** erlaubt.

## Aufgabe 4 — Diagnose ohne Lookup

| Fehler | Wahrscheinlichste Ursache |
|---|---|
| `CKR_PIN_INCORRECT` | Login mit falscher PIN oder mit der falschen Rolle (User- statt SO-PIN oder umgekehrt). |
| `CKR_KEY_FUNCTION_NOT_PERMITTED` | Versuch, einen Key fuer eine Operation zu nutzen, fuer die er nicht geflagt ist (z.B. `signing-key` zum Decrypt, KEK zum Encrypt). Klassische CKA-Usage-Trennung. |
| `CKR_MECHANISM_INVALID` | Mechanism vom Token nicht unterstuetzt — `pkcs11-tool --list-mechanisms` zeigt, was angeboten wird. |
| `CKR_ATTRIBUTE_SENSITIVE` | Versuch, `CKA_VALUE` eines `CKA_SENSITIVE=true`-Keys zu lesen. Lehrbuchfall der HSM-Sicherheit. |
| `CKR_SESSION_HANDLE_INVALID` | Session-Handle aus einer alten oder einer fremden Session benutzt. Tritt nach `C_Finalize`/`C_CloseSession` auf oder wenn man Handles ueber Threads/Prozesse weiterreicht. |

## Selbst-Einschaetzung

- **0-1 Aufgaben sicher beantwortet:** starte mit Kap. 01 und nimm dir das Glossar als staendigen Begleiter.
- **2-3 Aufgaben sicher beantwortet:** typischer Stand nach Kap. 01-03. Diese Uebung in zwei Wochen erneut machen.
- **4 Aufgaben sicher beantwortet:** Kursziel "PKCS#11-Begriffe sauber erklaeren" ist erfuellt.
