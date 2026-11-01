# T9: Archive at the confirmed win, failure and AR4

## Goal
Confirming the match win archives it, then deletes the saved game; a failure keeps the save and shows a short
message; an already archived save is cleaned at launch (AR1 to AR4, D4, D9). Root plan "Approach" 7.

## Files
- `app/src/main/kotlin/.../ui/app/`: `AppState.kt` (archive status on the X01 game and Cricket game screens; events
  `ArchiveSucceeded`, `ArchiveFailed`), `AppReducer.kt`, `CricketReducer.kt`, `PersistenceEffect.kt`
  (`ArchiveMatch(archived, colors)`), `KosmoDartsViewModel.kt`.
- `app/src/main/kotlin/.../persistence/Persistence.kt` (archive request: archive, then delete on success only; launch
  check AR4); `history/ArchiveRepository.kt` call site; colors from `ui/theme/PlayerPalette.kt` as ARGB; app version
  from `PackageManager`.
- `ui/game/GameScreen.kt`, `ui/cricket/CricketGameScreen.kt`: disabled confirm while archiving, message line.
- Strings `values/` and `values-fr/`: archive failure message (short; FR: « Le match n'a pas pu être enregistré dans
  l'historique. »).
- Tests: `AppReducerArchiveTest`, `ArchiveFlowTest` (Robolectric), captures in `PersistenceScreenshotTest`.

## Depends on
T6, T7, T8.

## Rules
AR1 to AR4, AR7, R6, R9, R14, CR25, PC9.

## Tests
- Confirm: effect `ArchiveMatch` then nothing else until `ArchiveSucceeded` (End screen, as today); `ArchiveFailed`
  keeps the zone, shows the message, re-enables confirm; retry archives once.
- Queue order: archive before delete; failing repository keeps the saved file; closed database fails cleanly.
- Abandon, "New game" then "Delete", unreadable save: no archive call (AR1).
- Launch with a save whose id is archived: deleted, no resume; with a pre-update save: archived on confirm (AR7).
- Captures X01 and Cricket failure, portrait, landscape, `fontScale = 2f`.

## Done when
`./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: effect and reducer wiring with specified rules and tests.

## Implementation notes
