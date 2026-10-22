# ADR 0005: Save format version 2 for matches in legs and sets

- Status: Proposed (design review of issue #39); accepted when its PR is merged
- Date: 2026-10-21
- Extends: ADR 0001 (save format). ADR 0002 (save lifecycle) is unchanged, except that the between-legs screen counts
  as the game screen for R1.
- Rules: `docs/rules/persistence.md` (R9, R11, R12, R13 to R16), `docs/rules/match.md` (M1, M4, M6, M8, M9, M10, M12)

## Context

Issue #39 adds matches over several legs and sets. ADR 0001 requires a new format version for any new key, with the
older decoders kept. A saved match must hold the format, what is needed of every confirmed leg, the current leg, and
whether the between-legs screen is shown.

## Options for the confirmed legs

- A. A summary per confirmed leg (winner, then each player's points and darts) and the darts of the current leg only
  (chosen). M8 makes confirmed legs final, and M9 and M10 only need their winner, points and darts.
- B. Every dart of every leg: only needed for a history, which is out of scope. A long match (up to 1,089 legs) would
  pass 10,000 darts and stop being saved (R12), and the size limit would have to grow far more.
- C. Match totals per player only: points over a long match pass the 5-digit integer limit (1,089 x 501), and the
  save could not be checked leg by leg.

## Decision

Option A, in format version 2 of both files. Every write uses version 2; version 1 files are still decoded.

### Game file, version 2 (keys in this order)

```
kosmodarts=saved-game
version=2
game=x01
start-score=301
checkout=single-out
legs-per-set=2
sets=2
players=2
player=QW5u
player=Qm9i
legs=2
leg=0 301/9 120/6
leg=1 200/9 301/9
darts=T20 20
awaiting-next-player=false
between-legs=false
crc32=03fea0a7
end
```

- `legs-per-set`, `sets`: 1 to 9 (M1).
- `legs`: the number of confirmed legs, 0 to `maxLegs` = (n(L-1)+1) x (n(S-1)+1) (M12), then exactly that many `leg`
  lines in play order. A `leg` value is the winner index, then for each player in configuration order
  `points/darts` (canonical integers).
- The ranges of a leg are the engine's (`X01LegSummary`): winner in range; the winner's points equal the start score;
  every other player's points from 0 to start score - 1 (S1); darts from 0; at most 60 points per dart. The codec
  maps the engine's `IllegalArgumentException` to `OUT_OF_RANGE` and adds only the storage limit of R12 (at most
  10,000 darts in a leg). A wrong number of figures, or a figure without its `/`, is `MALFORMED`.
- The winner sequence is replayed by the engine (`MatchScore`, through `X01Match`): a leg after the match is
  decided, or confirmed legs that decide the match (the deciding leg is never confirmed), is `REJECTED_BY_ENGINE`.
- `darts`: the current leg only, as in version 1, replayed with the starter `legs mod players` (M6).
- `between-legs`: `true` only when at least one leg is confirmed, the current leg has no dart, and
  `awaiting-next-player` is `false` (`INCONSISTENT_WAITING_FLAG` otherwise).

### Last setup file, version 2

Version 1 lines plus `legs-per-set` and `sets` after `checkout`.

### Strict keys (S2)

For each version, the ordered list of keys is defined once in `GameSaveCodec` and in `LastSetupCodec` (their
`V1_LAYOUT` and `V2_LAYOUT`), and both writing and reading follow it through the strict `BodyReader`. Any missing,
extra, duplicated or out-of-order key is `MALFORMED`, and so is a version 1 file that carries a version 2 key.

### Version 1 files (R16)

A version 1 game is a single game: format 1 and 1, no confirmed leg, not between legs. A version 1 last setup has the
format 1 and 1. No message is shown. The next write uses version 2. `SaveFormat.decode` receives the versions a kind
of file accepts and returns the version read; any other version is `UNSUPPORTED_VERSION`.

### Older app finding version 2 (S3)

An older app rejects version 2 (R9, ADR 0001). Under ADR 0002 decision 5, it deletes every rejected file: a version
2 game file is deleted with the R9 message shown once, a version 2 last setup is deleted silently (R10). Nothing is
kept for a later upgrade. This is accepted for the same reason as in ADR 0001: there is only one user, who installs
builds from PRs.

### Size limit

`GameSaveCodec.MAX_FILE_BYTES` goes from 65,536 to 131,072 bytes, one named constant used by the tests.

- A `leg` line is at most `leg=` (4) + winner (1) + 4 x ` 501/10000` (10 each) + LF (1) = 46 bytes, so 1,089 lines
  take at most 50,094 bytes.
- The new header lines (`legs-per-set=9`, `sets=9`, `legs=1089`, `between-legs=false`) add under 64 bytes.
- The version 1 content is under 41 KB (ADR 0001).
- The total stays under 92 KB, below 131,072. A test builds the largest valid save (4 names of 20 emoji, 9 legs and
  9 sets, the longest leg sequence with the widest lines, 10,000 darts in the current leg) and checks that it is
  under the limit and decodes; a file of `MAX_FILE_BYTES + 1` bytes is rejected (`TOO_LARGE`).

The encoder refuses (returns `null`, so the previous file stays, as R12) when the current leg or a confirmed leg has
more than 10,000 darts.

## Consequences

- The save format is frozen again by exact-bytes tests: version 2 of a single game and of a match. The version 1
  files written by the version 1 encoders are kept as test fixtures (`app/src/test/resources/persistence/v1`) and must
  keep decoding.
- A single game re-encodes to exactly its version 1 content, with `version=2` and the new keys inserted at their
  places (tested).
- Later changes (history, other modes) need a version 3 and a new ADR.
