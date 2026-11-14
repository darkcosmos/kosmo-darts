# T8: Delete and anonymous profile

## Goal
Profile deletion with the single anonymous profile, and its display in the existing reads. Root "Approach" 5.

## Files
- `app/.../history/PlayersDao.kt`, `PlayersRepository.kt`: `deletionCount(id)` (matches PM6 will erase) and
  `delete(id, at)`: one transaction, anonymous profile found or created (`ProfileRules.ANONYMOUS_NAME_KEY`),
  participations moved, matches of `deletionPlan` deleted child first (dart, leg, match_player, match_record,
  session), profile deleted.
- `HistoryDao.kt`, `HistoryRepository.kt`: `HistoryPlayer.isDeleted`; anonymous excluded from the player filter.
- `ProfileRepository.kt` / `HistoryDao.profilesByRecency`: anonymous never suggested. `PlayersDao` list excludes it.
- `ui/history/` rows and detail: "Deleted player" resource when `isDeleted`.
- `values/strings.xml`, `values-fr/strings.xml`: "Deleted player" / "Joueur supprimé".
- `docs/adr/0014-anonymous-profile.md` (new, review A1): the reserved name key `""` in a normal `profile` row; the
  rejected alternative (a `hidden` column, schema version 2 and a migration); deletion moves participations to it and
  deletes the matches left with it only; invariant: every query listing profiles, suggestions or player filters
  excludes the reserved key (list the queries).
- History mapping of `color_argb` through `PlayerColor.fromStored` (D16).
- Tests: `PlayersRepositoryTest` (delete cases), `HistoryRepositoryTest`, `ProfileRepositoryTest`, `HistoryRowsTest`.

## Depends on
T7.

## Rules
PM6, PM7, PF3, PF5, AR8, HS2, HS3, HS5, AR9.

## Tests
- Delete with a solo match (match gone with its legs and darts), a shared match (reassigned, shown "Deleted
  player"), a second deletion reusing the same anonymous profile, a match whose two players are deleted (gone).
- `deletionCount` equals the number of matches actually erased; `delete` returns the count recomputed inside its
  transaction (review T5).
- Foreign keys enforced (`PRAGMA foreign_keys` on, as the app database): deleting a profile whose solo match has a
  record, legs and darts raises no constraint violation (child-first order, reviews A3, S3).
- A failure injected after the first delete rolls back everything: same row counts in every table.
- A History row stored with alpha `00` in `color_argb` is shown opaque, no crash (D16, review S1).
- Anonymous absent from Players list, suggestions (empty field included) and History filter.
- Archiving a match never picks the anonymous profile.

## Done when
`./gradlew :app:testDebugUnitTest`, `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: specified rules PM6 and PM7 over T7's DAO, no schema change.

## Implementation notes
- Deviation: detekt TooManyFunctions (11) forbade putting the delete queries in `PlayersDao`. They live in a new
  `ProfileDeletionDao` (`ProfileDeletion.kt`, also holds `SessionParticipant`), and the plan/apply logic in
  `ProfileDeleter` (same file), used by `RoomPlayersRepository`. `PlayersDao` keeps `moveParticipations`/`deleteProfile`.
- `PlayersRepository` gained `deletionCount(id): DbResult<Int>` and `delete(id, at): DbResult<Int>` (matches erased,
  computed in the delete transaction). Unknown or anonymous id: no-op giving 0. The anonymous profile (stored name
  "Deleted player", key `""`) is created lazily, only when a match is reassigned. `FakePlayersRepository` has
  `deletionCountResult`, `deleteResult` and a `deletes` call log for T9.
- History: `MatchPlayerRow.nameKey`, `HistoryPlayer.isDeleted` (default false), color read through
  `PlayerColor.fromStored`. `HistoryDao.profilesByRecency` excludes the key (also feeds `ProfileRepository.recent`).
- UI: `HistoryPlayer.shownName(resources)` (HistoryRows.kt) replaces every `.name` display in History rows and the
  detail; resource `history_deleted_player` in both languages. T9 can reuse it.
- ADR 0014 lists the queries that must exclude the reserved key.
