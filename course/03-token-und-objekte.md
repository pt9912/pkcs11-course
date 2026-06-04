# 03 — Token und Objekte

> **Didaktischer Pfad:** Vorher → [`02-lab-setup.md`](02-lab-setup.md) · Nachher → [`04-signieren-und-verifizieren.md`](04-signieren-und-verifizieren.md)

## Bevor du anfaengst — was vermutest du?

> Du legst einen RSA-Key auf SoftHSM an, machst dir eine Notiz `id=01`, listest spaeter `pkcs11-tool --list-objects`. Wo schaust du als erstes nach, um *deinen* Key wiederzufinden — beim Label, beim Handle, beim Slot?

Wahrscheinliche Vermutung: am Handle. Ein Handle ist eine Zahl, die zeigt auf ein konkretes Objekt, das ist doch wie ein Pointer. Mentale Karte: **Objekt-Identitaet = Handle. Zwei Aufrufe mit demselben Handle treffen denselben Key**.

Diese Karte uebersieht zwei Ebenen. Erstens: Object-Handles sind **session-lokal** (PKCS#11 §11.7). Ein Handle aus Session A ist in Session B bedeutungslos — selbst im selben Prozess, selbst auf demselben Token. Zweitens: der **persistente** Identifier eines Objekts ist seine Attribut-Menge, nicht das Handle. Zwei Anwendungen, die denselben Key meinen, einigen sich ueber `CKA_LABEL`/`CKA_ID` — die Bytes, die im Token persistent stehen. Halte die "Handle = stabile Adresse"-Karte fest. Dieses Kapitel zeigt, dass Identitaet von Objekten **ueber Attribute** laeuft, und dass `CKA_EXTRACTABLE` als Einbahnstrasse beim **Erzeugen** entschieden wird, nicht spaeter.

## Lernziele

Nach diesem Kapitel kannst du:

- einen SoftHSM-Token initialisieren.
- Slots und Token-Labels unterscheiden.
- RSA-Keypairs im Token erzeugen.
- Public Key, Private Key und Zertifikat als PKCS#11-Objekte einordnen.
- `CKA_LABEL` und `CKA_ID` fuer Anwendungen erklaeren.
- **(Bloom 5 — evaluate)** die `CKA_EXTRACTABLE`-Einbahnstrasse aus PKCS#11 §10.2.6 fuer eine konkrete Key-Erzeugung gewichten — was ist der Recovery-Pfad bei falscher Wahl?

> **Geschaetzte Bearbeitungszeit:** ~60 min (Lesen 20 min + Lab + Eigenexperiment `CKA_EXTRACTABLE` 25 min + Uebung 15 min).

## Lab-Bezug

Passende Targets:

```bash
make init-token
make list-slots
make gen-rsa
make list-objects
```

## Token initialisieren

```bash
lab/scripts/01-init-token.sh
```

Danach Slots anzeigen:

```bash
lab/scripts/02-list-slots.sh
```

SoftHSM verschiebt initialisierte Tokens häufig in einen anderen Slot. Verlasse dich deshalb nicht blind auf Slot `0`. Für Skripte ist `--token-label dev-token` stabiler.

## RSA-Keypair erzeugen

```bash
lab/scripts/04-generate-rsa.sh
```

Das erzeugt ein RSA-2048-Keypair im Token:

- Label: `signing-key`
- ID: `01`
- Verwendungszweck: Signieren/Verifizieren

## Objekte anzeigen

```bash
lab/scripts/05-list-objects.sh
```

Du solltest mindestens sehen:

- Public Key Object
- Private Key Object

## Objektidentität

In PKCS#11 sind `CKA_LABEL` und `CKA_ID` wichtig.

- `CKA_LABEL` ist menschenlesbar.
- `CKA_ID` ist für Zuordnung wichtig, z. B. Private Key ↔ Zertifikat.

Bei Java wird daraus oft ein Alias. Wenn Alias-Mapping nicht passt, findet Java den Schlüssel nicht, obwohl er im Token existiert.

## `CKA_SENSITIVE` und `CKA_EXTRACTABLE` — das Sicherheitsmodell in zwei Attributen

Zwei Attribute steuern, was mit einem privaten oder symmetrischen Schluessel ausserhalb des Tokens passieren darf:

- **`CKA_SENSITIVE=true`** verbietet das Lesen des Schluesselwerts ueber `C_GetAttributeValue(CKA_VALUE)`. Versuche enden mit `CKR_ATTRIBUTE_SENSITIVE`. Das ist der Default bei privaten und secret Keys auf realen HSMs.
- **`CKA_EXTRACTABLE=false`** verbietet zusaetzlich den verschluesselten Export ueber `C_WrapKey`. Der Schluessel kann das Token also unter keinen Umstaenden in irgendeiner Form verlassen — auch nicht gewrappt.

Wichtig ist die **Einbahnstrasse**: PKCS#11 §10.2.6 erlaubt fuer `CKA_EXTRACTABLE` nur den Uebergang `true → false`, nie `false → true`. Wer einen produktiven Key ohne Backup-Strategie auf `CKA_EXTRACTABLE=false` setzt, kann ihn spaeter nicht mehr aus dem HSM herausholen — auch nicht fuer ein Disaster-Recovery-Szenario.

Im Kurs spielt das Attribut an drei Stellen eine konkrete Rolle:

- **Java/SunPKCS11** in [Kapitel 06](06-java-sunpkcs11.md): wenn der Pubkey ueber den Default-Provider verifiziert werden soll, scheitert das bei `CKA_EXTRACTABLE=false`, weil die JCA-Pubkey-Instanz dann nur ein PKCS#11-Handle ist, kein materialgefuelltes Objekt.
- **Hybride Verschluesselung** in [Kapitel 13](13-verschluesselung.md): der RSA-Wrap-Key ist bewusst `CKA_EXTRACTABLE=false`, der per RSA-OAEP gewrappte AES-Session-Key ist nur eine Datei und nie ein Token-Objekt — andere Welt.
- **Backup/Wrap** in [Kapitel 20](20-key-wrap.md): hier braucht man bewusst `CKA_EXTRACTABLE=true`, sonst weist `C_WrapKey` mit `CKR_KEY_UNEXTRACTABLE` ab.

Im Lab erzwingt seit 0.16.0 der Go-Helper `lab/go/pkcs11-keygen` ein sortenreines Template — das macht `make validate-key-usage` als Drift-Check sichtbar.

## Eigenexperiment

- **`CKA_EXTRACTABLE`-Einbahnstrasse empirisch zeigen.** Erzeuge einen Test-Key bewusst mit `CKA_EXTRACTABLE=true`, lies den Wert ueber `pkcs11-tool --read-object` aus, setze ihn dann via `--set-attr CKA_EXTRACTABLE:false` und versuche den Read erneut. Erwartet: nach dem Wechsel meldet das Token `CKR_ATTRIBUTE_SENSITIVE` (oder leeren Wert), und der Versuch, ihn zurueck auf `true` zu setzen, faellt mit `CKR_ATTRIBUTE_READ_ONLY`. Das ist die Spec-§10.2.6-Realitaet, einmal selbst geklickt.

- **Vertiefende Aufgaben.** Die strukturierten Lab-Aufgaben fuer dieses Kapitel — Token initialisieren, Key erzeugen, Slot-Wandern beobachten — liegen in [`exercises/01-token.md`](../exercises/01-token.md) und [`exercises/02-key-signature.md`](../exercises/02-key-signature.md).

## Selbsttest

<details>
<summary>1. Du siehst beim <code>make list-objects</code> einen Privkey mit Label <code>signing-key</code> und <code>CKA_ID=01</code>. Was muss am Zertifikat zwingend uebereinstimmen, damit Java es als zusammengehoerig erkennt?</summary>

Die `CKA_ID` muss identisch sein (`01`). Das `CKA_LABEL` ist hilfreich, aber nicht zwingend. SunPKCS11 baut den Alias aus dem Cert mit gleicher `CKA_ID` wie der Privkey — kein passendes Cert, kein Alias, kein Private-Key-Entry im KeyStore.
</details>

<details>
<summary>2. Welche Folge hat es, einen produktiven Privkey versehentlich mit <code>CKA_EXTRACTABLE=false</code> anzulegen, wenn man spaeter ein Backup will?</summary>

Keine Backup-Moeglichkeit. PKCS#11 §10.2.6 erlaubt fuer `CKA_EXTRACTABLE` nur den Uebergang `true → false`, nie zurueck. Ein nachtraegliches Wrap-Backup ist damit unmoeglich; einziger Recovery-Pfad ist Vendor-spezifisches HSM-Backup (Cluster-Sync oder Hersteller-Backup-Format).
</details>

<details>
<summary>3. Warum verlaesst du dich nicht auf Slot <code>0</code>, obwohl SoftHSM frisch initialisiert genau dort den Token zeigt?</summary>

Slot-IDs sind nicht stabil. SoftHSM verschiebt initialisierte Tokens haeufig in einen anderen Slot, und Hardware-HSMs vergeben Slot-IDs nach Einsteck-Reihenfolge. Stabile Identifier sind Token-Label oder PKCS#11-URI (RFC 7512); Anwendungen, die `slotListIndex=0` hartcodieren, brechen, sobald ein zweites Token erscheint.
</details>
