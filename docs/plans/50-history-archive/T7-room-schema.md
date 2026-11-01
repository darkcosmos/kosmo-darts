# T7: Room schema v1, DAOs and archive repository

## Goal
The archive database at version 1 with the final tables, its exported schema, the DAOs, and the transactional,
idempotent archive (AR2, AR4, AR5). ADR 0012 is the specification.

## Files
- In `app/src/main/kotlin/.../history/`: entities `ProfileEntity`, `SessionEntity`, `MatchRecordEntity` (table
  `match_record`, review A6), `MatchPlayerEntity`, `LegEntity`, `DartEntity` (replace the T1 placeholder);
  `ProfileDao` (with the one `upsertUsed`, review A3), `ArchiveDao`, `HistoryDao`; `HistoryDatabase.kt` (version 1,
  no destructive fallback, corruption callback of ADR 0012); `DbResult.kt`; `SettingsText.kt` (settings text v1);
  `ArchiveRepository.kt` (`archive(archived, colorsArgb, appVersion): DbResult<ArchiveOutcome>` with `ARCHIVED`,
  `ALREADY_ARCHIVED`; never throws, ADR 0012 "Failure contract").
- `app/schemas/.../1.json` regenerated.
- Tests in `app/src/test/kotlin/.../history/` (Robolectric, in-memory).

## Depends on
T1 (Room wiring), T4 (`ArchivedMatch`).

## Rules
AR2, AR4, AR5, AR6, AR8, AR9, AR10, PF1, PF2 (profile upsert inside the archive), D8, D11, D13. ADR 0012 "Tables",
"Decision" and "Failure contract".

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
- Idempotence: the same match twice gives one session and the second returns `ALREADY_ARCHIVED`.
- Rollback (review T4 minor): the insert failure is injected by a test-only `ArchiveDao` subclass whose dart insert
  throws after the session, match record, players and legs were inserted; afterwards no row exists in any table and
  the profile upsert of that archive is rolled back too (no new profile, `last_used_at` unchanged); result `Failed`.
- Corrupt file (review T8 minor), using the mechanism T1 recorded in ADR 0012: an on-disk database file filled with
  garbage bytes gives `Failed` for the archive and for a read, the app does not crash, the file keeps the same bytes
  (not deleted, not recreated empty), and no destructive migration runs. Assert only these facts.
- Failure contract: a closed database gives `Failed` for the archive and each `HistoryDao`-backed read, never an
  exception.
- Legacy match: legs without journal, null start time.
- 1,000 archived matches: the list query time is logged in the notes (no timing assert). Tests run on the calling
  thread with a fixed clock: no sleep, no idling wait (review T5 minor).
- Merged manifest still has no new permission or exported component (review S3).

## Done when
`./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build` green; `git status` clean in
`app/schemas/` after a second build; every new test runs on a direct (same-thread) executor with a fake `Clock`, with
no sleep and no idling wait; `kosmo-detekt` exit 0.

## Model
opus. Why: case "new or changed stored format" (schema v1 that can never be dropped, no destructive migration).

## Implementation notes
