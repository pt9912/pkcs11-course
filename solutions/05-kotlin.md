# Lösung 05 — Kotlin über SunPKCS11

## Kernablauf

Kotlin/JVM nutzt denselben Stack wie die Java-Demo:

1. `Security.getProvider("SunPKCS11")` holen.
2. Provider mit `softhsm.cfg` konfigurieren.
3. `KeyStore.getInstance("PKCS11", provider)` öffnen.
4. `load(null, pin.toCharArray())` ausführen.
5. Alias `signing-key` suchen.
6. `PrivateKey` und Zertifikat aus dem KeyStore lesen.
7. Mit `Signature.getInstance("SHA256withRSA", provider)` signieren.
8. Mit dem Public Key aus dem Zertifikat verifizieren.

## Erwarteter Output

```text
Provider: SunPKCS11-SoftHSM
Alias: signing-key
Verifikation: true
```

## Typische Fehler

| Fehler | Ursache |
|---|---|
| Kein Alias sichtbar | Zertifikat fehlt oder `CKA_ID` passt nicht zum privaten Key |
| Login-Fehler | Falsche User-PIN |
| Provider-Load scheitert | Falscher `library`-Pfad in `softhsm.cfg` |
