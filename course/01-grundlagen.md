# 01 — Grundlagen

## Lernziele

Nach diesem Kapitel kannst du:

- die Begriffe Module, Slot, Token, Session, Object und Mechanism unterscheiden.
- erklaeren, warum private Schluessel im Token bleiben.
- die typische PKCS#11-Aufrufkette grob einordnen.
- Mechanism-Namen wie `CKM_SHA256_RSA_PKCS`, `CKM_RSA_PKCS_PSS` und `CKM_ECDSA_SHA256` auseinanderhalten.

## Lab-Bezug

Passende Targets:

```bash
make list-slots
make list-mechanisms
```

## Was ist PKCS#11?

PKCS#11, auch Cryptoki genannt, ist eine API für kryptografische Tokens. Eine Anwendung spricht nicht direkt mit einem privaten Schlüssel in einer Datei, sondern mit einer PKCS#11-Bibliothek. Diese Bibliothek leitet Operationen an ein Token oder HSM weiter.

Wichtig: PKCS#11 ist kein Zertifikatsformat und kein Keystore-Format. Es ist eine Schnittstelle.

"Token" oder "HSM" ist dabei nicht eine Geraeteklasse, sondern eine ganze Familie — TPM, Smartcard, USB-Token, PCIe-/Netzwerk-HSM, HLSM, Cloud-HSM, Cloud-KMS sprechen alle entweder PKCS#11 nativ oder haben eine Bruecke dorthin. Eine Einordnung mit Entscheidungsmatrix steht in [docs/hsm-kategorien.md](../docs/hsm-kategorien.md).

## Zentrale Begriffe

Ergaenzender Nachschlag fuer Abkuerzungen und Praefixe: [Glossar](../docs/glossar.md).

| Begriff | Bedeutung |
|---|---|
| Module | Native PKCS#11-Bibliothek, z. B. `libsofthsm2.so` |
| Slot | Logischer Steckplatz, in dem ein Token vorhanden sein kann |
| Token | Kryptografischer Container mit Objekten |
| Session | Verbindung einer Anwendung zu einem Slot/Token |
| Login | Authentifizierung mit User-PIN — gilt token-weit innerhalb derselben Anwendung. Sessions desselben Prozesses sehen den Login-State, fremde Anwendungen auf demselben Slot nicht. |
| Object | Key, Zertifikat oder Datenobjekt im Token |
| Mechanism | Kryptografischer Algorithmus/Modus, z. B. `SHA256-RSA-PKCS` |
| Attribute | Eigenschaften eines Objekts, z. B. `CKA_SIGN`, `CKA_ID`, `CKA_LABEL` |

## Warum ist das relevant?

Bei einem HSM soll der private Schlüssel das System nie verlassen. Die Anwendung übergibt Daten oder Hashes an das Token. Das Token führt die Operation aus und gibt nur das Ergebnis zurück, z. B. eine Signatur.

## Typischer Ablauf

```text
Anwendung
  -> PKCS#11-Module
    -> Slot
      -> Token
        -> Session/Login
          -> Object suchen
            -> Mechanism wählen
              -> Operation ausführen
```

## Wichtige Unterscheidung

- `CKM_RSA_PKCS`: rohes RSA-PKCS#1-v1.5-Padding. Der Mechanismus signiert beliebige Eingaben bis Modulus-Länge minus 11 Bytes. Wenn daraus eine Hash-basierte Signatur (z. B. "SHA256withRSA") werden soll, ist es Aufgabe der Anwendung, vorab eine vollständige DigestInfo (`SEQUENCE { algorithm OID, OCTET STRING hash }`) zu bilden und genau diese an den Mechanismus zu uebergeben.
- `CKM_SHA256_RSA_PKCS`: Token hasht und signiert.
- `CKM_RSA_PKCS_PSS`: RSA-PSS, moderne Signaturvariante, aber Details wie Salt-Länge und MGF-Hash müssen passen.
- `CKM_ECDSA_SHA256`: ECDSA mit SHA-256 auf einer EC-Kurve, kleiner und schneller als RSA.

Viele Fehler kommen daher, dass Anwendung und Token unterschiedliche Annahmen über Hashing, Padding oder Signatur-Encoding (raw `r||s` vs. DER `SEQUENCE`) haben. Details in [11 — ECDSA und RSA-PSS](11-ec-und-pss.md).
