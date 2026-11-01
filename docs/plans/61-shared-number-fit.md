# Plan: shared fitting of the big numbers (issue #61)

## Goal

Issue [#61](https://github.com/darkcosmos/kosmo-darts/issues/61) (findings A1 and A2 of PR #60). The X01 score area
(`ScoreArea` in `ui/components/CurrentPlayerZone.kt`) and the Cricket zone number (`CricketZoneTotal` in
`ui/cricket/CricketZoneTotal.kt`) contain the same code to fit a big number: measure a reference text at the base size,
take the smallest of 1, the width ratio and the height ratio (with one pixel less of height), and scale the base size.
Extract it once, use it in both places, and document `ZONE_MPR_REFERENCE`. Pure refactoring: nothing changes for the
player, every Roborazzi capture stays byte-identical.

## Scope and out of scope

In scope:
- one shared fitting helper in `:app`, used by `ScoreArea` and `CricketZoneTotal`;
- the pure ratio computation as a plain function with plain JUnit tests;
- the A2 comment on `ZONE_MPR_REFERENCE`, backed by a measurement test;
- the byte-for-byte `cmp` check of every Roborazzi PNG against baseline `a6745c5`.

Out of scope: any change of size, layout, alignment, text, semantics or reference strings; other text-fitting code
(checkout banner, keypad, tiles); `:game-engine` (UI-only change, no game rule is touched); the `:capture` module.

## Approach

New file `app/src/main/kotlin/io/github/darkcosmos/kosmodarts/ui/components/FittedNumberSize.kt`, two functions:

1. `internal fun fitScale(maxWidth: Int, maxHeight: Int, referenceWidth: Int, referenceHeight: Int): Float`, pure, no
   Compose type: `minOf(1f, maxWidth.toFloat() / referenceWidth.coerceAtLeast(1),
   (maxHeight - 1).coerceAtLeast(1).toFloat() / referenceHeight.coerceAtLeast(1))`, with the existing comment on the
   one pixel less. Exactly today's expression, same operand order, so the floats are bit-identical.
2. `@Composable internal fun rememberFittedNumberSize(reference: String, style: TextStyle, baseSize: Float,
   constraints: Constraints): Float`: reads `LocalDensity`, uses `rememberTextMeasurer()`, measures `reference` with
   `style.copy(fontSize = density.fixedSp(baseSize))`, `softWrap = false`, `maxLines = 1`, and returns
   `baseSize * fitScale(...)`, inside `remember(constraints, style, baseSize, density, reference)`. These keys are
   the union of today's keys: `ScoreArea` uses `(constraints, baseStyle, scoreSize, density)` (its reference is a
   constant) and `CricketZoneTotal` uses `(constraints, baseStyle, density, reference)` (its base size is a
   constant). Every input of the computation is a key, and the extra key in each caller never changes, so the
   result and the recomposition behavior stay as they are (D4).

`ScoreArea` calls it with `SCORE_REFERENCE`, `scoreHuge`, `scoreSize`, `constraints`. `CricketZoneTotal` calls it with
`reference`, `scoreHuge`, `KosmoTypography.SCORE_PORTRAIT`, `constraints`. Each one keeps its own `BoxWithConstraints`,
`Text`, alignment, tag and semantics. Both functions are `internal`, as the rest of the shared components. `ui/cricket`
already imports from `ui/components`, so the dependency direction stays the same.

Alternatives considered:
- A shared `FittedNumberText` composable (box plus text). Rejected: `ScoreArea` shares its box with the checkout banner
  and uses a start alignment in landscape; `CricketZoneTotal` adds a content description. The common part is the size,
  as the issue says.
- An extension on `BoxWithConstraintsScope` instead of a `constraints` parameter. Rejected: an explicit parameter is
  easier to read and to call from any box.
- Keeping `fitScale` private. Rejected: as `internal`, it can be tested on the JVM without Robolectric.

A2: the comment on `ZONE_MPR_REFERENCE` says that in the tabular figures of `scoreHuge` the comma (French) and the
point (English) have the same width, so one reference gives the same size in both languages. A Robolectric test
measures it (Test plan). If the widths differ, the build agent stops and reports to main (open question 1). It does
not change the reference or the comment wording on its own.

## Files and types added or changed

- Added `app/src/main/kotlin/io/github/darkcosmos/kosmodarts/ui/components/FittedNumberSize.kt` (`fitScale`,
  `rememberFittedNumberSize`).
- Changed `app/src/main/kotlin/io/github/darkcosmos/kosmodarts/ui/components/CurrentPlayerZone.kt` (`ScoreArea`).
- Changed `app/src/main/kotlin/io/github/darkcosmos/kosmodarts/ui/cricket/CricketZoneTotal.kt` (call, KDoc no longer
  says "the X01 file stays untouched", A2 comment).
- Added `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/ui/components/FitScaleTest.kt` (plain JUnit).
- Added `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/ui/cricket/MprReferenceWidthTest.kt` (Robolectric, same
  setup as `ui/theme/SairaWidthSpikeTest.kt`).

## Data read, stored or exposed

None. No stored format, permission, resource or string changes.

## Screens and states

No visible change. Affected: X01 current player zone (portrait and landscape, every mode except CHECKOUT) and the
Cricket portrait zone number (total, or MPR in a solo game). Proof: every capture is unchanged (Test plan).

## Test plan

Rules: CR22 (Cricket zone number); the X01 score area has no rule identifier in `docs/rules/` (design system 6.3).

`FitScaleTest` (plain JUnit, no Robolectric):
- area larger than the reference in both directions -> exactly `1f` (never grows);
- width limits: max 100 x 1000, reference 200 x 100 -> `0.5f`;
- height limits with the one pixel less: max 1000 x 51, reference 100 x 100 -> `0.5f`;
- height equal to the reference: max 100 x 100, reference 50 x 100 -> `0.99f`;
- reference width or height 0 -> treated as 1, no division by zero, finite result;
- max height 0 and 1 -> height ratio uses 1 pixel, so the result is `1f / referenceHeight`;
- max width 0 -> `0f`.

`MprReferenceWidthTest` (Robolectric, Pixel 7): measure `"8,8"` and `"8.8"` with `scoreHuge` at
`SCORE_PORTRAIT` (`fixedSp`) and assert the same width; also assert that `ZONE_MPR_REFERENCE` is `"8,8"`. Both
strings are measured in the same test method, with one `TextMeasurer`, one `Density` and one style, so the comparison
cannot depend on the Robolectric default locale or on environment drift between runs (D5).

Existing tests stay green unchanged, including `X01ScreensTest`, `ZoneLayoutTest`, `CricketScreenTest`.

Roborazzi, record only (procedure of `40-cricket-mode.md`, "Roborazzi captures"):
1. `git worktree add "$(git rev-parse --git-dir)/squad/baseline" a6745c5`, then `./gradlew :app:testDebugUnitTest`
   in it.
2. `./gradlew :app:testDebugUnitTest` on the task head.
3. Compare every PNG of both `app/build/outputs/roborazzi` directories with `cmp` (X01, Cricket and all others), and
   check that both directories hold the same file names (a missing or new file is a difference).
4. `git worktree remove` the baseline.

Any differing or missing PNG is a blocker: report it to main with the file names, never accept it silently. If the same
commit gives different PNGs twice, report it too. Never skip or weaken the comparison.

## How Kosmo verifies on the phone

Nothing should change. Start an X01 game and a Cricket game (also a solo Cricket game for the MPR), in portrait and
landscape: the big numbers have the same size as in the previous version and are never cut.

## Tasks

| Id | Title | Task file | Depends on | Model | Why this model |
|---|---|---|---|---|---|
| T1 | Shared number fitting, its tests, A2 comment, capture check | `61-shared-number-fit/T1-shared-fit.md` | - | sonnet | Small specified refactoring inside `:app` with listed tests |

No `opus` task.

## Decisions

- D1: pure refactoring, no product question (Kosmo, via main).
- D2: the helper lives in `:app`, `ui/components`, `internal`; the pure ratio is a separate function tested on the JVM.
- D3: every Roborazzi PNG, not only X01 and Cricket, is compared with `cmp` against `a6745c5`; any difference blocks.
- D4 (design review, A1 minor): the `remember` keys of `rememberFittedNumberSize` are the union of the original keys
  of both callers (see "Approach"); the build agent checks this against the original code.
- D5 (design review, T1 minor): `MprReferenceWidthTest` measures both strings in one test with the same
  `TextMeasurer`, `Density` and style.
- D6: on a width mismatch in `MprReferenceWidthTest`, the build agent stops and main decides (open question 1).
- Design review: the four reviewers approved the plan (0 blocking); minors A1 and T1 are folded in as D4 and D5.

## Open questions

1. If `"8,8"` and `"8.8"` do not have the same width in `scoreHuge`, the A2 comment from the issue would be false:
   main decides (for example, comment states the real relation, or one reference per language). None other.
