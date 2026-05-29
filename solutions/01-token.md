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
