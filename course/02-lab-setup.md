# 02 — Lab-Setup

## Inhalt des Labs

Das Lab enthält:

- Debian 13
- SoftHSM2
- OpenSC mit `pkcs11-tool`
- OpenSSL 3 mit `libengine-pkcs11-openssl`
- JDK 21
- Gradle

## Start

```bash
make build
make shell
```

Im Container kannst du alle Skripte direkt ausführen:

```bash
lab/scripts/01-init-token.sh
lab/scripts/02-list-slots.sh
```

## Wichtige Pfade

| Pfad | Bedeutung |
|---|---|
| `/usr/lib/softhsm/libsofthsm2.so` | PKCS#11-Modul für SoftHSM |
| `/etc/softhsm/softhsm2.conf` | SoftHSM-Konfiguration |
| `/var/lib/softhsm/tokens` | Token-Speicher |
| `lab/work` | Arbeitsdateien für Signaturen/Public Keys |

## PINs im Kurs

Nur für das lokale Lab:

| PIN | Wert |
|---|---|
| SO-PIN | `1234` |
| User-PIN | `987654` |

In echten Systemen gehören PINs nicht in Skripte, Logs, Repositories oder Docker-Images. Zusätzlicher Stolperer in der Praxis: `--pin <wert>` auf der Kommandozeile ist auf Multi-User-Systemen über `ps -ef` für andere Prozesse sichtbar. Im Lab-Container egal, in Produktion ein Grund, stattdessen `--pin-source` oder `--pin-env` zu nutzen (sofern das jeweilige Tool das unterstützt) oder die PIN interaktiv abzufragen.

## Reproduzierbarkeit

Der Dockerfile pinnt keine apt-Paketversionen — für einen Kurs ist das pragmatisch, kann aber nach Debian-Point-Releases dazu führen, dass sich Pfade oder das Verhalten einzelner Tools verschieben. Wenn du den Lab-Stand "einfrieren" willst, fixiere die Paketversionen explizit oder publiziere ein eigenes Base-Image mit Tag.
