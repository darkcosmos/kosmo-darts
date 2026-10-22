# ADR 0006: Save format version 3 for the players' color slots

- Status: Proposed (design review of issue #49); accepted when its PR is merged
- Date: 2026-10-22
- Extends: ADR 0001 (save format) and ADR 0005 (version 2). ADR 0002 (save lifecycle) is unchanged.
- Rules: `docs/rules/persistence.md` (R9, R10, R17, R18), `docs/rules/players.md` (PC1, PC6 to PC8)

## Context

Issue #49 gives each player a color slot (1 to 4) that follows him through the whole match, a resume, a rematch and
the next setup (owner Q6). A slot cannot be derived from the player's position: removing the second of four players
and adding one gives slots 1, 3, 4, 2 (PC2, PC3), and shuffling keeps each player's slot (PC4). ADR 0001 requires a
new format version for any new key, with the older decoders kept.

## Options

- A. A `colors` line in both files, one slot per player (chosen): one short line, checked like every other value.
- B. The slot appended to each `player` line: changes the meaning of an existing key and of its canonical Base64
  check, for no gain.
- C. Not saving the slots and deriving them by position: breaks PC6 to PC8 as soon as the order is not 1 to n.

## Decision

Option A, in format version 3 of both files. Every write uses version 3; versions 1 and 2 are still decoded.

### Game file, version 3

The version 2 layout with one more line right after the `player` lines:

```
...
players=2
player=QW5u
player=Qm9i
colors=1 2
legs=2
...
```

### Last setup file, version 3

The version 2 layout with the same `colors` line after the `player` lines (the last line of the body).

### The `colors` value

- One slot number per player, in configuration order, separated by single spaces: as many tokens as the `players`
  value. Another count, or an empty token (double, leading or trailing space, empty value), is `MALFORMED`.
- Each token is a canonical integer from 1 to 4; anything else (`0`, `5`, `04`, `x`, `-1`) is `OUT_OF_RANGE`.
- Slots are all distinct (PC1); a repeated slot is `OUT_OF_RANGE`.
- An invalid line makes the game file unreadable (R9: deleted, message shown once) or the last setup unreadable (R10:
  deleted silently).

### Strict keys (S2)

For each version, the ordered list of keys is defined once in `GameSaveCodec` and `LastSetupCodec` (`V3_LAYOUT`),
and both writing and reading follow it through the strict `BodyReader`. So a missing `colors` line, a duplicated
`colors` key, a second `colors` line, a `colors` line placed before the `player` lines, and a version 2 file that
carries a `colors` line are all `MALFORMED`. Each case has a test (`SaveColorsCodecTest`).

### Older versions (R18)

A version 1 or version 2 file gives each player the slot of his position (1 to n). No message is shown. The next
write uses version 3. The version 1 fixtures (`app/src/test/resources/persistence/v1`) and the version 2 golden
literals of the tests are frozen: they are never regenerated with the version 3 writer.

### Older app finding version 3

An older app rejects version 3 (R9, R10; ADR 0005, "Older app finding version 2"): the saved game is deleted with
the R9 message shown once, the last setup is deleted silently. Nothing is kept for a later upgrade. Accepted for the
same reason as in ADR 0001 and ADR 0005: there is only one user, who installs builds from PRs. The PR states it under
"Known limitations".

### Size limit

Unchanged. The `colors` line is at most `colors=1 2 3 4` plus a line feed (15 bytes), far below the margin of
ADR 0005 (a valid save stays under 92 KB, the limit is 131,072 bytes). The largest-save test of ADR 0005 now writes
version 3 and still passes.

## Consequences

- The format is frozen again by exact-bytes tests of version 3 (a single game with slots 2 and 1, and a match).
- A single game read from version 1 re-encodes to exactly its version 1 content, with `version=3` and the keys of
  versions 2 and 3 inserted at their places (`colors` by position).
- Later changes need a version 4 and a new ADR.
