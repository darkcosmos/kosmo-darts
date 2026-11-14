# T18: Heatmap aggregation

## Goal
Count darts per zone and misses, with percentages, for a profile selection and for one player's last leg from a match
journal. Root "Rules", Heatmap.

## Files
- `game-engine/.../engine/stats/HeatmapAggregation.kt` (new): `Heatmap(counts: Map<BoardZone, Int>, misses: Int,
  total: Int, includesCricket: Boolean)` with `share(zone)` and `missShare` (null when total is 0), `topZones(5)`;
  `heatmapOf(matches, profileId, modeFilter)` where the mode filter is All modes / X01 filter / Cricket filter;
  `lastLegHeatmap(journal, playerIndex, lastLeg: LastLeg)` with `enum LastLeg { COMPLETED, CURRENT }`: COMPLETED
  reads `journal.legs[legs.size - 2]` (between legs, where `currentLeg` is the empty next leg), CURRENT reads
  `journal.currentLeg` (end screens); root "Approach" 7, decision D6. The mode (Cricket or not) comes from the
  journal configuration. `topZones(5)` returns zones with count and percentage, so state holders format only.
- Out-of-range player index, missing leg or empty journal: an empty heatmap, never an exception.
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
- HM9 between legs: a journal fixture whose `currentLeg` is empty (the next leg) with `LastLeg.COMPLETED` gives the
  non-empty counts of the last completed leg.
- HM9 end: a finished journal with `LastLeg.CURRENT` gives the counts of the finished leg.
- Empty selection: total 0, shares null.
- `topZones` order on ties (by count, then zone order) is stable; percentages asserted (rounding stated in the KDoc).
- ST10: a profile selection with one corrupt match (skipped by T3) gives the counts of the valid matches alone; an
  out-of-range index gives an empty heatmap.

## Done when
`./gradlew :game-engine:test` and `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: counting rules fully specified.

## Implementation notes
