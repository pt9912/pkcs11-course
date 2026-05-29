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

Wenn Loesung und eigene Ausgabe abweichen, klaere zuerst:

1. Ist der Token initialisiert?
2. Existiert der RSA-Key mit `CKA_ID=01`?
3. Existiert fuer Java/Kotlin ein Zertifikat mit derselben `CKA_ID`?
4. Passt der Mechanism zur Verifikation?
5. Laeuft der Befehl im Devcontainer oder ausserhalb ueber Docker Compose?
