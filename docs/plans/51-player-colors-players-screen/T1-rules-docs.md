# T1: Rules and design system text

## Goal
Write the rules of this mission into `docs/rules/` and the new components into the design system, so that later
tasks cite them. Text only, no code.

## Files
- `docs/rules/players.md`: title, status line (issue #51), vocabulary, PC1 to PC17.
- `docs/rules/history.md`: PF1, PF3, PF5, AR8, HS2, HS3, HS5 rewritten or completed; new section "Players screen"
  with PM1 to PM11.
- `docs/rules/persistence.md`: R17 rewritten, R31 added under "Match history".
- `docs/design/design-system.md`: section 2, after the player color table: player colors are any color of
  `docs/rules/players.md` PC11 to PC13, the four tokens are the palette, `onPlayerN` is computed by PC13.
  Components table: `ColorSwatchRow` and `PlayersScreen` as described in root "Screens and states".

## Depends on
None.

## Rules
The root plan section "Rules (new and rewritten)" is the exact text to copy. Keep every other rule unchanged.

## Tests
None (documentation). Check that no rule id is duplicated: `grep -c '^- PC1[0-7]\.' docs/rules/players.md` is 8.

## Done when
`./gradlew build` green (unchanged code); `kosmo-detekt` exit 0.

## Model
haiku. Why: text copied from the root plan, no design choice.

## Implementation notes
