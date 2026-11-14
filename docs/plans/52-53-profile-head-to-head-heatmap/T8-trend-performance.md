# T8: Trend series and performance test

## Goal
The per-match series of the trend curve, and the proof that all Profile figures compute fast enough. Root "Approach"
3, decision D2.

## Files
- `game-engine/.../engine/stats/TrendSeries.kt` (new): one point per selected match in end order: 3-dart average
  (X01) or MPR (Cricket); a match where the value is not computable gives no point. `TrendSummary` (number of
  points, first, last, lowest, highest value, dates of the first and last match) for the curve text and TalkBack
  (PS5, review U7).
- `game-engine/src/test/.../engine/stats/TrendSeriesTest.kt`.
- `game-engine/src/test/.../engine/stats/StatsPerformanceTest.kt`: 1,000 generated matches (seeded, X01 and Cricket,
  1 to 4 players, several legs), validates them (T3) then computes selection, X01, Cricket and trend for one profile
  under 1 s (median of a few runs after a warm-up, to avoid a flaky test). The budget is generous: record the local
  time in the notes below.

## Depends on
T4, T7.

## Rules
PS5, ST7, ST10.

## Tests
- Order by end, values match `X01Stats` and `CricketStats` on one match each.
- Empty selection: empty series; one match: one point.
- `TrendSummary`: lowest and highest with equal values, first and last dates, empty and one-point cases.
- Single replay: the stats functions take `StatsMatch` only (validated, facts precomputed) and have no access to a
  replaying engine; the review checks it, and the test generates the matches once.
- Performance test as above. If it fails, stop and report (a cache is a design change, root "Approach" 3).

## Done when
`./gradlew :game-engine:test` and `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: small pure function plus a benchmark test.

## Implementation notes
