# T5: Saved game format v1 with match id and journal (clean slate)

## Goal
The saved game restarts at format version 1 (clean slate, R30, D24, D25): the version 4 body plus the match id, start
time and journal of every leg (R26, R28, R29). No earlier version is read. ADR 0011 is the specification.

## Files
- Changed in `app/src/main/kotlin/.../persistence/`: `SaveFormat.kt` (version taken from the caller; no shared
  `VERSION` default for the saved game), `GameSaveCodec.kt` (own `VERSION = 1`, only version accepted; the version 1
  to 3 layouts and the "no mode line means X01" branch removed, D25), `SaveBody.kt`, `CricketSaveBody.kt`,
  `SavedGame.kt`, `SavedCricketGame.kt`, `StoredGame.kt` (adds `journal: MatchJournal`), `SaveLoader.kt`,
  `InvalidSaveReason` (`INCONSISTENT_JOURNAL`), `MAX_FILE_BYTES`. Anything shared with `LastSetupCodec` stays as is:
  the last setup is T14.
- Tests in `app/src/test/kotlin/.../persistence/`: new `SaveJournalCodecTest.kt`; fixtures and existing saved game
  tests moved to version 1 with a journal. Saved game cases that only prove the reading of versions 1 to 3 (R16, R18,
  R25, old cases of R23; in `SaveV3CompatibilityTest`, `GameSaveMatchCodecTest`, `SaveColorsCodecTest`,
  `GameSaveCodecTest`, `GameSaveDecodeInvalidTest`) are deleted, each listed by name in the notes with its withdrawn
  rule; no other test is removed or weakened.
- `docs/adr/0011-save-format-v1-journal.md`: status line only if the implementation had to deviate (report it).

## Depends on
T3.

## Rules
R26, R28, R29, R30, R9 to R12, R23, DJ4, AR4. ADR 0011 (all sections), ADR 0008 (version 4 body unchanged).

## Approach
- The codec maps lines to `MatchJournal`; replay and checks call the T3 engine code. No id source in the codec: ids
  and times come only from the app (T6).
- No file is renamed, searched for or deleted at launch for old builds (ADR 0011, "Clean slate").
- `encode` refuses above 1 MiB and above 10,000 darts in play in the current leg (R28).

## Tests
- Round trip X01 and Cricket with undos and several legs.
- Version: `version=1` is the only one accepted; a `version=4` saved game is `UNSUPPORTED_VERSION`; a version 1 file
  without the journal lines is `MALFORMED` (R30, through R9: deleted, message once).
- Old layout (review T1): one literal fixture written in the old version 1 saved game layout (`version=1`, no mode
  line, no journal, valid checksum), kept in the test source: rejected, through R9 (file deleted, message once),
  never decoded as a new version 1 game.
- Each R29 rejection: bad UUID, bad time, journal count, DJ4 mismatch, undo with nothing to undo, confirmed journal
  winner mismatch.
- Size: a match just under 1 MiB round trips; just over is not encoded; 10,000 darts in play plus 500 voided encodes.
- `SaveFuzzTest` on version 1 saved games with journals: never throws.
- Numbers (ADR 0011, review S2): 20 digits, a value over `Long.MAX_VALUE`, `-0`, `+1`, `007`, `-5`: each `MALFORMED`,
  never a `NumberFormatException` nor a wrapped value. A leg journal of 30,001 events is `OUT_OF_RANGE` and is not
  encoded. One file of about 58,000 events over several legs round trips; its decode time is logged in the notes,
  never asserted.

## Done when
`./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: no migration any more (D24, D25); the format, its limits and every rejection are fully specified in
ADR 0011 with listed tests, so none of the three `opus` cases applies.

## Implementation notes
