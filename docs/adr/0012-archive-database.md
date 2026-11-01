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
| `profile` | `id`, `name`, `name_key`, `created_at`, `last_used_at`, `reset_at` (null) | unique `name_key`; index `last_used_at` |
| `session` | `id`, `type` (`MATCH`/`TRAINING`), `started_at` (null), `ended_at`, `rules_version`, `app_version` | index `ended_at` |
| `match` | `session_id`, `match_uuid`, `mode` (`X01`/`CRICKET`), `variant`, `settings` (text), `settings_version` | primary `session_id` (FK); unique `match_uuid`; index `mode` |
| `match_player` | `session_id`, `position`, `profile_id`, `color_argb`, `final_rank`, `legs_won`, `sets_won` | primary (`session_id`, `position`); index `profile_id` |
| `leg` | `id`, `session_id`, `ordinal`, `set_number`, `leg_number`, `starter_position`, `winner_position`, `has_journal` | unique (`session_id`, `ordinal`) |
| `dart` | `id`, `leg_id`, `player_position`, `visit`, `rank`, `segment`, `multiplier`, `bust`, `voided`, `source`, `x_mm` (null), `y_mm` (null), `entered_at` (null) | index (`leg_id`, `visit`, `rank`) |

- Every foreign key is `NO ACTION` on delete: nothing is deleted (AR8). Times are epoch milliseconds.
- `segment`: 0 (miss), 1 to 20, 25 (outer bull and bull, with `multiplier` 1 or 2); `multiplier`: 1 to 3.
  `source`: `MANUAL` or `CAMERA`. Coordinates are `REAL` millimetres (BC1).
- `settings`: `v=1;` then `key=value` pairs joined with `;`, in a fixed order. X01: `start=501|301`,
  `out=double|single`, `legs=<legs per set>`, `sets=<sets>`. Cricket: `variant=standard|cut-throat`, `bull=1|0`,
  `legs`, `sets`. `settings_version` repeats `v` as a column so a later version can be queried.
- Archive (AR2, AR4): one `runInTransaction`: if `match_uuid` exists, return `ALREADY_ARCHIVED`; else upsert the
  profiles by `name_key` (PF2), insert the session, match, players, legs and darts.

## Consequences

- The app gains Room and its transitive libraries (SQLite framework wrapper, kotlinx-coroutines); the PR lists them
  with their versions.
- Evaluating the move from Room 2.8 to Room3 is a backlog item (`docs/backlog.md`), unless T1 already fell back.
- Tests (Robolectric, in-memory database): every DAO, idempotence, archive failure keeping the save, profile
  migration and legacy match archive. After each build, `git status` shows no change in `app/schemas/`: the committed
  schema matches the entities.
