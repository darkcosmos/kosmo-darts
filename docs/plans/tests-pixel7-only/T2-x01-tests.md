# T2 - X01 game tests on the Pixel 7 only

## Goal

Remove or retarget every X01 game test and capture of `:app` that uses a size smaller than the Pixel 7 (groups S1,
S2, S3 of the root plan "Inventory"), and move the near-Pixel 7 literals to `TestDevices`. Apply the disposition
column of the root plan "Inventory" table row by row; never drop a rule that has no Pixel 7 test left.

## Files

- `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/ui/x01/GameScreenFitTest.kt`
- `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/ui/x01/RedesignScreensTest.kt`
- `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/ui/x01/CheckoutBannerFitTest.kt`
- `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/ui/x01/ZoneLayoutTest.kt`
- `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/ui/x01/VisitTotalLayoutTest.kt`
- `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/ui/between/BetweenLegsLayoutTest.kt`
- `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/screenshots/DesignSystemScreenshotTest.kt`
- `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/ui/layout/GameLayoutPolicyTest.kt`
- `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/ui/layout/ScreenLayoutPolicyTest.kt`
- `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/ui/RevealNode.kt` (KDoc only, if its example names 360 x 640)

## Depends on

T1.

## Rules

U1, U3, U7, U10, U11, U12, T5, T9, M6, M15, M16, CS7, VT11, VT13 (identifiers as cited in the tests; the rules
themselves do not change).

## Tests

- Retargeted tests keep their rule identifier and assertions; only the device and the size in the name change
  (for example `U1 small portrait` becomes `U1 Pixel 7 portrait inside its bars`).
- `CheckoutBannerFitTest`: the `+fr-land` group moves to the Pixel 7 landscape without its `frland` pre-fix check
  (D6); the sets-match, long-name and undo edge cases move to the inside-bars sizes at scale 2.0.
- `GameLayoutPolicyTest` (pure JVM): apply D7. For each smaller-size input, try the inside-bars size; keep the small
  input only when no supported size reaches the branch, with a one-line comment naming P1 of the root plan, and write
  in the Implementation notes which branch is unreachable on the Pixel 7 (evidence for P1).
- Edge cases: 4 players with long names, sets match (S and M chips), font scale 1.0 and 2.0, both orientations.

## Done when

`./gradlew :app:testDebugUnitTest` and `./gradlew build` green, `kosmo-detekt` exit 0, and
`grep -rnE 'w(360|640|393)dp' app/src/test` prints nothing for these files.

## Model

`sonnet`. Why: test edits from a fully specified table, with per-test judgement on Pixel 7 equivalents.

## Implementation notes
