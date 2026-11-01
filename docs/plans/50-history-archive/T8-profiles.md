# T8: Profiles: creation and launch migration

## Goal
Profiles are created and touched at Start and Rematch, and created on launch from the last setup and the saved game
(PF1, PF2, PF4). D1, D8, D12.

## Files
- `game-engine/src/main/kotlin/.../engine/player/PlayerListRules.kt`: `nameKey(name): String` public, used by the
  duplicate check (same behavior).
- `app/src/main/kotlin/.../history/ProfileRepository.kt` (`touchOrCreate(names, at)`, `createMissing(names, at)`,
  `recent()`).
- `app/src/main/kotlin/.../persistence/Persistence.kt` (requests `TouchProfiles` and the launch migration on the
  queue, before the first load result is delivered); `ui/app/PersistenceEffect.kt` (`TouchProfiles`),
  `SetupTransitions.kt` (emit it on Start and Rematch).
- Tests: engine `PlayerListRulesNameKeyTest`; app `ProfileRepositoryTest`, `ProfileMigrationTest` (Robolectric).

## Depends on
T7.

## Rules
PF1, PF2, PF4, PF5, R7, R22.

## Tests
- `nameKey`: trim, NFC, case variants equal; existing duplicate tests unchanged and green.
- Start with a new name creates it with that spelling; " KEVIN " for "Kevin" touches "Kevin" and keeps its spelling.
- Recency order after several starts.
- Migration: last setup names and saved game names become profiles; existing ones unchanged; running it twice
  creates nothing; an unreadable file contributes nothing and never fails the launch.

## Done when
`./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: specified rules over T7's DAOs, with listed tests.

## Implementation notes
