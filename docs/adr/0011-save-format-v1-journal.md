# ADR 0011: Clean slate: save formats restart at version 1, with the match id and the dart journal

- Status: Proposed (design review of issue #50); accepted when its PR is merged. Supersedes the version history of
  ADR 0001, 0005, 0006 and 0008 (their envelope, body layouts and checks stay; their versions 1 to 4 are no longer
  read).

## Context

Issue #50 archives every confirmed match with all its darts, voided ones included, and their entry times
(`docs/rules/history.md`, DJ1 to DJ4, AR1 to AR6). Today the saved game (version 4, ADR 0008) keeps only the darts of
the current leg, and undo removes a dart. A match must survive a process death until it is archived, and be archived
once (AR4). The saved game therefore needs the match id, the match start time and the journal of every leg.

Kosmo (product owner, 2026-11-01, decision D24 of the plan): "Concernant le format de sauvegarde, on va tailler dans
le lard... Jusqu'à présent on était en mode proto. Avec ces évolutions, on peut considérer que l'on va vers une vraie
v1. Considere donc que l'on fait table rase des précédentes versions." Then (decision D25): "On fait table rase de
tout. Plus globalement, Tant que l'on n'a pas une vraie clé d'application donc qui permet des mises à jour, considère
que je fais tout le temps de la suppression et réinstallation de l'appli. Donc je n'ai aucun historique, aussi bien
sur les utilisateurs, sur les formats de sauvegarde et sur tout le bazar. [...] C'est qu'à partir de ce moment-là
qu'on considérera qu'on doit assurer de la backward compatibilité."

Until a release version that can be updated exists (its own issue, task T15 of the plan), the app is always installed
on an empty device storage: no stored data of an earlier build has to be read. Both files therefore restart at a
clean version 1: no migration, no reading of versions 1 to 4, no legacy branch in the code or in the archive.

## Options

1. Keep entries with a `voided` flag in the file. Rejected: the visit and rank of a voided dart depend on the darts in
   play when it was entered, which the flags alone cannot rebuild (a, b, undo, undo, c and a, undo, b, undo, c give
   the same flags but different ranks for b). The file would have to store the derived values too.
2. Store the journal as events (a dart with its time, or an undo) and derive everything by replay in the engine.
   Chosen: the stored data is the minimum, and DJ3 is one engine function with tests.
3. A database row per dart during the game. Rejected: two stores for one game in progress, and the saved game would
   no longer be the single source of a resume (ADR 0002).

## Decision

### Clean slate

- The files keep their names (`saved_game`, `last_setup`), their kind lines and the shared envelope of `SaveFormat`
  (kind, version, checksum, end). Each codec owns its version constant, and each accepts only `version=1`.
- Last setup, version 1: exactly the version 4 layout of ADR 0008 (shared roster, both modes' settings), renumbered.
  Saved game, version 1: the version 4 body of ADR 0008 (both modes, mode line included) plus the lines below.
- ADR 0001, 0005, 0006 and 0008 describe earlier formats: from now on they are historical only (review A10). No code
  reads a file as they number it; their body layout lives on only through this ADR, which is the one specification
  of both files.
- The version 1 to 3 layouts, the "no mode line means X01" branch, the default color slots of old files, their
  fixtures and their tests are removed (rules R16, R18, R25 and the old-version cases of R23 are withdrawn).
- No step looks for, converts or deletes files of an earlier build: none exists after a reinstall. Should one exist
  anyway (a development phone), it goes through the existing checks with no new code: a saved game whose version is
  not 1, or a version 1 written by an earlier build (no `match-id` line), is rejected (R9: message once, file
  deleted); an unreadable last setup is ignored silently (R10). Nothing can crash (R11).
- Options considered:
  1. Keep reading versions 1 to 4 and migrate them (the reviewed plan before D24). Rejected by Kosmo (D24, D25).
  2. Bump to version 5 and reject versions 1 to 4. Rejected: keeps numbers that mean nothing after a clean slate.
  3. New file names with a launch step deleting the old files unread. Rejected: legacy handling for data that cannot
     exist while Kosmo always reinstalls (D25).
  4. Restart at version 1 under the same names, the generic checks covering any stray file. Chosen.

### Saved game file, version 1

The version 4 body of ADR 0008, without change, then these lines, in this order, each once:

```
match-id=1b4e28ba-2d1c-41f1-9b7e-6a2f1c0d9e55
started-at=1793520000000
journals=2
journal=T20@1793520001200 5@1793520003400 u 1@1793520006100 ...
journal=D16@1793520088000 0@1793520090000
```

- `match-id`: a lower-case UUID (`java.util.UUID` text form). Anything else is `MALFORMED`.
- `started-at`: epoch milliseconds, canonical non-negative integer.
- `journals`: the number of `journal` lines, always the confirmed leg count plus one (the current leg last).
- `journal`: space-separated events in entry order, possibly none (`journal=`). A dart event is the dart token of the
  body, `@`, and its entry time in epoch milliseconds (canonical non-negative integer, absolute: D7). An undo event is
  `u`. Every leg has its journal and every dart its time: there is no "unknown" marker. The source is not stored:
  every event of version 1 is `MANUAL` (a later version adds the source with the camera).
- The `darts` line of the body stays and must equal the darts in play after replaying the last journal (DJ4);
  otherwise `INCONSISTENT_JOURNAL` (new `InvalidSaveReason`). Why it stays (review A1, D15): the body decoder and its
  checks (R9, R24, the waiting flags) are reused unchanged, so the new lines are added without touching tested code,
  and the cross-check catches a body and journal written out of step. The app has one
  place that changes both: `GameProgress` (T6), which applies a dart or an undo to the match and to the journal
  together; reducers never change one without the other.
- Numbers (review S2): every time is at most 19 digits, without sign, without leading zero (except `0`), and at most
  `Long.MAX_VALUE`; `-0`, `+1`, `007`, 20 digits or an overflowing value are `MALFORMED`, checked before any
  conversion, never a `NumberFormatException` nor a wrapped value. A journal line holds at most 30,000 events
  (10,000 darts in play, voided darts and undos); more is `OUT_OF_RANGE`. Replay is linear in the event count.
- A replay that the engine rejects (an undo with nothing to undo, a dart after the win) is `REJECTED_BY_ENGINE`.
- A confirmed leg journal must replay to a finished leg whose winner is that leg's winner in the body's `leg` line;
  otherwise `INCONSISTENT_JOURNAL`.

### Limits (R28)

- The 10,000-dart limit of R12 counts darts in play only, as before; voided darts and undo events do not count.
  A leg journal of more than 30,000 events is not encoded either (previous file kept), so a written file is always
  readable by its own decoder.
- `MAX_FILE_BYTES` of the saved game is 1,048,576. A dart event takes at most 17 bytes (`T20@` and a
  13-digit time) plus a space: a 1 MiB file holds about 58,000 events, far above any real match. The
  encoder returns `null` above the limit and the previous file is kept (R12); the archive is built from memory, so a
  match too large to save is still archived.
- A version this app does not know is `UNSUPPORTED_VERSION` (R9 for the saved game, R10 for the last setup).

### Engine types

`engine/journal`: `JournalEvent` (`DartEntered(dart, enteredAt: Long, source)`, `Undone`), `LegJournal(events)`,
`MatchJournal(matchId, startedAt: Long, legs: List<LegJournal>)` and the replay giving `JournalDart` values (DJ3).
No field is nullable: there is no match without id, start or journal. The codec only maps lines to these types;
replay and checks are engine code.

## Consequences

- The saved game grows by about 18 bytes per dart; a 9-set match of 81 legs of 60 darts stays near 90 KB.
- Undo and resume behave as before for the player; R4 and R5 are unchanged.
- Less code: the version 1 to 3 layouts and branches of both codecs, with their fixtures and tests, are removed.
- Until the release version (T15's issue), a format may still change in place, by owner decision, without migration.
  From that release on, every change of a stored format ships a tested migration.
- Tests: round trip of each mode with undos, each rejection reason, the size limit on both sides (just under and just
  over 1 MiB), `version=1` the only version accepted by each codec (a `version=4` file is `UNSUPPORTED_VERSION`), and
  the fuzz test on both files.
