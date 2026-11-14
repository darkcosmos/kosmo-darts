# T21: Profile Heatmap section (state)

## Goal
State of the Profile "Heatmap" section (named "Heatmap" in English and French, owner decision): mode filter, mode filters, period, heatmap, selected zone. Root "Rules", HM3,
HM6.

## Files
- `app/.../ui/profile/HeatmapSectionState.kt` (new): mode (ALL, X01, CRICKET), X01 and Cricket filters, period,
  `Heatmap` (T18), selected zone (or none: band "Touchez une zone"), miss label kind (HM4), top 5 rows (zone, darts,
  percentage) and the TalkBack zone texts with count and %, all computed here, never in a composable (review A6).
- `app/.../ui/profile/ProfileState.kt`, `app/.../ui/app/ProfileReducer.kt`, `ProfileEvent.kt`: section HEATMAP events
  (mode, filters, period, zone tapped).
- Tests: `HeatmapSectionStateTest.kt`, cases added to `ProfileReducerTest.kt`.

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
- Top 5: order by count, ties by zone order, exact percentages asserted (with misses in the total); fewer than 5
  zones hit gives fewer rows.
- No zone selected at open and after the selected zone falls to 0 darts: the "nothing selected" band state.
- TalkBack zone text includes the zone name, the count and the percentage.
- ST10: the ignored-matches count of the Profile also applies to this section's data.

## Done when
`./gradlew build` and `./gradlew :app:testDebugUnitTest` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: state holder with JVM tests, behavior specified.

## Implementation notes
