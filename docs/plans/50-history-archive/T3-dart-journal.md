# T3: Dart journal and replay

## Goal
`:game-engine` keeps a per-leg journal of events and replays it into dart records with player, visit, rank, bust and
voided (DJ1 to DJ5). Root plan "Approach" 2, ADR 0011 "Engine types" and "Options".

## Files
- Added in `game-engine/src/main/kotlin/.../engine/journal/`: `Clock.kt` (`fun interface Clock { fun nowMillis():
  Long }`), `DartSource.kt` (`MANUAL`, `CAMERA`), `JournalEvent.kt` (`DartEntered(dart, enteredAt: Long?, source)`,
  `Undone`), `LegJournal.kt`, `MatchJournal.kt` (`matchId: String`, `startedAt: Long?`, `legs: List<LegJournal?>`,
  `null` = unknown, DJ5; operations `enter(dart, at)`, `undo()`, `startNextLeg()`, `legacy(...)`), `JournalDart.kt`,
  `LegReplay.kt` (X01 and Cricket replay of a `LegJournal` with the leg's config and starter).
- Tests in `game-engine/src/test/kotlin/.../engine/journal/`.

## Depends on
None.

## Rules
DJ1 to DJ5. Existing X01 and Cricket rules (`docs/rules/x01.md`, `docs/rules/cricket.md`) for busts and turns.

## Approach
- Replay applies events to the engine leg (`X01Game` / `CricketGame`): `DartEntered` records the player, visit and
  rank from the state before the throw, then throws; `Undone` marks the latest non-voided record voided and calls
  `undo()`. An `Undone` with nothing to undo, or a dart after the win, throws a dedicated exception (T5 maps it to
  `REJECTED_BY_ENGINE`).
- Bust (X01): every record of a bust visit, from the replayed state's completed turns; Cricket: always false.
- `MatchJournal.undo()` must only be called for an effective undo (DJ2): T6 checks it.

## Tests
- Visits and ranks for 1 to 4 players across turns and with a non-zero starter.
- Undo across a turn boundary; the case a, b, undo, undo, c (b rank 2) versus a, undo, b, undo, c (b rank 1).
- Undo of the winning dart (X01 and Cricket): it is voided, the leg is no longer won; undo right after a bust; undo
  of the first dart of a new leg journal (only that leg changes; with nothing to undo it is rejected, DJ2). DJ4 is
  asserted after each of these undos.
- X01 busts named by finish mode, on 501 and on 301: double-out over-score, remainder 1, and finish on a non-double
  are busts (all darts of the visit marked bust); in single-out the same finishing dart wins and is not bust. The
  winning dart is never bust. A leg won with 1 dart and with 2 darts in the last visit: visit and rank values.
- Cricket never bust.
- DJ4: darts in play equal the leg's darts; `legacy(darts)` gives unknown times and no voided dart (DJ5).
- Rejections: undo on an empty leg, dart after the win.

## Done when
`./gradlew :game-engine:test` and `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: specified engine rules DJ1 to DJ5 with listed tests.

## Implementation notes
