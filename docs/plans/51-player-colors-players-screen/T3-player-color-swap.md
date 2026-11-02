# T3: PlayerColor replaces ColorSlot everywhere

## Goal
Every engine and app type carries a `PlayerColor` instead of a `ColorSlot`; `ColorSlot` is deleted. Behavior and
stored files do not change: only palette colors exist until T5. Root "Approach" 1 and 2.

## Files
- Engine: `player/ColorSlot.kt` (removed; `requireValidColors` and `InvalidPlayerColorsException` move to
  `PlayerColor.kt`), `player/Roster.kt` (`RosterEntry.color`, PC2 through `ColorChoices.defaultFor`),
  `x01/X01MatchConfig.kt`, `x01/X01Match.kt`, `cricket/CricketMatchConfig.kt`, `cricket/CricketMatch.kt`,
  `archive/MatchArchiver.kt`, `archive/ArchivedPlayer.kt`.
- App: `ui/theme/PlayerPalette.kt` (`colorOf(PlayerColor)`, `onColorOf` from `PlayerColorRules.textOn`), every app
  file listed by `grep -rln ColorSlot app/src/main`; `history/ArchiveRepository.kt` stores `PlayerColor.argb`.
- Persistence (`SaveBody.kt`, `CricketSaveBody.kt`, `LastSetupCodec.kt`): still write and read slot numbers 1 to 4,
  through a temporary `PlayerColor.PALETTE` index mapping; T4 replaces it. A non-palette color cannot occur yet.
- Tests: every test using `ColorSlot` (`grep -rln ColorSlot */src/test`), mechanically.

## Depends on
T2.

## Rules
PC1 to PC9, PC13; R17.

## Tests
- Existing tests stay green after the rename, none removed or weakened; slot assertions become color assertions.
- New `PlayerPaletteTest` (JVM): `onColorOf` equals the former `onPlayerN` token for each palette color (PC13).
- `Roster` tests for PC2 to PC5 with colors; a roster with a non-palette color (T5 will create them) passes PC1.
- Captures (recorded by `testDebugUnitTest` under `app/build/outputs/roborazzi`): same images as on `main`, compared
  by eye on the setup, game and History captures.

## Done when
`./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
opus. Why: case "refactoring across modules" (about 55 files in both modules, one green step).

## Implementation notes
