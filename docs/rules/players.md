# Player rules (color slots)

Status: v1 (issue #49, with the owner answers Q2 and Q6 of 2026-10-22, recorded in `docs/plans/49-design-system.md`,
section "Decisions").
This document is the single source of truth for the color of each player: tests cite these rules, never the issue
text. Rule ids (PC1, PC2, ...) are stable. They use the prefix PC so that they never collide with the X01 rules
(`docs/rules/x01.md`), the match rules (M), the persistence rules (R), the announcement rules (AN), the visit total
rules (VT) or the checkout suggestion rules (CS).

Vocabulary:
- *Color slot*: a number from 1 to 4 that the engine gives to each player (`ColorSlot`). The engine knows nothing of
  colors; the app shows slot n with its color token `playerN` (`docs/design/design-system.md`, section 2).

## Engine

- PC1. A player has exactly one color slot from 1 to 4; two players of a setup or a match never share a slot.
- PC2. Adding a player in the setup gives him the first free slot in slot order.
- PC3. Removing a player (any position) frees his slot; the other players keep theirs.
- PC4. Shuffle changes the order only; each player keeps his slot.
- PC5. A match configuration with slots of the wrong count, or repeated slots, is rejected. Without explicit slots,
  each player gets the slot of his position (1 to n).
- PC6. Slots never change during a match (every leg and set).

## App

- PC7. Rematch keeps each player's slot (the first player moves to the end with his slot, M17).
- PC8. The setup prefilled from the last setup restores each player's slot.
- PC9. The UI shows slot n with token `playerN`; the color never carries information alone (name always shown).

## Related rules

- Persistence: R17 (slots saved with the game and the last setup) and R18 (older files read with slots by position)
  (`docs/rules/persistence.md`, format version 3: `docs/adr/0006-save-format-v3-colors.md`).
- Match: M13 (setup player list), M14 (indicators) (`docs/rules/match.md`).
