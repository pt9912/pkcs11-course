# Loesung 05 - Kotlin ueber SunPKCS11

## Lauf

```bash
make init-token
make gen-rsa
make import-cert
make kotlin-demo
```

Erwartet (gekuerzt — die Demo druckt zusaetzlich `Mechanismus: ...`, `Aliase im PKCS#11-KeyStore:` und `Signatur (Base64): ...`):

```text
Provider: SunPKCS11-SoftHSM
Alias: signing-key
Verifikation: true
```

## Kernablauf im Code

`lab/kotlin/pkcs11-demo/src/main/kotlin/dev/course/pkcs11/KotlinPkcs11Demo.kt` nutzt denselben JCA-Fluss wie Java:

1. `Security.getProvider("SunPKCS11")`.
2. Provider mit `softhsm.cfg` konfigurieren.
3. `KeyStore.getInstance("PKCS11", provider)`.
4. `load(null, pin.toCharArray())`.
5. Alias `signing-key` suchen.
6. `PrivateKey` und Zertifikat aus dem KeyStore lesen.
7. Mit `Signature.getInstance("SHA256withRSA", provider)` signieren.
8. Mit dem Public Key aus dem Zertifikat verifizieren.

## Fehler pruefen

`make kotlin-demo` haengt an `import-cert -> gen-rsa -> init-token`. Sobald du eine ENV-Variable umstellst, kann eine dieser Vorstufen scheitern, bevor Kotlin gestartet wird. Deshalb Vorstufe wie gewohnt laufen lassen und die Demo direkt mit der Manipulation aufrufen.

Falsche PIN:

```bash
make init-token gen-rsa import-cert
docker compose -f lab/docker-compose.yml run --rm \
  -e PKCS11_USER_PIN=000000 \
  pkcs11-kotlin bash -lc 'cd lab/kotlin/pkcs11-demo && ./gradlew --quiet --no-daemon run'
```

Erwartet: `reportFailure` druckt eine `ProviderException`-Kette inklusive `CKR_PIN_INCORRECT`.

Falsche Library:

```bash
docker compose -f lab/docker-compose.yml run --rm \
  -e PKCS11_LIBRARY=/nicht/da \
  pkcs11-kotlin bash -lc 'cd lab/kotlin/pkcs11-demo && ./gradlew --quiet --no-daemon run'
```

Erwartet: Provider-Load schlaegt mit `IOException`/`CKR_GENERAL_ERROR` fehl.

Wenn das Zertifikat fehlt, ist der private Key fuer den Java-KeyStore nicht als Private-Key-Alias nutzbar. `make kotlin-demo` repariert das ueber die Abhaengigkeit `import-cert` automatisch; fuer den Fehlerfall musst du die Demo direkt starten.

## Antworten zu den Reflexionsfragen

**1. (Recall) Java vs Kotlin fuer PKCS#11:** Fast nichts. Kotlin nutzt **denselben** JCA-Provider, denselben `KeyStore`, dieselbe `Signature`-API. Unterschiede sind syntaktisch (Properties statt Getter, `companion object` statt `static`, `?.let`-Idioms). Sicherheitsrelevant ist die Wahl der Sprache nicht — wer die JCA-Eigenheiten in Java verstanden hat, hat sie in Kotlin verstanden. Der eigentliche Unterschied ist ein Build-System-Detail: Kotlin braucht das `kotlin-stdlib` zur Laufzeit, Java nicht.

**2. (Analyse) Zertifikat-Bedingung gilt auch fuer Kotlin:** Weil der Stack derselbe ist, gilt die SunPKCS11-Alias-Regel identisch — ohne Cert mit passender `CKA_ID` kein Private-Key-Alias. Die Stelle, an der man theoretisch umbauen koennte: SunPKCS11s `P11KeyStore`-Implementierung (im `sun.security.pkcs11`-Package) baut die Alias-Liste, indem es Cert-Objekte enumeriert und jedem mit gleicher `CKA_ID` einen Privkey zuordnet. Wer eine eigene Provider-Subklasse oder den IAIK-PKCS11-Provider nutzt, kann den Alias auch ueber `CKA_LABEL` direkt aufbauen — dann braucht es kein Cert. In Lab und Production ist das selten; der `make import-cert`-Pfad bleibt der pragmatische Default.

**3. (Analyse) Coroutines + SunPKCS11:** `Dispatchers.IO` ist ein Thread-Pool, der Coroutines auf bis zu 64 Worker-Threads verteilt. Eine Coroutine wechselt potentiell bei jedem `suspend`-Aufruf (`delay`, `withContext`, IO-Lese-Operationen) den Thread. Konsequenz fuer `Signature`-Instanzen: sie sind **stateful** zwischen `update()` und `sign()`. Wenn die Coroutine zwischen diesen beiden Calls den Thread wechselt, ist das technisch ok (JCA-Instanzen sind nicht thread-local-gebunden), aber **niemals zwei Coroutines auf derselben Instanz**. Pattern: pro Sign-Aufruf eine neue `Signature.getInstance(...)`-Instanz (so wie die SunPKCS11-Pool-Skizze in Kap. 09 zeigt), nicht die Instanz teilen. Das matched mit Kap. 17 §"SunPKCS11 (Java/Kotlin)" — `Mac`/`Signature`/`Cipher` sind stateful, also pro Thread oder pro Operation.

**4. (Evaluate) Java vs Kotlin in gemischtem Team:** Zwei nicht-technische Faktoren, die schwerer wiegen als Code-Aequivalenz:

- **Wartbarkeit unter Stress.** PKCS#11-Bugs treten oft nachts unter Produktionslast auf. Der Oncall-Engineer muss die Codebasis dann lesen koennen, ohne erst die Sprache zu lernen. In einem Team mit 80 % Java-Skill: Java schreiben, auch wenn Kotlin die schoenere DSL hat. Die Sicherheits-Domaene ist nicht der Platz fuer Onboarding-Hürden.
- **Build-/Tooling-Stabilitaet.** Java hat im Enterprise-Setup laengere Toolchain-Lebenszeit (JDK-LTS, Maven, Spotbugs/PMD). Kotlin ist langlebig, aber das Ecosystem (KSP, kotlinx-coroutines-Versionen, IDE-Plugin-Verträglichkeit) bringt mehr Update-Aufwand. Fuer eine Service-Komponente, die 10 Jahre laufen soll, kann das den Ausschlag geben.

Reasonable Gegenargument: in einem Kotlin-First-Team mit aktivem Kotlin-Stack ist das umgekehrt — dann ist Java die fragile Fremdsprache. Die Entscheidung ist **kontextsensitiv**, nicht universal.
