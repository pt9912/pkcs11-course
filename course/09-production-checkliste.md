# 09 — Produktionscheckliste

## Lernziele

Nach diesem Kapitel kannst du:

- SoftHSM-Lab-Annahmen von echtem HSM-Betrieb trennen.
- Sicherheits-, Betriebs- und Compliance-Fragen vor Projektstart stellen.
- Mechanism-, Session-, Backup- und Audit-Risiken benennen.
- Cloud-HSMs als eigene Integrationsklasse einordnen.

## Lab-Bezug

Nutze das Lab, um Mechanisms, Objekte und Fehlerszenarien vorzubereiten. Uebertrage keine SoftHSM-Annahme ungeprueft auf Produktion.

## Lokales SoftHSM vs. echtes HSM

| Thema | SoftHSM | Echtes HSM |
|---|---|---|
| Sicherheit | Test/Lernzweck | Hardware-/Cloud-Sicherheitsgrenze |
| Performance | lokal, einfach | Latenz, Limits, Sessions relevant |
| Mechanisms | abhängig vom Build | hersteller- und modellabhängig |
| Backup | Dateibasiert | Vendor-Prozess |
| HA | selbst gebaut | Cluster/Partitionen/Vendor |
| Audit | schwach | oft stark, aber aufwendig |
| FIPS-Modus | nein | meist optional, schaltet Mechanisms ab |

## Produktionsfragen

- Wie werden Keys erzeugt?
- Wer darf Keys erzeugen?
- Wie werden PINs/Secrets verwaltet (Vault, KMS, sealed secrets)?
- Wie läuft Key-Rotation?
- Wie läuft Backup/Restore?
- Was passiert bei HSM-Ausfall?
- Welche Mechanisms sind erlaubt?
- Gibt es Audit-Logs und werden sie ausgewertet?
- Welche Latenz ist akzeptabel?
- Wie viele parallele Sessions sind erlaubt?
- Wie werden Zertifikate erneuert?
- Wie wird das HSM überwacht (Heartbeats, Fehlerquoten)?

## Sicherheitsregeln

- Keine PINs in Git.
- Keine Private Keys exportieren (`CKA_SENSITIVE=TRUE`, `CKA_EXTRACTABLE=FALSE`).
- Mechanisms begrenzen (nur erlaubte `CKM_*`).
- Rollen trennen: Admin, Operator, Anwendung, Auditor.
- Test- und Produktions-HSM strikt trennen.
- Logs auf sensitive Daten prüfen.
- Monitoring für Fehlerquoten und Latenz einbauen.
- Notfall-Wiederherstellungs-Verfahren regelmäßig üben.

## FIPS und Compliance

- FIPS 140-2/140-3 zertifizierte HSMs schalten im FIPS-Mode bestimmte Mechanisms ab (z. B. nicht zugelassene Padding-Modi). Vorher mit `--list-mechanisms` prüfen.
- Common Criteria / eIDAS relevant, wenn qualifizierte Signaturen erzeugt werden.
- BSI TR-03145 für Vertrauensdiensteanbieter.

## Cloud- und Managed-HSMs

| Anbieter | Schnittstelle | Besonderheit |
|---|---|---|
| AWS CloudHSM | PKCS#11, JCE, KMIP | Cluster, Mandant pro VPC |
| Azure Managed HSM | PKCS#11 über `azure-keyvault-pkcs11`, REST | Rollenmodell über Azure RBAC |
| Google Cloud HSM | PKCS#11-Provider (`libkmsp11`) als Wrapper über die KMS-API | Auth über Service-Account, Keys leben in Cloud KMS |
| Thales DPoD | PKCS#11 | Subscription, Cloud-natives Partitionsmodell |

Bei Cloud-HSMs ist die PKCS#11-Bibliothek meist proprietär und braucht zusätzliche Auth-Schritte (Cluster-Cert, IAM-Token). Tests auf SoftHSM übertragen sich nicht 1:1.

## Vendor-Lock-in

PKCS#11 standardisiert die API, aber nicht alle Betriebsdetails. Mechanisms, Attribute, Session-Limits, Login-Verhalten und Zertifikatsimport unterscheiden sich. Plane Vendor-spezifische Tests ein.
