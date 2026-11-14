# T18: Heatmap aggregation

## Goal
Count darts per zone and misses, with percentages, for a profile selection and for one player's last leg from a match
journal. Root "Rules", Heatmap.

## Files
- `game-engine/.../engine/stats/HeatmapAggregation.kt` (new): `Heatmap(counts: Map<BoardZone, Int>, misses: Int,
  total: Int, includesCricket: Boolean)` with `share(zone)` and `missShare` (null when total is 0), `topZones(5)`;
  `heatmapOf(matches, profileId, modeFilter)` where the mode filter is All modes / X01 filter / Cricket filter;
  `lastLegHeatmap(journal, playerIndex, isCricket)` from the last leg of a `MatchJournal`.
- Tests: `HeatmapAggregationTest.kt`.

## Depends on
T3, T17.

## Rules
HM1, HM2, HM3 (mode filter), HM4 (`includesCricket` drives the label), HM7, HM9 (data), ST1, ST2.

## Tests
- Voided darts excluded, misses counted in the total, percentages sum to 100 %.
- All modes adds X01 and Cricket darts; `includesCricket` true as soon as a Cricket dart counts.
- Last leg: 1 to 4 players, undone darts excluded, only the last leg of a multi-leg match, the winner's incomplete
  last visit.
- Empty selection: total 0, shares null.
- `topZones` order on ties (by count, then zone order) is stable.

## Done when
`./gradlew :game-engine:test` and `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: counting rules fully specified.

## Implementation notes
