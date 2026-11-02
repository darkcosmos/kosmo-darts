# T1: Measure, failing fit test, fix the CHECKOUT zone fitting

## Goal
The CHECKOUT banner text is always whole (D1 to D5). Order: measure (root plan "Approach" 1), write
`CheckoutBannerFitTest` and see it fail, then fix (root plan "Approach" 2 to 4).

## Files
- Added `app/src/main/kotlin/io/github/darkcosmos/kosmodarts/ui/components/CheckoutFit.kt`.
- Changed `ui/components/CurrentPlayerZone.kt`, `ui/layout/GameLayoutPolicy.kt`.
- Added tests `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/ui/components/CheckoutFitTest.kt`,
  `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/ui/x01/CheckoutBannerFitTest.kt`; changed
  `ui/x01/ZoneLayoutTest.kt`, `ui/layout/GameLayoutPolicyTest.kt`.

## Depends on
None. Base: `main` at 7441ae3.

## Rules
M15, CS8, design system 6.3, U10. Decisions D1 to D5. No string, no touch target change; tokens only
(`KosmoTheme.typography`, `fixedSp`, `CHECKOUT_BANNER_MIN`).

## Tests
- Measurement: a temporary probe (not committed) logging the values of root plan "Approach" 1; write them and the
  confirmed cause in the notes. Unknown cause: stop, report to main (open question 1).
- `CheckoutBannerFitTest`: the configurations and assertions of root plan "Test plan"; run it before the fix and
  paste the failing assertion lines in the notes; green after the fix.
- `CheckoutFitTest`, `ZoneLayoutTest`, `GameLayoutPolicyTest`: root plan "Test plan".
- Existing tests unchanged and green; do not re-record captures here (T2).

## Done when
`./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build` green; `kosmo-detekt`
exit 0.

## Model
sonnet. Why: UI fix fully specified by the root plan, with its JVM and Robolectric tests; no stored format, no
cross-module change.

## Implementation notes
