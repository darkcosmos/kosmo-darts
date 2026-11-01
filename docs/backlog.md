# Backlog

Known work that is deliberately not in the current missions, with the decision it depends on.

- Evaluate the move from Room 2.8 to Room3 (issue #50, ADR 0012).
- Re-enable Android Auto Backup (no `INTERNET` permission needed) so that the history survives a change of phone.
  This revises the decision "backup and device transfer disabled" (`allowBackup="false"`, `docs/rules/persistence.md`).
  Owner decision required; nothing changes in this mission.
- Move to a real release version that can be updated (signing key, release build); backward compatibility of saves,
  database and settings becomes mandatory only from that release (decision D25, issue #66).
