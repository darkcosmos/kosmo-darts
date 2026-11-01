# ADR 0012: Archive database (Room, schema version 1)

- Status: Proposed (design review of issue #50); accepted when its PR is merged. T1 of
  `docs/plans/50-history-archive.md` records here whether Room 2.8 or Room3 is used (fallback rule, decision D3).

## Context

Issue #50 archives every confirmed match and its darts for the History screens and the future statistics (H3),
heatmap (H4) and camera milestones. Queries filter by player and mode over 1,000 matches and more; data is never
deleted (AR8) and must survive every future version (no destructive migration).

## Options

1. Text files per match, like the saved game. Rejected: filters and statistics would read every file.
2. Room 2.8.5 (latest stable, checked 2026-11-01 on Google Maven) with KSP 2.3.13, in `:app`. Chosen, as the issue
   asks. Fallback: Room3 3.0.3 (latest stable) if Room 2.8 does not compile or its KSP processing fails with Kotlin
   2.4.21, without a workaround or a long diagnosis (D3).
3. SQLDelight. Rejected: not asked, and a new tool for the squad.

## Decision

- Dependencies: `androidx.room:room-runtime`, `androidx.room:room-compiler` (KSP), the Room Gradle plugin and the KSP
  plugin, versions in the catalog only. No `room-ktx` (merged into `room-runtime` since 2.7), no Paging, no
  `sqlite-bundled` (the framework SQLite driver is used, which Robolectric supports).
- `schemaDirectory("$projectDir/schemas")`; the exported JSON of version 1 is committed. Database `kosmo-history.db`,
  version 1, in the app's private storage. No `fallbackToDestructiveMigration*` call, now or later: every future
  version ships a tested `Migration`.
- Threading: DAOs are blocking and run on the persistence queue (`Persistence`, R12), so the archive and the deletion
  of the saved game keep their order (AR2). History reads run on a separate single-thread read executor and post
  their result to the main thread. No coroutines code of our own.

### Tables (version 1)

| Table | Columns | Keys and indexes |
|---|---|---|
| `profile` | `id`, `name`, `name_key`, `created_at`, `last_used_at`, `reset_at` | unique `name_key`; index `last_used_at` |
| `session` | `id`, `type` (`MATCH`/`TRAINING`), `started_at`, `ended_at`, `rules_version`, `app_version` | index `ended_at` |
| `match_record` | `session_id`, `match_uuid`, `mode` (`X01`/`CRICKET`), `variant`, `settings` (text), `settings_version` | primary `session_id` (FK); unique `match_uuid`; index `mode` |
| `match_player` | `session_id`, `position`, `profile_id`, `color_argb`, `final_rank`, `legs_won`, `sets_won` | primary (`session_id`, `position`); index `profile_id` |
| `leg` | `id`, `session_id`, `ordinal`, `set_number`, `leg_number`, `starter_position`, `winner_position` | unique (`session_id`, `ordinal`) |
| `dart` | `id`, `leg_id`, `player_position`, `visit`, `rank`, `segment`, `multiplier`, `bust`, `voided`, `source`, `x_mm` (null), `y_mm` (null), `entered_at` | index (`leg_id`, `visit`, `rank`) |

- Every column is `NOT NULL` (plan D31, Kosmo: "on met juste par contre toutes les colonnes mandatory, puisque il
  n'y a pas d'histoire de backward compatibilité"), and the matching entity fields are non-nullable Kotlin types.
  There is no `has_journal` column: every archived leg has its journal (D25), so such a flag would always be 1 and
  nothing would read it.
- `reset_at` (plan D32) is `NOT NULL` and equals `created_at` when the profile is created: statistics count from
  the creation; nothing changes it in this mission (PF1).
- `x_mm` and `y_mm` (plan D33) are the only nullable columns. Null means "position unknown": a miss (BC2), or any
  dart whose position is not known, now or from a later source. Both are null or both are set: they are written from
  one `BoardPoint?` (Room has no `CHECK` annotation; T7 tests both cases read back). Every consumer of positions
  (later heatmap and statistics) ignores darts whose position is null; it never reads them as (0, 0).
- Every foreign key is `NO ACTION` on delete: nothing is deleted (AR8). Times are epoch milliseconds.
- `segment`: 0 (miss), 1 to 20, 25 (outer bull and bull, with `multiplier` 1 or 2); `multiplier`: 1 to 3.
  `source`: `MANUAL` or `CAMERA`. Coordinates are `REAL` millimetres (BC1).
- `settings`: `v=1;` then `key=value` pairs joined with `;`, in a fixed order. X01: `start=501|301`,
  `out=double|single`, `legs=<legs per set>`, `sets=<sets>`. Cricket: `variant=standard|cut-throat`, `bull=1|0`,
  `legs`, `sets`. `settings_version` repeats `v` as a column so a later version can be queried.
- Archive (AR2, AR4): one `runInTransaction`: if `match_uuid` exists, return `ALREADY_ARCHIVED`; else upsert the
  profiles by `name_key` (PF2) through `ProfileDao.upsertUsed(name, nameKey, at)`, the one upsert also used by
  `ProfileRepository` (review A3), insert the session, match record, players, legs and darts.
- Table names (review A6): `match_record`, not `match`, because `MATCH` is an SQLite keyword; schema version 1 can
  never be dropped, so it is named once and for all.
- Snapshot (review A2, AR10): `dart.visit`, `rank` and `bust` are the engine values under `session.rules_version` at
  archive time; a later rules version never rewrites them.
- Names (review S4): `profile.name` is at most `PlayerListRules.MAX_NAME_LENGTH` code points (the setup limit);
  profile names, match ids and times are never written to logs.
- Manifest (review S3): Room merges `MultiInstanceInvalidationService`, which must stay `exported="false"`; the
  merged manifest gains no permission and no exported component (checked in T1 and T7).

### Failure contract (review S1, AR9)

- Every repository (`ArchiveRepository`, `ProfileRepository`, `HistoryRepository`, `ArchiveCheck`) catches the
  exceptions of Room and SQLite (`SQLiteException` and its subclasses, `IllegalStateException` from a database that
  cannot open) and returns a result: `DbResult.Ok(value)` or `DbResult.Failed`. Nothing above them sees a database
  exception; the cause is not logged with data.
- The database is opened lazily, on the queue or the read executor, never on the main thread. A corrupt file is
  neither deleted nor recreated by the app: Room is built without a destructive fallback, and the default corruption
  handler of the framework is replaced by one that only reports the failure (`openHelperFactory` with a callback
  whose `onCorruption` does nothing), so the next access fails again with `Failed`. If the Room version in use offers
  no supported way to do this, T1 stops and reports to main (plan, open question 1) instead of working around it.
  T1 completes this point with the Room version, the API used and the verified behavior before T7 starts (review A8).
- Mapping: AR4 check `Failed` means "keep the save";
  archive `Failed` gives `ArchiveFailed` (AR3); History and detail `Failed` give the HS7 failure state.
- Tests use a repository fake that returns `Failed` and a closed in-memory database (a real exception path).

## Consequences

- The app gains Room and its transitive libraries (SQLite framework wrapper, kotlinx-coroutines); the PR lists them
  with their versions.
- Evaluating the move from Room 2.8 to Room3 is a backlog item (`docs/backlog.md`), unless T1 already fell back.
- Tests (Robolectric, in-memory database): every DAO, idempotence, archive failure keeping the save. After each
  build, `git status` shows no change in `app/schemas/`: the committed schema matches the entities.
- Clean slate (plan D25) and mandatory columns (plan D31): `started_at` and `entered_at` are `NOT NULL` and
  `has_journal` is removed (owner decision, replacing the nullable columns of the earlier schema). A later source
  that lacks one of these values (a `TRAINING` session, a `CAMERA` dart, a match entered afterwards) is a schema
  change: in place before the release, a tested `Migration` after it. Until the release version that can be updated
  (plan T15), the schema stays version 1 with its exported schema and no destructive fallback, and may be edited in
  place only by owner decision; from that release on, every change ships a tested `Migration`.
