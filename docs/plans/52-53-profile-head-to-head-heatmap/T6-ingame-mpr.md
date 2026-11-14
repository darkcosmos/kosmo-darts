# T6: In-game MPR on useful marks

## Goal
The MPR shown during a Cricket game, between legs and at the end counts useful marks (owner Q2 B), derived from the
match journal. Root "Approach" 2, decision D1.

## Files
- `game-engine/.../engine/cricket/CricketMatch.kt`: MPR of a player computed from the match journal (completed legs
  and current leg) with `usefulMarks` (T5); `marksHit` stays for the leg summary checks (R24) and CR14.
- `app/.../ui/cricket/CricketGameScreenState.kt`, `app/.../ui/between/BetweenLegsState.kt`,
  `app/.../ui/end/EndRow.kt` (or their Cricket builders): use the new MPR.
- `app/.../ui/app/AppState.kt` and the reducer that builds `Screen.CricketEnd`: `CricketEnd` carries the
  `MatchJournal` (also needed by T26).
- Tests citing CR13: `CricketStatisticsTest`, `CricketMatchTest`, `CricketClosingTest`,
  `CricketGameScreenStateTest`; `MprFormatTest` unchanged.

## Depends on
T5.

## Rules
CR13 (rewritten), CR14 (unchanged), CR11 undo, DJ2.

## Tests
- MPR over two completed legs and the current one on useful marks; surplus on a dead target lowers the MPR.
- Undo at every step: undoing a closing dart, a scoring dart, the winning dart (voided darts excluded).
- Solo: MPR counts only closing marks.
- No dart: no MPR. Process death: a saved game reloaded gives the same MPR (journal replay).
- CR14 visit marks still count all marks.

## Done when
`./gradlew :game-engine:test`, `./gradlew build` and `./gradlew :app:testDebugUnitTest` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: wiring of an engine function into existing state holders, tests specified.

## Implementation notes
