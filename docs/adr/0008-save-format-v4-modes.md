# ADR 0008: Save format version 4 for game modes (X01 and Cricket)

- Status: Proposed (design review of issue #40); accepted when its PR is merged
- Date: 2026-10-23
- Extends: ADR 0001 (save format), ADR 0005 (version 2) and ADR 0006 (version 3). ADR 0002 (save lifecycle) is
  unchanged.
- Rules: `docs/rules/persistence.md` (R9 to R12, R20, R21, R23 to R25), `docs/rules/cricket.md`,
  `docs/rules/match.md` (M4, M6, M12)

## Context

Issue #40 adds Cricket next to X01. There is still one saved game on the device, whatever its mode (R20), and a
Cricket game must resume exactly like an X01 game (R21). The saved game file of version 3 can only hold an X01 match:
its body starts with `game=x01`, and its confirmed legs hold X01 figures. ADR 0001 requires a new format version for
any new key, with the older decoders kept.

## Options

- A. A `mode` line right after the version line, then a body per mode (chosen). The X01 body is the version 3 body,
  unchanged; the Cricket body has its own keys. One decoder per mode, the same frame (kind, version, checksum, end).
- B. Reusing `game=cricket` in the version 3 layout: the X01 keys (`start-score`, `checkout`) would have to be optional
  or meaningless for Cricket, against the strict keys of ADR 0006 (S2).
- C. One file per mode: two saved games could exist at once, against R20, and deleting one when starting the other
  would need a second atomic write.

## Decision

Option A, in format version 4 of the saved game file. Every write uses version 4; versions 1 to 3 are still decoded,
as X01 (R25).

### The mode line

- `mode=x01` or `mode=cricket`, the first body line of a version 4 file, covered by the checksum like every body
  line.
- A version 4 file without a mode line is `MALFORMED` (the strict `BodyReader` expects `mode` first). Any other value
  is `OUT_OF_RANGE`.
- A body that does not match its mode is `MALFORMED`: the X01 body starts with `game=`, the Cricket body with
  `variant=`, and each decoder reads only its own keys.
- Versions 1 to 3 have no mode line: their layouts start with `game`, so a mode line in them is `MALFORMED`.
- The checksum covers the mode line: a checksum computed without it, or a mode line edited afterwards, is
  `CHECKSUM_MISMATCH`.

### X01 body, version 4

`mode=x01`, then exactly the version 3 body (ADR 0006), including its `game=x01` line:

```
kosmodarts=saved-game
version=4
mode=x01
game=x01
start-score=301
...
```

### Cricket body, version 4

The lines, in this order, each once:

```
mode=cricket
variant=standard
bull=1
legs-per-set=3
sets=2
players=2
player=QW5u
player=Qm9i
colors=1 2
legs=2
leg=0 21/8/0/21 0/6/0/0
leg=1 0/6/0/0 21/8/0/21
darts=
awaiting-next-player=false
between-legs=true
```

- `variant`: `standard` or `cut-throat`. `bull`: `1` when the 25-Bull is in play, `0` without it. Both are closed
  sets; anything else is `OUT_OF_RANGE`.
- `legs-per-set`, `sets`, `players`, `player`, `colors`, `legs`, `darts`, `awaiting-next-player` and `between-legs`
  have the meaning and checks of versions 1 to 3 (ADR 0001, 0005, 0006), with the same dart tokens.
- `leg`: one line per confirmed leg, in order: the winner index, then `marks/darts/points/board` for each player in
  configuration order: the CR2 marks hit, the darts thrown, the points at the end of the leg, and the board marks
  (CR12). The board marks are stored because the leg summary of the engine (`CricketLegSummary`) keeps them for the
  CR12 ranking, and they cannot be rebuilt without the darts. A wrong number of tokens or of figures is `MALFORMED`.
- The starter of the current leg is not stored: it is `legs % players` (M6), as for X01.

### Confirmed legs are the accepted record (review A5)

As in ADR 0001 and ADR 0005, the confirmed-leg summaries are the match's accepted record: their darts are not stored,
so they cannot be replayed. The decoder checks these bounds (R24), and each broken bound rejects the file (R9), never
with an exception:

- player count 1 to 4, with names valid under `PlayerListRules`; an `InvalidCricketConfigException` is caught and
  gives `INVALID_CONFIG`;
- variant and 25-Bull tokens from their closed sets;
- each figure is a canonical integer of at most five digits (`SaveFormat`, unchanged), so points are from 0 to 99,999
  and never signed;
- in each confirmed leg: a winner index below the player count, darts at most the 10,000-dart limit of R12, marks hit
  at most 3 per dart (CR2), board marks at most the marks hit and the full board, and a full board for the winner
  (CR8). These are the checks of the `CricketLegSummary` constructor, plus the darts limit;
- the leg count respects M12, and no confirmed leg decides the match or follows its decision (M4): the deciding leg
  stays current until "Finish game". The engine (`CricketMatch`) rejects such a list: `REJECTED_BY_ENGINE`;
- the darts of the current leg are replayed by the engine in one call; a dart after the win
  (`CricketLegFinishedException`, CR9) or any engine error is `REJECTED_BY_ENGINE`;
- the waiting and between-legs flags agree with the replayed match, as for X01: a won leg must wait, only a leg whose
  last dart completed a turn may wait, and between legs needs a confirmed leg and no dart in the current one
  (`INCONSISTENT_WAITING_FLAG`).

With at most 1,089 legs (M12) of at most 4 players and 99,999 points, every sum of the MPR and ranking code stays far
below `Int.MAX_VALUE`.

### Writing limits

`encode` returns `null`, and the previous file is kept (R12), when:

- the current leg, or a confirmed leg, has more than 10,000 darts (as in ADR 0001 and 0005);
- a confirmed Cricket leg has more than 99,999 points: reaching it takes more than 1,600 darts in one leg;
- the encoded file is over `MAX_FILE_BYTES` (see "Size limit").

### Size limit

`MAX_FILE_BYTES` stays 131,072 bytes. An X01 save stays under 92 KB (ADR 0005). A Cricket `leg` line is at most
90 bytes (`leg=3` and four `30000/10000/99999/21` figures), so the largest undecided match (4 players, 9 sets of
9 legs: 1,088 confirmed legs, M12) of the largest legs, with 10,000 triples in the current leg, would reach about
138 KB. No real game comes near it: such legs have 10,000 darts each. Rather than raising the limit, which the older
decoders also check, the encoder does not write a file over the limit, so a file it writes is always readable by
itself. Tests prove both sides: that match with misses in the current leg (about 118 KB) round trips, and with
triples it is not encoded.

### `Dart` and `Multiplier` stay in `engine/x01` (review A3)

`engine/cricket` imports `Dart` and `Multiplier` from `engine/x01`. Moving them to a shared package (`engine/dart`) is
a mechanical change over about 60 files, out of this mission's size rule. A follow-up issue to move them in a `chore`
pull request is to be created by the owner. Until then, `Dart` and `Multiplier` gain no Cricket-specific member: the
Cricket meaning of a dart (marks, target) lives in `engine/cricket` (`CricketMarks`, `CricketTarget`). The save
format is not affected: the dart tokens are the same for both modes.

### Last setup file

The last setup file moves to version 4 with the per-mode settings of R22 in the same mission (plan task T6), which
completes this ADR with its layout. Until then it is still written in version 3.

### Older app finding version 4

An older app rejects version 4 (R9, R10; ADR 0005, "Older app finding version 2"): the saved game is deleted with the
R9 message shown once. Accepted for the same reason as in ADR 0001, 0005 and 0006: there is only one user, who
installs builds from PRs. The PR states it under "Known limitations".

## Consequences

- The format is frozen again by exact-bytes tests of version 4: an X01 single game, an X01 match and a Cricket match
  between legs.
- The version 3 game and last setup files written before version 4 are committed under
  `app/src/test/resources/persistence/v3` and are never regenerated; the version 1 fixtures and the version 2 and 3
  golden literals are unchanged and keep decoding the same.
- An X01 file read from versions 1 to 3 re-encodes as version 4, with `mode=x01` after the version line.
- Later changes need a version 5 and a new ADR.
