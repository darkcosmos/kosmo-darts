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

## Done when
`./gradlew :app:testDebugUnitTest` and `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: removal of specified branches with their tests; judging which test proves only an old version needs
care, no design choice.

## Implementation notes
