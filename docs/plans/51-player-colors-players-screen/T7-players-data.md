# T7: Players data: list, rename, reset, merge

## Goal
Room reads and writes behind the Players screen, without a schema change. Root "Approach" 5.

## Files
- `app/.../history/PlayersDao.kt` (new): profile list with match count and latest `ended_at` (PM2), session ids
  of a profile, update name and key, update dates, move participations, delete profile.
- `app/.../history/PlayersRepository.kt` (new): `interface PlayersRepository` (`list()`, `rename(id, name)`,
  `reset(id, at)`, `merge(sourceId, targetId)`), each returning `DbResult`; `RoomPlayersRepository` using
  `ProfileRules` (T6); merge and rename in one `runInTransaction`.
- `app/.../history/HistoryDatabase.kt`: expose `PlayersDao` (no entity change; `app/schemas/.../1.json` unchanged).
- Tests: `PlayersRepositoryTest` (Robolectric), fake `FakePlayersRepository` for T9.

## Depends on
T6.

## Rules
PM2, PM3, PM4, PM5, PM10; PF1; AR9.

## Tests
- List: order by last use, counts and last end date, a profile with no match.
- Rename: new spelling, case-only, clash returns the target id and changes nothing; History shows the new name.
- Reset: reset date updated; sessions, legs, darts and players counts unchanged.
- Merge: participations moved, source gone, dates as PM4; refused on a shared match with nothing changed; a failure
  injected after the move rolls everything back.
- Closed database: `Failed`, no crash. No name in logs.
- `HistorySchemaTest` still green (schema version 1 unchanged).

## Done when
`./gradlew :app:testDebugUnitTest`, `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: DAO and repository over the existing schema, rules and tests specified.

## Implementation notes
