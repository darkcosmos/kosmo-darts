# T2: Board geometry and nominal points

## Goal
`:game-engine` computes the nominal point of a manually entered dart in millimetres (BC1 to BC3).

## Files
- Added `game-engine/src/main/kotlin/.../engine/board/BoardGeometry.kt` (ring radii, sector order, source cited in
  KDoc: WDF Playing Rules / PDC dartboard specification, the version and section used) and `BoardPoint.kt`
  (`data class BoardPoint(val xMm: Double, val yMm: Double)`).
- Added `fun nominalPoint(dart: Dart): BoardPoint?` in `engine/board`.
- Added test `game-engine/src/test/kotlin/.../engine/board/NominalPointTest.kt`.

## Depends on
None.

## Rules
BC1, BC2, BC3 (`docs/rules/history.md`); D33.

## Approach
- Sector order clockwise from the top: 20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5; sector
  i is centred at i × 18° clockwise from +y: `x = r sin θ`, `y = r cos θ`.
- Radii from the cited source (inner edges of the wires as the source gives them). Mean radius of a zone =
  (inner + outer) / 2: single = between the triple outer and the double inner radius; triple and double = middle of
  their ring. 25 and Bull: (0, 0). Miss: `null`, the "position unknown" value of the archive (D33).

## Tests
- Every segment 1 to 20 × single, double, triple: radius equals the expected mean radius (tolerance 1e-9) and angle
  equals the sector bisector.
- Orientation: D20 on +y (x = 0), D6 on +x (y = 0), D3 on -y, D11 on -x.
- Anchors with literal millimetre values written in the test from the cited source (not from the production
  constants): D20, T20 and S20 at (0, y) with the expected y; S20's y is above the triple outer radius (outer single
  zone, never the inner single zone).
- 25 and Bull at (0, 0); miss gives `null`.

## Done when
`./gradlew :game-engine:test` and `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: fully specified rule with listed tests.

## Implementation notes
- Code in package `...engine.board`: `BoardPoint`, `BoardGeometry` (internal object with radii and sector order) and
  the public `nominalPoint(dart)` in the same file. No deviation.
- Radii (mm, WDF Playing Rules / PDC specification): triple 99-107, double 162-170. Mean radii: S 134.5, T 103,
  D 166. 25 and Bull at (0, 0), miss `null`. Later tasks only call `nominalPoint`.
