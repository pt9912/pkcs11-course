# Uebung 07 - Hybride Verschluesselung

## Ziel

Du erzeugst einen sortenreinen RSA-Wrap-Key im Token, verschluesselst ein Dokument hybrid (AES-256-GCM + RSA-OAEP-Wrap) und entschluesselst es ueber den HSM zurueck. Anschliessend ergaenzt du eine der vier Sprach-Demos.

## Vorbereitung

```bash
make init-token
```

## Aufgabe 1 — Wrap-Key erzeugen

1. Lege den Wrap-Key an:
   ```bash
   make gen-rsa-wrap
   ```
2. Liste die Objekte:
   ```bash
   make list-objects
   ```

## Aufgabe 2 — Bash-Encrypt/Decrypt

1. Schreibe deinen Klartext nach `lab/work/document.txt` (oder benutze den Default, den das Skript anlegt).
2. Verschluesseln:
   ```bash
   make encrypt
   ```
3. Entschluesseln und Round-Trip pruefen:
   ```bash
   make decrypt
   ```

## Aufgabe 3 — Tampering erkennen

1. Encrypt einmal sauber durchlaufen lassen.
2. Eine **einzige** Byte-Position in `lab/work/document.enc` aendern (z.B. mit `printf 'X' | dd of=lab/work/document.enc bs=1 seek=0 conv=notrunc`).
3. `make decrypt` erneut starten.
4. Erwartung: das Decrypt-Skript bricht mit Exit 2 ab und meldet "ungueltiger Auth-Tag" — AES-GCM erkennt die Manipulation.

## Aufgabe 4 — Eine Sprach-Demo durchspielen

Waehle eine der vier:

```bash
make go-encrypt-demo        # miekg/pkcs11 direkt
make csharp-encrypt-demo    # Pkcs11Interop direkt
make java-encrypt-demo      # SunPKCS11 mit Software-OAEP-Workaround
make kotlin-encrypt-demo    # Kotlin-Pendant zu Java
```

Java und Kotlin brauchen vorher den Plumbing-Cert (das `make`-Target `issue-wrap-cert` wird automatisch ausgefuehrt).

## Erwartete Ausgabe

- `make encrypt` schreibt `lab/work/wrapped-key.bin`, `lab/work/aes-iv.bin`, `lab/work/document.enc`.
- `make decrypt` endet mit `Round-Trip OK` und legt `lab/work/document.dec` an, das byte-identisch zu `lab/work/document.txt` ist.
- Die Sprach-Demos enden mit `Round-Trip: OK` und schreiben Artefakte mit Praefix `go-*`, `java-*`, `kotlin-*`, `csharp-*` nach `lab/work/`.

## Reflexionsfragen

- Warum wird der AES-Key auf dem Host erzeugt und nicht im HSM? Was waere der Nachteil, wenn der AES-Key dauerhaft im HSM laege?
- Wieso fragen Java/Kotlin nicht direkt nach `RSA/ECB/OAEPPadding` mit dem SunPKCS11-Provider, sondern paddieren in Software?
- Welches Risiko entsteht, wenn der Sender den gewrappten AES-Key wiederverwendet (gleicher Key fuer zwei Dokumente, neuer IV)?
- Was passiert konkret, wenn Sender und Empfaenger verschiedene OAEP-Hash-Algorithmen waehlen?

## Musterloesung

Siehe `solutions/07-encrypt.md`.
