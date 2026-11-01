# T12: Match detail screen

## Goal
Tapping a match in History opens its read-only detail: summary, then leg by leg and visit by visit (HS5, HS6).
Root plan "Screens and states", Match detail.

## Files
- `app/src/main/kotlin/.../ui/history/`: `MatchDetailState.kt` (pure: summary with settings text, duration or "—",
  ranks, legs and sets; legs with set/leg headers, starter, winner; visits grouped from darts, voided removed, bust
  flag, visit total), `MatchDetailScreen.kt`, tags.
- `ui/app/AppState.kt` (`Screen.MatchDetail(id)`), `AppReducer.kt` (Back returns to History with its filters),
  `KosmoDartsViewModel.kt`, `history/HistoryRepository.kt` (one match read).
- Strings `values/` and `values-fr/` (duration, "Bust", "Darts not recorded for this leg", headers).
- Tests: `MatchDetailStateTest` (JVM), `MatchDetailScreenTest` (Compose), `MatchDetailScreenshotTest`.

## Depends on
T11.

## Rules
HS5, HS6, AR7, PC9, BC2 (coordinates are not shown).

## Tests
- Grouping: visits in order, voided darts absent, a visit whose darts are all voided absent, bust marked, X01 visit
  totals (bust visit total shown as bust), Cricket marks text as on the game screen.
- Duration formats (minutes, hours) and "—" without start; legacy leg line.
- Back keeps the History filters.
- Captures: X01 2 sets of 3 legs, Cricket 4 players, legacy match, portrait, landscape, `fontScale = 2f`.

## Done when
`./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: screen composed from the design system with a state holder tested on the JVM.

## Implementation notes
