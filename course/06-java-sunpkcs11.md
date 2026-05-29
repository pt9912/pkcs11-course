# 06 — Java mit SunPKCS11

## Lernziele

Nach diesem Kapitel kannst du:

- den SunPKCS11-Provider konfigurieren.
- einen PKCS#11-Token als Java-`KeyStore` laden.
- den Zusammenhang zwischen Zertifikat, `CKA_ID` und Java-Alias erklaeren.
- mit JCA ueber PKCS#11 signieren und mit einem Public Key verifizieren.

## Lab-Bezug

Passende Targets:

```bash
make import-cert
make java-demo
```

## Grundidee

Java greift nicht direkt auf `libsofthsm2.so` zu. Java lädt den `SunPKCS11` Provider, der eine native PKCS#11-Bibliothek konfiguriert. Danach nutzt du normale JCA-Klassen wie `KeyStore`, `Signature` und `PrivateKey`.

## Config

Datei: `lab/java/pkcs11-demo/src/main/resources/softhsm.cfg`

```text
name = SoftHSM
library = /usr/lib/softhsm/libsofthsm2.so
slotListIndex = 0
```

Das Format ist die NSS-aehnliche Config-Sprache von SunPKCS11 (Oracle PKCS#11 Reference Guide §Configuration File), nicht das Java-`.properties`-Format — Strings werden anders escaped und `attributes = { ... }`-Bloecke sind erlaubt.

Der finale Provider-Name wird daraus `SunPKCS11-SoftHSM`. In echten Setups ist `slotListIndex` fragil. Besser ist eine Token-Label-basierte Auswahl — OpenJDKs SunPKCS11 kennt dafür kein portables Property, also entweder vorgelagerte Slot-Ermittlung (`C_GetSlotList`/`C_GetTokenInfo` mit Label-Match) oder ein Stack mit eigener Label-Auswahl wie der IAIK-PKCS11-Provider. Siehe [docs/api.md §5](../docs/api.md#5-java-sunpkcs11).

Die Lab-Demo nimmt die Datei wahlweise als Pfad oder, wenn `PKCS11_SLOT_ID` bzw. `PKCS11_LIBRARY` gesetzt sind, als Inline-Config-Override (siehe `Pkcs11Demo.java` → `buildConfigArgument`). Damit laesst sich der Slot wechseln, ohne `softhsm.cfg` zu veraendern.

## Voraussetzungen

`KeyStore.getInstance("PKCS11", provider)` macht einen Private-Key-Alias nur sichtbar, wenn im Token ein Zertifikat mit derselben `CKA_ID` wie der private Schlüssel existiert. `make java-demo` ruft `import-cert` automatisch auf und kettet ueber dessen Abhaengigkeiten `gen-rsa` und `init-token`. Du kannst also direkt:

```bash
make java-demo
```

Die einzelnen Schritte sind nur dann zu nennen, wenn du eine Stufe gezielt isoliert testen willst — z. B. `make init-token` ohne anschliessenden Key.

## Java-Fluss

1. Provider konfigurieren via `Provider.configure(...)` — entweder mit einem Pfad oder mit Inline-Config-String (`"--name=...\nlibrary=...\n"`).
2. `KeyStore.getInstance("PKCS11", provider)` laden, PIN als `char[]`.
3. Alias suchen.
4. Private Key holen.
5. Mit `Signature` ueber den PKCS#11-Provider signieren.
6. Public Key aus dem Zertifikat ziehen und verifizieren — in der Lab-Demo bewusst ebenfalls ueber den PKCS#11-Provider, damit auch nicht-extrahierbare EC-Public-Keys funktionieren.

`Security.addProvider(...)` ist nicht noetig, wenn die Provider-Instanz direkt an `KeyStore.getInstance(..., provider)` und `Signature.getInstance(..., provider)` weitergereicht wird. Globale Registrierung erst dann, wenn man ueber Algorithmus-Namen ohne Provider-Argument arbeiten will.

Verifizieren mit dem Default-Provider ist nur eine Variante: Public Keys sind nicht sensitiv, das ist das Trennungsmodell von HSMs gegenueber Konsumenten. Sobald aber `CKA_EXTRACTABLE=false` ist (auf vielen produktiven HSMs Default), klappt der Default-Provider-Pfad nicht mehr.

## Typische Stolpersteine

- Leerer KeyStore, obwohl `pkcs11-tool --list-objects` Objekte zeigt → meistens kein Zertifikat oder unpassende `CKA_ID`.
- `KeyStore.aliases()` zeigt zwei Aliase für einen Key → manche Provider exponieren Cert-Alias und Key-Alias getrennt.
- `Signature.getInstance("SHA256withRSA")` ohne Provider beim Signieren → der Default-Provider versucht den Key zu extrahieren, das schlägt bei `sensitive`/`non-extractable` Keys fehl.
