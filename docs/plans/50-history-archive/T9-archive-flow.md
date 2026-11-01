# T9: Archive at the confirmed win, failure and AR4

## Goal
Confirming the match win archives it, then deletes the saved game; a failure keeps the save and shows a short
message; an already archived save is cleaned at launch (AR1 to AR4, D4, D9). Root plan "Approach" 7.

## Files
- `app/src/main/kotlin/.../ui/app/`: `AppState.kt` (archive status on the X01 game and Cricket game screens; events
  `ArchiveSucceeded`, `ArchiveFailed`), `AppReducer.kt`, `CricketReducer.kt`, `PersistenceEffect.kt`
  (`ArchiveMatch(archived, colors)`), `KosmoDartsViewModel.kt`.
- `app/src/main/kotlin/.../history/MatchArchiveRequest.kt` (archive, then delete the save only on `ARCHIVED` or
  `ALREADY_ARCHIVED`) and `ArchiveCheck.kt` (launch AR4: `Archived`, `NotArchived`, `Failed`; only `Archived`
  deletes); `persistence/Persistence.kt` only sequences them (review A5). Colors from `ui/theme/PlayerPalette.kt` as
  ARGB; app version from `PackageManager`, a non-null `String` (`app_version` is `NOT NULL`, D31: the build file
  sets `versionName`, so its absence is a programming error, not a stored null).
- `ui/game/GameScreen.kt`, `ui/cricket/CricketGameScreen.kt`: while archiving, confirm disabled with the label
  « Enregistrement… » and a description saying why; failure message as text in a polite live region (review U2).
- Strings `values/` and `values-fr/`: archiving label and description, failure message (short; FR: « Le match n'a
  pas pu être enregistré dans l'historique. »).
- Tests: `AppReducerArchiveTest`, `ArchiveFlowTest` (Robolectric), captures in `PersistenceScreenshotTest`.

## Depends on
T6, T7, T8.

## Rules
AR1 to AR4, AR9, R2, R6, R9, R14, CR25, PC9; ADR 0012 "Failure contract".

## Tests
- Confirm: effect `ArchiveMatch` then nothing else until `ArchiveSucceeded` (End screen, as today); `ArchiveFailed`
  keeps the zone, shows the message, re-enables confirm; retry archives once.
- Queue order: archive before delete; failing repository keeps the saved file; closed database fails cleanly.
- Abandon, "New game" then "Delete", unreadable save: no archive call (AR1).
- Launch with a save whose id is archived: deleted, no resume; with a save whose id is not archived: offered (R2),
  then archived on confirm with its start time and every leg's darts.
- Named tests (review T3), in `AppReducerArchiveTest` unless stated:
  - `secondConfirmWhileArchivingEmitsNothing`: a second `FinishGameTapped` while archiving emits no second
    `ArchiveMatch`;
  - `retryAfterFailureAlreadyArchivedDeletesSave` (`ArchiveFlowTest`): first write committed but reported failed,
    retry returns `ALREADY_ARCHIVED`: the save is deleted and the End screen shows;
  - `retryAfterFailureDeletesOnlyOnSuccess` (`ArchiveFlowTest`, review S1/S3 note): a retry that fails again keeps
    the save; deletion only after `ARCHIVED` or `ALREADY_ARCHIVED`;
  - `processDeathWhileArchivingResumesOnZone`: the saved state has no archiving flag; the resumed X01 game shows the
    CHECKOUT zone, the Cricket game its win zone, confirm enabled, and the retry archives;
  - `cutThroatMatchArchives` and `soloMatchArchives`: the same flow for Cricket CUT_THROAT and a 1-player match.
- Database failures (AR9, review S1), fake returning `Failed` and closed database: the AR4 launch check fails, the
  save is NOT deleted and is offered (R2); the archive fails (AR3).
- Tests run with a same-thread executor and a fixed clock: no sleep, no idling wait.
- Captures X01 and Cricket archiving and failure, portrait, landscape, `fontScale = 2f`.

## Done when
`./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: effect and reducer wiring with specified rules and tests.

## Implementation notes
- Flow: confirming the win moves `ArchiveStatus` Idle to Saving(archived); `persistenceEffects` emits `ArchiveMatch(archived, colorsArgb)` on that transition. The answer returns as `AppEvent.ArchiveSucceeded` / `ArchiveFailed` (posted on the main looper by `KosmoDartsViewModel`). Success goes to the end screen and deletes the save; failure keeps the save, sets `Failed` and shows a message; Confirm retries (a retry on an already archived match counts as success and deletes the save).
- `DeleteGame` is suppressed when the previous state was Saving; abandon still emits it. Other events (Back, second Confirm) are ignored while Saving.
- Code: `history/MatchArchiveRequest`, `history/ArchiveCheck` (AR4 launch check, deletes the save only on a positive "archived"), `history/ArchiveGateway` (seam; `ArchiveRepository` implements it and adds `isArchived`), `ui/game/ArchiveStatus`, `ui/game/ArchiveUi.kt` (`confirmAction`, `ArchiveFailedMessage`), reducers, `PersistenceEffect`, `Persistence.archiveMatch` (answers false without an archive request).
- UI deviations: the failure message sits above the action bar; Cricket landscape gets a 36dp message line that shrinks the board band; labels are mixed case ("Saving...", "Enregistrement..."); `BarAction` got a `description`.
- `KosmoDartsApplication` is now `open` with `protected open fun createPersistence()`. Tests: `TestKosmoDartsApplication` + `src/test/resources/robolectric.properties` (direct executor, instant gateway); `FakeArchiveGateway`; `AppProcess` has its own save thread and `settle()` (awaitIdle + main looper idle). Screen tests that confirm a win must settle.
- A ViewModel test checks the injected id and clock at Start. The 10 new captures in `PersistenceScreenshotTest` are record-only: the PNGs were not inspected.
