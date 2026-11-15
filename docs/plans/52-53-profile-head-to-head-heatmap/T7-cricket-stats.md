# T7: Cricket statistics

## Goal
All Cricket figures of the Profile, as pure functions over selected `StatsMatch` for one profile. Root "Rules",
Cricket.

## Files
- `game-engine/.../engine/stats/CricketStats.kt` (new): `CricketFigures` (MPR, best visit in useful marks, useful
  marks per target 20 to 15 and Bull, points scored total and per leg (Standard matches), points given total and per
  leg (Cut-throat matches), results per variant: won, lost, win ratio, legs, sets); optional values `null` when not
  computable (ST6). Constant for the 9-mark visit maximum. Reads the per-dart `DartYield` stored in `StatsMatch` by
  the validator (T3); never replays a leg. An out-of-range position gives empty figures, never an exception.
- Tests: `CricketStatsTest.kt` with `StatsMatchFixtures` (T3).

## Depends on
T3, T5.

## Rules
STC2 to STC5, ST1, ST5, ST6, ST8.

## Tests
- STC2 over several matches and legs; voided darts excluded.
- STC3 best visit 9 max; marks per target with 25 and Bull together.
- STC4 totals and averages per leg played; the 40-point case; Standard points only from Standard matches.
- STC5 with "All variants": two result blocks; with a variant filter, one; solo excluded from results.
- No Cricket match: every value null.
- ST10: valid matches plus one corrupt match give the figures of the valid matches alone; no exception.

## Done when
`./gradlew :game-engine:test` and `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: formulas specified, tests listed.

## Implementation notes
- `cricketFigures(matches, profileId, variant: CricketVariant? = null): CricketFigures` in `engine/stats/CricketStats.kt`
  (style of `x01Figures`). `variant` is the selection's filter and only decides the result blocks (STC5): both
  variants for `null`, one otherwise, always present even without a match (win ratio `null`).
- Deviation: the per-target marks use `CricketMarks.of(dart, settings.isBullInPlay).target` on the stored dart, since
  `DartYield` holds no target. `marksPerTarget` has every target (0 by default), Bull covering 25 and Bull.
- Points per leg = per leg of the matches of the variant where the profile plays (all legs of the match); `pointsScored`
  / `pointsGiven` are `null` without such a leg, 0 when legs exist but nothing scored.
- Solo matches count in MPR, visits, marks and points, never in `results` (ST5). Profile not in a match: ignored.
- Constant `MAX_USEFUL_MARKS_PER_VISIT = 9` (used by the tests as the STC3 bound, not clamped in code).
