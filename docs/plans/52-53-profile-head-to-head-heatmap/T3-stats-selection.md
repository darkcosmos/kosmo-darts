# T3: Stats input and match selection

## Goal
The engine input type for statistics and the selection of matches by reset date, filters and period. Root
"Approach" 1 and 2.

## Files
- `game-engine/.../engine/stats/StatsMatch.kt` (new): `StatsMatch` (session id, startedAt, endedAt,
  `ArchivedSettings`, players with profile id, position, rank, legs won, sets won; legs; darts as `ArchivedDart`
  ordered by leg, visit, rank), `isSolo`, `dartsOf(position)` excluding voided darts (ST1).
- `game-engine/.../engine/stats/MatchSelection.kt` (new): `X01Filter(startScore: Int?, checkoutRule: CheckoutRule?)`,
  `CricketFilter(variant: CricketVariant?)`, `Period { SINCE_ALWAYS, LAST_30_DAYS, LAST_10_MATCHES }`, constants
  `PERIOD_DAYS = 30`, `LAST_MATCHES = 10`; `select(matches, profileId, resetAt, filter, period, now)`.
- Tests: `game-engine/src/test/.../engine/stats/MatchSelectionTest.kt`, a small builder `StatsMatchFixtures.kt` in
  the test sources, reused by later tasks.

## Depends on
T1.

## Rules
ST1, ST2, ST3, ST4.

## Tests
- ST2: start equal to the reset date counts, one millisecond before does not.
- ST3: every X01 combination (All, 501, 301 x All, double, single); Cricket All, Standard, Cut-throat; a Cricket match
  never passes an X01 filter and the reverse.
- ST4: end exactly 30 x 24 h ago counts, older does not; last 10 with 9, 10 and 11 matches; last 10 is taken after
  filters and ST2 (a filtered-out recent match does not take a slot).
- Voided darts are absent from `dartsOf`.

## Done when
`./gradlew :game-engine:test` and `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: specified filters and periods as pure functions.

## Implementation notes
