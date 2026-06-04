# 17 — Session-Pooling und Thread-Safety

## Bevor du anfaengst — was vermutest du?

> Wenn ein PKCS#11-`C_Logout` ein `close()` auf einem TCP-Socket waere — was wuerde dann beim Logout in einer Anwendung mit Pool zerbrechen?

Wahrscheinliche Vermutung: nichts Besonderes. Eine Session schliessen bedeutet, *diese* Session schliessen. Die anderen Sessions im Pool laufen weiter, weil sie ja "eigene Verbindungen" sind. So funktionieren Connection-Pools fuer Datenbanken oder HTTP — pro Session ein State.

Diese Vermutung ist falsch. `C_Login` und `C_Logout` wirken **anwendungsweit gegen das Token**, nicht session-weit (PKCS#11 §11.4). Ein versehentliches `Logout` pro Request wuerde alle anderen Sessions im Pool sofort in `CKR_USER_NOT_LOGGED_IN` schicken. Pool-Sessions teilen sich einen einzigen Login-State.

Halte die TCP-Socket-Karte fest. Dieses Kapitel ersetzt sie durch ein anderes Modell: Sessions sind Ausfuehrungs-Slots, der Login ist Anwendungs-global.

## Lernziele

Nach diesem Kapitel kannst du:

- erklaeren, warum eine PKCS#11-Session in den meisten Bindings nicht thread-safe ist.
- ein Sessions- bzw. Operationen-Pool-Pattern in der jeweiligen Sprache aufbauen.
- empirisch einschaetzen, wann Pooling tatsaechlich Durchsatz bringt — und wann nicht (SoftHSM-Eigenheit).
- die typischen Stolperfallen rund um `C_Login`-Lebensdauer und `fork()` benennen.
- **(Bloom 5 — evaluate)** anhand eines gemessenen Benchmarks bewerten, ob ein gegebener Speedup vom HSM, vom Pool oder vom Anwendungs-Overhead stammt — und welche Pool-Groesse fuer einen Production-Service (mit dokumentiertem HSM-Session-Limit) die richtige Wahl ist.

## Lab-Bezug

```bash
make gen-hmac           # Voraussetzung: HMAC-Key auf ID=05
make go-pool-demo       # Channel-Pool, atomic.Int64-Counter
make csharp-pool-demo   # BlockingCollection-Pool, Task.WhenAll
make java-pool-demo     # BlockingQueue<Mac> + ExecutorService
make kotlin-pool-demo   # Kotlin-Pendant
```

## Warum ueberhaupt poolen?

Drei Kosten ohne Pool, jede unabhaengig:

| Kostenquelle | Was passiert |
|---|---|
| `C_OpenSession` | Token allokiert eine Session-Struktur. Bei SoftHSM ein paar Mikrosekunden, bei Netz-HSMs ein USB- oder TLS-Roundtrip. |
| `C_Login` | PIN-Validierung auf dem Token. Bei Hardware-HSMs mit Smartcard-PIN-Pad mehrere hundert Millisekunden. |
| Garbage Collection / Object Allocation | Pro Request neue Cipher/Signature/Mac-Instanz erzeugen kostet GC-Druck. |

Pool-Pattern: einmal beim Startup N Sessions oeffnen und einloggen, dann jede Anfrage holt sich eine Session aus der Queue, gibt sie zurueck.

## C_Login wirkt anwendungsweit, nicht session-weit

PKCS#11 §11.4 ist hier unmissverstaendlich: nach `C_Login(session, CKU_USER, pin)` ist die **gesamte Anwendung gegenueber diesem Token** eingeloggt — egal wieviele Sessions zusaetzlich geoeffnet werden. Daraus folgt:

- **Login einmal**, irgendwann beim Pool-Startup. Nicht pro Session, nicht pro Request.
- `C_Logout` ebenso einmal beim Shutdown. Versehentliches Logout pro Request bricht alle anderen aktiven Sessions.
- Wer multi-tenant arbeitet (mehrere unabhaengige Tokens im selben Prozess), braucht **mehrere PKCS#11-Library-Instanzen** oder ein Token-Slot-Mapping — Login-State ist nicht pro Token, sondern pro Application+Token.

## Thread-Safety pro Binding

| Binding | Was ist thread-safe? | Was nicht? |
|---|---|---|
| **SunPKCS11 (Java/Kotlin)** | Provider, KeyStore, SecretKey-Lookup. Interne SunPKCS11-Session-Pool ist transparent. | `Mac`/`Signature`/`Cipher`-Instanzen sind stateful — eine Instanz pro Thread oder Pool von Instanzen. |
| **miekg/pkcs11 (Go)** | Library handle. Read-only Aufrufe wie `GetSlotList`. | `SessionHandle`-gebundene Calls (`Sign`, `Encrypt`, `Find*`). Wer parallel signiert, braucht parallel Sessions. |
| **Pkcs11Interop (C#)** | Library, wenn mit `AppType.MultiThreaded` geladen. `ISlot`-Lookup. | `ISession`-Operationen — gleicher Grund wie Go. |

## Pool-Patterns pro Sprache

| Sprache | Datenstruktur | Sync-Primitive |
|---|---|---|
| Go | `chan pkcs11.SessionHandle` | unbuffered channel ist selbst die Semaphore |
| C# | `BlockingCollection<ISession>` | interne Semaphore, `Take()`/`Add()` |
| Java | `BlockingQueue<Mac>` (oder `<ISession>`) | `take()`/`put()` |
| Kotlin | wie Java; alternativ Coroutines + `Channel` | mit Coroutines `withContext(Dispatchers.IO)` |

In allen vier Demos liegt die Pool-Groesse bei 8 Sessions und der Lasttest macht 10000 HMAC-SHA256-Operationen. Der gemessene Speedup zeigt eine wichtige Realitaet:

## Realitaets-Check: SoftHSM serialisiert

Auf SoftHSM 2.6 ist der Speedup mit 8 Workern **maximal etwa 1.3x**, oft sogar leicht unter 1.0 (Task-Spawn-Overhead). Grund: SoftHSM holt sich intern eine globale Library-Mutex pro Crypto-Operation. Mehr Parallelitaet auf der Anwendungsseite hilft nichts, wenn die Library im Backend serialisiert.

**Reale HSMs** mit Hardware-Parallelitaet (mehrere Crypto-Engines pro Modul, parallele Channels) skalieren mit der Pool-Groesse bis zur Anzahl der internen Engines. Bei Thales Luna oder Utimaco SecurityServer sind 50-80% lineare Skalierung bis Pool-Size ≈ 16-32 normal.

Lesson: **Pooling ist Korrektheits-Pattern, nicht Performance-Garantie**. Es verhindert das (oft unbemerkte) Anti-Pattern, dass eine Anwendung mit `n` Threads aber 1 Session reihenweise `CKR_OPERATION_ACTIVE`-Fehler erntet — und nutzt automatisch die HSM-Parallelitaet, falls vorhanden.

## `fork()`-Falle

Wer Worker-Prozesse forkt (Gunicorn-Style, Apache prefork), darf die PKCS#11-Library **NICHT** im Parent initialisieren. PKCS#11-Sessions sind nicht fork-safe: das Child erbt File-Descriptors zum HSM, aber der Token-internal State (Login-Status, Object Handles) ist beim ersten Aufruf zerschossen — typisches Symptom `CKR_DEVICE_ERROR` oder unerklaerliche Hangs.

Pattern: jeder Worker-Prozess ruft selbst `C_Initialize`/`C_Login` nach dem `fork()`. Im Parent passiert nur Bind/Listen/Dispatch, kein PKCS#11-Call.

## Eigenexperiment

- Aendere `POOL_SIZE` auf 1 und `TOTAL_OPS` auf 100 — beobachte den `CKR_OPERATION_ACTIVE`-Fehler... der NICHT kommt, weil die Demo eine einzige Session sequenziell nutzt. Setze stattdessen `POOL_SIZE = 1` UND spawne `TOTAL_OPS = 8` Worker, die alle die selbe Session direkt nutzen — `CKR_OPERATION_ACTIVE` ist da.
- Aendere `POOL_SIZE` auf 32 oder 64 — beobachte, dass der Speedup auf SoftHSM nicht weiter waechst, weil das Bottleneck im HSM liegt.
- Setze auf einem echten HSM (Cloud-HSM, YubiHSM) die Pool-Groesse und vergleiche.

Strukturierte Aufgaben in [`exercises/11-session-pooling.md`](../exercises/11-session-pooling.md).

## Selbsttest

<details>
<summary>1. Warum ist Pooling auf SoftHSM kaum spuerbar, auf realen HSMs aber stark?</summary>

SoftHSM 2.6 nimmt eine globale Library-Mutex pro Crypto-Operation — Anwendungs-Parallelitaet bringt nichts, wenn die Library im Backend serialisiert. Reale HSMs haben Hardware-Parallelitaet (mehrere Crypto-Engines, parallele Channels), Pooling skaliert dann annaehernd linear bis zur Anzahl interner Engines.
</details>

<details>
<summary>2. Du forkst einen Worker-Prozess. PKCS#11 wurde im Parent initialisiert. Was geht kaputt?</summary>

Sessions sind nicht fork-safe. Das Child erbt File-Descriptors zum HSM, aber der Token-internal State (Login-Status, Object Handles) ist beim ersten Aufruf inkonsistent — typisches Symptom `CKR_DEVICE_ERROR` oder unerklaerliche Hangs. Pattern: jeder Worker-Prozess ruft selbst `C_Initialize`/`C_Login` nach dem `fork()`. Im Parent passiert nur Bind/Listen/Dispatch, kein PKCS#11-Call.
</details>

<details>
<summary>3. Dein Service liefert 200 Request/s, das HSM 50 ops/s. Mehr Pool-Sessions — Loesung oder Symptom?</summary>

Symptom. Wer auf einem HSM-Limit haengt, kommt mit mehr Sessions an die Saettigung schneller, aber nicht ueber sie hinaus. Loesungen: HSM-Cluster (mehrere Geraete parallel), Cache-Layer (idempotente Operationen nicht wiederholen), Batching wo erlaubt, oder Re-Architektur (z.B. JWT mit Caching statt jeden Request neu zu signieren). Pool-Tuning ist nur die richtige Antwort, wenn das HSM noch nicht saturiert ist.
</details>
