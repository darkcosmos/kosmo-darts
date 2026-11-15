# T26: End-screen heatmap and tab state

## Goal
The between-legs and end screens (X01 and Cricket) know their selected tab (Result or Leg heatmap) and the heatmap
of the last leg for the selected player, derived from the match journal. Root "Approach" 7, HM9, HM10, D9.

## Files
- `app/.../ui/app/AppState.kt`: `Screen.End` carries the `MatchJournal` (`CricketEnd` gets it in T6); each of
  `BetweenLegs`, `End`, `CricketBetweenLegs`, `CricketEnd` gets `endTab: EndTab` (`RESULT`, `LEG_HEATMAP`; default
  `RESULT`), `heatmapPlayer: Int` and `heatmapZone: BoardZone?`. Not saved (D9).
- The reducers that build these screens (`AppReducer.kt`, `CricketReducer.kt`): pass the journal; the default player
  comes from one named function `defaultHeatmapPlayer(journal, screen kind)` implementing N2 A (leg winner between
  legs, match winner at the end), injectable so that answer B is a one-line change (D7); new events
  `EndTabSelected(tab)`, `HeatmapPlayerSelected(index)`, `HeatmapZoneTapped(zone)`.
- `app/.../ui/between/BetweenLegsState.kt` and end state builders: expose `lastLegHeatmap` (T18) with
  `LastLeg.COMPLETED` between legs and `LastLeg.CURRENT` on the end screens (D6), the top 5 rows with percentages, the
  zone texts, and the player list (name, colour) for the selector, all computed here (review A6). No log line.
- Tests: `EndHeatmapStateTest.kt`, reducer cases.

## Depends on
T18 (and T6 for `CricketEnd.journal`).

## Rules
HM4, HM6 (band text, top 5), HM9, HM10, DJ2, DJ4, R9, R26, R29.

## Tests
- X01 and Cricket, 1 to 4 players: values match the last leg only, undone darts excluded.
- Between legs: a state whose `journal.currentLeg` is empty shows non-empty values equal to the last completed leg.
- End screen: values equal to the finished current leg.
- Top 5 order with ties and percentages asserted.
- `defaultHeatmapPlayer`: leg winner between legs, match winner at the end; replaced by a fake in one test.
- Every screen opens on `RESULT`; `EndTabSelected` switches; the player selection changes the values; a zone tap
  sets the band text, another tap replaces it, a player switch keeps the zone with that player's values (as a Profile filter change, T21).
- Tab, player and zone survive a rotation (AppState). After process death on the between-legs screen the journal is
  restored (R26), it has passed the R9 / R29 checks of the saved-game loader, the tab is `RESULT` and the default
  player is selected.
- "Next leg" from the Leg heatmap tab opens the next between-legs screen later on `RESULT` (fresh tab state).
- Rematch and New game from the end screen start a fresh match (no stale heatmap, tab back to `RESULT`).
- Solo: one player, no selector data.

## Done when
`./gradlew build` and `./gradlew :app:testDebugUnitTest` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: wiring of the journal and three selection events, tests specified.

## Implementation notes
- `ui/app/EndHeatmap.kt`: `EndTab`, `EndScreenKind` (`BETWEEN_LEGS`, `MATCH_END`), the injectable
  `HeatmapPlayerChoice` and `defaultHeatmapPlayer(kind, lastLegWinner, matchWinner)` (N2 A; B is a change of this one
  function), and the sealed `EndHeatmapScreen` implemented by the four screens (`journal`, `endTab`, `heatmapPlayer`,
  `heatmapZone`, `kind`, `legSetup`, `playerNames`, `playerColors`, `withHeatmap`). Deviation: the function takes the
  winners of the last completed leg and of the match (read from the match by the callers) instead of the journal,
  which would need a replay.
- Injection: `AppReducer(random, heatmapPlayer = ::defaultHeatmapPlayer)`, `onCricketGame(..., heatmapPlayer)`,
  `initialState(..., heatmapPlayer)` and the `toScreen(heatmapPlayer)` functions. The screens' own default constructor
  parameters use the real function, so `Screen.BetweenLegs(match, journal)` is already on `RESULT`, winner selected.
- Events `AppEvent.EndTabSelected`, `HeatmapPlayerSelected` (out-of-range index ignored), `HeatmapZoneTapped` (a
  second tap replaces the zone, a player switch keeps it); handled by `reduceHeatmap` after the screen's own events,
  so the abandon dialog stays modal. Fresh screens (next leg, rematch, new game, resume) get fresh fields.
- `Screen.End` now carries the journal; its `End(game)` secondary constructor moved to the test fixture
  `endScreen(game)` (`testing/ScreenFixtures.kt`), the other tests use `endScreen`.
- `ui/between/EndHeatmapState.kt`: `EndHeatmapScreen.heatmapState()` gives `EndHeatmapState(tab, players, section)`:
  `players` are `HeatmapPlayerChip`s (empty when solo) and `section` is the Profile `HeatmapSectionState` (zone shares,
  `topZones`, `selected`, `figureOf`, `isEmpty`, `missLabelRes`); T27 formats the texts with the `ZoneText.kt`
  helpers. `LastLeg.COMPLETED` between legs, `CURRENT` at the end.
- The D11 constant lives in T27 (`LegHeatmapTab.kt`): the reducer accepts every zone tap.
