# T14: Last setup format restarts at version 1 (clean slate)

## Goal
The last setup file restarts at format version 1, exactly the current version 4 layout renumbered, and reads no
earlier version (R30, D25). ADR 0011, "Clean slate".

## Files
- Changed in `app/src/main/kotlin/.../persistence/`: `LastSetupCodec.kt` (own `VERSION = 1`, only version accepted;
  the version 1 to 3 layouts, the "no mode line" and default color slot branches removed), `SaveFormat.kt` (remove
  the `VERSION_1` to `VERSION_4` constants and any layout left unused after T5 and this task), `SaveBody.kt` only if
  a shared layout becomes unused.
- Tests in `app/src/test/kotlin/.../persistence/`: `LastSetupCodecTest.kt` and fixtures moved to version 1. Cases
  that only prove the reading of versions 1 to 3 (R16, R18, R25, old cases of R23) are deleted, each listed by name in
  the notes with its withdrawn rule; no other test is removed or weakened.

## Depends on
T5 (same files; avoids conflicting edits).

## Rules
R7, R8, R10, R11, R13, R17, R22, R23, R30; ADR 0008 (layout unchanged), ADR 0011 "Clean slate".

## Approach
- The written content is the version 4 content with `version=1`; nothing else changes (roster, modes, settings).
- No launch step for files of earlier builds: a stray old file is ignored silently by R10.

## Tests
- Round trip of the last setup with both modes' settings, colors and 1 to 4 players (existing cases, version 1).
- `version=1` is the only one accepted; a `version=4` last setup is ignored silently (R10) and the setup shows the
  defaults; the saved game is not affected (R8).
- `SaveFuzzTest` on version 1 last setups: never throws.
- Old layout (review T1): one literal fixture written in the old version 1 last setup layout (`version=1`, valid
  checksum), kept in the test source: ignored silently (R10, defaults shown, file deleted), never read as a new
  version 1 setup.

## Done when
`./gradlew :app:testDebugUnitTest` and `./gradlew build` green, `SaveFuzzTest` included and still covering both
codecs after the `VERSION_1` to `VERSION_4` constants are removed (review T2); `kosmo-detekt` exit 0.

## Model
sonnet. Why: removal of specified branches with their tests; judging which test proves only an old version needs
care, no design choice.

## Implementation notes
- `LastSetupCodec`: own `VERSION = 1` (only version accepted), written content is the former version 4 layout; the
  `VERSION_1..4` constants, the V1 to V3 layouts, the "no mode line" branch and `LAYOUTS` are gone. `SaveFormat.kt` and
  `SaveBody.kt` needed no change (T5 had already removed the constants; `readBody` and layouts stay used by the game).
- Literal old-layout fixture: `SaveFixtures.OLD_V1_LAST_SETUP` (valid checksum, from the former
  `resources/persistence/v1/last-setup.save`). Tests: `LastSetupCodecTest` `R30 a file in the layout of the old
  version 1 is ignored` and `R30 a version 4 last setup is ignored`; `SaveLoaderTest` `R30 a last setup in the old
  version 1 layout is ignored and deleted` (defaults shown, file deleted, game kept).
- `src/test/resources/persistence/` (v1 and v3 last setups and READMEs) removed. `SaveFuzzTest` unchanged: it still
  covers both codecs (it never used the constants).
- Tests deleted, with the rule each withdraws (R30, clean slate):
  - `LastSetupCodecTest`: `R13 R17 R25 a version 3 last setup is read as X01` (R17, R25), `R25 an older last setup
    gives the Cricket defaults with the roster` (R25), `R18 a version 2 last setup has slots by position` (R18),
    `R16 a version 1 last setup is a single game` (R16).
  - `SaveColorsCodecTest`: `R18 a version 1 last setup gets slots by position` (R18).
  - `SaveV3CompatibilityTest` (file): `R25 the version 3 last setup decodes to its X01 setup` (R25).
- Tests adapted, not weakened: `LastSetupCodecTest` `R23 mode line rejections` lost its two entries for a mode line in
  versions 2 and 3; `R10 keys must follow the layout of their version` became `R10 keys must follow the version 1
  layout` (version 1 to 3 cases dropped, version 4 cases kept on version 1); the `version=5` case is now `version=2`.
