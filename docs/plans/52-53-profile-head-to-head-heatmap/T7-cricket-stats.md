# T7: Cricket statistics

## Goal
All Cricket figures of the Profile, as pure functions over selected `StatsMatch` for one profile. Root "Rules",
Cricket.

## Files
- `game-engine/.../engine/stats/CricketStats.kt` (new): `CricketFigures` (MPR, best visit in useful marks, useful
  marks per target 20 to 15 and Bull, points scored total and per leg (Standard matches), points given total and per
  leg (Cut-throat matches), results per variant: won, lost, win ratio, legs, sets); optional values `null` when not
  computable (ST6). Constant for the 9-mark visit maximum.
- Tests: `CricketStatsTest.kt`.

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

## Done when
`./gradlew :game-engine:test` and `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: formulas specified, tests listed.

## Implementation notes
