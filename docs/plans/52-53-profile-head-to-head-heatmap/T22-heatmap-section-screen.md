# T22: Profile Heatmap section (screen)

## Goal
The "Heatmap" tab of the Profile (label "Heatmap" in English and in French, owner decision): filters, board with the chosen scale, legend, misses line, tap band, top 5 list,
empty state. Root "Screens and states", Profile.

## Files
- `app/.../ui/profile/HeatmapSection.kt` (new): reuses `Dartboard`, `HeatmapScale`, `HeatmapLegend`.
- `app/.../ui/board/ZoneBand.kt`, `ZoneText.kt` (new): the fixed band (zone name, darts, %) and zone names
  ("T20", "Simple 5" / "S5", "25", "Bull") in both languages; shared with T27. `ZoneBand` stays a self-contained
  composable (state in, no logic) so that T27 can switch it off with one constant (decision D11).
- `app/.../ui/profile/ProfileScreen.kt`: the third tab.
- Strings en and fr, one key per label: tab ("Heatmap" in both languages), filters, miss labels (EN "Miss" / "Miss or outside Cricket numbers",
  FR "Raté" / "Raté ou hors numéros Cricket", HM4), band with its "nothing selected" text ("Touch a zone" /
  "Touchez une zone"), top 5, empty; dart counts as `<plurals>`. No text built in a composable: the band and the top 5
  show the state's values (T21).
- Tests: `HeatmapSectionTest.kt`, `ZoneTextTest.kt`; captures in `ProfileScreenshotTest.kt`.

## Depends on
T13, T20, T21.

## Rules
HM1 to HM8, PS8.

## Tests
- Tap a zone shows the band; band and top 5 list readable by TalkBack; misses line with count and %.
- Band shows "Touchez une zone" before any tap.
- Captures (Pixel 7, portrait, landscape, 200 % font): empty, spread, concentrated, a zone selected, Cricket label
  (the long HM4 label wraps and is not cut at 200 %).

## Done when
`./gradlew build` and `./gradlew :app:testDebugUnitTest` green (captures recorded); `kosmo-detekt` exit 0.

## Model
sonnet. Why: screen from the plan with Compose tests and captures.

## Implementation notes
- `ui/profile/HeatmapSection.kt`: `HeatmapFilters` (mode chips, X01 or Cricket filters, period; reuses the now
  `internal` `FilterGroup`, `PeriodGroup` and label functions of `ProfileFilters.kt`) and `HeatmapSection(state, onEvent)`
  (board, band, misses line via `FigureRowView`, `HeatmapLegend`, top 5 list; HM7 text instead when `isEmpty`).
  `HeatmapTags` in `HeatmapTags.kt`. `ProfileScreen`: the heatmap is one lazy item in portrait and the right column in
  landscape; the placeholder (string, tag, `FigureBlock` branch) is gone.
- Deviations: `ZoneText.kt` lives in `ui/profile` (it formats `ZoneName`/`ZoneFigure`, which are there) and is
  resource based (`ZoneName.text`, `dartsText`, `ZoneFigure.detail`, `description`), so it serves composables and the
  TalkBack lambda alike; zone names are the T21 ones ("Triple 20", "Simple 5"), not "T20"/"S5". `ZoneBand`
  (`ui/board`, `ZoneBandTags`) takes `title` and nullable `detail` only: T27 switches it off by not calling it (D11).
  Added `HeatmapSectionState.zoneShares` (ratios for the colour scale, so no counting in the composable) with a test.
- Strings: `heatmap_filter_mode`, `heatmap_mode_all`, `heatmap_mode_cricket`, `heatmap_empty`, `heatmap_board_description`,
  `heatmap_top_title`, plural `heatmap_darts`, `heatmap_zone_detail`, `heatmap_zone_description` (en and fr).
- Tests: `HeatmapSectionTest` (HM2 to HM7, PS8), `ZoneTextTest`, `ProfileScreenTest` heatmap tab. Merged-semantics
  nodes (band, top rows) are read through their merged text. Captures `profile_heatmap_*` in `ProfileScreenshotTest`
  (empty, spread, concentrated, selected, Cricket label; portrait, landscape, 200 % font).
- Open (UX): in portrait the band sits under a 360 dp board, so at the top of the screen it is just below the fold
  (the Heatmap scrolls; the top 5 list is the reliable access, HM6). T27 may want the filters collapsed.
