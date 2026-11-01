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
HS5, HS6, HS7, AR9, AR10, PC9, BC2 (coordinates are not shown), D31.

## Tests
- Grouping: visits in order, voided darts absent, a visit whose darts are all voided absent, bust marked, X01 visit
  totals (bust visit total shown as bust), Cricket marks text as on the game screen.
- Duration formats (minutes, hours), from `started_at` and `ended_at`, both always present (D31): no "unknown
  duration" branch.
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
- `MatchDetailState` (pure) builds the summary from stored columns; `MatchDetailText` maps them to string resources
  and the TalkBack visit text (Cricket marks worded as on the game screen, via `CricketMarks` in `:game-engine`).
- `MatchDetailScreen` uses T11's `HistoryScaffold`; leg headings (« Set 2 · Leg 3 ») are headings, NOT pinned.
  The list scrolls; read-only, no delete, no popup. Failure state shows a message and "Retry".
- `Screen.MatchDetail(id)` lives in `AppState`; Back (`AppReducer`) restores the History filters and scroll anchor.
- Captures in `app/build/outputs/roborazzi/detail_*.png` (X01, Cricket, failure; portrait, landscape, fontScale 2).
  Viewed: X01 landscape font 200 (viewport clips the summary: it is a static capture of a scrolling list, Back is in
  the top bar) and Cricket portrait FR. The other PNGs were not opened.
