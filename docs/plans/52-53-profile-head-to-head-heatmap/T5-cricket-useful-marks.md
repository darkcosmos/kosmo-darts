# T5: Cricket useful marks per dart

## Goal
Replay a Cricket leg and give, for each dart in play, its useful marks and the points it scored (Standard) or gave
(Cut-throat, summed over opponents). Shared by the in-game MPR (T6) and the stats validator (T3), whose stored
yields feed the Profile (T7). Root "Approach" 2.

## Files
- `game-engine/.../engine/stats/CricketUsefulMarks.kt` (new): `DartYield(useful: Int, pointsScored: Int,
  pointsGiven: Int)`; `usefulMarks(config: CricketConfig, darts: List<PlayedDart>): List<DartYield>`, built on the
  existing `CricketGame` replay (state before and after each dart), no copy of the CR rules.
- Tests: `CricketUsefulMarksTest.kt`.

## Depends on
T1.

## Rules
STC1, STC4 (points per dart); CR2 to CR6, CR15.

## Tests
- Closing marks useful; Standard surplus useful only while an opponent has the target open; Cut-throat surplus
  useful only while an opponent has it open, points given = target value x open opponents (20 to 2 opponents = 40).
- Dead target: 0 useful; the triple that closes with 1 mark and makes the target dead gives 1.
- Closing marks on a target every opponent already closed: useful (it closes), surplus of that dart not useful.
- Solo: only closing marks useful, never points.
- 25-Bull on: 25 = 1 mark, Bull = 2; off: 0 marks, the dart still listed.
- Misses and 1 to 14 give 0.
- 2, 3 and 4 players, both variants.
- Corrupt input (a dart the replay rejects, a dart after the win, a player index out of range): the function returns
  an explicit failure value (or the yields up to the faulty dart, flagged invalid) and never throws; T3 relies on it.

## Done when
`./gradlew :game-engine:test` and `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: STC1 is fully specified after owner Q9; replay reuses existing engine code.

## Implementation notes
