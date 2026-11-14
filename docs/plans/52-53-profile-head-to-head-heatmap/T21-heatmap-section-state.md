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
- `ui/profile/HeatmapSectionState.kt`: `HeatmapMode` (ALL, X01, CRICKET), `HeatmapSelections(mode, x01Filter,
  cricketFilter, period, selectedZone)` with `statsFilter` and `showsModeFilters` (filters kept per mode, one period
  shared), `HeatmapFigures(matchCount, heatmap)` (T18 `Heatmap`), `ZoneName(resId, number?)` + `BoardZone.displayName()`,
  `ZoneFigure(zone, name, count, percent 0..100 unrounded)`, `HeatmapSectionState(selections, figures)` with `isEmpty`
  (HM7), `missLabelRes` (HM4), `missPercent`, `topZones` (HM6), `selected` (null: show `BAND_NONE_RES`), `figureOf(zone)`
  (TalkBack text of any zone). The screen (T22) formats the texts with the resource ids and `heatmapPercentText`.
- `ProfileState`: `heatmapSelections`, `heatmap: HeatmapSectionState?`; `ProfileQuery` gained `heatmapFilter` and
  `heatmapPeriod`, `ProfileFigures` gained `heatmap` (computed in the same second effect, ST2 applies through `select`,
  ST10 by the validated data). Top-level `heatmapSelected(selections, at)` (recomputes only when the engine filter or
  the period changes; a filter of the mode not in play, or a zone, never recomputes) and `heatmapZoneTapped(zone)`;
  top-level because `ProfileState` is at the detekt function limit. Events: `HeatmapModeSelected`,
  `HeatmapX01FilterSelected`, `HeatmapCricketFilterSelected`, `HeatmapPeriodSelected`, `HeatmapZoneTapped`.
- Decisions: a tap always selects the zone, even with 0 darts (band shows 0 darts, 0 %); a new computation drops a
  selected zone that has 0 darts (back to "Touchez une zone"); an empty selection (no dart) shows nothing selected.
  `isSelectionEmpty` for HEATMAP is "no match for the filters" (PS6 notice); `HeatmapSectionState.isEmpty` is HM7 "no
  dart". Strings added (en, fr): `heatmap_zone_*`, `heatmap_miss`, `heatmap_miss_cricket`, `heatmap_band_none`; the mode
  labels (All modes, X01, Cricket) are left to T22.
- Tests: `HeatmapSectionStateTest` (HM3, HM4, HM6, HM7, ST10), three cases in `ProfileReducerTest`.
