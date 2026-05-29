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

```properties
name = SoftHSM
library = /usr/lib/softhsm/libsofthsm2.so
slotListIndex = 0
```

Der finale Provider-Name wird daraus `SunPKCS11-SoftHSM`. In echten Setups ist `slotListIndex` fragil. Besser ist eine Token-Label-basierte Auswahl — OpenJDKs SunPKCS11 kennt dafür kein portables Property, also entweder vorgelagerte Slot-Ermittlung (`C_GetSlotList`/`C_GetTokenInfo` mit Label-Match) oder ein Stack mit eigener Label-Auswahl wie der IAIK-PKCS11-Provider. Siehe [docs/api.md §5](../docs/api.md#5-java-sunpkcs11).

## Voraussetzungen

`KeyStore.getInstance("PKCS11", provider)` macht einen Private-Key-Alias nur sichtbar, wenn im Token ein Zertifikat mit derselben `CKA_ID` wie der private Schlüssel existiert. Deshalb:

```bash
make init-token
make gen-rsa
make import-cert
```

Erst danach:

```bash
make java-demo
```

`make java-demo` ruft `import-cert` automatisch auf.

## Java-Fluss

1. Provider konfigurieren.
2. Provider registrieren.
3. `KeyStore.getInstance("PKCS11", provider)` laden.
4. Alias suchen.
5. Private Key holen.
6. Mit `Signature` über den PKCS#11-Provider signieren.
7. Public Key aus dem Zertifikat ziehen, mit Default-Provider verifizieren.

Verifizieren ohne PKCS#11 ist Absicht: Public Keys sind nicht sensitiv, das ist genau das Trennungsmodell, das HSMs gegenüber Konsumenten bieten.

## Typische Stolpersteine

- Leerer KeyStore, obwohl `pkcs11-tool --list-objects` Objekte zeigt → meistens kein Zertifikat oder unpassende `CKA_ID`.
- `KeyStore.aliases()` zeigt zwei Aliase für einen Key → manche Provider exponieren Cert-Alias und Key-Alias getrennt.
- `Signature.getInstance("SHA256withRSA")` ohne Provider beim Signieren → der Default-Provider versucht den Key zu extrahieren, das schlägt bei `sensitive`/`non-extractable` Keys fehl.
