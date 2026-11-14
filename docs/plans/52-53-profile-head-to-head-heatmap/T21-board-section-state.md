# T21: Profile Board section (state)

## Goal
State of the Profile "Board" section: mode filter, mode filters, period, heatmap, selected zone. Root "Rules", HM3,
HM6.

## Files
- `app/.../ui/profile/BoardSectionState.kt` (new): mode (ALL, X01, CRICKET), X01 and Cricket filters, period,
  `Heatmap` (T18), selected zone, miss label kind (HM4).
- `app/.../ui/profile/ProfileState.kt`, `app/.../ui/app/ProfileReducer.kt`, `ProfileEvent.kt`: section BOARD events
  (mode, filters, period, zone tapped).
- Tests: `BoardSectionStateTest.kt`, cases added to `ProfileReducerTest.kt`.

## Depends on
T11, T18.

## Rules
HM2, HM3, HM4, HM6, HM7, ST2, ST4.

## Tests
- Mode ALL hides the mode filters; switching mode keeps the period.
- Miss label: X01 only gives "Miss"; Cricket or ALL with Cricket darts gives the combined label.
- Tap selects a zone; a second tap on another zone replaces it; a filter change keeps the zone selected with its new
  values.
- Empty selection gives the empty state.
- Top 5 zones exposed for TalkBack.

## Done when
`./gradlew build` and `./gradlew :app:testDebugUnitTest` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: state holder with JVM tests, behavior specified.

## Implementation notes
