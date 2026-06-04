# Uebung 11 - Session-Pooling

## Ziel

Du laesst die vier Sprach-Demos laufen, beobachtest empirisch den (geringen) SoftHSM-Speedup, provozierst gezielt einen `CKR_OPERATION_ACTIVE`-Fehler durch missbraeuchliche Session-Wiederverwendung und ueberlegst, wann Pooling **trotzdem** Pflicht ist.

## Vorbereitung

```bash
make init-token gen-hmac
```

## Aufgabe 1 — Baseline-Benchmark

Eine Sprache waehlen und laufen lassen:

```bash
make go-pool-demo
make csharp-pool-demo
make java-pool-demo
make kotlin-pool-demo
```

Erwartete Ausgabe (Beispiel Go):

```text
Operationen:    10000 × HMAC-SHA256(64 Bytes)
Pool-Groesse:   8 Sessions
Sequenziell:    ... ms (... ops/s)
Parallel (×8): ... ms (... ops/s)
Speedup:        ~1.0-1.3x
```

Der Speedup auf SoftHSM ist niedrig — siehe Kapitel-Doku zur Erklaerung.

## Aufgabe 2 — Anti-Pattern provozieren

In der Go-Demo (`lab/go/pkcs11-pool-demo/main.go`):

1. Aendere `poolSize` von 8 auf 1.
2. Lass alle Worker (weiterhin 8) auf **dieselbe** Session los — z.B. indem du das `<-pool` und `pool <- s` durch `allSessions[0]` ersetzt.
3. Starte erneut.

Erwartet: `CKR_OPERATION_ACTIVE`-Fehlerflut oder Korruption. Das ist genau das Anti-Pattern, das der Pool verhindert.

## Aufgabe 3 — Pool-Groesse variieren

Setze `poolSize` auf 1, 4, 16, 32. Notiere die ops/s und beobachte:

- Bei `1` ist Sequenziell und Parallel identisch (logisch).
- Zwischen `4` und `16` aendert sich auf SoftHSM kaum etwas.
- Bei `32` kann Parallel sogar langsamer sein (Kontext-Switching dominiert).

Auf einem echten HSM mit z.B. 8 Crypto-Engines waere die Skalierung bis `POOL_SIZE = 8` etwa linear.

## Aufgabe 4 — Login-Lebensdauer

Erweitere eine der Sprach-Demos so, dass sie ZWEI Logins macht: einen vor Sequenziell, einen vor Parallel. Auf einem PKCS#11-konformen Token kommt beim zweiten `C_Login` ein `CKR_USER_ALREADY_LOGGED_IN` zurueck — sauberes Login-State-Management gehoert in den Pool-Init, nicht in den Hot-Path.

## Aufgabe 5 — Bonus: fork-Falle nachvollziehen

Die fork-Falle ist im Lab schwer reproduzierbar, weil SoftHSM ein Dateibackend nutzt — auf realer Vendor-Library schlaegt sie sofort als `CKR_DEVICE_ERROR` zu. Reproduktion-Pattern (Cookbook): siehe [`course/17-session-pooling.md` §"Cookbook: fork-Falle ohne neue Demo reproduzieren"](../course/17-session-pooling.md#cookbook-fork-falle-ohne-neue-demo-reproduzieren). Fuehre den dort beschriebenen Zwei-Terminal-Test aus und notiere das beobachtete Verhalten.

Erwartet: das Token-Listing wird zeitweise inkonsistent (Slot wandert, Token-Label kurzzeitig weg). Das ist die abgeschwaechte Lab-Variante des Phaenomens; produktiv ist die Konsequenz dramatischer.

## Reflexionsfragen

Vier Stufen — eine Recall-, zwei Analyse- und eine Evaluate-Frage:

1. **(Recall)** Welche Situation provoziert `CKR_OPERATION_ACTIVE` zuverlaessig?
2. **(Analyse)** Warum bringt Pooling auf SoftHSM kaum Speedup, aber auf realen HSMs oft 5x bis 10x? Verfolge das Bottleneck-Argument: was passiert intern in der SoftHSM-Library, das Anwendungs-Parallelitaet wirkungslos macht?
3. **(Analyse)** Was ist der Unterschied zwischen "Pool von Sessions" (Go/C#) und "Pool von Mac-Instanzen" (Java)? Wieso reicht in Java das zweite? Beziehe dich auf die SunPKCS11-interne Session-Verwaltung aus [Kap. 17 §"Thread-Safety pro Binding"](../course/17-session-pooling.md).
4. **(Evaluate)** Dein Service liefert 200 Request/s, das HSM 50 ops/s. Mehr Pooling — Loesung oder Symptom? Welche zwei Architektur-Alternativen (Caching idempotenter Operationen, HSM-Cluster, Re-Architektur mit gecachten JWTs statt jeden Request neu zu signieren) wuerdest du gegenueber "groesserer Pool" abwaegen — und welche Messung beweist, dass das HSM saturiert ist?

## Musterloesung

Siehe `solutions/11-session-pooling.md`.
