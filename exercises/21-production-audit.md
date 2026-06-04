# Uebung 21 - Production-Readiness-Audit

## Ziel

Du auditierst einen bestehenden Signatur-Service (deinen aus Kap. 10 oder Kap. 26 — alternativ ein Open-Source-Beispiel oder ein Service aus deinem Arbeitskontext) gegen die zwoelf Produktionsfragen aus [Kapitel 09](../course/09-production-checkliste.md). Das ist die einzige Uebung, die das Kursziel "abschaetzen, was sich bei echten HSMs aendert" (`course/00-kursuebersicht.md`, Z. 20) als Artefakt einfordert — Bloom-Stufe 5 (evaluate).

## Vorbereitung

Einer der drei Wege:

- **Track-1-Service** aus Kap. 10 (kleiner Signatur-Service, RSA-PKCS#1).
- **Track-2-Service** aus Kap. 26 (CMS-Service mit Pool, Audit-Log, RFC-3161).
- **Drittsystem**: ein Service aus deinem Arbeitsumfeld oder ein OSS-Projekt, das PKCS#11 nutzt (Apache, nginx mit pkcs11-engine, ein eigener Microservice).

Wenn du noch keinen Service gebaut hast, nimm die Micronaut-Skizze aus [`course/07-service-integration.md`](../course/07-service-integration.md) §"Konkretes Skelett" als Audit-Objekt — auch fuer eine Skizze laesst sich das Audit machen, und es macht die Auditfragen konkreter, bevor du selbst baust.

## Aufgabe — der Audit-Report

Pro Audit-Frage notierst du **drei** Felder:

1. **Befund:** "wie ist es jetzt?", ein Satz.
2. **Soll bei echtem HSM:** "was waere die Production-Antwort?", ein Satz.
3. **Aufwand zum Schliessen:** **klein** (Config), **mittel** (Code-Aenderung), **gross** (Architektur-/Org-Eingriff).

Der Report ist eine `audit.md`-Datei (oder ein Issue/Ticket im Tracker). Format unten.

### Die zwoelf Audit-Fragen

(aus `course/09-production-checkliste.md` §Produktionsfragen)

1. **Wie werden Keys erzeugt?** — Lab nutzt `pkcs11-tool --keygen` / `pkcs11-keygen`. Produktiv ueblicherweise via HSM-Konsole oder Ceremony mit M-of-N.
2. **Wer darf Keys erzeugen?** — Lab: jeder mit User-PIN. Produktiv: getrennte Rollen Admin/Operator/Anwendung.
3. **Wie werden PINs/Secrets verwaltet?** — Lab: ENV oder hartkodiert. Produktiv: Vault, KMS, sealed secrets.
4. **Wie laeuft Key-Rotation?**
5. **Wie laeuft Backup/Restore?**
6. **Was passiert bei HSM-Ausfall?**
7. **Welche Mechanisms sind erlaubt?** — gibt es eine Allowlist?
8. **Gibt es Audit-Logs und werden sie ausgewertet?**
9. **Welche Latenz ist akzeptabel?**
10. **Wie viele parallele Sessions sind erlaubt?**
11. **Wie werden Zertifikate erneuert?**
12. **Wie wird das HSM ueberwacht (Heartbeats, Fehlerquoten)?**

### Format-Vorgabe pro Eintrag

```markdown
### 7. Welche Mechanisms sind erlaubt?

- **Befund:** Service ruft `Signature.getInstance(cfg.getMechanism())` mit beliebigem String. Keine Allowlist.
- **Soll bei echtem HSM:** Allowlist im Config (`mechanisms: [SHA256withRSA, RSASSA-PSS]`). Eingehende Requests mit `mechanism` ausserhalb der Allowlist → `400 Bad Request`.
- **Aufwand:** klein (Validation im Controller).
```

## Aufgabe 2 — Risiko-Ranking

Nach dem Bauen des Reports: ordne die zwoelf Punkte in drei Buckets:

- **Showstopper** (ohne Loesung kein Production-Go): in der Regel 1-3, 8, evtl. 12.
- **Operational Debt** (man kann live gehen, aber bezahlt es bald): typisch 4, 5, 6, 11.
- **Optimization** (tunen, wenn man weiss, wie sich das System verhaelt): 9, 10, evtl. 7.

Schreibe **eine Zeile** Begruendung pro Eintrag im Showstopper-Bucket — das ist die Liste, die du einem Tech-Lead vorlegen muesstest.

## Aufgabe 3 — Schreibtisch-Migration

Aus den Showstoppern: waehle **einen** Punkt und beschreibe in einem **kurzen Absatz** (5-10 Saetze), wie du ihn konkret schliessen wuerdest. Nenne:

- die Komponente, die du anpasst (Config, Code-Klasse, Skript, Infrastruktur-Modul).
- die neue Schnittstelle (z.B. "Pin-Resolver liest aus Vault statt aus ENV").
- die Vor-/Nachteile dieses spezifischen Wegs gegenueber einer naheliegenden Alternative.

Das ist die Bloom-6-Komponente: nicht nur erkennen, sondern ein Stueck Architektur entwerfen.

## Erwartete Ausgabe

- `audit.md` mit zwoelf Eintraegen im obigen Format.
- Risiko-Ranking als drei Listen.
- Ein kurzer Migrationsabsatz fuer einen Showstopper.
- Optional: Kopie an dein Team / in dein Onboarding-Wiki.

## Reflexionsfragen

Drei Stufen — eine Analyse-, eine Evaluate-, eine Create-Frage:

1. **(Analyse)** Welche der zwoelf Fragen hat dir am laengsten gestockt? Das ist typischerweise die Stelle, an der dein Service unter Produktionslast als erstes brechen wuerde — welche zwei Anschluss-Themen aus den Kursmodulen (Kap. 13–25) wuerden dieselbe Lehre noch tiefer behandeln?
2. **(Evaluate)** Wieviele Befunde hast du als "klein" eingestuft, die in Wirklichkeit "mittel" sind, weil sie Tests, Doku und Schulung mitnehmen? Eine Faustregel aus der Praxis: jede HSM-Config-Aenderung kostet im Lab eine Stunde, in Production zwei Wochen. Welche **eine** Faustregel wuerdest du fuer dein Team als Schaetzheuristik in den Onboarding-Wiki schreiben?
3. **(Create)** Wenn du beide Tracks gebaut hast: welcher Service hat besser abgeschnitten — und warum? Track 2 hat mehr Kontaktflaeche, also mehr Auditpunkte, aber auch mehr explizit gebaute Antworten. Skizziere eine "Track 3"-Vision (HSM-Pattern, das in keinem der beiden Tracks vorkommt) und ordne sie in dein eigenes Stufen-1/2/3-Schema ein.

## Musterloesung

Siehe `solutions/21-production-audit.md` — der Sample-Report ist gegen die Micronaut-Skizze aus Kap. 07 gefuehrt, nicht gegen einen vollstaendig gebauten Service. Vergleichen, dann eigenen Service gegenchecken.
