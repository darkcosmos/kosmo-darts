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
AR2, AR4, AR5, AR6, AR8, AR9, AR10, PF1, PF2 (profile upsert inside the archive), D8, D11, D13, D31 to D33, BC2.
ADR 0012 "Tables", "Decision" and "Failure contract".

## Approach
- Indexes and keys exactly as in ADR 0012; enum columns as text; `REAL` coordinates.
- Every column `NOT NULL` and every entity field non-nullable (D31), except `x_mm` and `y_mm` (`Double?`, D33),
  both written from the one `BoardPoint?` of the dart: both null or both set; no other null branch in the mapping.
- `ProfileDao.upsertUsed` creates a profile with `reset_at = created_at` (D32, PF1); an update never changes it.
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
- Mandatory columns (D31 to D33): the exported `1.json` has `notNull: true` for every column except exactly
  `dart.x_mm` and `dart.y_mm`; no `has_journal` column; the session is read back with its `started_at` and
  `app_version`, every dart with its `entered_at`.
- Positions (D33, review A11): Robolectric round trip through the mapper of a hit (read back with the same
  `BoardPoint`, both columns set) and a miss (point null, both columns null, never one of the two); a dart archived
  without a point behaves as the miss. Only the entity mapper sees `x_mm`/`y_mm`; other types hold `BoardPoint?`.
- `reset_at` (D32): a new profile has `reset_at == created_at`; a later upsert of the same name key changes
  `last_used_at` only.
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
- Code in `app/.../history/`: `HistoryEntities.kt` (the six entities, enum columns via Room's built-in enum-as-text
  converter), `ProfileDao.kt` (abstract class: `findByNameKey`, `@Transaction upsertUsed(name, nameKey, at): Long`),
  `ArchiveDao.kt` (interface: `isArchived(matchUuid)` for T9's `ArchiveCheck`, inserts only), `HistoryDao.kt`
  (`matchSummaries(mode?, profileId?)`, `matchPlayers(mode?, profileId?)` with the same filters, so no `IN` list over
  1,000 ids; `profilesByRecency`, `matchSummary`, `playersOf`, `legsOf`, `dartsOf`; `null` filter = "All"),
  `ArchiveMapper.kt`, `SettingsText.kt` (`encode`, `decode(text, mode)`), `DbResult.kt` (`DbResult` and the
  `dbResult { }` wrapper every repository uses), `ArchiveRepository.kt`, `HistoryDatabase.kt`
  (`builder(context)` / `open(context)`, `keepingCorruptFile()`; `KeepCorruptFileOpenHelperFactory` of T1 kept).
- Deviations and choices a later task must know:
  - Name key: `PlayerListRules.nameKey` is made public by T8, so `ArchiveRepository(database, nameKey, archiveDao)`
    takes the key function; T9 wires `PlayerListRules::nameKey`. Tests use trim + lower case.
  - Profile time at archive: `upsertUsed(..., at = archived.startedAt)` (PF2: last use = start). `upsertUsed`
    moves `last_used_at` only forward (`MAX`), never changes name, `created_at` or `reset_at`.
  - Bull: the engine's bull (segment 50, single) is stored as segment 25, multiplier 2, as ADR 0012 says; only
    `ArchiveMapper` converts, both ways. `ArchiveMapper.archivedDart(DartWithLegRow)` gives back the engine
    `ArchivedDart` (with `point: BoardPoint?`): T12 reads darts through it, never `x_mm`/`y_mm`.
  - Read faults: an unreadable stored dart or settings text throws `IllegalStateException`, which `dbResult` maps to
    `Failed` (HS7). `colorsArgb` is one ARGB per player by position; a wrong size gives `Failed` without writing.
  - `dbResult` catches `android.database.SQLException` (all `SQLiteException`s) and `IllegalStateException`; the
    cause is dropped, never logged.
  - Tests build the database with `inMemoryHistoryDatabase(context)` / `onCallingThread()` (`HistoryFixtures.kt`:
    direct executors, `allowMainThreadQueries`, `FakeClock`, `x01Match`, `cricketMatch`); reuse them in T8 to T12.
  - `HistoryDatabaseSpikeTest` (T1) now uses the real DAOs; its corrupt-file control tests are unchanged.
- Corrupt file: garbage `kosmo-history.db` opened through `HistoryDatabase.builder` gives `Failed` for the archive and
  every read, twice, and keeps its bytes (`HistoryDatabaseFailureTest`). A closed database gives `Failed` everywhere.
- 1,000 archived matches (2 players, 2 legs, 7 darts each): the three list queries (summaries, players, one
  filtered summary) took 52 ms in total under Robolectric on the sandbox (`HistoryDaoTest`, printed, not asserted).
- Merged manifest (debug and release) after this task: no `INTERNET`, no new permission, no new exported component;
  `MultiInstanceInvalidationService` stays `exported="false"`. T7 adds no dependency and no manifest entry.
- Checks: `./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build` green; second build
  leaves `app/schemas/` clean; `kosmo-detekt` exit 0.
