# T8: Profiles: creation and launch migration

## Goal
Profiles are created and touched at Start and Rematch, and created on launch from the last setup and the saved game
(PF1, PF2, PF4). D1, D8, D12.

## Files
- `game-engine/src/main/kotlin/.../engine/player/PlayerListRules.kt`: `nameKey(name): String` public, used by the
  duplicate check (same behavior).
- `app/src/main/kotlin/.../history/ProfileRepository.kt` (`touchOrCreate(names, at)`, `createMissing(names, at)`,
  `recent()`, all returning `DbResult`, all through `ProfileDao.upsertUsed` or its read, review A3) and
  `ProfileMigration.kt` (PF4 as its own unit, review A5).
- `app/src/main/kotlin/.../persistence/Persistence.kt` only sequences: `TouchProfiles` and the launch migration on
  the queue, before the first load result is delivered; `ui/app/PersistenceEffect.kt` (`TouchProfiles`),
  `SetupTransitions.kt` (emit it on Start and Rematch).
- Tests: engine `PlayerListRulesNameKeyTest`; app `ProfileRepositoryTest`, `ProfileMigrationTest` (Robolectric).

## Depends on
T7.

## Rules
PF1, PF2, PF4, PF5, AR9, R2, R7, R22; ADR 0012 "Failure contract".

## Tests
- `nameKey`: trim, NFC, case variants equal; existing duplicate tests unchanged and green.
- Start with a new name creates it with that spelling; " KEVIN " for "Kevin" touches "Kevin" and keeps its spelling.
- Recency order after several starts.
- Migration: last setup names and saved game names become profiles; existing ones unchanged; running it twice
  creates nothing; an unreadable file contributes nothing and never fails the launch.
- Database failure (AR9, review S1), with a `ProfileRepository` fake returning `Failed` and with a closed database:
  the migration is skipped, the saved game is still loaded and offered (R2), no crash; `TouchProfiles` failing at
  Start does not block the game start.
- A name of `MAX_NAME_LENGTH` code points is stored whole; no profile name appears in logs (review S4).

## Done when
`./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: specified rules over T7's DAOs, with listed tests.

## Implementation notes
