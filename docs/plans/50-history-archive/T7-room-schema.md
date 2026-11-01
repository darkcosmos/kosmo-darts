# T7: Room schema v1, DAOs and archive repository

## Goal
The archive database at version 1 with the final tables, its exported schema, the DAOs, and the transactional,
idempotent archive (AR2, AR4, AR5). ADR 0012 is the specification.

## Files
- In `app/src/main/kotlin/.../history/`: entities `ProfileEntity`, `SessionEntity`, `MatchEntity`,
  `MatchPlayerEntity`, `LegEntity`, `DartEntity` (replace the T1 placeholder); `ProfileDao`, `ArchiveDao`,
  `HistoryDao`; `HistoryDatabase.kt` (version 1, no destructive fallback); `SettingsText.kt` (settings text v1);
  `ArchiveRepository.kt` (`archive(archived, colorsArgb, appVersion): ArchiveResult` = `ARCHIVED`,
  `ALREADY_ARCHIVED`, failure as an exception caught by the caller).
- `app/schemas/.../1.json` regenerated.
- Tests in `app/src/test/kotlin/.../history/` (Robolectric, in-memory).

## Depends on
T1 (Room wiring), T4 (`ArchivedMatch`).

## Rules
AR2, AR4, AR5, AR6, AR8, PF1, PF2 (profile upsert inside the archive), D8, D11. ADR 0012 "Tables" and "Decision".

## Approach
- Indexes and keys exactly as in ADR 0012; enum columns as text; `REAL` coordinates.
- No `@Delete`, no delete query anywhere (AR8).
- The archive upserts each player's profile by name key, inserts session, match, players, legs, darts in one
  `runInTransaction`; an existing `match_uuid` returns `ALREADY_ARCHIVED` without writing.
- `HistoryDao` exposes the queries T11 and T12 need: match summaries filtered by mode and profile ordered by
  `ended_at` desc, players of those matches, profiles by `last_used_at` desc, one match with legs and darts.

## Tests
- Each DAO query on fixtures; the filtered list order and the mode/profile filters combined.
- Archive of an X01 and a Cricket `ArchivedMatch` read back field by field (settings text, ARGB, coordinates, voided).
- Idempotence: the same match twice gives one session; an exception during insert leaves no row (rollback).
- Legacy match: legs without journal, null start time.
- 1,000 archived matches: the list query time is logged in the notes.

## Done when
`./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build` green; `git status` clean in
`app/schemas/` after a second build; `kosmo-detekt` exit 0.

## Model
opus. Why: case "new or changed stored format" (schema v1 that can never be dropped, no destructive migration).

## Implementation notes
