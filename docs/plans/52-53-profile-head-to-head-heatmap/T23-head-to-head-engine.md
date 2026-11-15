# T23: Head-to-head engine

## Goal
Common matches of two profiles, duel results, legs won and side-by-side figures over the common matches. Root
"Rules", Head-to-head.

## Files
- `game-engine/.../engine/stats/HeadToHead.kt` (new): `commonMatches(matches, a, resetA, b, resetB)`,
  `HeadToHeadFigures` (common count, wins A, wins B, draws, legs won A and B, X01 figures STX1 to STX4 for each,
  Cricket MPR for each), applying the ST3 filters or All, and the ST4 periods on common matches; `commonCounts` for
  the picker (HH1).
- Tests: `HeadToHeadTest.kt`, reusing `StatsMatchFixtures`.

## Depends on
T4, T7.

## Rules
HH1 (counts), HH2 to HH6, ST1, ST3, ST4.

## Tests
- Common matches at 2, 3 and 4 players; a match with only one of the two is excluded.
- Both reset dates: the latest applies (start equal counts).
- Duel: A rank 1 vs B rank 3 is a win for A; A rank 2 vs B rank 2 is a draw; counts add up.
- Legs won from stored values.
- Figures computed only on common matches (a solo match of A does not change A's average here).
- "Last 10" takes the last 10 common matches; mode All gives counters over every mode plus X01 and Cricket blocks.
- ST10: one corrupt common match (skipped by T3) leaves the figures equal to those of the valid matches alone; an
  unknown profile id gives empty figures, no exception.

## Done when
`./gradlew :game-engine:test` and `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: rules specified, tests listed.

## Implementation notes
- `engine/stats/HeadToHead.kt`: `commonMatches(matches, a, resetA, b, resetB)` (both play, started >= max reset,
  a == b gives empty), `commonCounts(matches, a, resetA, resets: Map<Long, Long>): Map<Long, Int>` (HH1; every key
  present, 0 included; sorting by count then name is left to T24), `selectCommon(..., filter, period, now)` (common
  matches, then `select` with the latest reset so the filter and "Last 10" apply to common matches only) and
  `headToHead(matches, a, resetA, b, resetB, filter, period, now): HeadToHeadFigures`.
- `HeadToHeadFigures(commonCount, winsA, winsB, draws, legsWonA, legsWonB, x01A, x01B, cricketA, cricketB)`; X01 and
  Cricket blocks are always present (empty figures, null averages, when the selection holds no match of that mode);
  a `CricketFilter` variant is passed to `cricketFigures`. Dates: the date line (HH8) is not here; T24 uses
  `ProfileRules.isReallyReset` and the max of the two reset dates.
- No deviation. Ranks are compared directly (lower is better); equal ranks are a draw.
