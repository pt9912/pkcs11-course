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

## Reproduzierbarkeit

Der Dockerfile pinnt keine apt-Paketversionen. Fuer einen Kurs ist das pragmatisch, kann aber nach Debian-Point-Releases dazu fuehren, dass sich Pfade oder Tool-Verhalten verschieben. Wenn du einen Lab-Stand einfrieren willst, fixiere Paketversionen explizit oder publiziere ein eigenes Base-Image mit Tag.

## Naechste Uebung

Weiter mit `exercises/01-token.md`.
