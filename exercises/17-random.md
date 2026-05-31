# Uebung 17 - HSM-RNG (C_GenerateRandom)

## Ziel

Du holst Random-Bytes aus dem HSM-RNG, vergleichst ihren Durchsatz und ihre Verteilung gegen `/dev/urandom`, und siehst, warum die SoftHSM-Zahlen nicht repraesentativ fuer reale Hardware sind.

## Vorbereitung

```bash
make init-token
```

## Aufgabe 1 — Random ueber pkcs11-tool

```bash
make random-gen
```

Erwartet:

- `CKF_RNG: gesetzt` (SoftHSM hat es immer)
- Drei Hex-Dumps fuer 16, 32 und 32 Byte, alle drei unterschiedlich
- Drei Dateien in `lab/work/random-*.bin`

Stell sicher, dass zwei Laeufe hintereinander **verschiedene** Bytes liefern — wenn nicht, ist der RNG-Pfad kaputt oder der Token deterministisch geseedet.

## Aufgabe 2 — Throughput und Verteilung

```bash
make random-bench
```

Erwartet:

- HSM-Pfad ist deutlich langsamer als `/dev/urandom` (typisch ~0.5 MB/s vs ~15 MB/s), weil pkcs11-tool pro Chunk eine neue Session oeffnet
- `/dev/zero` zeigt Entropie 0.0 und einen Chi^2-Wert weit jenseits 255 — der Verteilungs-Check funktioniert
- HSM und `/dev/urandom` zeigen Shannon-Entropie zwischen 7.99 und 8.00 bit/byte und Chi^2 nahe 255

Notiere die HSM-MB/s — du wirst sie in Aufgabe 3 mit den Sprach-Demos vergleichen.

## Aufgabe 3 — Sprach-Demo mit persistenter Session

```bash
make go-random-demo
make csharp-random-demo
make java-random-demo
make kotlin-random-demo
```

Erwartete Muster pro Demo:

- 32 Byte Proof-of-Life als Hex-Dump
- Durchsatz HSM vs OS-RNG (in MB/s)
- Shannon-Entropie ueber 64 KB HSM-Bytes — tatsaechlicher Wert ~7.99, das im Demo-Code verbaute Sicherheitsnetz schlaegt erst bei < 7.5 zu (offensichtlich kaputter RNG)

Auf SoftHSM ist der HSM-Pfad **schneller** als der OS-RNG, weil SoftHSM intern dasselbe OpenSSL-`RAND_bytes` nutzt, aber in-Process bleibt und kein syscall-Overhead anfaellt. Auf realer Hardware ist die Reihenfolge umgekehrt. Halt das im Kopf, wenn du Zahlen aus diesem Lab interpretierst.

## Aufgabe 4 — CKF_RNG fehlt provozieren

Eine echte Anwendung muss `CKF_RNG` vor dem ersten `C_GenerateRandom`-Call pruefen. Simuliere den Fehlerpfad:

In `lab/go/pkcs11-random-demo/main.go` setze den Flag-Check um auf eine **Inversion**:

```go
if info.Flags&pkcs11.CKF_RNG != 0 { // <- == auf != geaendert
    return fmt.Errorf("Token meldet kein CKF_RNG ...")
}
```

Run `make go-random-demo`. Erwartet: das Programm beendet sich mit der Flag-Fehlermeldung, **ohne** `C_GenerateRandom` aufzurufen — der Schutz greift, bevor man das Token in einen undefinierten Zustand schickt.

Aenderung danach wieder zuruecksetzen.

## Aufgabe 5 — Bonus: kleine Chunks beobachten

In `lab/go/pkcs11-random-demo/main.go`:

```go
const chunkSize = 256 // war 8 * 1024
```

`make go-random-demo` zeigt jetzt deutlich niedrigeren HSM-Durchsatz (~10-30x weniger), weil 32x mehr PKCS#11-Calls noetig sind. Lektion: Pro PKCS#11-Call entsteht Overhead. Bei realer HSM-Hardware mit LAN-Anbindung ist das der dominante Effekt — Anwendungen sollten Random in Bloecken sammeln, nicht 32-Byte-weise.

Aenderung danach wieder zuruecksetzen.

## Reflexionsfragen

- Wann lohnt es sich, den HSM-RNG statt `/dev/urandom` zu nutzen, obwohl letzterer technisch ebenfalls kryptographisch sicher ist?
- Warum ist die SoftHSM-Performance-Zahl irrefuehrend, wenn man sie auf produktive HSM-Hardware extrapoliert?
- `C_SeedRandom` ist in der Spec optional. Warum lehnen die meisten HSMs es ab — und wieso ist das aus Sicherheitssicht ein **Feature**, kein Bug?
- Shannon-Entropie von 7.99 bit/byte beweist nicht, dass der RNG sicher ist. Was waere ein simpler RNG, der diesen Wert ueberbietet aber trotzdem komplett unsicher ist?

## Musterloesung

Siehe `solutions/17-random.md`.
