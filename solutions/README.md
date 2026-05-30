# Musterloesungen

Die Loesungen sind bewusst knapp gehalten. Sie sollen bestaetigen, dass dein Ergebnis plausibel ist, nicht die Uebung ersetzen.

| Uebung | Loesung | Fokus |
|---|---|---|
| `exercises/01-token.md` | `01-token.md` | Token initialisieren, Slot-Beobachtung |
| `exercises/02-key-signature.md` | `02-key-signature.md` | RSA-Key, Signatur, OpenSSL Verify |
| `exercises/03-java.md` | `03-java.md` | SunPKCS11, KeyStore, Zertifikat |
| `exercises/04-go.md` | `04-go.md` | native PKCS#11-Ablaufe in Go |
| `exercises/05-kotlin.md` | `05-kotlin.md` | Kotlin ueber JCA/SunPKCS11 |
| `exercises/06-csharp.md` | `06-csharp.md` | Pkcs11Interop und native Ressourcen |
| `exercises/07-encrypt.md` | `07-encrypt.md` | Hybride Verschluesselung, OAEP-Wrap, Tampering-Erkennung |
| `exercises/08-cms.md` | `08-cms.md` | CMS/PKCS#7-Dokumentsignatur, ASN.1-Struktur, Cross-Lib-Interop |
| `exercises/09-streaming.md` | `09-streaming.md` | Multi-Part Sign + Encrypt von Grossdateien, pkcs11-spy Beweis |
| `exercises/10-hmac.md` | `10-hmac.md` | HMAC-SHA256, GENERIC_SECRET-Key, HS256-JWT, MAC-vs-Signatur |
| `exercises/11-session-pooling.md` | `11-session-pooling.md` | Pool-Pattern, Speedup-Messung, CKR_OPERATION_ACTIVE provozieren |
| `exercises/12-tls-mit-hsm.md` | `12-tls-mit-hsm.md` | TLS-Handshake mit HSM-Key, nginx+curl, Cipher-Suite-Beobachtung |
| `exercises/13-ssh-mit-hsm.md` | `13-ssh-mit-hsm.md` | SSH-Pubkey-Login ueber PKCS11Provider, ssh-agent, PIN-Handling |

Wenn Loesung und eigene Ausgabe abweichen, klaere zuerst:

1. Ist der Token initialisiert?
2. Existiert der RSA-Key mit `CKA_ID=01`?
3. Existiert fuer Java/Kotlin ein Zertifikat mit derselben `CKA_ID`?
4. Passt der Mechanism zur Verifikation?
5. Laeuft der Befehl im Devcontainer oder ausserhalb ueber Docker Compose?

Hinweis: Die Sprach-Demos haben unterschiedliche Output-Konventionen. Java und Kotlin verifizieren die Signatur intern in der JVM (`Verifikation: true`); Go und C# delegieren die Verifikation an einen anschliessenden `openssl dgst -verify`-Aufruf im Lab-Skript (`Verified OK`). Es ist also normal, dass die Logzeilen nicht identisch aussehen.

Wenn eine Aufgabe einen Fehlerfall ueber eine ENV-Variable (z. B. `PKCS11_USER_PIN=000000`) ausloest, fuehre die Vorstufe (`make init-token gen-rsa [import-cert]`) zuerst mit den echten Werten aus und starte erst dann die Sprach-Demo direkt — sonst stoppt schon die Make-Dependency-Kette und der Fehler erscheint nicht in der Sprache, in der er beobachtet werden soll.
