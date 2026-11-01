# T1: Shared number fitting, its tests, A2 comment, capture check

## Goal
One fitting helper used by the X01 `ScoreArea` and by `CricketZoneTotal`, with nothing visible changing. API and
exact expression: root plan "Approach".

## Files
- Added `app/src/main/kotlin/io/github/darkcosmos/kosmodarts/ui/components/FittedNumberSize.kt` (`fitScale`,
  `rememberFittedNumberSize`).
- Changed `ui/components/CurrentPlayerZone.kt` (`ScoreArea` calls the helper) and `ui/cricket/CricketZoneTotal.kt`
  (calls the helper, KDoc updated, A2 comment on `ZONE_MPR_REFERENCE`).
- Added tests `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/ui/components/FitScaleTest.kt` and
  `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/ui/cricket/MprReferenceWidthTest.kt`.

## Depends on
None. Baseline commit for the captures: `a6745c5`.

## Rules
CR22. Decisions D1 to D6 of the root plan. Check that the `remember` keys are the union of the original keys of
both callers (D4, root plan "Approach").

## Tests
- `FitScaleTest`: the cases of root plan "Test plan", exact float equality (the expression is unchanged).
- `MprReferenceWidthTest`: `"8,8"` and `"8.8"` measure the same width in `scoreHuge` at `SCORE_PORTRAIT`; setup as in
  `ui/theme/SairaWidthSpikeTest.kt`; both strings in one test with the same `TextMeasurer`, `Density` and style
  (D5). If they differ, stop and report to main (root plan, open question 1); do not
  change the reference or invent another comment.
- Existing tests unchanged and green.
- Captures: the `cmp` procedure of root plan "Test plan" over every PNG and file name. Any difference: stop and
  report to main with the file names.

## Done when
`./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build` green; every PNG
byte-identical to `a6745c5` (`cmp`), with the file count written in the notes; `kosmo-detekt` exit 0.

## Model
sonnet. Why: small specified refactoring inside `:app`, with listed tests; no stored format, no cross-module change.

## Implementation notes
- No deviation. Helper in `ui/components/FittedNumberSize.kt`; `ScoreArea` and `CricketZoneTotal` call
  `rememberFittedNumberSize`. `remember` keys `(constraints, style, baseSize, density, reference)` are the union of the
  original keys of both callers (checked against the original code).
- `MprReferenceWidthTest`: "8,8" and "8.8" measure the same width; the A2 comment is on `ZONE_MPR_REFERENCE`.
- Captures: 212 PNG in both directories, same names, `cmp` finds 0 difference against `a6745c5`.
