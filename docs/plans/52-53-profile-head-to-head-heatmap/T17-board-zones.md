# T17: Board zone geometry

## Goal
`:game-engine` exposes the heatmap zones and their geometry, the same source as the nominal points of BC2, and a
point-to-zone lookup. Root "Approach" 2.

## Files
- Public names (review A5), used as is by T18, T19 and the camera milestone: `BoardGeometry` (public object, the
  geometry) and `BoardZone` (the zone type). No other public board name.
- `game-engine/.../engine/board/BoardGeometry.kt`: from `internal` to public object; public radii for Bull, 25,
  inner and outer single limits, triple
  and double rings (official dimensions, source cited), sector order and angle (existing constants made public or
  wrapped).
- `game-engine/.../engine/board/BoardZone.kt` (new): `BoardZone` (number 1..20 with ring SINGLE / DOUBLE / TRIPLE,
  `OUTER_BULL`, `BULL`), `BoardZone.ALL` (62 zones), `BoardZone.of(dart): BoardZone?` (null for a miss),
  and `BoardGeometry.zoneAt(point: BoardPoint): BoardZone?`.
- Tests: `BoardZoneTest.kt` (zone list and `of`) and `BoardGeometryTest.kt` (existing, extended with `zoneAt` and the
  public radii); test names use `BoardZone` and `BoardGeometry`.

## Depends on
T1.

## Rules
BC1, BC2, BC4, BC5, HM1.

## Tests
- 62 zones; both single rings map to the one SINGLE zone.
- Every nominal point of `Dart.SCORING` lies in `BoardZone.of(dart)` (BC4).
- Borders: a point exactly on a ring radius or sector edge maps to exactly one zone; outside the double ring gives
  null; centre gives BULL.

## Done when
`./gradlew :game-engine:test` and `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: geometry from official dimensions with tests, no design choice beyond the API.

## Implementation notes
