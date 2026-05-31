# 23 — HSM als Random-Quelle (C_GenerateRandom)

## Lernziele

Nach diesem Kapitel kannst du:

- `C_GenerateRandom` und `C_SeedRandom` einordnen und ihren Status (mandatory/optional) erklaeren.
- den Token-Flag `CKF_RNG` lesen und vor dem ersten RNG-Aufruf pruefen.
- TRNG, PRNG, RDRAND und HSM-RNG voneinander abgrenzen und NIST SP 800-90A/B/C zumindest aus der Vogelperspektive einordnen.
- den Performance-Trade-off zwischen Host-RNG und HSM-RNG bewerten — und entscheiden, wann der HSM-RNG es wert ist.
- den HSM-RNG aus Bash, Go, C# und Java/Kotlin (JCA `SecureRandom`) ansprechen.

## Lab-Bezug

```bash
make random-gen         # pkcs11-tool --generate-random in drei Groessen
make random-bench       # Durchsatz HSM vs /dev/urandom (+ Verteilungs-Check)
make go-random-demo     # C_GenerateRandom mit persistenter Session, Throughput-Vergleich
make csharp-random-demo # session.GenerateRandom + RandomNumberGenerator
make java-random-demo   # SecureRandom.getInstance("PKCS11", provider)
make kotlin-random-demo # Kotlin-Pendant
```

Begriffe (TRNG/PRNG/CSPRNG, Seed, Reseed, FIPS 140-3, SP 800-90A/B/C) und Abkuerzungen: [Glossar](../docs/glossar.md).

## Warum ein HSM als Random-Quelle?

Linux-Kernel-RNG (`/dev/urandom`, `getrandom(2)`) ist auf modernen Systemen kryptographisch unbedenklich: Mix aus Hardware-Events, RDRAND (wo verfuegbar), und einem ChaCha20-basierten CSPRNG. Fuer 99 % der Anwendungen reicht das. Drei Faelle, in denen man trotzdem zum HSM-RNG greift:

1. **Compliance.** FIPS-140-3 verlangt fuer kryptographische Module einen zertifizierten RNG-Pfad. Wer eine FIPS-zertifizierte Anwendung betreibt, darf den Host-RNG haeufig nicht als alleinige Quelle nutzen. Der HSM-RNG ist im Modul-Boundary, also zertifiziert; der Host-RNG ist es typisch nicht.
2. **Cold-Start-Entropie.** Frisch gestartete VMs ohne Hardware-Events haben oft duenne `/proc/sys/kernel/random/entropy_avail`-Pools (mittlerweile durch `getrandom`-Bootstrapping besser, aber historisch das Problem von "boot-time keys"). Ein HSM ist autark und nutzt einen physikalischen TRNG, der ab Sekunde eins Output liefert.
3. **Auditierbarer Trust-Boundary.** Wer einen Audit-Trail "Schluessel wurde im HSM erzeugt, hat den HSM nie verlassen" beweisen will, muss auch die Random-Bytes aus dem HSM holen — sonst fliesst ein externer Seed in den Schluessel ein und der Trust-Boundary ist gebrochen.

Wer den HSM ohnehin schon ueber PKCS#11 anspricht und keine 100 MB/s Random pro Sekunde braucht, hat hier wenig Aufwand und viel Compliance-Effekt.

## TRNG, PRNG, CSPRNG, RDRAND, HSM-RNG

| Quelle | Typ | Eigenschaften |
|---|---|---|
| `/dev/random` (Linux ≤ 5.6) | CSPRNG mit Entropy-Counter | Blockiert bei "leerem Pool", historisch UX-Falle |
| `/dev/urandom`, `getrandom(2)` | CSPRNG (ChaCha20-basiert) | Nie blockierend nach Init-Seed, kryptographisch sicher |
| `RDRAND`/`RDSEED` (Intel/AMD) | Hardware-CSPRNG / TRNG | Per CPU-Instruktion; FIPS-zertifiziert pro CPU-Modell, aber **vom Host-Prozess steuerbar** |
| HSM-TRNG | Hardware-TRNG (Ringoszillator, Zener, etc.) | Physikalische Entropie-Quelle, FIPS-140-3-zertifiziert als Teil des HSM-Moduls |
| HSM-PRNG (CSPRNG) | softwareseitig im HSM | NIST-SP-800-90A-konformer DRBG, vom TRNG geseedet |

**TRNG** (True RNG) ist eine physikalische Quelle — Quantenrauschen, Schaltungsjitter, Photonenzaehler. **CSPRNG** (Cryptographically Secure PRNG) ist ein deterministischer Algorithmus, der vom TRNG geseedet ist (typisch CTR_DRBG mit AES, HMAC_DRBG oder Hash_DRBG mit SHA-256/512 nach NIST SP 800-90A). Reale HSMs kombinieren beide: TRNG fuettert kontinuierlich einen CTR_DRBG, dessen Output an `C_GenerateRandom` geht.

Das hat zwei praktische Konsequenzen:

- Auch HSM-Output ist eigentlich **PRNG-Output** — nur die Seed-Quelle ist hardware-basiert und auditiert.
- Der Durchsatz ist **durch den Reseed-Pfad** limitiert. NIST SP 800-90A erlaubt fuer CTR_DRBG mit AES-256 bis zu 2^48 Generate-Calls zwischen zwei Reseeds, mit je bis zu 2^19 Bit pro Call — reale HSMs reseeden viel konservativer. Bei Hochlast kann der HSM trotz Hardware-TRNG bottleneck-en, wenn der Reseed-Pfad mit dem Reseed-Intervall nicht mithaelt.

## `C_GenerateRandom` und `C_SeedRandom`

Beide Funktionen sind in der PKCS#11-Spec definiert, aber:

| Funktion | Status laut Spec | Indikator |
|---|---|---|
| `C_GenerateRandom(session, *RandomData, ulRandomLen)` | optional | `CKF_RNG` in `CK_TOKEN_INFO.flags` |
| `C_SeedRandom(session, *Seed, ulSeedLen)` | optional, separate Garantie | `CKF_RNG` impliziert dies **nicht** — viele HSMs lehnen `C_SeedRandom` mit `CKR_RANDOM_SEED_NOT_SUPPORTED` ab |

Wer den HSM-RNG nutzt, sollte:

```text
flags = C_GetTokenInfo(slot).flags
if not (flags & CKF_RNG):
    → "Dieser Token hat keinen RNG. Fallback auf Host-RNG dokumentieren."
    → ggf. ein anderes Token finden
```

`C_SeedRandom` ist in der Praxis **selten brauchbar**: HSMs vertrauen ihrem internen TRNG-Pool und akzeptieren externes Seed-Material entweder gar nicht oder rechnen es nur als zusaetzlichen Input in den DRBG. Wer denkt, er muesse einen HSM "seeden", verwechselt typisch HSM mit klassischen Userland-PRNGs.

## SoftHSM ist hier nicht repraesentativ

SoftHSM 2.6 implementiert `C_GenerateRandom` ueber OpenSSL `RAND_bytes`. Das ist genau der gleiche CSPRNG, der `/dev/urandom` speist — nur mit PKCS#11-Schale drumherum. **Performance** und **Auditierbarkeit** sind im Lab daher nicht aussagekraeftig:

- Im Bash-Bench (`make random-bench`) schneidet der HSM-Pfad schlechter ab, weil pkcs11-tool pro Aufruf eine neue Session oeffnet → Per-Call-Overhead dominiert.
- In den Sprach-Demos schneidet der HSM-Pfad sogar **besser** ab als der Default-`SecureRandom`/`crypto.rand` — beides nutzt am Ende `/dev/urandom`, der HSM-Pfad hat aber weniger Wrapping-Schichten und bleibt in-Process. SoftHSM ist also schneller, **nicht weil es ein besserer RNG ist, sondern weil es kein anderer ist**.

Bei realen HSMs sehen die Zahlen anders aus:

| Geraet | Typischer `C_GenerateRandom`-Durchsatz | Vergleich |
|---|---|---|
| YubiKey 5 (USB-Token) | 1-5 KB/s | ~10000x langsamer als /dev/urandom |
| Thales Luna 7 (PCIe) | 1-5 MB/s | ~3-15x langsamer als /dev/urandom |
| Utimaco SecurityServer (LAN) | 200-800 KB/s | netzwerk-limitiert |
| AWS CloudHSM (Cavium/Marvell LiquidSecurity) | ~10 MB/s | LAN, gleicher Cluster |

Das stimmt fuer kontinuierliche Streams. Fuer einzelne kleine Random-Calls (32 Byte fuer einen IV) ist der RTT-Overhead immer der dominierende Faktor — sub-Millisekunde vs 1-10 ms je nach Anbindung.

## Implementierungs-Pattern

### Bash via pkcs11-tool

```bash
pkcs11-tool --module $PKCS11_MODULE \
  --login --pin $PIN --token-label $TOKEN \
  --generate-random 32 > /tmp/random.bin
```

Per Default ist der Output **Roh-Binary auf stdout**. Pro Aufruf wird ein eigener Modul-Init/Session-Login gemacht — fuer Streams ungeeignet, fuer eine einmalige Salt-/Nonce-Generierung perfekt.

### Go (miekg/pkcs11)

```go
data, err := p11.GenerateRandom(session, 32)
```

Die Session bleibt offen — fuer Hochlast den Session-Pool aus Kapitel 17 wiederverwenden.

### C# (Pkcs11Interop)

```csharp
byte[] data = session.GenerateRandom(32);
```

Symmetrisch zum Go-Beispiel; `Pkcs11Interop` macht intern einen `byte[]`-Alloc plus `C_GenerateRandom`-Call.

### Java/Kotlin (SunPKCS11/JCA)

```java
SecureRandom hsm = SecureRandom.getInstance("PKCS11", sunPkcs11Provider);
byte[] data = new byte[32];
hsm.nextBytes(data);
```

JCA-Eleganz: `SecureRandom` ist die gleiche Schnittstelle, mit der jeder Java-Code RNG-Bytes holt. Die HSM-Variante laesst sich tropfenweise einbauen — z.B. nur fuer die Erzeugung von TLS-Session-IDs, waehrend der Rest der Anwendung weiter den Default-`SecureRandom` nutzt.

JCA bietet **kein** `SeedRandom`-Aequivalent ueber den Provider — `SecureRandom.setSeed(byte[])` ist ein No-Op fuer Provider-RNGs, die das nicht implementieren (das ist explizit in der JCA-Doku so vorgesehen). Wer auf reale HSMs zielt, ist damit konform: auch dort wuerde `C_SeedRandom` typisch fehlschlagen.

## Was die Demo zeigt

Drei Schritte, in jeder Sprache identisch:

1. **Proof-of-Life:** 32 Byte aus dem HSM, Hex-Dump auf stdout. Beweist, dass der RNG-Pfad funktioniert.
2. **Durchsatz-Vergleich:** 1 MB ueber HSM vs 1 MB ueber Host-RNG. Auf SoftHSM gewinnt der HSM-Pfad (weil OpenSSL-RAND in-Process), auf realer Hardware verliert er typisch.
3. **Verteilungs-Check:** Shannon-Entropie ueber die ersten 64 KB. Werte nahe 8.0 bit/byte bedeuten: keine offensichtliche Bias. Das ist **kein Sicherheitsbeweis** — fuer den braucht es NIST SP 800-22 oder dieharder mit vielen MB Input. Aber es zeigt, ob der Output offensichtlich kaputt waere.

## Was die Demo nicht zeigt

- **NIST-SP-800-22-Tests**: brauchen 1-10 MB Input, sind zudem rechenintensiv. Fuer ernsthafte RNG-Validierung das `nist-sp800-22`-Tool oder `dieharder`/`PractRand` nutzen.
- **Continuous Random Test (CRT)**: FIPS-140-3 verlangt, dass der RNG bei jedem Aufruf einen Health-Check macht (Repetition Count Test, Adaptive Proportion Test — beide aus NIST SP 800-90B). Das macht der HSM intern; eine Anwendung kann es nicht von aussen sehen.
- **Reseeding-Verhalten**: PKCS#11 exponiert keine API fuer "wann wurde zuletzt reseedet". Compliance-Audits muessen sich auf das HSM-FIPS-Zertifikat verlassen.

## Eigenexperiment

- Loesch im Bash-Bench-Skript den `pkcs11-tool`-Loop und ersetze ihn durch einen einzigen Aufruf mit `--generate-random $TOTAL_BYTES`. Vergleich die Zahlen — der Session-Overhead war 80-90 % der Wallclock. (Achtung: manche HSMs lehnen `C_GenerateRandom` mit n > 1024 mit `CKR_DATA_LEN_RANGE` ab; bei SoftHSM kein Problem.)
- Setze in der Go-Demo `chunkSize` auf `256` statt `8192`. Beobachte, wie viele PKCS#11-Calls noetig werden und wie der Durchsatz einbricht — relevante Lektion fuer Anbindungen mit hoher RTT.
- Erzeuge in der Java-Demo einen RSA-2048-Key via `KeyPairGenerator.getInstance("RSA", sunPkcs11Provider)` und uebergib den HSM-`SecureRandom` als Quelle. Beobachte: das `C_GenerateKeyPair` im HSM ignoriert den uebergebenen `SecureRandom` komplett — JCA-Konvention vs PKCS#11-Realitaet.

Strukturierte Aufgaben in [`exercises/17-random.md`](../exercises/17-random.md).
