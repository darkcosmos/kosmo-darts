# T8: Trend series and performance test

## Goal
The per-match series of the trend curve, and the proof that all Profile figures compute fast enough. Root "Approach"
3, decision D2.

## Files
- `game-engine/.../engine/stats/TrendSeries.kt` (new): one point per selected match in end order: 3-dart average
  (X01) or MPR (Cricket); a match where the value is not computable gives no point.
- `game-engine/src/test/.../engine/stats/TrendSeriesTest.kt`.
- `game-engine/src/test/.../engine/stats/StatsPerformanceTest.kt`: 1,000 generated matches (seeded, X01 and Cricket,
  1 to 4 players, several legs), computes selection, X01, Cricket and trend for one profile under 1 s (median of a few
  runs after a warm-up, to avoid a flaky test).

## Depends on
T4, T7.

## Rules
PS5, ST7.

## Tests
- Order by end, values match `X01Stats` and `CricketStats` on one match each.
- Empty selection: empty series; one match: one point.
- Performance test as above. If it fails, stop and report (a cache is a design change, root "Approach" 3).

## Done when
`./gradlew :game-engine:test` and `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: small pure function plus a benchmark test.

## Implementation notes
