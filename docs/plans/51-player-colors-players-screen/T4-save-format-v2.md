# T4: Saved game and last setup format v2 (ARGB)

## Goal
The saved game and the last setup store each player's color as ARGB, at format version 2. Root "Approach" 2 and
"Data read, stored or exposed".

## Files
- `app/.../persistence/SaveBody.kt`: `parseColors(value, playerCount)` replaces `parseColorSlots`; encoder writes
  `%08X` per player; the T3 palette index mapping is removed.
- `CricketSaveBody.kt`, `LastSetupCodec.kt`, `GameSaveCodec.kt` (`VERSION = 2`), `LastSetupCodec` `VERSION = 2`.
- `docs/adr/0013-save-format-v2-argb-colors.md` (new): the `colors` line, version 2, why version 1 is not read
  (R30, D9), what changes at the first release.
- Tests: `SaveColorsCodecTest`, `LastSetupCodecTest`, `GameSaveCodec` tests, `SaveFuzzTest` (both codecs still
  covered), `PersistenceHistoryTest` as needed.

## Depends on
T3.

## Rules
R17, R31, R9, R10, R11, R30; PC1, PC7, PC8.

## Tests
- Round trip of X01 and Cricket saves and of the last setup with a generated (non-palette) color.
- Rejected (saved game: R9; last setup: R10, ignored): a version 1 file; a token that is not 8 hex digits;
  lowercase hex; alpha not `FF`; a repeated color; a wrong count. No crash (R11).
- Rematch after resume keeps the colors (PC7); setup from the last setup restores them (PC8).
- Fuzz test still runs on both codecs.
- D15 (review S2): a save whose opaque, distinct colors fail PC11 (for example `FF000000` and `FF0A0F1E`) is
  accepted and resumes with those colors.

## Done when
`./gradlew :app:testDebugUnitTest`, `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
opus. Why: case "new or changed stored format" (saved game and last setup format version 2).

## Implementation notes
