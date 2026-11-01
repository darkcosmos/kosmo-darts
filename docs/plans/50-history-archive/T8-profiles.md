# T8: Profiles: creation at Start and Rematch

## Goal
Profiles are created and touched at Start and Rematch (PF1, PF2). No launch migration: PF4 is withdrawn (clean slate,
D25), profiles exist only from the first use of a name. D1, D8, D12.

## Files
- `game-engine/src/main/kotlin/.../engine/player/PlayerListRules.kt`: `nameKey(name): String` public, used by the
  duplicate check (same behavior).
- `app/src/main/kotlin/.../history/ProfileRepository.kt` (`touchOrCreate(names, at)`, `recent()`, both returning
  `DbResult`, both through `ProfileDao.upsertUsed` or its read, review A3).
- `app/src/main/kotlin/.../persistence/Persistence.kt` only sequences `TouchProfiles` on the queue;
  `ui/app/PersistenceEffect.kt` (`TouchProfiles`), `SetupTransitions.kt` (emit it on Start and Rematch).
- Tests: engine `PlayerListRulesNameKeyTest`; app `ProfileRepositoryTest` (Robolectric).

## Depends on
T7.

## Rules
PF1, PF2, PF5, AR9, R7, R22; ADR 0012 "Failure contract".

## Tests
- `nameKey`: trim, NFC, case variants equal; existing duplicate tests unchanged and green.
- Start with a new name creates it with that spelling; " KEVIN " for "Kevin" touches "Kevin" and keeps its spelling.
- Recency order after several starts.
- No launch step: a launch with a last setup and a saved game creates no profile (PF4 withdrawn).
- Database failure (AR9, review S1), with a `ProfileRepository` fake returning `Failed` and with a closed database:
  no crash; `TouchProfiles` failing at Start does not block the game start.
- A name of `MAX_NAME_LENGTH` code points is stored whole; no profile name appears in logs (review S4).

## Done when
`./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: specified rules over T7's DAOs, with listed tests.

## Implementation notes
- `PlayerListRules.nameKey(name)` is public (trim, NFC, code point case fold, no accent folding); the duplicate check
  uses it. `PlayerListRulesNameKeyTest` covers it. T9 wires `PlayerListRules::nameKey` into both repositories.
- `history/ProfileRepository.kt`: `interface ProfileRepository` (`touchOrCreate(names, at)`, `recent()`, both
  `DbResult`), `RoomProfileRepository(database, nameKey)` (writes only through `ProfileDao.upsertUsed`, one
  transaction; `recent()` maps `HistoryDao.profilesByRecency` to `Profile(name, nameKey, lastUsedAt)`). The interface
  lets tests (and T10) use a fake returning `Failed`.
- Deviation: `TouchProfiles(names, at)` is emitted by `persistenceEffects` (`PersistenceEffect.kt`), not by
  `SetupTransitions.kt`, because the effect is derived from the state change: Setup, End or CricketEnd to Game or
  CricketGame (Start and Rematch, X01 and Cricket). `at` is `journal.startedAt`. It comes last in the effect list.
- `Persistence(..., profiles: ProfileRepository? = null)` and `touchProfiles(names, at)` queue the touch on the
  executor and ignore the result (a `Failed` never blocks the start). Without a repository it does nothing.
  `KosmoDartsApplication` does not yet pass one: T9 opens the database and wires it (nothing opens the database at
  launch, PF4 withdrawn; `ProfileRepositoryTest` loads a saved game and last setup and finds no profile).
- No name is logged anywhere (`dbResult` drops the cause).
- Checks: `./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build` green; `kosmo-detekt`
  exit 0.
