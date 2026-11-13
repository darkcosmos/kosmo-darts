# ADR 0014: The anonymous profile of deleted players

- Status: Proposed (design review of issue #51); accepted when its PR is merged. Amends ADR 0012: no schema change,
  the archive database stays at version 1.

## Context

Deleting a profile (PM6 of `docs/rules/history.md`) must keep the matches it played with other players: they show
"Deleted player" in its place. The `match_player.profile_id` foreign key is NOT NULL (plan D31), so a participation
needs a profile row to point to.

## Options

1. One reserved profile row, found by its name key `""` (chosen). A normal `profile` row: a name key is never empty for
   a real player (the name rules refuse a blank name), so the key cannot clash. No schema change.
2. A `hidden` (or `is_anonymous`) column on `profile`: needs schema version 2 and a migration for a boolean that the
   reserved key already carries.

## Decision

- The anonymous profile is the `profile` row whose `name_key` is `ProfileRules.ANONYMOUS_NAME_KEY` (`""`), created
  by the first deletion that needs it (name stored: "Deleted player", never displayed: the screens use the
  `history_deleted_player` resource, which is translated).
- Deleting a profile moves its participations in matches shared with a real player to the anonymous profile, deletes
  the matches left with it only (the profile and the anonymous profile alone), child first (`dart`, `leg`,
  `match_player`, `match_record`, `session`), then deletes the profile: one transaction (PM10).
- Invariant: every query that lists profiles, suggests names or offers a player filter excludes the reserved key.
  The queries today:
  - `PlayersDao.rows` (Players list, PM2, PM7);
  - `HistoryDao.profilesByRecency` (History player filter HS3 and name suggestions PF3, through
    `RoomProfileRepository.recent`).
  A new query of that kind must exclude the key too and be added to this list.
- The anonymous profile cannot be renamed, reset or merged (PM7): those actions treat it as unknown.

## Consequences

- No migration. A history of several deleted players shows them all as one "Deleted player" (they are one row).
- A reader that joins `match_player` to `profile` for display marks the row deleted from the key
  (`HistoryPlayer.isDeleted`).
