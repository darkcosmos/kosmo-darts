# Z6: Zoom row on the screen, strings, captures

## Goal
The zoom slider and its value on the capture screen, as the root plan "Screens and states" specifies.

## Files
- Added `capture/.../capture/ui/ZoomRow.kt`: value text and `Slider`, renders `state.zoom`, `state.zoomEnabled`;
  the position comes from `ZoomSelection.position` (no mapping in the composable).
- `CaptureScreen.kt`: `ZoomRow` between the preview box and `ControlRow`.
- `CaptureActions.kt`: `onZoomChanged: (Float) -> Unit`, `onZoomChangeFinished: () -> Unit`, mapped in `of(...)`.
- `CaptureTags.kt`: `ZOOM_ROW`, `ZOOM_SLIDER`, `ZOOM_VALUE`.
- `res/values/strings.xml`, `res/values-fr/strings.xml`: the four strings of the root plan table.
- Tests: `CaptureScreenTest`, `CaptureScreenHarness` (zoom in the sample states), `CaptureScreenshotsTest`.

## Depends on
Z5.

## Rules
CP14, CP15, CP2. Decision DZ8; owner decision Q14 (42dp landscape).

## Tests
- CP14: value `2.4×` in English and `2,4×` in French; slider `stateDescription` "2.4 times" / "2,4 fois",
  `contentDescription` "Zoom"; the value text node is not read separately.
- CP14: a swipe on the enabled slider calls `onZoomChanged` at least once, then `onZoomChangeFinished` once.
- CP15: slider enabled in idle with camera ready; not enabled while running, saving, camera starting, permission
  missing; range 1..1 shows "Zoom not available" (EN and FR) and is not enabled.
- Layout: zoom row height 42dp at 915x412 and 640x360; preview at least 96dp tall at 915x412, font scale 2.0;
  nothing clipped (existing clipping checks still pass).
- Captures: every existing capture regenerated (row visible); new `zoom_unavailable` EN and FR at 915x412.

## Done when
`./gradlew :capture:testDebugUnitTest build` green; captures reviewed under
`capture/build/outputs/roborazzi/`; `git diff origin/main -- app game-engine` empty; `kosmo-detekt` exit 0.
If the preview check at font scale 2.0 fails, stop and report to main (layout change is a design decision).

## Model
sonnet. Why: screen change from a specified layout, strings and height budget.

## Implementation notes
