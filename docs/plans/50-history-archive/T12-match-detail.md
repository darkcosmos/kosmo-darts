# T12: Match detail screen

## Goal
Tapping a match in History opens its read-only detail: summary, then leg by leg and visit by visit (HS5, HS6).
Root plan "Screens and states", Match detail.

## Files
- `app/src/main/kotlin/.../ui/history/`: `MatchDetailState.kt` (pure: summary built from the stored mode, variant
  and settings columns mapped to string resource ids, never by parsing `settings` text in a composable, review A7;
  duration; ranks, legs and sets; legs with set/leg headers, starter, winner; visits grouped from darts, voided
  removed, bust flag, visit total; loading and failed states), `MatchDetailScreen.kt` (in T11's `HistoryScaffold`;
  leg headers as headings, not pinned; visit rows: player name in his color, darts as text « T20 », « D16 », « Bull »,
  « 25 », « Raté », total, « BUST » as text; one TalkBack description per visit, Cricket marks read as on the game
  screen, review U8), tags.
- `ui/app/AppState.kt` (`Screen.MatchDetail(id)`, kept across rotation and process return), `AppReducer.kt` (Back
  returns to History with its filters and scroll anchor), `KosmoDartsViewModel.kt`, `history/HistoryRepository.kt`
  (one match read, `DbResult`).
- Strings `values/` and `values-fr/` (duration with `plurals`, "Bust", headers, read failure, "Retry"; review U9).
- Tests: `MatchDetailStateTest` (JVM), `MatchDetailScreenTest` (Compose), `MatchDetailScreenshotTest`.

## Depends on
T11.

## Rules
HS5, HS6, HS7, AR9, AR10, PC9, BC2 (coordinates are not shown).

## Tests
- Grouping: visits in order, voided darts absent, a visit whose darts are all voided absent, bust marked, X01 visit
  totals (bust visit total shown as bust), Cricket marks text as on the game screen.
- Duration formats (minutes, hours).
- Back keeps the History filters and scroll anchor; the open detail survives a rotation and a process return.
- Read failure (AR9, HS7), `HistoryRepository` fake returning `Failed` and a closed database: failure state with
  message and "Retry"; "Retry" reads again. Loading as HS7 (fake clock).
- Settings summary for each X01 finish mode and both Cricket variants with and without 25-Bull, from resources.
- Same-thread executor and fake clock, no sleep or idling wait.
- Captures: X01 2 sets of 3 legs, Cricket 4 players, read failure, portrait, landscape,
  `fontScale = 2f`.

## Done when
`./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: screen composed from the design system with a state holder tested on the JVM.

## Implementation notes
