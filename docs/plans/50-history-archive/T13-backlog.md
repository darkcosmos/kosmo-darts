# T13: Backlog entries

## Goal
Create `docs/backlog.md` with the two entries the issue asks for (issue #50, "Documentation") and the
release entry of D25.

## Files
- Added `docs/backlog.md`: title "Backlog", one short line of purpose, then a list.

## Depends on
None. If T1 fell back to Room3 (see T1 notes), the first entry says "Re-evaluate Room3 against Room 2.8" instead.

## Rules
None (documentation).

## Content (English, one item each, with the issue link)
1. Evaluate the move from Room 2.8 to Room3 (issue #50, ADR 0012).
2. Re-enable Android Auto Backup (no `INTERNET` permission needed) so that the history survives a change of phone.
   This revises the decision "backup and device transfer disabled" (`allowBackup="false"`,
   `docs/rules/persistence.md`). Owner decision required; nothing changes in this mission.
3. Move to a real release version that can be updated (signing key, release build); backward compatibility of saves,
   database and settings becomes mandatory only from that release (decision D25, the GitHub issue opened by T15).

## Tests
None (documentation only).

## Done when
`./gradlew build` green; `kosmo-detekt` exit 0.

## Model
haiku. Why: three documentation entries whose text is given.

## Implementation notes
