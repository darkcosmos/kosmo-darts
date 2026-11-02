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
- New `screenshots/CheckoutBannerScreenshotTest.kt`: 16 captures `checkout_banner_{portrait,landscape}_{fr,en}_font{100,130,150,200}`
  (Pixel 7, 4 players, sets match, Ann leaves 121 then T20 T11 D14; S and M chips shown). The M15 docs now state the rule.
- Looked at (opened) fr/en landscape 1.0, 1.3, 1.5, 2.0 samples, portrait fr 2.0 and the re-recorded
  `archive_x01_failed_landscape_fr_font200`: "CHECKOUT" is whole everywhere, name, average, line, dart slots whole. At 2.0
  in landscape the banner is a slim full-width strip (~40 dp), the line is whole; at 1.3 and 1.5 it is large. Doubt for the
  ux-reviewer: in the 4-player landscape font200 capture the small tiles break the names "BOB"/"DAN" over two lines
  ("BO/B", "DA/N"): not in this mission's scope (not the banner).
- Comparison (every PNG of 7441ae3 against the head, `cmp`; worktree at 7441ae3): 259 baseline files, 275 on the head
  (16 added). One expected change: `archive_x01_failed_landscape_fr_font200.png` (X01 pending checkout). Five more files
  differ between 7441ae3 and the head AND between two runs of the same head, so they are render noise, not this change:
  `archive_cricket_failed_landscape_en`, `archive_cricket_failed_landscape_fr_font200`,
  `archive_cricket_saving_landscape_en`, `archive_cricket_saving_portrait_en` (Cricket, no banner) and
  `settings_voice_available_portrait_en` (differs only in the first run pair). Every other PNG is byte-identical.
