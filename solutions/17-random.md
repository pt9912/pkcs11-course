# Loesung 17 - HSM-RNG (C_GenerateRandom)

## Random ueber pkcs11-tool

`make random-gen` liefert drei Hex-Dumps. Beispiel:

```text
CKF_RNG: gesetzt (Token bietet einen Random-Source an)
  16 Byte angefordert -> lab/work/random-16.bin (16 Byte geliefert)
    Hex (erste 32 Byte): 7052b86931ece33ef2c0fa4847ec9e4d
  32 Byte angefordert -> lab/work/random-32.bin (32 Byte geliefert)
    Hex (erste 32 Byte): aae768d4023fca72909699cb845c662e943ecac34538b2c1087101e380992ef4
  32 Byte angefordert -> lab/work/random-32.bin (32 Byte geliefert)
    Hex (erste 32 Byte): 98143d4ca9f38b3c2c524da17e4d74752e117a33e4d45277372a5a159d477015
```

Die zweite und dritte Zeile sind unterschiedlich, obwohl beide 32 Byte anfordern — der Reseed/PRNG-Zustand schiebt sich pro Aufruf weiter.

## Throughput und Verteilung

```text
=== HSM-RNG vs /dev/urandom Throughput ===
Volumen pro Quelle: 64 KB (16 Chunks a 4096 Byte)

  HSM (pkcs11-tool/sess)    0.134s      0.47 MB/s  lab/work/random-hsm-64k.bin
  /dev/urandom              0.004s     15.62 MB/s  lab/work/random-urandom-64k.bin
  /dev/zero (Anti-Test)     0.002s     31.25 MB/s  lab/work/random-zero-64k.bin

=== Verteilungs-Check ===
lab/work/random-hsm-64k.bin:
  Shannon-Entropie: 7.9975 bit/byte
  Chi^2 (df=255):   224.8
lab/work/random-urandom-64k.bin:
  Shannon-Entropie: 7.9969 bit/byte
  Chi^2 (df=255):   283.3
lab/work/random-zero-64k.bin:
  Shannon-Entropie: -0.0000 bit/byte
  Chi^2 (df=255):   16711680.0
```

HSM und urandom liegen in derselben Verteilungs-Klasse. /dev/zero zeigt, dass der Check ueberhaupt sensitiv ist.

## Sprach-Demos

Beispiel Go:

```text
=== 1) Proof-of-Life: 32 Byte aus dem HSM ===
  Hex: 4e5583654d3431134429703b6793337675ad63c0118382a0c006bb86cfd22d32

=== 2) Durchsatz HSM vs crypto/rand ===
  HSM (C_GenerateRandom, persistente Session)          0.002s    457.57 MB/s
  crypto/rand (Linux getrandom)                        0.004s    257.68 MB/s
  HSM ist Faktor 1.8x schneller als crypto/rand (unerwartet — SoftHSM-Spezialfall)

=== 3) Verteilungs-Check ueber 64 KB HSM-Bytes ===
  Shannon-Entropie: 7.9973 bit/byte (Idealwert: 8.0)
```

Java- und Kotlin-Demos zeigen 30-50x Vorsprung des HSM-Pfads — `NativePRNG` macht pro Aufruf einen `getrandom`-syscall, der HSM-Pfad bleibt in-Process. Das ist ein SoftHSM-Artefakt, **kein** Argument fuer "HSM ist schneller als Kernel-RNG".

## CKF_RNG-Inversion

Mit dem invertierten Check beendet sich das Go-Programm mit:

```text
CKF_RNG: gesetzt
Fehler: Token meldet kein CKF_RNG — C_GenerateRandom nicht verfuegbar
exit status 1
```

Beachte: die "CKF_RNG: gesetzt"-Zeile erscheint, weil die Println vor dem Check steht. Wuerde der Token wirklich kein CKF_RNG melden, koennte der Code C_GenerateRandom trotzdem aufrufen — viele HSMs antworten dann mit `CKR_RANDOM_NO_RNG`, manche aber auch mit nicht-zufaelligen Bytes (kaputter Vendor-Code in der Vergangenheit). Der Flag-Check ist deshalb **Vorbedingung**, nicht "nice to have".

## Kleine Chunks

Mit `chunkSize = 256` statt `8192`:

```text
  HSM (C_GenerateRandom, persistente Session)          0.041s     24.13 MB/s
  crypto/rand (Linux getrandom)                        0.025s     40.00 MB/s
```

Statt 128 PKCS#11-Calls (1 MB / 8 KB) sind es jetzt 4096. Der Faktor ~20-30 weniger Durchsatz entspricht dem Per-Call-Overhead. Bei realer HSM-Hardware mit 1-10 ms RTT pro Call ist das eine Stunde Differenz zwischen 32-Byte-Chunks und 8-KB-Chunks bei gleichem Datenvolumen.

## Antworten zu den Reflexionsfragen

**Wann lohnt sich der HSM-RNG?**
Drei Anwendungsfaelle:
1. **Compliance** (FIPS-140-3, eIDAS, BSI TR-03116-Vorgaben), wo der RNG zertifiziert sein muss und der Host-RNG es nicht ist.
2. **Auditierbarer Trust-Boundary** — wenn der Anspruch "Schluessel hat den HSM nie verlassen" auch fuer die Zufallszahlen gelten soll, die in die Schluesselgenerierung einfliessen.
3. **Cold-Start-Entropie** auf frischen VMs ohne Hardware-Events (historisch das groessere Problem als heute).

In allen anderen Faellen ist `/dev/urandom`/`getrandom(2)` ausreichend.

**SoftHSM-Performance irrefuehrend:**
SoftHSM ist eine reine Software-Implementierung, die intern `OpenSSL RAND_bytes` aufruft. Das ist genau das, was `/dev/urandom` auch macht — nur ohne den syscall-Overhead, weil SoftHSM in-Process laeuft. Reale HSMs muessen ueber PCIe, USB oder LAN sprechen; pro Call sind 1-10 ms RTT typisch. Wer SoftHSM-Zahlen auf Thales Luna oder AWS CloudHSM extrapoliert, unterschaetzt die Latenz um 3-5 Groessenordnungen.

**Warum HSMs C_SeedRandom ablehnen:**
Ein HSM ist der Trust-Anker — sein RNG soll nicht von aussen beeinflussbar sein. Wenn eine Anwendung dem HSM "ihren" Seed unterschiebt, kann sie den RNG-Output potentiell vorhersagen und damit Backdoor-Schluessel erzeugen lassen. Aus Sicherheitssicht ist `CKR_RANDOM_SEED_NOT_SUPPORTED` ein Feature: der HSM laesst sich auf seinen TRNG-Pool ein, nicht auf das, was die Anwendung anbietet.

**Shannon-Entropie 7.99 trotz kaputtem RNG:**
Ein Zaehler-RNG `output = i++; i++; i++; ...` produziert ueber 64 KB perfekt 256 mal jeden Byte-Wert — Shannon-Entropie = 8.0, Chi^2 = 0. Aber er ist trivial vorhersagbar. Shannon-Entropie misst **Verteilung pro Symbol**, nicht **Sequenz-Eigenschaften**. Deshalb braucht eine ernsthafte RNG-Pruefung zusaetzlich Sequenztests (NIST SP 800-22: Frequency-Within-Block, Runs, Linear Complexity, etc.) und deutlich mehr Input als diese Demo.

Sequenztests liefern aber keinen kryptographischen Sicherheitsbeweis. Sie sind Fehlerfinder. Ein RNG, der NIST STS, dieharder, PractRand oder TestU01 besteht, kann trotzdem deterministisch, schlecht geseedet oder absichtlich backdoored sein. Fuer kryptographische Sicherheit braucht man zusaetzlich ein sauberes Design (z.B. SP-800-90A-DRBG), eine belastbare Entropiequelle, Health-Tests, Review und Zertifizierung. Die statistischen Tests sind trotzdem sinnvoll, weil sie viele kaputte Implementierungen sofort entlarven.

Praktische Tools:

| Tool | Einsatz | Typischer Aufruf / Hinweis |
|---|---|---|
| **NIST STS / SP 800-22** | Offizielle Suite fuer Bitstrom-Tests wie Frequency, Block Frequency, Runs, Linear Complexity, Serial, Approximate Entropy | C-Tool `assess <sequenceLength>`, klassisch z.B. `assess 1000000`; Input als ASCII-Bits oder binaer |
| **dieharder** | Bequemer CLI-Sammeltest, enthaelt Diehard-, NIST- und weitere Tests | `dieharder -a -f random.bin`; gut fuer schnelle Regressionen |
| **PractRand** | Moderne Streaming-Tests fuer PRNGs, skaliert von MB bis GB/TB | `./rng | RNG_test stdin64 -tlmax 1GB`; findet viele lineare/strukturierte Fehler sehr schnell |
| **TestU01** | Forschungs-/Library-Standard fuer PRNGs, mit SmallCrush, Crush, BigCrush | C-Library; sinnvoll, wenn der Generator direkt angebunden werden kann |
| **NIST SP 800-90B EntropyAssessment** | Entropiequellen/TRNGs bewerten, min-entropy statt nur "sieht zufaellig aus" | Relevanter fuer Hardware-RNGs als SP 800-22 allein |
| **ent** | Minimaler Smoke-Test: Entropie, Chi^2, Mittelwert, Korrelation | Gut fuer Demos; kein Ersatz fuer die oben genannten Suites |

Input-Groessen: 64 KB wie im Lab reichen nur, um offensichtliche Totalausfaelle wie `/dev/zero` zu zeigen. Fuer NIST STS ist 1,000,000 Bit pro Sequenz ein ueblicher Startpunkt, aber ernsthafte Aussagen brauchen mehrere Sequenzen und mehrere MB. Fuer PractRand/TestU01 sind hundert MB bis GB normal, wenn ein PRNG wirklich belastet werden soll.
