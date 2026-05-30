# 17 — Session-Pooling und Thread-Safety

## Lernziele

Nach diesem Kapitel kannst du:

- erklaeren, warum eine PKCS#11-Session in den meisten Bindings nicht thread-safe ist.
- ein Sessions- bzw. Operationen-Pool-Pattern in der jeweiligen Sprache aufbauen.
- empirisch einschaetzen, wann Pooling tatsaechlich Durchsatz bringt — und wann nicht (SoftHSM-Eigenheit).
- die typischen Stolperfallen rund um `C_Login`-Lebensdauer und `fork()` benennen.

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
