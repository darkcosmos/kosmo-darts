# T26: End-screen heatmap state

## Goal
The between-legs and end screens (X01 and Cricket) know the heatmap of the last leg for the selected player, derived
from the match journal. Root "Approach" 7, HM9.

## Files
- `app/.../ui/app/AppState.kt`: `Screen.End` carries the `MatchJournal` (`CricketEnd` gets it in T6); each of
  `BetweenLegs`, `End`, `CricketBetweenLegs`, `CricketEnd` gets `heatmapPlayer: Int`.
- The reducers that build these screens (`AppReducer.kt`, `CricketReducer.kt`): pass the journal; default player per
  the N2 answer (leg winner / match winner if N2 A); new event `HeatmapPlayerSelected(index)`.
- `app/.../ui/between/BetweenLegsState.kt` and end state builders: expose `lastLegHeatmap` (T18) and the player list
  (name, colour) for the selector.
- Tests: `EndHeatmapStateTest.kt`, reducer cases.

## Depends on
T18 (and T6 for `CricketEnd.journal`).

## Rules
HM4, HM9, DJ2, DJ4, R26.

## Tests
- X01 and Cricket, 1 to 4 players: values match the last leg only, undone darts excluded.
- Selection changes the values; it survives a rotation (AppState); after process death on the between-legs screen the
  journal is restored (R26) and the default player is shown.
- Rematch from the end screen starts a fresh match (no stale heatmap).
- Solo: one player, no selector data.

## Done when
`./gradlew build` and `./gradlew :app:testDebugUnitTest` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: wiring of the journal and one selection event, tests specified.

## Implementation notes
