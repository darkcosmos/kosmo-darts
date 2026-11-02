# T6: Engine profile rules

## Goal
Pure rules of the Players screen in `:game-engine`, ready for the data layer. Root "Approach" 4.

## Files
- `game-engine/.../engine/profile/ProfileRules.kt` (new):
  - `ANONYMOUS_NAME_KEY = ""` (PM7);
  - `rename(newName, profileKey, otherKeys): RenameOutcome` (`Invalid(violation)`, `Renamed(name, key)`,
    `Clash(targetKey)`), validation through `PlayerListRules`;
  - `canMerge(sourceSessions, targetSessions): Boolean`; `mergedDates(source, target): ProfileDates`;
  - `deletionPlan(profileId, anonymousId, matches: Map<Long, List<Long>>): DeletionPlan` (sessions to delete,
    sessions to reassign);
  - `isLocked(nameKeys, savedGameNames): Boolean` (PM8).
- `.../engine/profile/RosterProfileUpdates.kt` (new): `Roster.withProfileRenamed`, `withProfileMerged`,
  `withProfileDeleted` (PM9), matching entries by name key.
- Tests: `ProfileRulesTest`, `RosterProfileUpdatesTest`.

## Depends on
T1.

## Rules
PM3, PM4, PM6, PM7, PM8, PM9; PF1.

## Tests
- Rename: blank, too long (21 code points), same key with another case (Renamed), key of another profile (Clash),
  surrounding spaces trimmed.
- Merge: disjoint sessions allowed, one shared session refused; dates: reset and last-used latest, creation earliest.
- Deletion plan: solo match deleted; match with another player reassigned; match with two players where the other
  is already anonymous deleted; match with the anonymous profile and a third player reassigned.
- Lock: a saved game name with another case locks the profile; no saved game never locks.
- Roster: rename keeps color; merge with target absent renames, with target present removes; delete of the only
  entry leaves one empty name with its color.

## Done when
`./gradlew :game-engine:test`, `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: rules PM3 to PM9 fully specified with the tests listed.

## Implementation notes
- Package `engine.profile`. `rename(newName, profileKey, otherKeys: Set<String>)`; `Invalid` carries the first `PlayerListError`.
- `canMerge` takes `Set<Long>` of match ids; `ProfileDates(resetAt, createdAt, lastUsedAt)` in epoch millis.
- `deletionPlan(profileId, anonymousId: Long?, matches: Map<matchId, List<profileId>>)`; `anonymousId` null before the
  anonymous profile exists. `isLocked(nameKeys: Set<String>, savedGameNames: List<String>)` compares by name key.
- Roster extensions take name keys: `withProfileRenamed(profileKey, newName)`, `withProfileMerged(sourceKey, targetName)`,
  `withProfileDeleted(profileKey)`; unmatched keys leave the roster unchanged.
