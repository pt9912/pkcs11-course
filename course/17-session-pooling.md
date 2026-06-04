# 17 — Session-Pooling und Thread-Safety

> **Didaktischer Pfad:** Vorher → [`16-hmac.md`](16-hmac.md) · Nachher → [`18-tls-mit-hsm.md`](18-tls-mit-hsm.md)

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

> **Geschaetzte Bearbeitungszeit:** ~75 min (Lesen 25 min + ein Sprach-Worked-Example 25 min + Pool-Size-Variation als Faded 25 min). Die "SoftHSM serialisiert"-Lektion ist mental teurer als der Code — nicht ueberlesen.

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

## Pool-Patterns pro Sprache: Ueberblick

Vier Demos, alle mit Pool-Groesse 8 und 10000 HMAC-SHA256-Operationen. Wir arbeiten den Go-Pool vollstaendig durch (`chan`-basiert, Standard-Pattern) und lassen die drei anderen als Faded Examples folgen.

| Sprache | Datenstruktur (kurz) | Sync-Primitive |
|---|---|---|
| Go | `chan pkcs11.SessionHandle` | unbuffered channel ist selbst die Semaphore |
| C# | `BlockingCollection<ISession>` | interne Semaphore, `Take()`/`Add()` |
| Java | `BlockingQueue<Mac>` (oder `<ISession>`) | `take()`/`put()` |
| Kotlin | wie Java; alternativ Coroutines + `Channel` | `withContext(Dispatchers.IO)` |

### Worked Example: der Go-Pool (vollstaendig)

```bash
make gen-hmac           # Voraussetzung: HMAC-Key auf ID=05
make go-pool-demo       # Channel-Pool, atomic.Int64-Counter
```

**Schritt 1 — Init: einmal `C_Initialize`, einmal `C_Login`.** Der `main`-Goroutine ruft `p.Initialize()` einmal beim Startup. Wichtig: Login wirkt anwendungsweit (PKCS#11 §11.4) — wir loggen **einmal** mit einer der spaeter geoeffneten Sessions und nicht pro Session erneut.

**Schritt 2 — Pool aufbauen: N Sessions vorab oeffnen.** Eine Schleife `for i := 0; i < poolSize; i++` ruft `p.OpenSession(slot, CKF_SERIAL_SESSION|CKF_RW_SESSION)` und legt das Handle in den Channel `pool := make(chan pkcs11.SessionHandle, poolSize)`. Nach der Schleife hat der Channel N Handles, der Buffer ist voll.

**Schritt 3 — Hot-Path: Borrow/Return ueber Channel-Operationen.** Jeder Worker macht:

```go
session := <-pool                 // blockierend, wenn keine Session frei
sig, err := p.Sign(session, msg)  // HMAC-Operation
pool <- session                   // zurueck in den Pool
```

Der Channel **ist** die Semaphore. Wenn 8 Sessions im Pool sind und 12 Worker laufen, blockieren 4 Worker, bis eine Session zurueckkommt — kein `sync.WaitGroup`, kein eigener Mutex.

**Schritt 4 — Mess-Schleife.** Sequenziell: ein Worker, 10000 Operationen, Wallclock. Parallel: 8 Worker, `sync.WaitGroup`, 10000 Operationen verteilt, Wallclock. `atomic.Int64` zaehlt erfolgreiche Operationen, falls einer scheitert.

**Schritt 5 — Cleanup.** `for i := 0; i < poolSize; i++ { p.CloseSession(<-pool) }` leert den Channel und schliesst jede Session. Danach `p.Logout(session)` einmal und `p.Finalize()`. **Reihenfolge** ist wichtig — `Finalize` vor `CloseSession` waere ein Fehler.

**Schritt 6 — Ergebnis lesen.** Auf SoftHSM: Sequenziell ~xxx ops/s, Parallel ~1.0-1.3x. Auf realem HSM mit Hardware-Parallelitaet: bis ~8x bei Pool-Groesse 8. Pool ist Korrektheits-Pattern (verhindert `CKR_OPERATION_ACTIVE`), nicht Performance-Garantie.

Der gewonnene Schema-Kern: **Pool besteht aus drei Stueck: (1) gemeinsame Library-Init + Login einmal, (2) N persistente Sessions im Buffer, (3) sync-Primitive, die als Semaphore wirken. Der Hot-Path beruehrt keine PKCS#11-Lifecycle-Funktionen mehr.**

### Faded Examples — die drei Sprach-Pfade

Pro Pfad: kurze Tabellen-Beschreibung und **drei Leitfragen**.

#### C# (`Pkcs11PoolDemo`)

| Datenstruktur | Sync-Primitive | Worker-Lib |
|---|---|---|
| `BlockingCollection<ISession>` | interne Semaphore, `Take()`/`Add()` | `Task.WhenAll` mit `Task.Run(...)` pro Worker. |

Leitfragen:

1. **Was passiert bei `Take()`, wenn die Collection leer ist?** Vergleiche mit Go's `<-pool`. Welche Komponente blockiert wo?
2. **Pkcs11Interop wird mit `AppType.MultiThreaded` initialisiert. Was bedeutet das fuer die Library-internen Locks, und welcher CKR-Fehler waere bei `SingleThreaded` mit mehreren Workern zu erwarten?**
3. **Wo passiert `Login` — einmal beim Setup oder pro Session?** Wenn pro Session: was wuerde ein `Logout` in einem der Worker auf alle anderen Sessions auswirken?

#### Java (`pkcs11-pool-demo`)

| Datenstruktur | Sync-Primitive | Besonderheit |
|---|---|---|
| `BlockingQueue<Mac>` — Pool von vorgefertigten `Mac`-Instanzen, **nicht** `ISession` direkt. | `take()`/`put()`. | SunPKCS11 hat einen *internen* Session-Pool, der unter den `Mac`-Instanzen liegt — Java-Programmierer muss ihn nicht direkt verwalten. |

Leitfragen:

1. **Warum Pool von `Mac` und nicht von `Session`?** (Tipp: SunPKCS11 macht das transparente Session-Management; was knapp wird, sind die stateful `Mac`-Instanzen pro `getInstance`-Aufruf, die GC-Druck erzeugen.)
2. **Wo siehst du, dass SunPKCS11 wirklich Sessions wiederverwendet — gibt es ein Indiz im Code oder muss man den Provider-Source lesen?** (Antwort: nicht direkt im Anwendungs-Code; `pkcs11-spy`-Trace zeigt es.)
3. **Setze die Pool-Groesse auf 1 und starte 8 parallele Worker mit demselben `Mac`. Was passiert?** Vergleiche mit dem Go-Pfad — gleiche Fehlerklasse oder andere?

#### Kotlin (`pkcs11-pool-demo`)

| Variante 1 | Variante 2 |
|---|---|
| Identisch zu Java: `BlockingQueue<Mac>` plus `ExecutorService`. | Coroutines: `Channel<Mac>(capacity=8)` plus `withContext(Dispatchers.IO)` pro Worker. |

Leitfragen:

1. **Wann ist Coroutines + `Channel` der bessere Pool — und wann ist es nur eine schickere Java-Variante?** Vergleiche mit der `Dispatchers.IO`-Threadpool-Groesse (default 64) — was wuerde aus 8 Pool-Slots werden, wenn 200 Coroutines parallel `take()` versuchen?
2. **Eine HSM-Sign-Operation ist blockierend (PKCS#11 hat keine async-API). Was bedeutet das fuer eine Suspend-Funktion mit `withContext(Dispatchers.IO)`?**
3. **Wuerde `Dispatchers.Default` statt `IO` funktionieren — und wenn nein, woran wuerde es scheitern?** (Tipp: CPU-bound vs blockierend.)

### Wenn alle vier Pfade im Kopf zusammenkommen

Pool-Pattern ist sprachunabhaengig: N persistente Sessions, Sync-Primitive als Semaphore, Login einmal. Der Sprach-Unterschied ist die Wahl der Datenstruktur — und ob die Lib (Java/SunPKCS11) den Session-Layer transparent macht oder ob die Anwendung ihn selbst verwaltet (Go/C#). Auf realer HSM-Hardware ist die Performance-Kurve nicht von der Sprache, sondern vom HSM bestimmt.

## Realitaets-Check: SoftHSM serialisiert

Auf SoftHSM 2.6 ist der Speedup mit 8 Workern **maximal etwa 1.3x**, oft sogar leicht unter 1.0 (Task-Spawn-Overhead). Grund: SoftHSM holt sich intern eine globale Library-Mutex pro Crypto-Operation. Mehr Parallelitaet auf der Anwendungsseite hilft nichts, wenn die Library im Backend serialisiert.

**Reale HSMs** mit Hardware-Parallelitaet (mehrere Crypto-Engines pro Modul, parallele Channels) skalieren mit der Pool-Groesse bis zur Anzahl der internen Engines. Bei Thales Luna oder Utimaco SecurityServer sind 50-80% lineare Skalierung bis Pool-Size ≈ 16-32 normal.

Lesson: **Pooling ist Korrektheits-Pattern, nicht Performance-Garantie**. Es verhindert das (oft unbemerkte) Anti-Pattern, dass eine Anwendung mit `n` Threads aber 1 Session reihenweise `CKR_OPERATION_ACTIVE`-Fehler erntet — und nutzt automatisch die HSM-Parallelitaet, falls vorhanden.

## `fork()`-Falle

Wer Worker-Prozesse forkt (Gunicorn-Style, Apache prefork), darf die PKCS#11-Library **NICHT** im Parent initialisieren. PKCS#11-Sessions sind nicht fork-safe: das Child erbt File-Descriptors zum HSM, aber der Token-internal State (Login-Status, Object Handles) ist beim ersten Aufruf zerschossen — typisches Symptom `CKR_DEVICE_ERROR` oder unerklaerliche Hangs.

Pattern: jeder Worker-Prozess ruft selbst `C_Initialize`/`C_Login` nach dem `fork()`. Im Parent passiert nur Bind/Listen/Dispatch, kein PKCS#11-Call.

### Cookbook: fork-Falle ohne neue Demo reproduzieren

Der Effekt laesst sich ohne neue Lab-Binary mit dem bestehenden Bash-Pfad sichtbar machen. Idee: ein Sign-Prozess haelt den Token-State offen, ein paralleler Init-Versuch trifft auf das Storage-Lock von SoftHSM.

```bash
# Terminal A: lang laufender Sign-Prozess
make init-token gen-rsa
while :; do make -s sign >/dev/null; done &
SIGN_PID=$!

# Terminal B: paralleler Token-Reinit simuliert das fork+second-C_Initialize
SOFTHSM2_CONF=/etc/softhsm/softhsm2.conf \
  pkcs11-tool --module /usr/lib/softhsm/libsofthsm2.so --list-token-slots
# Erwartet: erscheint manchmal ohne Token, manchmal mit anderem Slot-Index.
# Auf realen HSMs ist das Verhalten dramatischer (CKR_DEVICE_ERROR).

kill $SIGN_PID
```

Lessen: SoftHSM zeigt das Phaenomen abgeschwaecht (Datei-basiertes Backend statt Vendor-Library mit Pro-Process-State), aber das **Lehr-Pattern** ist da — `C_Initialize` in zwei Prozessen mit demselben Token bringt einen Lab-Effekt; auf einer realen Vendor-Library bringt es `CKR_DEVICE_ERROR` oder Hangs. In Produktion gilt: `C_Initialize` immer nach `fork()`, nie davor.

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
