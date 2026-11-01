# Z6: Zoom row on the screen, strings, captures

## Goal
The zoom row (value, "−", slider, "+") on the capture screen, as the root plan "Screens and states" specifies
(layout, width and height budgets, accessibility, strings).

## Files
- Added `capture/.../capture/ui/ZoomRow.kt`: value text (`WordSafeText`, 64dp to 130dp, 2 lines max), "−" button,
  `Slider` (polite live region, DZ17), "+" button (disabled style DZ16); renders `state.zoom`, `zoomEnabled`, `zoomOutEnabled`, `zoomInEnabled`; the position comes
  from `ZoomSelection.position` (no mapping or step arithmetic in the composable).
- `CaptureScreen.kt`: `ZoomRow` between the preview box and `ControlRow`, 8dp below it (DZ13); named values
  `ZOOM_BUTTON_WIDTH = 48.dp` and `ZOOM_BUTTON_HEIGHT = CONTROL_HEIGHT` next to `CONTROL_HEIGHT` (DZ15).
- `CaptureActions.kt`: `onZoomChanged: (Float) -> Unit`, `onZoomChangeFinished: () -> Unit`,
  `onZoomStep: (Int) -> Unit` ("−" sends -1, "+" sends +1), mapped in `of(...)`.
- `CaptureTags.kt`: `ZOOM_ROW`, `ZOOM_SLIDER`, `ZOOM_VALUE`, `ZOOM_OUT`, `ZOOM_IN`.
- `res/values/strings.xml`, `res/values-fr/strings.xml`: the strings of the root plan table (symbols in `values`
  only, `translatable="false"`).
- Tests: `CaptureScreenTest`, `CaptureScreenHarness` (zoom in the sample states), `CaptureScreenshotsTest`; re-run
  `CaptureActionRowTest` unchanged.

## Depends on
Z5.

## Rules
CP14, CP15, CP2. Decisions DZ8, DZ12 to DZ17; owner decisions Q14 (42dp landscape, height only), KZ7.

## Tests
- CP14: value `2.4×` in English and `2,4×` in French; slider `stateDescription` "2.4 times" / "2,4 fois",
  `contentDescription` "Zoom", live region Polite (DZ17); the value text node is not read separately; buttons
  `contentDescription` "Zoom out" / "Zoom in" and « Réduire le zoom » / « Augmenter le zoom », no `stateDescription`.
- CP14: a swipe on the enabled slider calls `onZoomChanged` at least once, then `onZoomChangeFinished` once; a click
  on "−" calls `onZoomStep(-1)` once, on "+" `onZoomStep(1)` once; with a harness that applies the step (ratio 2.4,
  range 1..10), a "+" click changes the slider's `stateDescription` to "2.5 times". The real TalkBack announcement
  stays a manual phone step (root "How Kosmo verifies", step 8).
- CP15: slider and buttons enabled in idle with camera ready; `assertIsNotEnabled()` on slider, "−" and "+" while
  running, saving, camera starting, permission missing (value still displayed); `assertIsNotEnabled()` on "−" at
  1.0 (range minimum) and on "+" at the range maximum, the other button still enabled; range 1..1 shows "Zoom not
  available" (EN and FR) and nothing is enabled. Disabled style DZ16 (no fill, `States.DISABLED_ALPHA`) named in
  the test comment and visible in the range-end capture.
- Layout: zoom row height exactly 42dp at 915x412 (font scale 1.0 and 2.0) and 640x360; on both buttons
  `assertWidthIsAtLeast(48.dp)` and `assertHeightIsAtLeast(42.dp)` at 915x412, 640x360 and 915x412 font scale 2.0,
  with the `assertInsideScreen` pattern of `CaptureActionRowTest` (U21); gap zoom row to control row 8dp; preview at least 96dp tall at 915x412 font scale 2.0; at 640x360 font
  scale 1.0 preview at least 160dp and control row inside the screen (T4); nothing clipped (existing checks pass).
- Width (U17), "Zoom not available" state, EN and FR: slider width at least 120dp at 915x412 font scale 2.0 and at
  least 96dp at 640x360 font scale 1.0; the value text has no visual overflow and breaks only between words
  (`breaksOnlyBetweenWords`).
- Captures: every existing capture regenerated (row visible); new `zoom_unavailable` EN and FR at 915x412; new
  `zoom_at_minimum` EN at 915x412 ("−" disabled, "+" enabled).

## Done when
`./gradlew :capture:testDebugUnitTest build` green; captures reviewed under
`capture/build/outputs/roborazzi/`; `git diff origin/main -- app game-engine` empty; `kosmo-detekt` exit 0.
If a preview height or slider width minimum fails, stop and report to main (layout change is a design decision).

## Model
sonnet. Why: screen change from a specified layout, strings and height budget.

## Implementation notes
`ZoomRow.kt` renders the row; `CaptureScreen` places it with `padding(bottom = 4.dp)` on top of the 4dp column gap (8dp).
Added `CaptureTags.CONTROL_ROW` (control row tag, to measure the 8dp gap: its buttons are centred in a 48dp row).
`ZoomRowTest` holds the layout and semantics tests; `showZoomHarness` applies steps like the view model. Deviation: the
`TextLayoutResult` of an autosized text is the unshrunk one, so the value text fit is checked on drawn bounds inside its box.
Swipe test uses explicit offsets (`swipeRight` from the edge sent no change). Extra capture `zoom_unavailable` font scale 2 FR.
