# T4: X01 statistics

## Goal
All X01 figures of the Profile, as pure functions over selected `StatsMatch` for one profile. Root "Rules", X01.

## Files
- `game-engine/.../engine/stats/X01Stats.kt` (new): `X01Figures` (average, first-9 average, checkout successes and
  attempts with the ratio, best checkout, bands 180 / 140+ / 100+ / 60+, best visit, busts, average darts per leg won,
  best leg, matches won, lost, win ratio, legs won, sets won); each optional value is `null` when not computable
  (ST6). Named constants for 180, 140, 100, 60, 9 darts, the double-out range 2..40 and 50.
- The figures read the per-dart facts of `StatsMatch` (remaining before each dart, T3); they never replay a leg. An
  out-of-range position gives empty figures, never an exception.
- Tests: `X01StatsTest.kt` with matches built through `StatsMatchFixtures` (T3).

## Depends on
T3.

## Rules
STX1 to STX8, ST1, ST5, ST6, ST8; X01 out rules of `docs/rules/x01.md`.

## Tests
- STX1: a bust visit scores 0 and its 3 darts count; voided darts ignored.
- STX2: leg won in 7 darts uses 7.
- STX3: double out, remaining 40, 50, 41 (not an attempt), 2; mid-visit attempts; a busting attempt fails; single out,
  remaining 60, 57, 25, 23 (not an attempt).
- STX4: 1-dart and 2-dart winning visits.
- STX5: bands at 59, 60, 99, 100, 139, 140, 179, 180; bust visits excluded.
- STX6, STX7: best leg and average darts per leg won, solo legs included; none won gives null.
- STX8 and ST5: solo match excluded from won, lost, win ratio, legs, sets; 3- and 4-player rank 2 and 3 are losses.
- 501 and 301; sets > 1.
- ST10: the figures over valid matches plus one corrupt match (skipped by the validator) equal those of the valid
  matches alone; unknown profile id or position gives empty figures, no exception.

## Done when
`./gradlew :game-engine:test` and `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: formulas fully specified, tests listed.

## Implementation notes
- `X01Stats.kt`: `x01Figures(matches, profileId): X01Figures` and public `isCheckoutAttempt(remaining, rule)`. Input is
  `List<StatsMatch>` already validated and selected; Cricket matches and matches without the profile are skipped.
- Counts (won, lost, legs, sets, busts, bands, checkout successes and attempts) are `Int` and 0 when empty; ratios,
  averages, best visit, best checkout and best leg are `null` when not computable (ST6). Solo matches add 0 to results.
- A visit is the player's darts sharing a `visit` number in a leg; a bust visit scores 0, its darts count (also in the
  first 9). A leg win is the stored winner's last dart (success); best checkout is the total of that winning visit.
- No deviation from the task file. Tests build solo matches through the real validator (`solo` helper in
  `X01StatsTest`) because `StatsMatchFixtures` has fixed visits.
