# T22: Profile Board section (screen)

## Goal
The "Board" tab of the Profile: filters, board with the chosen scale, legend, misses line, tap band, top 5 list,
empty state. Root "Screens and states", Profile.

## Files
- `app/.../ui/profile/BoardSection.kt` (new): reuses `Dartboard`, `HeatmapScale`, `HeatmapLegend`.
- `app/.../ui/board/ZoneBand.kt`, `ZoneText.kt` (new): the fixed band (zone name, darts, %) and zone names
  ("T20", "Simple 5" / "S5", "25", "Bull") in both languages; shared with T27.
- `app/.../ui/profile/ProfileScreen.kt`: the third tab.
- Strings en and fr: tab, filters, miss labels ("Ratés" / "Ratés ou hors numéros Cricket"), band, top 5, empty.
- Tests: `BoardSectionTest.kt`, `ZoneTextTest.kt`; captures in `ProfileScreenshotTest.kt`.

## Depends on
T13, T20, T21.

## Rules
HM1 to HM8, PS8.

## Tests
- Tap a zone shows the band; band and top 5 list readable by TalkBack; misses line with count and %.
- Captures (Pixel 7, portrait, landscape, 200 % font): empty, spread, concentrated, a zone selected, Cricket label.

## Done when
`./gradlew build` and `./gradlew :app:testDebugUnitTest` green (captures recorded); `kosmo-detekt` exit 0.

## Model
sonnet. Why: screen from the plan with Compose tests and captures.

## Implementation notes
