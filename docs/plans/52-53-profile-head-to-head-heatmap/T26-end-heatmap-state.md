# T26: End-screen heatmap state

## Goal
The between-legs and end screens (X01 and Cricket) know the heatmap of the last leg for the selected player, derived
from the match journal. Root "Approach" 7, HM9.

## Files
- `app/.../ui/app/AppState.kt`: `Screen.End` carries the `MatchJournal` (`CricketEnd` gets it in T6); each of
  `BetweenLegs`, `End`, `CricketBetweenLegs`, `CricketEnd` gets `heatmapPlayer: Int`.
- The reducers that build these screens (`AppReducer.kt`, `CricketReducer.kt`): pass the journal; the default player
  comes from one named function `defaultHeatmapPlayer(journal, screen kind)` implementing N2 A (leg winner between
  legs, match winner at the end), injectable so that answer B is a one-line change (decision D7; N2 still open for
  Kosmo); new event `HeatmapPlayerSelected(index)`.
- `app/.../ui/between/BetweenLegsState.kt` and end state builders: expose `lastLegHeatmap` (T18) with
  `LastLeg.COMPLETED` between legs and `LastLeg.CURRENT` on the end screens (D6), the top 5 rows with percentages, the
  zone texts, and the player list (name, colour) for the selector, all computed here (review A6). No log line.
- Tests: `EndHeatmapStateTest.kt`, reducer cases.

## Depends on
T18 (and T6 for `CricketEnd.journal`).

## Rules
HM4, HM6 (top 5), HM9, DJ2, DJ4, R9, R26, R29.

## Tests
- X01 and Cricket, 1 to 4 players: values match the last leg only, undone darts excluded.
- Between legs: a state whose `journal.currentLeg` is empty shows non-empty values equal to the last completed leg.
- End screen: values equal to the finished current leg.
- Top 5 order with ties and percentages asserted.
- `defaultHeatmapPlayer`: leg winner between legs, match winner at the end; replaced by a fake in one test.
- Selection changes the values; it survives a rotation (AppState); after process death on the between-legs screen the
  journal is restored (R26), it has passed the R9 / R29 checks of the saved-game loader, and the default player is
  shown.
- Rematch from the end screen starts a fresh match (no stale heatmap).
- Solo: one player, no selector data.

## Done when
`./gradlew build` and `./gradlew :app:testDebugUnitTest` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: wiring of the journal and one selection event, tests specified.

## Implementation notes
