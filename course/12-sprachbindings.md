# 12 - Sprachbindings im Vergleich

> **Didaktischer Pfad:** Vorher → [`06-java-sunpkcs11.md`](06-java-sunpkcs11.md) · Nachher → [`08-debugging.md`](08-debugging.md) (vorgezogen, weil Debugging Grundwerkzeug ist)

## Lernziele

Nach diesem Kapitel kannst du:

- Java/Kotlin ueber JCA/SunPKCS11 von nativen Bindings in Go und C# unterscheiden.
- in jeder Sprache denselben PKCS#11-Grundfluss wiedererkennen.
- typische Fehler pro Stack schneller einordnen.
- **(Bloom 5 — evaluate)** fuer eine konkrete Anwendung entscheiden, welche Abstraktionsebene (JCA-`KeyStore`, native PKCS#11-API, Pkcs11Interop-Session) zur Architektur passt — und welche zwei Stack-Eigenschaften (Cleanup-Modell, vorhandene Cert-Plumbing-Erwartung) die Wahl tragen, nicht die Sprachpraeferenz.

> **Geschaetzte Bearbeitungszeit:** ~45 min (Lesen 25 min + alle vier Demos einmal laufen lassen 20 min). Die Lab-Dauer ist kurz, weil die Demos sich gegenseitig validieren — der Lernwert steckt im Vergleich der Stacks.

## Lab-Bezug

Passende Targets:

```bash
make java-demo
make go-demo
make kotlin-demo
make csharp-demo
```

Nachschlag zu Abkuerzungen wie JCA, JCE, Provider, Engine und PKCS#11-Praefixen: [Glossar](../docs/glossar.md).

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

| Sprache | Library/API | Abstraktion | Key-Auswahl | Zertifikat noetig?¹ |
|---|---|---|---|---|
| Java | JCA/JCE `SunPKCS11` | hoch | Java-KeyStore-Alias | ja, fuer Private-Key-Alias |
| Kotlin | JCA/JCE `SunPKCS11` | hoch | Java-KeyStore-Alias | ja, wie Java |
| Go | `github.com/miekg/pkcs11` | niedrig | Attribute wie `CKA_ID` | nein |
| C# | Pkcs11Interop | niedrig bis mittel | Attribute wie `CKA_ID` | nein |

¹ Bezug ist die Sichtbarkeit eines Private-Key-Alias im Java-`KeyStore`-Modell, nicht die PKCS#11-Signatur selbst. Auch ohne Zertifikat liesse sich der Key per nativem Provider/IAIK ueber `CKA_ID` ansprechen — die JCA-Konvention `KeyStore.getInstance("PKCS11")` macht es aber zur Pflicht.

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

## Selbsttest

<details>
<summary>1. Welcher der vier Bindings braucht das Zertifikat im Token, damit der Privkey adressierbar wird — und warum die anderen drei nicht?</summary>

Java/Kotlin (SunPKCS11). Die JCA-`KeyStore`-Abstraktion baut Aliase aus Cert/Privkey-Paaren mit gleicher `CKA_ID` — ohne Cert kein Alias. Go (`miekg/pkcs11`) und C# (`Pkcs11Interop`) sind duenne Wrapper ueber die C-API; sie suchen Privkeys direkt ueber `CKA_CLASS=CKO_PRIVATE_KEY` plus `CKA_ID=...`. Es ist keine PKCS#11-Pflicht, sondern eine Sprach-API-Konvention.
</details>

<details>
<summary>2. Welche zwei Cleanup-Schritte sind bei <code>miekg/pkcs11</code> in Go explizit zu pflegen, die SunPKCS11 transparent erledigt?</summary>

`CloseSession`/`Logout` (Session-Lifecycle) und `Finalize` (Library-Shutdown). SunPKCS11 macht das ueber JVM-Shutdown-Hooks und Provider-Lifecycle. miekg-Code muss `defer p.Finalize()` und `defer p.CloseSession(session)` explizit setzen, sonst leakt die Anwendung Handles und der Token bleibt im `CKR_USER_ALREADY_LOGGED_IN`-State.
</details>

<details>
<summary>3. Du baust einen Service, in dem die Hauptanwendung in Java geschrieben ist, ein Background-Worker aber in Go. Beide nutzen denselben SoftHSM-Token. Was waere die wahrscheinlichste Falle?</summary>

Sie nutzen unterschiedliche `CKA_*`-Konventionen. Java schreibt das Cert ueber SunPKCS11 mit bestimmten Default-Attributen, Go sucht ueber `CKA_ID=01` mit roher Byte-Equal. Klassische Falle: Java schreibt `CKA_ID` als String-Bytes (`"01"` = `0x30 0x31`), Go erwartet rohes Byte `0x01`. Resultat: derselbe Privkey, beide Stacks finden ihn nicht. Lab-Test mit `make list-objects` zeigt das immer; die SunPKCS11-Konvention setzt das Byte numerisch, aber das ist ein Implementations-Detail.
</details>
