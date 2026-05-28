# 02 — Lab-Setup

## Inhalt des Labs

Das Lab enthält:

- Debian 12
- SoftHSM2
- OpenSC mit `pkcs11-tool`
- OpenSSL 3 mit `libengine-pkcs11-openssl`
- JDK 17
- Maven

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

In echten Systemen gehören PINs nicht in Skripte, Logs, Repositories oder Docker-Images.
