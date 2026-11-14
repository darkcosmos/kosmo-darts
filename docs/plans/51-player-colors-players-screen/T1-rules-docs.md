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
- Done in commit a058eee: `players.md` rewritten as v2 (title, status, vocabulary, PC1 to PC17 copied from the root
  "Rules" section; the v1 "Engine" and "App" headings are gone; "Related rules" now cites R17 and R31).
- `history.md`: PF1, PF5, AR8 rewritten; PF3, HS2, HS3, HS5 completed with the anonymous-profile sentences; new
  "## Players screen" section (PM1 to PM11) placed after "History screens", before "Related rules".
- `persistence.md`: R17 rewritten; R31 added at the end of "Match history" (after R30).
- `design-system.md`: paragraph after the player color table (PC11 to PC13, palette, `onPlayerN` by PC13), in French
  like the rest of the file; `ColorSwatchRow` and `PlayersScreen` rows appended to the components table.
- Existing code and tests still cite the v1 PC ids (slots) and R17 wording until T2 to T4 change them; that is expected
  and not a T1 concern.
- Checks: `./gradlew build` BUILD SUCCESSFUL (code unchanged), `kosmo-detekt` exit 0.
