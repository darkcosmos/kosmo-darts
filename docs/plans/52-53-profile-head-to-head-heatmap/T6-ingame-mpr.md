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
- `CricketGameScreenStateTest`: 3- and 4-player cases (MPR per player on useful marks).
- Regression: the X01 statistics of the game, between-legs and end screens (501 and 301) are unchanged; their
  existing tests pass untouched.

## Done when
`./gradlew :game-engine:test`, `./gradlew build` and `./gradlew :app:testDebugUnitTest` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: wiring of an engine function into existing state holders, tests specified.

## Implementation notes
- Deviation from "Files": `CricketMatch` cannot compute the MPR alone (leg summaries keep no darts, and the saved
  format is unchanged), so `CricketMatchPlayerStats` lost `marksPerRound` (now `dartsThrown` only) and the MPR lives in
  `engine/stats/CricketMatchMpr.of(match, journal)`. Completed legs are replayed from the journal (`LegReplay`, voided
  darts excluded, exact starter per leg from `MatchScore`), the current leg from `match.currentLeg.darts`, with
  T5 `usefulMarks`. A journal out of step with the summaries (DJ4) falls back to the summaries' all-marks MPR.
- UI: `CricketGameScreenState.mprs(journal)` (replaces `mprOf`), computed once in `CricketGameScreen` and passed to
  `Zone`/`CricketTopBand`/`LandscapeGame`; `CricketGameScreen`, `CricketBetweenLegsScreen`, `CricketEndScreen`,
  `BetweenLegsState.of(CricketMatch, journal)` and `endRows(CricketMatch, journal)` take the `MatchJournal`;
  `Screen.CricketEnd(match, journal)` carries it (T26 can use it). `marksHit` kept for the summaries (R24) and CR14.
- Tests: `CricketStatisticsTest` (useful MPR, dead target, solo, 3 players, undo, reload, fallback),
  `CricketGameScreenStateTest` (2 to 4 players, undo, solo), `EndContentTest`, `BetweenLegsContentTest`;
  `CricketClosingTest` marks-hit test renamed CR2. Test helpers: `journalOf` (engine), `MatchJournals.fromDarts` (app).
  X01 code and tests untouched. Visible effect: Cricket MPR drops when marks land on dead targets, and solo MPR counts
  closing marks only (max 3.0 per 3 darts of closing, no surplus).
