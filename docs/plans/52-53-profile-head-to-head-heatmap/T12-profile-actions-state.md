# T12: Actions moved into the Profile (state)

## Goal
Rename, merge, reset and delete move from the Players state to the Profile state, unchanged in behavior, with the new
outcomes of PS7. Root "Approach" 5.

## Files
- `app/.../ui/profile/ProfileActions.kt`, `ProfileAnswers.kt` (moved from `ui/players/PlayersActions.kt`,
  `PlayersAnswers.kt`): panels, requests, answers, messages; `isManageOpen` (PS2).
- `app/.../ui/players/PlayersState.kt`: list only (load, empty, failure); row tap event opens the Profile.
- `app/.../ui/app/PlayersReducer.kt`, `PlayersEffects.kt`, `ProfileReducer.kt`: requests run from the Profile; PM9
  last setup update kept; after merge (shown profile = source) the target's Profile replaces the current one; after
  delete, Back to the parent reloaded, or to the History list when the parent is a detail of an erased match.
- Tests moved and adapted: `PlayersStateTest`, `PlayersReducerTest`, `PlayersEffectTest` split into Players (list)
  and Profile (actions) tests; no test case dropped.

## Depends on
T11.

## Rules
PM2, PM3 to PM10, PS7.

## Tests
- Every existing action test, now from the Profile (rename invalid, clash to merge, merge refusal, reset, delete
  count, PM8 lock, PM9, PM10).
- Merge from source lands on the target's Profile; merge from target stays and reloads.
- Delete from a Profile opened from Players, from History detail (match kept), from a detail of an erased solo match
  (lands on History list).
- Reset reloads the figures and the since label.

## Done when
`./gradlew build` and `./gradlew :app:testDebugUnitTest` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: move of tested code plus specified outcomes.

## Implementation notes
- Moved to `ui/profile/`: `ProfileActions.kt`, `ProfileAnswers.kt`, `ProfileActionsPanel.kt` (panel composable moved
  as is, not yet shown by any screen). `PlayersState` is the list only; a row tap raises `PlayersEvent.ProfileTapped`.
- `ProfileEvent` carries the action events and `Answered(profileId, request, answer)`; `ProfileEffect` gained
  `RunProfileRequest` and `LoadProfileRoster` (the profiles list the actions need, read with each Profile read).
- `ui/app/ProfileActionsReducer.kt`: PS7 exits. Merge with the shown profile as source: the target's Profile replaces
  it over the same parent, read again; merge into the shown profile: it stays and reads again. Delete: back to the
  parent, reloaded; if the parent is the detail of a match the delete erased, to the History list (or the nearest
  non-detail screen). PM9 last setup is updated whatever the screen on top.
- `playersOf` (PlayersEffects) now looks through the stack, so a Players list reloaded behind a Profile reads again.
- `PlayersFlowTest` is `@Ignore`d (cases kept): its taps need the actions on the Profile screen, which T13/T15
  provide. T15 must move it to the Profile and remove the `@Ignore`.
