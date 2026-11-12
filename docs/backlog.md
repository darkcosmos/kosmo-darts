# Backlog

Known work that is deliberately not in the current missions, with the decision it depends on.

- Evaluate the move from Room 2.8 to Room3 (issue #50, ADR 0012).
- Re-enable Android Auto Backup (no `INTERNET` permission needed) so that the history survives a change of phone.
  This revises the decision "backup and device transfer disabled" (`allowBackup="false"`, `docs/rules/persistence.md`).
  Owner decision required; nothing changes in this mission.
- Move to a real release version that can be updated (signing key, release build); backward compatibility of saves,
  database and settings becomes mandatory only from that release (decision D25, issue #66).

## Moved from the sandbox backlog (2026-11-12, to re-check against the current app)

- Product: legs and sets; more game modes (Cricket in progress, around the world); training mode (routines to define);
  look and feel proposals to compare before a mission; history and statistics; camera milestone.
- CI: detekt `ForbiddenImport` rule so that the `persistence` package cannot import `ui`; stable debug key (it
  changes on every CI build); blocking CodeQL.
- Known limitations: at 3 to 4 players in portrait the bottom of the list can leave the screen; a failed save is not
  reported; the setup screen input is lost if Android kills the app; beyond 10,000 darts the game is no longer saved.
- Android developer verification becomes mandatory in 2027: handle it before distributing the app to friends.
