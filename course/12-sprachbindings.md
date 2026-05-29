# 12 - Sprachbindings im Vergleich

## Lernziele

Nach diesem Kapitel kannst du:

- Java/Kotlin ueber JCA/SunPKCS11 von nativen Bindings in Go und C# unterscheiden.
- in jeder Sprache denselben PKCS#11-Grundfluss wiedererkennen.
- typische Fehler pro Stack schneller einordnen.
- entscheiden, welche Ebene fuer eine Anwendung passend ist.

## Lab-Bezug

Passende Targets:

```bash
make java-demo
make go-demo
make kotlin-demo
make csharp-demo
```

## Gemeinsamer Ablauf

Alle Sprachbeispiele tun fachlich dasselbe:

1. PKCS#11-Modul laden.
2. Token `dev-token` finden.
3. Session oeffnen.
4. Mit `PKCS11_USER_PIN` einloggen.
5. Private Key `signing-key` mit `CKA_ID=01` finden.
6. Daten signieren.
7. Signatur mit Public Key verifizieren.
8. Ressourcen freigeben.

Der Unterschied liegt in der Abstraktionsebene.

## Vergleich

| Sprache | Library/API | Abstraktion | Key-Auswahl | Zertifikat noetig? |
|---|---|---|---|---|
| Java | JCA/JCE `SunPKCS11` | hoch | Java-KeyStore-Alias | ja, fuer Private-Key-Alias |
| Kotlin | JCA/JCE `SunPKCS11` | hoch | Java-KeyStore-Alias | ja, wie Java |
| Go | `github.com/miekg/pkcs11` | niedrig | Attribute wie `CKA_ID` | nein |
| C# | Pkcs11Interop | niedrig bis mittel | Attribute wie `CKA_ID` | nein |

## Java und Kotlin

Java und Kotlin verwenden denselben Sicherheitsstack:

```text
Kotlin/Java Code
  -> JCA/JCE
    -> SunPKCS11 Provider
      -> libsofthsm2.so
```

Vorteile:

- vertraute Java-APIs wie `KeyStore` und `Signature`
- Provider-Modell passt gut in bestehende Java-Anwendungen
- Public-Key-Verifikation kann mit Default-Provider laufen

Stolpersteine:

- Private-Key-Alias wird ohne Zertifikat oft nicht sichtbar.
- `slotListIndex` ist fragil, wenn Slots wandern.
- Fehler sind oft in Java-Exceptions verpackt; die eigentliche Ursache steht tiefer in der Exception-Kette.

## Go

Go nutzt mit `github.com/miekg/pkcs11` eine duenne Schicht ueber die native C-API.

Vorteile:

- sehr nah an PKCS#11
- klare Kontrolle ueber Slots, Sessions, Attribute und Mechanisms
- gute Lernbasis fuer echte Cryptoki-Ablaufe

Stolpersteine:

- Cleanup muss explizit passieren: `Finalize`, `CloseSession`, `Logout`.
- Objekt-Handles sind Session-bezogen.
- Byte-Werte fuer `CKA_ID` muessen exakt passen.

## C#

C# nutzt Pkcs11Interop. Die API ist typisiert, bleibt aber nah an PKCS#11.

Vorteile:

- gute .NET-Integration
- `using`/`Dispose` passt gut zu PKCS#11-Ressourcen
- Attribute und Mechanisms bleiben sichtbar

Stolpersteine:

- native Library-Load-Fehler wirken zunaechst wie .NET-Probleme.
- falsche `CKA_ID` oder Objektklasse fuehrt schnell zu "kein Key gefunden".
- NuGet-Restore muss im Devcontainer in den Workspace-Cache laufen, nicht nach `/root`.

## Entscheidungshilfe

| Situation | Empfehlung |
|---|---|
| Java-/Kotlin-Service mit bestehendem JCA-Code | SunPKCS11 |
| Direkter Zugriff auf Attribute, Sessions, Mechanisms | Go oder C# native Binding |
| Provider-unabhaengige Signatur-API im Java-Stack | JCA mit sauberer Provider-Auswahl |
| Lernziel ist PKCS#11 selbst | Go oder C# lesen, dann Java-Mapping vergleichen |

## Uebungen

- `exercises/03-java.md`
- `exercises/04-go.md`
- `exercises/05-kotlin.md`
- `exercises/06-csharp.md`
