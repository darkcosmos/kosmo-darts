# T19: Dartboard Canvas component

## Goal
A reusable dartboard drawn with Compose Canvas: it receives one colour per zone and a selected zone, reports taps as
zones, and holds no rule logic (it will serve the camera milestone). Root "Approach" 2, HM8.

## Files
- `app/.../ui/board/Dartboard.kt` (new): `Dartboard(colors: Map<BoardZone, Color>, neutral: Color,
  selected: BoardZone?, onZoneTapped: (BoardZone) -> Unit, modifier)`; geometry from `BoardZone` / `BoardGeometry`
  radii scaled to the size; numbers around the ring; tap position converted to millimetres then `zoneAt` (BC5);
  selected zone outlined in `accent`.
- `app/.../ui/board/DartboardTags.kt` (new).
- Tests: `DartboardTest.kt` (taps at known points give the expected zones), captures `DartboardScreenshotTest.kt`
  with neutral colours and a test pattern.

## Depends on
T17.

## Rules
HM8, BC4, BC5, PS8 (no popup); design system sections 2 and 8.

## Tests
- Tap at the centre gives BULL; at T20 nominal point gives T20; outside the board gives nothing.
- Square layout in portrait and landscape sizes; numbers readable at 200 % font (they scale with the board, not the
  font, documented in the KDoc).
- The component receives colours only: no import of stats or rules other than geometry.

## Done when
`./gradlew build` and `./gradlew :app:testDebugUnitTest` green (captures recorded); `kosmo-detekt` exit 0.

## Model
sonnet. Why: logic-free drawing with tests and captures.

## Implementation notes
