# Loesung 01 - Token initialisieren

```bash
make init-token
make list-slots
```

Achte auf:

- `Label: dev-token`
- `Initialized: yes`
- `User PIN init.: yes`

Der Slot kann sich nach der Initialisierung aendern. SoftHSM verschiebt initialisierte Tokens haeufig aus Slot `0` in eine andere Slot-ID. Deshalb verwenden die Skripte das Token-Label statt eines festen Slots.

## Antworten zu den Reflexionsfragen

**`--token-label dev-token` vs fester Slot:** Slot-IDs sind im PKCS#11-Sinn keine stabile Identitaet — SoftHSM und viele Hardware-HSMs vergeben sie dynamisch (z.B. nach `slotsToReturnInGetSlotList`-Reihenfolge, die sich bei Token-Insertion aendert). Das Token-Label ist Teil des Token-Inhalts, ueberlebt also die naechste Library-Init. Skripte und Anwendungen, die ueber das Label gehen, brechen nicht, wenn eine zweite Smartcard eingesteckt oder ein zweites SoftHSM-Token initialisiert wird.

**Was die Anwendung wirklich braucht:** Token-Label oder eine PKCS#11-URI (`pkcs11:token=dev-token;...`) — RFC 7512. Optional zusaetzlich `model`, `serial` und `manufacturer`, wenn man zwischen mehreren identisch benannten Tokens unterscheiden muss. Wer hartcodierte Slot-IDs in eine Anwendung schreibt, hat einen Bug, der erst bei dem zweiten HSM im selben System sichtbar wird — oft Jahre nach dem Deployment.
