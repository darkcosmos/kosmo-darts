# Save format version 1 fixtures

Frozen output of the version 1 encoders (`GameSaveCodec`, `LastSetupCodec`) of `main` at commit db65670, generated
before the version 2 format (issue #39, ADR 0005). Never edit or regenerate these files: version 2 apps must keep
decoding them exactly (rule R16).

| File | Content |
|---|---|
| `game-golden.save` | Ann and Clément, 501 double out, `T20 20 D5`, waiting for "Next player" |
| `game-empty.save` | Ann and Bob, 501 double out, no dart |
| `game-mid-turn.save` | Ann and Bob, 501 double out, Ann threw `T20 5` |
| `game-after-next-player.save` | Ann and Bob, 501 double out, Ann threw `T20 T20 20`, Bob to throw |
| `game-waiting-checkout.save` | Ann and Bob, 301 single out, Ann checked out, waiting in the checkout dialog |
| `game-four-players-301-single-out.save` | Ann, Bob, Cid and Dan, 301 single out, `T20 50 0`, then Bob threw `25` |
| `last-setup.save` | Cid, Ann and Zoé, 301 single out |
