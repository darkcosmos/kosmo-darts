# T9: Players state holder and wiring

## Goal
Navigation, state and effects of the Players screen, testable on the JVM, with no composable yet. Root "Approach" 6
and "Screens and states", Players.

## Files
- `app/.../ui/players/PlayersState.kt` (new): load state (loading, failed, list), expanded profile, panel (`Rename`
  with text and error, `MergePick`, `MergeConfirm(target)`, `ResetConfirm`, `DeleteConfirm(count)`), message.
- `app/.../ui/app/AppState.kt`: `Screen.Players(home, players)`, events `PlayersTapped`, profile tapped, panel
  actions, results (`PlayersLoaded`, `RenameDone`, ...).
- `app/.../ui/app/PlayersReducer.kt` (new), `AppReducer.kt`: back to home; PM8 lock from
  `Home.resumableGame` names through `ProfileRules.isLocked`.
- `app/.../ui/app/PersistenceEffect.kt`, `persistence/Persistence.kt`: effects on the executor calling
  `PlayersRepository`, then the PM9 last setup rewrite with `RosterProfileUpdates`.
- Tests: `PlayersStateTest`, `PlayersReducerTest`, `PlayersEffectTest` with `FakePlayersRepository`.

## Depends on
T8, T4.

## Rules
PM2 to PM10.

## Tests
- Load success, empty, failure and retry.
- Rename: invalid, clash leading to the merge confirmation with source and target (Q6), success reloads.
- Merge refusal message on a shared match; reset and delete confirmations; delete shows the count.
- Lock: a profile of the saved game cannot be renamed, merged (source or target) or deleted; reset allowed.
- Last setup after rename, merge (target present or not), delete (only entry); write failure ignored.
- Database failure keeps the panel open with the message (PM10).
- After a reload, an expanded profile that no longer exists (merged or deleted) collapses, no panel left open
  (review T5). The delete result carries the count recomputed in the transaction.
- PM8 message names the player and says what to do (U5): "Kevin is in the game in progress. Finish or abandon it
  first."

## Done when
`./gradlew :app:testDebugUnitTest`, `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: state holder and effect wiring with JVM tests, rules specified.

## Implementation notes

- State holder `ui/players/PlayersState.kt` (types, class, helpers), `PlayersActions.kt` (user actions) and `PlayersAnswers.kt` (`answered`), split as extension functions to stay under the detekt function limits. Events are in `ui/app/PlayersEvent.kt`, the reducer in `ui/app/PlayersReducer.kt`, the effect runner and the two effect diffs in `ui/app/PlayersEffects.kt`.
- Request-in-state: the reducer sets `PlayersState.request`, `persistenceEffects` emits `RunPlayersRequest` when it changes, the answer returns as `PlayersEvent.Answered` and clears it. User actions are ignored while a request is pending. DB access is `Persistence.playersAccess` (`persistence/PlayersPersistence.kt`): reads (list, deletion count) on the read executor, writes on the save queue; `KosmoDartsApplication` passes `RoomPlayersRepository`.
- PM9 is done in the reducer (`reducePlayersAnswer`): a successful rename, merge or delete rewrites `state.lastSetup` via `RosterProfileUpdates`, even if the user left Players; the existing diff then emits `SaveLastSetup`.
- Deviation: deleting the only player leaves one nameless roster entry. `SharedRoster`, `LastSetupCodec` and `SetupState.prefilled` now accept a single empty name (`prefilled` builds from `lastSetups.roster` directly). Added `LastSetups.withRosterUpdated`.
- `Deleted(DbResult<Int>)` carries the erased count but nothing displays it (no message specified).
- For T10: `KosmoDartsApp.kt` has `is Screen.Players -> Unit` as a placeholder to replace by the screen; add the home button sending `AppEvent.PlayersTapped`; strings (en and fr) for `PlayersMessage.InProgress(playerName)` ("Kevin is in the game in progress. Finish or abandon it first."), `SharedMatch` and `DatabaseError`.
