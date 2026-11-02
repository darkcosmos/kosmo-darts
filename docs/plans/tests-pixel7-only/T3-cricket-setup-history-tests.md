# T3 - Cricket, setup and History tests on the Pixel 7 only

## Goal

Remove or retarget every Cricket, setup and History test and capture of `:app` that uses a size smaller than the
Pixel 7 (groups S1, S4, S5 of the root plan "Inventory"), and move the Pixel 7 literals (`w914dp-h411dp`,
`w411dp-h814dp`) to `TestDevices`. Apply the disposition column of the root plan "Inventory" table row by row.

## Files

- `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/ui/cricket/CricketEndScreenTest.kt`
- `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/ui/cricket/CricketScreenTest.kt`
- `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/ui/cricket/CricketLandscapeScreenTest.kt`,
  `CricketSystemBarsTest.kt`, `VerticalKeyLabelTest.kt` (literal to constant only)
- `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/ui/cricket/CricketBoardLayoutPolicyTest.kt`
- `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/screenshots/CricketScreenshotTest.kt`
- `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/ui/setup/SetupSuggestionsScreenTest.kt`
- `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/ui/history/HistoryScreenTest.kt`
- `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/ui/history/MatchDetailScreenTest.kt`

## Depends on

T1 (T2 not needed, but build T3 after T2 on the same branch).

## Rules

CR22, CR26, CR27 (`docs/rules/cricket.md`), HS2, HS5 (`docs/rules/history.md`), U3 (History review id).

## Tests

- `CricketEndScreenTest`: class device becomes the Pixel 7 portrait inside its bars; the 6 tests stay (CR26 and
  CR27 at scale 1.0 and 2.0, CR28) with "360 x 640" removed from their names.
- `CricketScreenTest` CR22 at 411 x 760: apply D8 (keep the pure policy proof in `CricketBoardLayoutPolicyTest`, delete
  the Robolectric test, add a one-line comment naming P3 at the policy test).
- `CricketBoardLayoutPolicyTest` (pure JVM): apply D7 as in T2; record the unreachable branches (P2, P3) in the notes.
- `CricketScreenshotTest`: retarget `between_legs_4p_360x640_fr` and `end_4p_360x640_fr` to 411x814, renamed
  `between_legs_4p_411x814_fr` and `end_4p_411x814_fr` (the existing Pixel 7 4-player captures are full-screen only,
  A3).
- `CricketScreenTest` (design review U2): confirm a 4-player test at scale 2.0 with the CR22 fit assertions exists at
  411x814 and at 800x360 (Cricket landscape tests run at 914x411 today); add the missing one.
- `SetupSuggestionsScreenTest`: Pixel 7 portrait (class), 4 tests kept.
- History HS2 and HS5 landscape at scale 2.0: Pixel 7 landscape inside its bars; the test must still show that the
  last item needs a scroll (its "does not exist before scrolling" assertion stays).

## Done when

`./gradlew :app:testDebugUnitTest` and `./gradlew build` green, `kosmo-detekt` exit 0, and
`grep -rnE 'w(360|640|731)dp|h760dp|SmallPhone' app/src/test` prints nothing. The report lists the test classes
touched with their test counts before and after (D13).

## Model

`sonnet`. Why: test edits from a specified table, with per-test judgement on Pixel 7 equivalents.

## Implementation notes
