# T27: End-screen heatmap layout

## Goal
Show the last-leg heatmap block on the between-legs and end screens, X01 and Cricket, in portrait and landscape on a
Pixel 7. Root "Screens and states", between legs and end screens.

## Files
- `app/.../ui/board/LegHeatmapBlock.kt` (new): title "BOARD · LEG n", player selector chips (colour strip and name,
  horizontal scroll, 48 dp, absent in solo), `Dartboard` with `HeatmapScale`, misses line with the HM4 label, legend,
  `ZoneBand` (if N3 A).
- `app/.../ui/between/BetweenLegsScreen.kt`, `app/.../ui/end/EndScreen.kt`,
  `app/.../ui/cricket/CricketBetweenLegsContent.kt`, `CricketEndContent.kt`: place the block per N1 (root "Screens and
  states"); the main button stays reachable without scrolling. Landscape: the third column scrolls on its own, with a
  minimum board size of 200 dp; at 200 % font the selector, misses line and band scroll under the board.
- Strings en and fr.
- Tests: `LegHeatmapBlockTest.kt`; captures added to `X01ScreensScreenshotTest.kt` and `CricketScreenshotTest.kt`.

## Depends on
T20, T26, and Kosmo's answers N1, N2, N3 (root "Open questions"). Do not start before they are recorded.

## Rules
HM4 to HM9, M16, M17, CR26, CR27, PS8; touch targets (42 dp tall allowed in landscape).

## Tests
- Selector switches players; band on tap; the main button is displayed and clickable in portrait and landscape.
- Captures (Pixel 7, portrait, landscape, 200 % font): X01 between legs 2 players, X01 end 4 players with long names,
  Cricket end 3 players (combined miss label), solo X01 end; plus X01 end 4 players in landscape at 200 % font (third
  column scrolled, board at least 200 dp).
- Landscape: the third column scrolls independently of the scoreboard column.

## Done when
`./gradlew build` and `./gradlew :app:testDebugUnitTest` green (captures recorded); `kosmo-detekt` exit 0.

## Model
sonnet. Why: layout from the plan with captures.

## Implementation notes
