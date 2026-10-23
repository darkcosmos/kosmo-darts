# Save format version 3 fixtures

Frozen output of the version 3 encoders (`GameSaveCodec`, `LastSetupCodec`) of `main` at commit e7372f8, generated
before the version 4 format (issue #40, ADR 0008). Never edit or regenerate these files: version 4 apps must keep
decoding them exactly, as X01 (rule R25).

| File | Content |
|---|---|
| `game-golden.save` | Ann and Clément, 501 double out, single game, `T20 20 D5`, waiting for "Next player" |
| `game-four-players-empty.save` | Ann, Bob, Cid and Dan, 501 double out, single game, no dart |
| `game-match-mid-leg.save` | Ann, Bob and Cid, 301 single out, 2 legs per set, slots 3 1 4, leg 1 won by Bob, then Bob (starter of leg 2) threw `T20` |
| `game-between-legs.save` | Ann and Bob, 301 single out, 3 legs, 2 sets, slots 2 4, legs won by Ann then Bob, between legs |
| `game-pending-match-win.save` | Ann and Bob, 301 single out, 2 legs per set, leg 1 won by Ann, Ann checked out the deciding leg, waiting |
| `last-setup.save` | Cid, Ann and Zoé, 301 single out, 3 legs, 2 sets, slots 2 4 1 |
