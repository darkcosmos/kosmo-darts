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
M15, CS8, design system 6.3, U10. Decisions D1 to D8 (D2 confirmed; D6 amended and D9 added: non-linear heights, scale found by
measuring candidates, 1.0 keeps pre-fix sizes). No string, no touch target change; tokens only
(`KosmoTheme.typography`, `fixedSp`, `CHECKOUT_BANNER_MIN`).

## Tests
- Measurement: a temporary probe (not committed) logging the values of root plan "Approach" 1; write them and the
  confirmed cause in the notes, plus the pre-fix chip and text sizes at scale 1.0 (D8) and whether heights are
  linear in the font scale (D6). Unknown cause or non-linear heights: stop, report to main (open question 1).
- `CheckoutBannerFitTest`: the configurations, edge cases, scale 1.0 and undo cases of root plan "Test plan"; run
  the same committed file before the fix and paste the failing lines (`fr-land`, 2.0) in the notes; never loosen
  an assertion afterwards; green after the fix.
- Floor hit with overflow in any configuration (D7): stop, report to main.
- `CheckoutFitTest`, `ZoneLayoutTest`, `GameLayoutPolicyTest`: root plan "Test plan".
- Existing tests unchanged and green; do not re-record captures here (T2).

## Done when
`./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build` green; `kosmo-detekt`
exit 0.

## Model
sonnet. Why: UI fix fully specified by the root plan, with its JVM and Robolectric tests; no stored format, no
cross-module change.

## Implementation notes

### Measurements (dp; Robolectric X01 301, 4 players, Ann at 121 then T20 T11 D14; on 7441ae3)
Causes a, b and c confirmed. Heights are not linear in the font scale (name row x1.33, line x1.67 at 2.0; the line
wraps to 3 lines at 2.0, width ~235 dp), hence D6 amended.

| Config | Scale | Zone | Chip | Name row | Line |
|---|---|---|---|---|---|
| fr-land (default) | 1.0 | 202 | 36 | 44 | 28 |
| fr-land | 1.3 | 208 | 34 | 46 | 34 |
| fr-land | 1.5 | 210 | 32 | 46 | 38 |
| fr-land | 2.0 | 223 | 0 (invisible) | 58 | 79 |
| 800x360 land xxhdpi | 1.0 | 231.7 | 65.3 | 44 | 28.3 |
| 800x360 land xxhdpi | 2.0 | 222.7 | 22.7 (text 14.7 = floor 12 sp) | 58.7 | 47.3 |
| 640x360 land xhdpi | 2.0 | 222.5 | 22.5 | 59 | 47 |
| 360x640 port xhdpi | 1.0 | 202 | 35 | - | - |
| 360x640 port xhdpi | 2.0 | 264.5 | 64 | 59 | 47 |

Dart slots: 46 dp.
