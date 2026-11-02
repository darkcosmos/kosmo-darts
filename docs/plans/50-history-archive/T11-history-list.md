# T11: History screen and home entry

## Goal
A "History" button on the home screen opens the list of archived matches with mode and player filters (HS1 to HS4,
HS6). Root plan "Screens and states", Home and History.

## Files
- `app/src/main/kotlin/.../ui/history/`: `HistoryState.kt` (pure state: filters, rows, loading/empty/no-match/
  failed, scroll anchor), `HistoryRows.kt` (pure mapping from DAO results to rows: date, mode and variant text keys,
  players with ARGB, winner text, legs/sets score only when the format has more than one leg, one merged TalkBack
  description), `HistoryScaffold.kt` (shared with T12: top bar with Back and its description, edge-to-edge insets and
  cutout, single column with a maximum content width in landscape, review U10), `HistoryScreen.kt`, `HistoryTags.kt`.
- `ui/app/AppState.kt` (`Screen.History`: filters and scroll anchor kept across rotation and process return, review
  U7), `AppReducer.kt` (navigation, Back to Home, "Show all matches", "Retry"), `KosmoDartsViewModel.kt` (reads on the
  read executor, ADR 0012), `ui/home/HomeScreen.kt` (button), `ui/KosmoDartsApp.kt`.
- `history/HistoryRepository.kt` (list and filter reads, `DbResult`).
- Strings `values/` and `values-fr/` (with `plurals`; dates in the device locale, review U9).
- Tests: `HistoryRowsTest`, `HistoryStateTest` (JVM), `HistoryScreenTest` (Compose), `HistoryScreenshotTest`.

## Depends on
T9.

## Rules
HS1 to HS4, HS6, HS7, PC9, AR8, AR9; "Screens and states" of the root plan (History row and filters).

## Tests
- Rows: order by end date desc; score shown only for multi-leg formats; winner; 1 to 4 players; date in the device
  locale.
- Filters: mode, player, both; "no match" state versus empty archive state; "Show all matches" resets both (HS4).
- Loading (HS7): nothing before 300 ms (fake clock), then the indicator with its description; never stuck.
- Read failure (AR9, HS7), `HistoryRepository` fake returning `Failed` and a closed database: failure state with
  message and "Retry"; "Retry" reads again and shows the rows.
- State save and restore (review U7): filters and scroll anchor survive a rotation and a process return (JVM test of
  the saved state); reducer test that Back from the detail restores them.
- Compose: home button opens History; Back returns home; tapping a row emits the detail event (target screen in T12);
  selected filter exposed as selected; filter chips and rows at least 48dp; one merged description per row naming
  date, mode, players, winner and score; winner given by text (review U4, U5).
- 1,000 rows: `LazyColumn` keys stable; the read time is logged in the notes (no timing assert). Same-thread executor
  and fake clock, no sleep or idling wait.
- Captures: loading, empty, read failure, filled (X01 and Cricket, 4 players with long names), no match, portrait,
  landscape, `fontScale = 2f`; home with the new button.

## Done when
`./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build` green; every new test runs on a
direct (same-thread) executor with a fake `Clock`, with no sleep and no idling wait; `kosmo-detekt` exit 0.

## Model
sonnet. Why: screen composed from the design system with state holders tested on the JVM.

## Implementation notes
- Pieces: `ui/history/` (`HistoryState`, `HistoryRows`, `HistoryScaffold` shared with T12, `HistoryScreen`,
  `HistoryFiltersBar`, `HistoryRowCard`, `HistoryTags`); `Screen.History` in `AppState` holds filters and scroll
  anchor, saved and restored with the app snapshot (read again after a process return); reads go through
  `Persistence` on the read executor with `DbResult`; `history/HistoryRepository.kt` does one light summary query.
- Row tap emits an event ignored until T12 adds the detail screen (`a tap on a match is ignored...` test).
- 1,000 matches: list read takes about 11 ms on the JVM (reported only); `LazyColumn` keys are `sessionId`.
- Captures recorded by Roborazzi (`history_*` PNGs in `app/build/outputs/roborazzi`): loading, empty, failed, filled,
  no match, portrait, landscape, fontScale 2, home button. Opened and checked: filled portrait and filled landscape
  at font 200; the others were not opened.
