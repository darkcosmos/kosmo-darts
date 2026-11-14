# T9: Profile data loader

## Goal
Load every archived match of a profile, with all players and all darts, and map it to `StatsMatch`. No schema change.
Root "Approach" 3, "Data read, stored or exposed".

## Files
- `app/.../history/StatsDao.kt` (new): queries joining `match_player.profile_id` to sessions, players, legs and darts
  of those sessions, ordered by leg ordinal, visit, rank; profile row (dates).
- `app/.../history/ProfileStatsRepository.kt` (new): `load(profileId): DbResult<ProfileStatsData>` (profile name,
  dates, `List<StatsMatch>`); also `loadPair(a, b)` is left to T24.
- `app/.../history/HistoryDatabase.kt`: expose the DAO (no version change).
- Mapping reuses `ArchiveMapper` decoding of settings where possible.
- Tests: `app/src/test/.../history/ProfileStatsRepositoryTest.kt` (Robolectric, in-memory database).

## Depends on
T3.

## Rules
AR5, AR6, AR9, ST7, PM7.

## Tests
- A profile in solo, 2-, 3- and 4-player matches: every match loaded with every player's darts, voided darts present
  (filtered later by the engine).
- A profile with no match; an unknown profile id gives a failure, not a crash.
- The anonymous profile is never loaded as a Profile.
- Read failure gives `DbResult` failure (AR9).
- Loading 1,000 generated matches stays under 1 s on Robolectric with the existing indices; if not, stop and report
  (an index would be a schema change).

## Done when
`./gradlew build` and `./gradlew :app:testDebugUnitTest` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: DAO and mapper over the existing schema.

## Implementation notes
