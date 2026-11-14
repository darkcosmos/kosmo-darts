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
