# T11: History screen and home entry

## Goal
A "History" button on the home screen opens the list of archived matches with mode and player filters (HS1 to HS4,
HS6). Root plan "Screens and states", Home and History.

## Files
- `app/src/main/kotlin/.../ui/history/`: `HistoryState.kt` (pure state: filters, rows, loading/empty/no-match),
  `HistoryRows.kt` (pure mapping from DAO results to rows: date, mode and variant text keys, players with slot or
  ARGB, winner, legs/sets score only when the format has more than one leg), `HistoryScreen.kt`, `HistoryTags.kt`.
- `ui/app/AppState.kt` (`Screen.History`), `AppReducer.kt` (navigation, Back to Home), `KosmoDartsViewModel.kt`
  (reads on the read executor, ADR 0012), `ui/home/HomeScreen.kt` (button), `ui/KosmoDartsApp.kt`.
- `history/HistoryRepository.kt` (list and filter reads).
- Strings `values/` and `values-fr/`.
- Tests: `HistoryRowsTest`, `HistoryStateTest` (JVM), `HistoryScreenTest` (Compose), `HistoryScreenshotTest`.

## Depends on
T9.

## Rules
HS1 to HS4, HS6, PC9, AR8.

## Tests
- Rows: order by end date desc; score shown only for multi-leg formats; winner; 1 to 4 players; date in the device
  locale.
- Filters: mode, player, both; "no match" state versus empty archive state.
- Compose: home button opens History; Back returns home; tapping a row emits the detail event (target screen in T12).
- 1,000 rows: `LazyColumn` keys stable; the read time is logged in the notes.
- Captures: empty, filled (X01 and Cricket, 4 players), no match, portrait, landscape, `fontScale = 2f`; home with
  the new button.

## Done when
`./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: screen composed from the design system with state holders tested on the JVM.

## Implementation notes
