# Übung 06 — C# über Pkcs11Interop

## Aufgabe

Voraussetzung: Der Token `dev-token` ist initialisiert und der RSA-Key `signing-key` mit ID `01` existiert (`make init-token`, `make gen-rsa`).

.NET-Container starten (Default `pkcs11-lab` enthält kein dotnet-SDK):

```bash
docker compose -f lab/docker-compose.yml run --rm pkcs11-csharp bash
# alternativ pkcs11-dev mit allen Sprachen
```

1. Erstelle eine kleine .NET-Console-App mit `Pkcs11Interop`.
2. Lade das Modul aus `PKCS11_MODULE` oder nutze `/usr/lib/softhsm/libsofthsm2.so` als Default.
3. Finde den Slot über das Token-Label `dev-token`.
4. Öffne eine Read/Write-Session, logge dich mit `PKCS11_USER_PIN` ein und suche den privaten Key per `CKA_CLASS=CKO_PRIVATE_KEY` und `CKA_ID=01`.
5. Signiere die Bytes `hello from csharp pkcs11` mit `CKM_SHA256_RSA_PKCS`.
6. Exportiere den Public Key mit `pkcs11-tool --read-object --type pubkey --id 01` und verifiziere die Signatur mit OpenSSL.

## Erwartung

- Das Programm meldet Token-Label, Key-Label und Signaturlänge.
- Die OpenSSL-Verifikation liefert `Verified OK`.
- Session und Library werden deterministisch freigegeben (`using`/`Dispose`).

## Fehler erzwingen

- Nutze einen falschen Modulpfad. Erwartet: klarer Load-Fehler vor dem Login.
- Nutze eine falsche PIN. Erwartet: PKCS#11-Fehler `CKR_PIN_INCORRECT`.
