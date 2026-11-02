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
- Captures: manual check (D18; the build records Roborazzi images but has no compare task). Record on `main` and on
  this branch, then `cmp` each PNG of `app/build/outputs/roborazzi`; list in the notes any image that differs and
  why. The colors themselves are proved by `PlayerPaletteTest`.

## Done when
`./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
opus. Why: case "refactoring across modules" (about 55 files in both modules, one green step).

## Implementation notes
- Names: `RosterEntry.color`, `Roster.colors`, `X01MatchConfig.colors`, `CricketMatchConfig.colors`, `X01Match.colors`,
  `CricketMatch.colors`, `ArchivedPlayer.color`, `SharedRoster.colors`, `GameScreenState.playerColorOf(index)`;
  UI content fields `slot` became `color` (`winnerColor`, `nextStarterColor`). `PlayerColor.byPosition(count)` (PC5
  default) replaces `ColorSlot.byPosition`. `Roster.MAX_PLAYERS` is now `PlayerListRules.MAX_PLAYERS`.
- `Roster.withPlayerAdded(random = Random.Default)` uses `ColorChoices.defaultFor`; T5 must pass the reducer's Random.
- `PlayerPalette.colorOf/onColorOf(color)` lost their `KosmoColors` parameter: `Color(argb)` and
  `PlayerColorRules.textOn`. The `playerN`/`onPlayerN` tokens stay in `KosmoColors` (still used by tests).
- Persistence slot mapping lives in `persistence/SlotColors.kt` (`parseSlotColors`, `slotColorsValue`, the latter
  throws for a non-palette color); T4 deletes this file. Moved out of `SaveBody.kt` for detekt `TooManyFunctions`.
- Deviation: `ArchiveRepository.kt` unchanged; `PersistenceEffect.archiveEffect` now passes `it.color.argb` instead
  of the Compose token, so the stored ARGB comes from `PlayerColor` (same values).
- Captures (D18), `cmp` of the 279 PNGs from `:app:testDebugUnitTest` on `main` (f699893) and on this branch: 274
  identical; 5 to 6 differ per run, all timing-dependent and also different between two runs of `main` itself:
  `archive_cricket_saving_*`, `archive_cricket_failed_landscape_*` (archive status animation),
  `settings_three_switches_landscape_en` (switch animation), `settings_voice_available_portrait_en` (voice check
  race: "Checking the voice..." vs "Offline voice available"). Visual check of an archive pair: no visible change.
