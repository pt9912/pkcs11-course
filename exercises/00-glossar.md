# Uebung 00 - Vokabular-Selbsttest

## Ziel

Bevor du in das erste Kapitel einsteigst (oder zwischendurch, wenn dir die Praefix-Familien durcheinander geraten): pruefe, ob du die zentralen PKCS#11-Begriffe ohne Glossar-Lookup zuordnen kannst. Schliesst das deklarierte Kursziel "PKCS#11-Begriffe sauber erklaeren" (`course/00-kursuebersicht.md`, Z. 11) als ueberpruefbares Outcome.

## Vorbereitung

Keine. Das ist ein reiner Vokabel- und Konzept-Check ohne Lab-Aktion. [docs/glossar.md](../docs/glossar.md) ist die Referenz, gegen die du am Ende vergleichst — beim ersten Durchgang aber **zu**.

## Aufgabe 1 — Praefix-Familien zuordnen

Ordne jedes Praefix in die richtige Spalte. Schreib die Antwort auf, bevor du in das Glossar schaust.

| Praefix | Steht fuer | Beispiel |
|---|---|---|
| `CKR_` | ? | ? |
| `CKM_` | ? | ? |
| `CKA_` | ? | ? |
| `CKO_` | ? | ? |
| `CKK_` | ? | ? |
| `CKF_` | ? | ? |
| `CKU_` | ? | ? |
| `CKZ_` | ? | ? |

Hinweis: alle acht sind im Kurs aktiv. `CKZ_` ist das seltenste und kommt in Kap. 13 (OAEP-Source-Parameter) vor.

## Aufgabe 2 — Sechs zentrale Begriffe in einem Satz erklaeren

In jeweils **einem Satz**, ohne Beispiele:

1. Module
2. Slot
3. Token
4. Session
5. Object
6. Mechanism

Anschliessend: welche dieser sechs Begriffe bezeichnen **persistente** Entitaeten und welche existieren nur zur Laufzeit?

## Aufgabe 3 — Drei Begriffspaare unterscheiden

Was ist der Unterschied? Je 1-2 Saetze:

1. `CKA_SENSITIVE` vs `CKA_EXTRACTABLE`
2. `CKM_RSA_PKCS` vs `CKM_SHA256_RSA_PKCS`
3. `CKU_USER` vs `CKU_SO`

## Aufgabe 4 — Diagnose ohne Lookup

Du bekommst die folgenden Fehler. Was ist die wahrscheinlichste Ursache, ohne dass du das Glossar oeffnest? Stichworte reichen.

1. `CKR_PIN_INCORRECT`
2. `CKR_KEY_FUNCTION_NOT_PERMITTED`
3. `CKR_MECHANISM_INVALID`
4. `CKR_ATTRIBUTE_SENSITIVE`
5. `CKR_SESSION_HANDLE_INVALID`

## Reflexionsfragen

Drei Stufen — eine Recall-, eine Analyse-, eine Evaluate-Frage:

1. **(Recall)** Welche der acht Praefix-Familien (Aufgabe 1) erscheint dir gerade am unklarsten? Notiere sie — beim ersten Auftreten im Kurs ist das deine Aha-Stelle.
2. **(Analyse)** Wenn dir Aufgabe 4 zu mehr als drei der fuenf Fehler nichts einfaellt: starte mit [`course/08-debugging.md`](../course/08-debugging.md). Dort wird genau diese Diagnose-Routine eingeuebt — welche Diagnose-Reihenfolge (Modul → Slot → Token → Session → Object → Attribute) wuerdest du als naechste lernen?
3. **(Evaluate)** Welcher der sechs Begriffe (Module, Slot, Token, Session, Object, Mechanism) ist beim Wechsel von "Lab-SoftHSM" auf "produktives Cloud-HSM" am staerksten betroffen — und warum waere "Slot" eine schlechtere Antwort als "Session"?

## Musterloesung

Siehe `solutions/00-glossar.md`.
