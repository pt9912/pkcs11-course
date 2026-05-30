# Loesung 11 - Session-Pooling

## Baseline

Beispielausgaben (Werte schwanken je nach Hostlast):

```text
Go:       Sequenziell 56k ops/s, Parallel 62k ops/s, Speedup 1.09x
C#:       Sequenziell 71k ops/s, Parallel 52k ops/s, Speedup 0.73x
Java:     Sequenziell 70k ops/s, Parallel 54k ops/s, Speedup 0.78x
Kotlin:   Sequenziell 65k ops/s, Parallel 65k ops/s, Speedup 0.99x
```

Werte unter 1.0x sind **richtig** auf SoftHSM — das Backend serialisiert intern, der Task-Spawn-Overhead drueckt parallel knapp unter sequenziell.

## Anti-Pattern reproduzieren

Mit zerstoertem Pool (poolSize=1 + 8 Worker auf allSessions[0]) im Go-Demo erscheint:

```text
worker sign error: pkcs11: 0x90: CKR_OPERATION_ACTIVE
```

`CKR_OPERATION_ACTIVE` heisst: ein `C_SignInit` ist auf dieser Session bereits aktiv, ein zweiter darf nicht starten, bis `C_Sign` oder `C_SignFinal` den State freigegeben hat. Bei serialisiertem Code unmoeglich, bei parallelem ohne Synchronisation garantiert.

## Login-Doppelfehler

```text
C_Login: pkcs11: 0x100: CKR_USER_ALREADY_LOGGED_IN
```

PKCS#11-Implementierungen sind hier unterschiedlich tolerant — manche melden `CKR_OK`, andere `CKR_USER_ALREADY_LOGGED_IN`. Saubere Loesung: Login-State im App-Code tracken (`AtomicBoolean loggedIn`), oder den zweiten Login still ignorieren.

## Antworten zu den Reflexionsfragen

**SoftHSM vs reale HSMs:** SoftHSM ist eine Software-Implementierung in einer einzigen Library-Instanz mit globaler Mutex. Reale HSMs haben mehrere parallele Crypto-Engines (typisch 4-32) und konnen Operationen tatsaechlich parallel ausfuehren. Plus: der USB-/Netz-Roundtrip versteckt einen grossen Teil der Latenz, den Parallelitaet ausgleichen kann.

**Pool von Sessions vs Pool von Mac-Instanzen:** SunPKCS11 hat einen internen Session-Pool, den die App nicht sieht. Eine `Mac`-Instanz holt sich beim ersten Aufruf eine Session aus dem internen Pool und gibt sie spaeter zurueck. Damit reicht es, einen Pool von Mac-Instanzen zu haben — sie sind die "App-Sicht" auf die Sessions. In Go/C# gibt es diesen internen Pool nicht; die Anwendung MUSS Sessions selbst poolen.

**`CKR_OPERATION_ACTIVE`:** Zwei Threads rufen ohne Synchronisation `C_*Init` auf derselben Session, ohne dass der erste `C_*Final` oder `C_*` (Single-Shot) abgeschlossen hat. Auch innerhalb eines Threads: ein zweites `SignInit` ohne dazwischenliegendes `Sign` brennt.

**200 req/s, HSM 50 ops/s:** Pooling hilft hier **nicht**. Das HSM ist der Bottleneck; mehr Workers fuehren nur zu Warteschlangen vor dem HSM. Drei Optionen:
1. Mehrere HSMs (Pool von HSM-Verbindungen, Round-Robin oder Load-Balanced)
2. Batching (Multi-Part-Sign mit groesseren Payloads pro Aufruf, reduziert RPC-Overhead)
3. Caching/Reduction: kann das Ergebnis kurzfristig wiederverwendet werden? (z.B. JWT-Sign nur einmal pro Minute, nicht pro Request)
