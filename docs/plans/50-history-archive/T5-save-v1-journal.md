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
- Entry time required (review S5, D31): a dart event with an unknown-time token (`T20@-`) or without time (`T20`,
  `T20@`) is `MALFORMED`; ADR 0011 has no "unknown" marker, so `entered_at` never needs a sentinel at archive time.
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
Where: `persistence/JournalCodec.kt` (`writeJournal`, `readJournal`, `checkJournal`), `GameSaveCodec` (version 1 only,
journal lines after the body), `SaveFormat` (`encode` takes the version; new `InvalidSaveReason.INCONSISTENT_JOURNAL`),
`StoredGame.journal`. Tests: `SaveJournalCodecTest`, `JournalFixtures`.

Deviations and what later tasks must know:
- **Transitional bridge, T6 must remove it.** `SavedGame` and `SavedCricketGame` get a last constructor parameter
  `journal`, defaulting to `TransitionalJournal.of(match)` (`TransitionalJournal.kt`): a made-up journal (id
  `0000...`, times 0, 1, 2...) in which each confirmed leg is won by its winner and the current leg holds its darts.
  It exists only so that the ~60 existing call sites keep compiling. T6 records the real journal, drops the default
  and deletes `TransitionalJournal.kt`. The version 1 goldens of the match and of Cricket use it (id `0000...`).
- **No `LegReplay` on decode.** It is quadratic near the 30,000-event cap. `checkJournal` uses a lazy stack
  (`LegCheck`) that rejects exactly what `LegReplay` would (undo with nothing, dart after the win, 10,000 darts in
  play), checking the engine only at local maxima of the stack, so the archive replay cannot throw later. A decode of
  a file of 58,028 events (580,873 bytes, four legs) took 10 ms on this machine (logged, not asserted).
- Rejections: journal count, darts-in-play (D25) and confirmed-leg winner mismatches are the new
  `INCONSISTENT_JOURNAL`; undo with nothing to undo and a dart after the win are `REJECTED_BY_ENGINE` (so would be a
  `JournalReplayException`); unknown dart token is `OUT_OF_RANGE`; a missing entry time (`T20@-`, `T20`, `T20@`, `@-`),
  a bad number or a bad id is `MALFORMED`. The engine check of the match itself still runs before the journal check.
- `encode` returns `null` (previous file kept) when the id is not a lower-case UUID, a time is negative, a source is
  not `MANUAL`, the journal count is wrong, a leg journal has more than 30,000 events, or the file exceeds 1 MiB.
- `LastSetupCodec` keeps its own private `VERSION_1..4` constants (marked "until T14"), because `SaveFormat.VERSION_*`
  were removed; T14 replaces them with its own version 1.
- `src/test/resources/persistence/v1` and `v3`: only the `game-*.save` files are removed; `last-setup.save` and the
  READMEs stay (T14). `V1Fixtures` and `V3Fixtures.GAMES` are removed.
- The literal old-version-1 layout fixture is inline in `R30 a file in the layout of the old version 1 is rejected`.

Tests deleted, each with the rule it withdraws (R30: clean slate, no migration):
- `GameSaveCodecTest`: `format v1 golden file`, `format v2 golden file`, `format v3 golden file` (R16, R18, R25).
  The v4 golden is now `format v1 golden file of a single game`.
- `GameSaveMatchCodecTest`: `format v2 golden file of a match`, `format v3 golden file of a match`,
  `R16 version 1 files decode as single games`, `R13 a single game is its version 1 content plus the new keys`
  (R13, R16). The v4 golden is now `format v1 golden file of a match`; the V1 key cases of `R9 keys must follow the
  layout of their version` were dropped (no version 1 old layout any more).
- `GameSaveDecodeInvalidTest`: `R23 a mode line in a version 1, 2 or 3 file is malformed` (R23 for old versions).
- `SaveColorsCodecTest`: `R18 version 1 games get slots by position` (R18; the last-setup test stays for T14).
- `SaveV3CompatibilityTest`: `R25 every version 3 saved game decodes to its X01 game` and `R25 a version 3 saved game
  re-encodes as version 4 with the X01 mode line` (R25; the last-setup test stays for T14).
- `MatchResumeScreensTest`: `R16 a version 1 save resumes as a single game` (R16).

Tests adapted (not weakened): `CricketSaveCodecTest` golden (now version 1 with journal lines);
`CricketSaveDecodeBoundsTest` R12 size test (the 1 MiB limit is now reached with 14,000 voided darts in each of four
legs, not with the darts alone) and the R24 decided-match test (journal lines added so the engine check is reached);
the same for `GameSaveMatchCodecTest` R9 decided-match; `SaveFuzzTest` seeds (version 1 games with journals, journal
mutations, canonical re-encoding check).
