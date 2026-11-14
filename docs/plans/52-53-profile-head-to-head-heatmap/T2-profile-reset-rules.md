# T2: Merge reset date and "really reset"

## Goal
The merge computes the reset date with the owner rule Q1 B, and the engine says whether a profile was really reset.
Root "Rules", PM4 and ST9.

## Files
- `game-engine/.../engine/profile/ProfileRules.kt`: the existing `mergedDates(source, target)` (PM4: latest real reset of the
  two, else the merged
  creation date; creation earliest; last used latest); `isReallyReset(dates: ProfileDates): Boolean` (ST9).
- `game-engine/src/test/.../engine/profile/ProfileRulesTest.kt`: update the merge date cases, add ST9 cases.
- `app/src/test/.../history/PlayersRepositoryTest.kt`: the merge date assertions follow PM4.

## Depends on
T1.

## Rules
PM4, ST9.

## Tests
- Neither reset: reset date = earliest creation (the owner example: January profile merged with June profile keeps
  January).
- Only the source really reset, only the target, both (the later reset wins).
- A real reset earlier than the other profile's creation: the real reset still wins (rule text, B).
- ST9: reset equal to creation is false; later is true; merged profile with no real reset is false.
- Repository: merge through Room stores the PM4 dates.

## Done when
`./gradlew :game-engine:test`, `./gradlew build` and `./gradlew :app:testDebugUnitTest` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: small specified engine rule with its tests.

## Implementation notes
- No deviation. `ProfileRules.mergedDates` now keeps the latest real reset (reset > creation) else the merged creation
  date; `ProfileRules.isReallyReset(dates)` is ST9, for the Profile header "Since always" / "Since <date>".
- The repository merge needed no code change (it already calls `mergedDates`); its tests now assert PM4, including a
  never-reset merge.
