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
