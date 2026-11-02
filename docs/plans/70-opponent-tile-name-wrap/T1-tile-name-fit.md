# T1: Failing tests, word-safe tile name

## Goal
A tile name is never broken inside a word: it shrinks down to its 1.0 size, then ends with "…" (root plan, Approach
steps 1 to 5; Decisions D1, D2, D4, D5).

## Files
- New `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/ui/x01/TileNameFitTest.kt`.
- `app/src/main/kotlin/io/github/darkcosmos/kosmodarts/ui/components/PlayerTile.kt` (`TileName`, comments).
- `app/src/main/kotlin/io/github/darkcosmos/kosmodarts/ui/components/WordSafeText.kt` (`WordSafeAutoSize` internal).
- `app/src/main/kotlin/io/github/darkcosmos/kosmodarts/ui/theme/KosmoTypography.kt`: one single named 18 sp constant
  read by both `playerNameTile` and `TileName`; no second literal 18 (A2).
- `TileName` fallback flag (root plan, Approach step 4): a comment says why it exists (one extra composition, rare
  multi-word overflow); `remember` keys include name, variant, max width and font scale, so it never sticks after a
  setting change (A1). It keeps the one-line state across recompositions; rotation gives a new width, so a flash at
  most once per name and width; tests check the final state after `waitForIdle` (U1).
- Comments only, assertions unchanged: `RedesignScreensTest` (U3), `GameScreenFitTest` (T8), `ZoneLayoutTest` (U3).

## Depends on
Nothing.

## Rules
VT11, U3, U4, T8, U11; design system `PlayerTile`; Decisions D1 to D5.

## Tests
- Root plan, "Test plan": the whole `TileNameFitTest` matrix and assertions. Commit it first and run it on the
  unchanged code: it must be red (BOB, DAN, MIM at `+fr-land` 2.0 match). Note the red result in the notes.
- Then fix and run it green, with U3, T8, `ZoneLayoutTest`, `GameLayoutPolicyTest`, `VisitTotalLayoutTest` and the
  rest of `:app:testDebugUnitTest` unchanged and green. If an existing 8-character assertion goes red, stop and
  report to main (D3 relaxes the rule only for the new match/landscape/4-player case).
- `ZoneLayoutTest`, `RedesignScreensTest`, `GameScreenFitTest`: comments only, assertions unchanged (T1 of review).
- Canary: the "Maxime Dupont" / "Bartholomew Jonathan" cases must fail if the fallback is removed; remove it
  once, run, restore (file by file), and note the result here (T2 of review).
- Edge cases: scale 1.0 (min = max, size unchanged), one-letter and accented names unaffected, a two-word name whose
  first word is wider than the line (fallback to one line with "…", no break inside a word after the extra frame).

## Done when
`./gradlew :app:testDebugUnitTest` and `./gradlew build` green; `kosmo-detekt` exit 0; tests and fix committed.

## Model
sonnet. Why: the behaviour, the API to reuse and every test case are specified; no stored format, no cross-module work.

## Implementation notes
- Red first (commit 98eca9f, unchanged code): 30 of 88 `TileNameFitTest` cases failed, among them
  `4p_match_bob_cid_dan land x2.0` ("BOB" breaks inside a word: [2, 3]), `4p_match_mim_kevin_kosmo land x2.0` ("MIM"
  [2, 3]), `4p_match_julien_maxime_jeanpaul land x2.0` ("JULIEN" [3, 6]), `4p_*_bartholomew_two_words port x1.0..2.0`
  ("BARTHOLOMEW" [10, 11] .. [7, 11]). DAN breaks the same way as BOB (same test, first failing assertion shown).
- Test deviation (commit 0503fff): under autosize `layoutInput.style.fontSize` keeps the style size, so the size
  assertions could not see the shrinking. The drawn size is now read from the first line height, compared with a
  `TextMeasurer` reference at the 1.0 size and the style size (same line count). The "no break inside a word"
  assertions did not change. Re-run on the unchanged main code with the final file: 29 of 88 red (BOB, MIM, ...).
- The 2p "Bartholomew" case is not ellipsized in landscape (wide tile); the "ends with …" next-player check (U4) runs
  on the 4p match case (BARTHOLOMEW is next, landscape 2.0). `TalkBack` description is asserted in every case.
- Fix: `TileName` (PlayerTile.kt) = `WordSafeAutoSize` (now `internal`) from the style size to
  `fixedSp(KosmoTypography.PLAYER_NAME_TILE)` (min never above the style size), lines = 2 only on NARROW/COMPACT
  variants with 2+ words, plus the `onTextLayout` fallback (`oneLineAtWidth` keyed on name, variant and font scale;
  it stores the layout width so a rotation clears it). `PLAYER_NAME_TILE` (18f) is the one constant of
  `KosmoTypography`, read by the `playerNameTile` style and `TileName`.
- Canary: with the fallback assignment neutralized (`oneLineAtWidth = NO_WIDTH`), 10 of 88 cases fail ("MAXIME
  DUPONT" land x1.5/x2.0, "BARTHOLOMEW JONATHAN" land x1.0/1.3, port x1.0..1.5 in match and single game). Restored.
- Comments only in `RedesignScreensTest`, `GameScreenFitTest`, `ZoneLayoutTest`; assertions untouched. All three and
  the rest of `:app:testDebugUnitTest` are green. T2 still has captures, VT11 and design-system wording.
