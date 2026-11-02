# T2: Captures, capture comparison, M15 and 6.3 wording

## Goal
Show the fixed CHECKOUT zone in captures, prove no other screen changed, and write the rule of D1/D2 in the docs.

## Files
- Added or changed a Roborazzi test under `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/screenshots/`
  (CHECKOUT mode, scenario of `ZoneLayoutTest` M15: ANN at 121, T20 T20 1).
- Changed `docs/rules/match.md` (M15) and `docs/design/design-system.md` (6.3, in French).

## Depends on
T1 (read its Implementation notes first).

## Rules
M15, design system 6.3. Decisions D1 to D3.

## Tests
- 16 captures: `+fr-port`, `+en-port`, `+fr-land`, `+en-land` at font scale 1.0, 1.3, 1.5, 2.0, plus
  `archive_x01_failed_landscape_fr_font200` re-recorded. Open each one: the word "CHECKOUT" is whole, the name,
  average and line are whole; note any doubt in the notes for the ux-reviewer.
- Comparison: record every capture on `main` (7441ae3) and on the head, `cmp` each PNG; only CHECKOUT-mode
  captures may differ. List the changed files and the total count in the notes. Any other difference: stop, report.
- Docs: M15 and 6.3 say the banner keeps a least height of 40 dp and the name row and the line shrink, never below
  their size at the default font size (D1, D2). No rule identifier changes.

## Done when
`./gradlew :app:testDebugUnitTest`, `./gradlew build` green; capture comparison done; `kosmo-detekt` exit 0.

## Model
sonnet. Why: adding captures is mechanical, but deciding which PNG differences are expected needs judgment.

## Implementation notes
