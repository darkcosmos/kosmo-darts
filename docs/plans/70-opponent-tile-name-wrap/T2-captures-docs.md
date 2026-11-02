# T2: Captures, comparison, VT11 and design-system wording

## Goal
Show the fixed tile names in captures, prove which screens changed, and write D1 to D3 in the docs.

## Files
- New capture test `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/screenshots/TileNamesScreenshotTest.kt`
  (Pixel 7, native graphics, record only, like `CheckoutBannerScreenshotTest`).
- `docs/rules/visit-total.md` (VT11, last item on names).
- `docs/design/design-system.md` (`PlayerTile` row, French).

## Depends on
T1 (read its Implementation notes first).

## Rules
VT11, design system `PlayerTile`, U3, T8. Decisions D1 to D3.

## Tests
- New captures (root plan, "Test plan"): `tile_names_4p_match_landscape_fr_font{130,150,200}` (Ann, Kevin, Kosmo,
  Maxime Dupont) and `tile_names_4p_portrait_fr_font200` (Ann, Bartholomew, Maxime Dupont, Cid). Open each one: no
  name broken inside a word, "…" on one line, scores and boxes whole. Note doubts for the ux-reviewer.
- Comparison: record every capture on `main` ec4ee46 (git worktree) and on the head, `cmp` each PNG. Expected changes:
  only captures whose tiles had a name broken inside a word or a multi-word name that now shrinks (e.g. the
  `checkout_banner_*` 4-player sets captures at 1.3 to 2.0, long-names captures). List the changed files and the
  total count; re-run the head twice to separate render noise. Any other difference: stop and report.
- Docs: VT11 and the design-system row say a tile name shrinks word-safe down to its 100 % size, then ends with "…" on
  one line, two lines only between words, and that "at least 8 characters" is not guaranteed in a sets match in
  landscape at 4 players at large font sizes (D3). No rule identifier changes.

## Done when
`./gradlew :app:testDebugUnitTest` and `./gradlew build` green; comparison done and listed; `kosmo-detekt` exit 0.

## Model
sonnet. Why: captures are mechanical, but deciding which PNG differences are expected needs judgment.

## Implementation notes
