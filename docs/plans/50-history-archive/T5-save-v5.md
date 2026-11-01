# T5: Save format v5 with migration of v1 to v4

## Goal
The saved game carries the match id, start time and the journal of every leg (format version 5), and older files
are migrated on read (R26 to R29). ADR 0011 is the specification.

## Files
- Changed in `app/src/main/kotlin/.../persistence/`: `SaveFormat.kt` (`VERSION_5`), `GameSaveCodec.kt`,
  `SaveBody.kt`, `CricketSaveBody.kt`, `SavedGame.kt`, `SavedCricketGame.kt`, `StoredGame.kt` (adds
  `journal: MatchJournal`), `SaveLoader.kt`, `InvalidSaveReason` (`INCONSISTENT_JOURNAL`), `MAX_FILE_BYTES`.
- Tests in `app/src/test/kotlin/.../persistence/`: new `SaveV5CodecTest.kt`, `SaveV5MigrationTest.kt`; fixtures.
- `docs/adr/0011-save-format-v5-journal.md`: status line only if the implementation had to deviate (report it).

## Depends on
T3.

## Rules
R26, R27, R28, R29, R9 to R12, DJ4, DJ5, AR4. ADR 0011 (all sections), ADR 0008 (version 4 body unchanged).

## Approach
- The codec maps lines to `MatchJournal`; replay and checks call the T3 engine code. A random id source is injected
  for R27 (tests use a fixed one).
- The last setup codec is not changed (stays version 4).
- `encode` refuses above 1 MiB and above 10,000 darts in play in the current leg (R28).

## Tests
- Round trip X01 and Cricket with undos, several legs, unknown leg journals and unknown times.
- Every existing v1 to v4 fixture reads as before plus the R27 values (new id from the injected source).
- Each R29 rejection: bad UUID, bad time, journal count, DJ4 mismatch, undo with nothing to undo, confirmed journal
  winner mismatch.
- Size: a match just under 1 MiB round trips; just over is not encoded; 10,000 darts in play plus 500 voided encodes.
- `SaveFuzzTest` extended to version 5 files: never throws.

## Done when
`./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
opus. Why: case "new or changed stored format with its migration" (format v5 and v1 to v4 migration).

## Implementation notes
