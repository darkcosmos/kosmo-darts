# T4 - Kosmo Capture tests on the Pixel 7 only, and the after measures

## Goal

Remove or retarget the `:capture` tests and captures at 640 x 360 (group S2 of the root plan "Inventory"), move the
`w915dp-h412dp` literals to the Pixel 7 landscape, then measure the whole suite after the mission (root plan "Test
plan", "Measures") and write the numbers in the root plan.

## Files

- `capture/src/test/kotlin/io/github/darkcosmos/kosmodarts/capture/ui/ZoomRowTest.kt`
- `capture/src/test/kotlin/io/github/darkcosmos/kosmodarts/capture/ui/CaptureActionRowTest.kt`
- `capture/src/test/kotlin/io/github/darkcosmos/kosmodarts/capture/ui/CaptureScreenshotsTest.kt`
- `capture/src/test/kotlin/io/github/darkcosmos/kosmodarts/capture/ui/CaptureScreenTest.kt` (literal only)
- `capture/src/test/kotlin/io/github/darkcosmos/kosmodarts/capture/TestDevices.kt` (added, same constants as `:app`,
  landscape ones only; `:capture` does not depend on `:app`, D3)
- `docs/plans/tests-pixel7-only.md` (section "Test plan", "Measures": the "after" column)

## Depends on

T1, T2, T3 (the "after" measures need every task done).

## Rules

`docs/rules/capture.md` (unchanged); review ids U11, U17 of the capture plans as cited in the tests.

## Tests

- `ZoomRowTest` 640 x 360 (zoom row 42 dp, preview at least 160 dp, slider at least 96 dp in English and French): each
  moves to the Pixel 7 landscape inside its bars (the tightest supported height), not deleted.
- `CaptureActionRowTest` U11 at 640 x 360: retargeted to 800x360 (its 915 x 412 sibling is full-screen, A3).
- `CaptureScreenshotsTest`: the `640x360_en` and `640x360_fr` sets retargeted to 800x360, renamed `800x360_en`,
  `800x360_fr`.
- No `robolectric.properties` default in `:capture` (D11): list each Compose UI test class and where it names its size.
- Compare the JUnit XML class lists of `:app` and `:capture` before (2f9e437) and after: no class disappears; confirm
  no persistence, corrupt-save or input-validation test was removed (D13).

## Done when

`./gradlew build` green, `kosmo-detekt` exit 0, the final greps of root plan "Test plan" print nothing, and the "after" column of "Measures" is filled with the commands of that section.

## Model

`sonnet`. Why: test edits from a specified table plus measures, no design choice.

## Implementation notes
