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

## Aufgabe 5 — Bonus: fork-Test

Modifiziere die Go-Demo so, dass sie nach `C_Initialize` einen Child-Prozess via `exec.Command("./pool-demo-child")` startet, der versucht, dieselbe Library erneut zu nutzen. (In einer einzelnen `go run`-Demo schwer zu zeigen — mach es als Cookbook-Notiz.) Erwartung: das Child sieht inkonsistenten State oder erntet `CKR_FUNCTION_FAILED`.

## Reflexionsfragen

- Warum bringt Pooling auf SoftHSM kaum Speedup, aber auf realen HSMs oft 5x bis 10x?
- Was ist der Unterschied zwischen "Pool von Sessions" (Go/C#) und "Pool von Mac-Instanzen" (Java)? Wieso reicht in Java das zweite?
- Welche Situation provoziert `CKR_OPERATION_ACTIVE` zuverlaessig?
- Wenn dein Service 200 Request/s hat und das HSM 50 ops/s liefert: hilft mehr Pooling, oder ist das Problem ein anderes?

## Musterloesung

Siehe `solutions/11-session-pooling.md`.
