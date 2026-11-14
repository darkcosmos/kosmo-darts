# T27: End-screen tabs and leg heatmap layout

## Goal
Add the tabs "Résultat" / "Leg heatmap" (owner N1) to the between-legs and end screens, X01 and Cricket, in portrait
and landscape on a Pixel 7. Root "Screens and states", between legs and end screens; HM9, HM10, D10, D11.

## Files
- `app/.../ui/board/ResultHeatmapTabs.kt` (new): tab row fixed at the top (48 dp, 42 dp tall allowed in landscape,
  labels wrap at 200 % font, TalkBack selected state), the selected tab content, and one shared action area outside
  the tab content (D10).
- `app/.../ui/board/LegHeatmapTab.kt` (new): "LEG n" title (M19 wording), player selector chips (colour strip and
  name, horizontal scroll, 48 dp, absent in solo), `Dartboard` with `HeatmapScale`, misses line with the HM4 label,
  legend, `ZoneBand` (T22) and top 5 list. Portrait: one scrolling column. Landscape: board at the left (as large as
  the height allows, never under 200 dp), right column scrolling on its own. The band is shown and zone taps are
  handled only when `END_SCREEN_ZONE_TAP_ENABLED` is true (D11, owner N3 A, may switch to B after Kosmo's test).
- `app/.../ui/between/BetweenLegsScreen.kt`, `app/.../ui/end/EndScreen.kt`,
  `app/.../ui/cricket/CricketBetweenLegsContent.kt`, `CricketEndContent.kt`: today's content becomes the "Résultat"
  tab unchanged; the main actions move into the shared action area at the places root "Screens and states" lists.
- Strings en and fr: tabs "Result" / « Résultat », "Leg heatmap" / « Leg heatmap », title; no new text in code.
- Tests: `ResultHeatmapTabsTest.kt`, `LegHeatmapTabTest.kt`; captures added to `X01ScreensScreenshotTest.kt` and
  `CricketScreenshotTest.kt`.

## Depends on
T22 (`ZoneBand`, `ZoneText`, after T20), T26.

## Rules
HM4 to HM10, M16, M17, M19, CR26, CR27, PS8; touch targets (42 dp tall allowed in landscape).

## Tests
- Tabs switch; the Result tab shows today's content (existing Compose tests of these screens stay green unchanged;
  their existing captures are re-recorded only for the added tab row).
- Selector switches players; band on tap; with the constant false (test helper parameter), taps do nothing, no band.
- The main action ("Next leg"; "Rematch" and "New game") is displayed and clickable in each tab, portrait and
  landscape, X01 and Cricket.
- Landscape Leg heatmap tab: the right column scrolls independently; the board is at least 200 dp.
- Captures (Pixel 7, portrait, landscape, 200 % font): Leg heatmap tab for X01 between legs 2 players, X01 end
  4 players with long names, Cricket end 3 players (combined miss label), solo X01 end; X01 end 4 players in landscape
  at 200 % font (right column scrolled); Result tab with the tab row, X01 between legs, portrait and landscape.

## Done when
`./gradlew build` and `./gradlew :app:testDebugUnitTest` green (captures recorded); `kosmo-detekt` exit 0.

## Model
sonnet. Why: layout from the plan with captures.

## Implementation notes
