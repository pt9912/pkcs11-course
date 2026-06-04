# 02 - Lab-Setup

## Lernziele

Nach diesem Kapitel kannst du:

- das Lab ausserhalb und innerhalb des Devcontainers starten.
- erklaeren, warum Make-Targets im Devcontainer direkt laufen.
- die wichtigsten Pfade, PINs und Umgebungsvariablen benennen.
- SoftHSM-Slots und Token reproduzierbar untersuchen.

## Inhalt des Labs

Das Lab enthaelt:

- Debian 13
- SoftHSM2
- OpenSC mit `pkcs11-tool`
- OpenSSL 3 mit `libengine-pkcs11-openssl`
- JDK 21
- Gradle
- Go
- .NET SDK 10
- Demos fuer Java, Kotlin, Go und C#

## Start ausserhalb des Devcontainers

```bash
make build
make shell
```

Oder direkt einzelne Targets:

```bash
make init-token
make list-slots
make gen-rsa
make sign
make verify
```

Ausserhalb des Devcontainers fuehrt das `Makefile` die Befehle ueber `docker compose -f lab/docker-compose.yml run ...` aus.

## Start im Devcontainer

Der Devcontainer verwendet den Compose-Service `pkcs11-dev`. In diesem Container sind alle Sprachen und Tools installiert. Das `Makefile` erkennt den Modus ueber:

```bash
PKCS11_IN_DEVCONTAINER=1
```

Dann laufen die Make-Targets direkt:

```bash
make list-slots
make init-token
make gen-rsa
make java-demo
make go-demo
make kotlin-demo
make csharp-demo
```

Docker im Devcontainer ist dafuer nicht notwendig. Das vermeidet Probleme mit Host-Docker-Socket, Pfad-Mounts und Docker-in-Docker.

## Wichtige Pfade

| Pfad | Bedeutung |
|---|---|
| `/usr/lib/softhsm/libsofthsm2.so` | PKCS#11-Modul fuer SoftHSM |
| `/etc/softhsm/softhsm2.conf` | SoftHSM-Konfiguration |
| `/workspace/lab/work/tokens` | Token-Speicher |
| `lab/work` | Arbeitsdateien fuer Signaturen und Public Keys |
| `.nuget/packages` | NuGet-Package-Cache im Workspace |
| `.gradle` | Gradle-Cache im Workspace |
| `.dotnet` | .NET CLI Home im Workspace |

## Umgebungsvariablen

| Variable | Default | Zweck |
|---|---|---|
| `PKCS11_MODULE` | `/usr/lib/softhsm/libsofthsm2.so` | PKCS#11-Modul |
| `PKCS11_TOKEN_LABEL` | `dev-token` | Token-Auswahl |
| `PKCS11_USER_PIN` | `987654` | User-PIN fuer Login |
| `PKCS11_SO_PIN` | `1234` | Security-Officer-PIN fuer Initialisierung |
| `PKCS11_IN_DEVCONTAINER` | leer oder `1` | Makefile-Modus |

## PINs im Kurs

Nur fuer das lokale Lab:

| PIN | Wert |
|---|---|
| SO-PIN | `1234` |
| User-PIN | `987654` |

In echten Systemen gehoeren PINs nicht in Skripte, Logs, Repositories oder Docker-Images. Zusaetzlicher Stolperer in der Praxis: `--pin <wert>` auf der Kommandozeile ist auf Multi-User-Systemen ueber `ps -ef` sichtbar. Im Lab-Container ist das akzeptabel, in Produktion nutzt du stattdessen `--pin-source`, `--pin-env`, Secret Stores oder interaktive Eingabe, je nach Tool.

## Fehlerfaelle direkt ausfuehren — Devcontainer vs. Docker Compose

Viele Uebungen verlangen, eine Demo bewusst mit einer falschen ENV-Variable zu starten (z.B. `PKCS11_USER_PIN=000000`, `PKCS11_LIBRARY=/nicht/da`). Wichtig: die `make`-Dependency-Kette wuerde mit einer falschen PIN schon in `init-token` abbrechen — der Fehler erscheint dann **nicht** in der Sprache, in der er didaktisch beobachtet werden soll. Loesung: Vorbereitungsschritte sauber durchlaufen lassen, dann die Sprach-Demo *direkt* mit der Fehler-ENV starten. Zwei Patterns je nach Modus:

**Pattern A — Devcontainer (`PKCS11_IN_DEVCONTAINER=1`):**

```bash
# 1. Vorbereitung mit echten Werten
make init-token gen-rsa [import-cert]

# 2. Demo direkt mit Fehler-ENV
PKCS11_USER_PIN=000000 lab/scripts/13-go-demo.sh
PKCS11_LIBRARY=/nicht/da (cd lab/java/pkcs11-demo && ./gradlew --quiet --no-daemon run)
```

**Pattern B — Docker Compose (Host ohne Devcontainer):**

```bash
# 1. Vorbereitung mit echten Werten
make init-token gen-rsa [import-cert]

# 2. Demo direkt im richtigen Compose-Service mit -e
docker compose -f lab/docker-compose.yml run --rm \
  -e PKCS11_USER_PIN=000000 \
  pkcs11-go bash -lc 'lab/scripts/13-go-demo.sh'

docker compose -f lab/docker-compose.yml run --rm \
  -e PKCS11_LIBRARY=/nicht/da \
  pkcs11-lab bash -lc 'cd lab/java/pkcs11-demo && ./gradlew --quiet --no-daemon run'
```

Die Uebungen 03–06 und 15 verweisen auf diese Patterns statt sie jedes Mal voll auszuschreiben.

## Reproduzierbarkeit

Der Dockerfile pinnt keine apt-Paketversionen. Fuer einen Kurs ist das pragmatisch, kann aber nach Debian-Point-Releases dazu fuehren, dass sich Pfade oder Tool-Verhalten verschieben. Wenn du einen Lab-Stand einfrieren willst, fixiere Paketversionen explizit oder publiziere ein eigenes Base-Image mit Tag.

## Naechste Uebung

Weiter mit `exercises/01-token.md`.

## Selbsttest

<details>
<summary>1. Welche ENV-Variable schaltet das Makefile in den Devcontainer-Modus, und was ist die operative Folge?</summary>

`PKCS11_IN_DEVCONTAINER=1`. Folge: das Makefile fuehrt Lab-Targets direkt aus, ohne `docker compose run` als Vorlauf. Im Devcontainer gesetzt, ausserhalb leer.
</details>

<details>
<summary>2. Wo liegt SoftHSMs Token-Storage im Lab, und warum ist der Pfad in einem Container-Volume relevant?</summary>

`/workspace/lab/work/tokens`. Im Volume liegend, weil sonst beim Container-Restart der Token (und damit alle Keys/Certs) verloren waere. Der Pfad wird durch `SOFTHSM2_CONF` festgelegt; `02-list-slots.sh` zeigt das implizit, wenn `make init-token` einmal lief.
</details>

<details>
<summary>3. Warum gehoeren die Lab-PINs (User <code>987654</code>, SO <code>1234</code>) nicht in eine reale Anwendung — was waere der Mindest-Fix?</summary>

Beide PINs sind hardcoded im Repository und auf der `make`-Kommandozeile ueber `ps -ef` sichtbar (`--pin <wert>`). Mindest-Fix: PIN ueber `--pin-source` aus einer 0600-geschuetzten Datei, ueber `--pin-env` aus einer ENV-Variable die zur Laufzeit aus Vault/SSM kommt, oder interaktiver Prompt. Hardcode in der Pipeline ist ein Compliance-Findung.
</details>
