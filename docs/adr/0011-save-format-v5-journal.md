# ADR 0011: Save format version 5 for the match id and the dart journal

- Status: Proposed (design review of issue #50); accepted when its PR is merged

## Context

Issue #50 archives every confirmed match with all its darts, voided ones included, and their entry times
(`docs/rules/history.md`, DJ1 to DJ5, AR1 to AR7). Today the saved game (version 4, ADR 0008) keeps only the darts of
the current leg, and undo removes a dart. A match must survive a process death until it is archived, and be archived
once (AR4). The saved game therefore needs the match id, the match start time and the journal of every leg.

## Options

1. Keep entries with a `voided` flag in the file. Rejected: the visit and rank of a voided dart depend on the darts in
   play when it was entered, which the flags alone cannot rebuild (a, b, undo, undo, c and a, undo, b, undo, c give
   the same flags but different ranks for b). The file would have to store the derived values too.
2. Store the journal as events (a dart with its time, or an undo) and derive everything by replay in the engine.
   Chosen: the stored data is the minimum, and DJ3 is one engine function with tests.
3. A database row per dart during the game. Rejected: two stores for one game in progress, and the saved game would
   no longer be the single source of a resume (ADR 0002).

## Decision

### Game file, version 5

The version 4 body (ADR 0008, both modes) without change, then these lines, in this order, each once:

```
match-id=1b4e28ba-2d1c-41f1-9b7e-6a2f1c0d9e55
started-at=1793520000000
journals=3
journal=?
journal=T20@1793520001200 5@1793520003400 u 1@1793520006100 ...
journal=D16@- 0@1793520090000
```

- `match-id`: a lower-case UUID (`java.util.UUID` text form). Anything else is `MALFORMED`.
- `started-at`: epoch milliseconds, canonical non-negative integer, or `-` for a match started before version 5
  (AR7).
- `journals`: the number of `journal` lines, always the confirmed leg count plus one (the current leg last).
- `journal`: `?` for a confirmed leg without journal (a match started before version 5); otherwise space-separated
  events in entry order, possibly none (`journal=`). A dart event is the version 1 dart token, `@`, and its entry time
  in epoch milliseconds (canonical non-negative integer; absolute, because a match resumed from an older version has
  no start time while its new darts have one), or `-` when unknown. An undo event is `u`. The
  source is not stored: every event of version 5 is `MANUAL` (a later version adds the source with the camera).
- The `darts` line of the version 4 body stays and must equal the darts in play after replaying the last journal
  (DJ4); otherwise `INCONSISTENT_JOURNAL` (new `InvalidSaveReason`). Why it stays (review A1): the version 4 body
  decoder and its checks (R9, R24, the waiting flags) are reused unchanged for every version, so version 5 adds lines
  without touching tested code, and the cross-check catches a body and journal written out of step. The app has one
  place that changes both: `GameProgress` (T6), which applies a dart or an undo to the match and to the journal
  together; reducers never change one without the other.
- Numbers (review S2): every time is at most 19 digits, without sign, without leading zero (except `0`), and at most
  `Long.MAX_VALUE`; `-0`, `+1`, `007`, 20 digits or an overflowing value are `MALFORMED`, checked before any
  conversion, never a `NumberFormatException` nor a wrapped value. A journal line holds at most 30,000 events
  (10,000 darts in play, voided darts and undos); more is `OUT_OF_RANGE`. Replay is linear in the event count.
- A replay that the engine rejects (an undo with nothing to undo, a dart after the win) is `REJECTED_BY_ENGINE`.
- A confirmed leg journal, when known, must replay to a finished leg whose winner is that leg's winner in the
  version 4 `leg` line; otherwise `INCONSISTENT_JOURNAL`.

### Older versions

Versions 1 to 4 are read as before, then given a new random match id, `started-at=-`, `?` for every confirmed leg and
a current-leg journal of the `darts` tokens with unknown times (R27, DJ5). The last setup file stays version 4.

### Limits (R28)

- The 10,000-dart limit of R12 counts darts in play only, as before; voided darts and undo events do not count.
  A leg journal of more than 30,000 events is not encoded either (previous file kept), so a written file is always
  readable by its own decoder.
- `MAX_FILE_BYTES` becomes 1,048,576 for every saved game version. A dart event takes at most 17 bytes (`T20@` and a
  13-digit time) plus a space: a 1 MiB file holds about 58,000 events, far above any real match. The
  encoder returns `null` above the limit and the previous file is kept (R12); the archive is built from memory, so a
  match too large to save is still archived.
- An older app finding version 5 rejects it as `UNSUPPORTED_VERSION` (R9), as in ADR 0008; Android does not
  downgrade an app without uninstalling it, which deletes the file anyway.

### Engine types

`engine/journal`: `JournalEvent` (`DartEntered(dart, enteredAt: Long?, source)`, `Undone`), `LegJournal(events)`,
`MatchJournal(matchId, startedAt: Long?, legs: List<LegJournal?>)` and the replay giving `JournalDart` values (DJ3).
The codec only maps lines to these types; replay and checks are engine code.

## Consequences

- The saved game grows by about 18 bytes per dart; a 9-set match of 81 legs of 60 darts stays near 90 KB.
- Undo and resume behave as before for the player; R4 and R5 are unchanged.
- Tests: round trip of each mode with undos, decoding of every version 1 to 4 fixture into version 5 values, each new
  rejection reason, and the size limit on both sides (just under and just over 1 MiB).
