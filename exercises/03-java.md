# Uebung 03 - Java ueber SunPKCS11

## Ziel

Du bindest denselben Token ueber Java JCA/JCE an und siehst, warum Java fuer Private-Key-Aliase ein Zertifikat mit passender `CKA_ID` braucht.

## Vorbereitung

```bash
make init-token
make gen-rsa
make import-cert
```

## Aufgabe

1. Starte die Java-Demo:
   ```bash
   make java-demo
   ```
2. Pruefe den Output:
   - Providername
   - sichtbarer Alias
   - Key- und Zertifikatsstatus
   - Signaturverifikation
3. Oeffne `lab/java/pkcs11-demo/src/main/java/dev/course/pkcs11/Pkcs11Demo.java` und verfolge den JCA-Fluss von Provider-Konfiguration bis `Signature.verify`.

## Erwartete Ausgabe

- Der Provider heisst `SunPKCS11-SoftHSM`.
- Mindestens ein Alias zeigt `key=true cert=true`.
- Die Verifikation liefert `true`.
- Der Exit-Code ist `0`.

## Fehlerfall

1. Setze in `lab/java/pkcs11-demo/src/main/resources/softhsm.cfg` testweise einen falschen `library`-Pfad.
2. Starte `make java-demo`.
3. Stelle den korrekten Pfad danach wieder her.

Erwartet: Der Fehler tritt bereits beim Provider-Load auf, nicht erst beim Signieren.

Optional: Loesche das Zertifikat direkt und starte die Java-Demo ohne das Make-Target, damit `import-cert` nicht automatisch repariert. Die genaue Befehlsfolge steht in `solutions/03-java.md`.

## Reflexionsfragen

- Warum sieht Java den privaten Key nicht sauber, wenn das Zertifikat fehlt?
- Warum wird mit dem PKCS#11-Provider signiert, aber mit dem Default-Provider verifiziert?

## Musterloesung

Siehe `solutions/03-java.md`.
