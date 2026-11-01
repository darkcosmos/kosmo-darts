# Z5: Zoom setting, UI state and view model

## Goal
The view model holds the zoom, applies it after every binding, locks it during a session, saves it on release and
puts the requested ratio in the session setup.

## Files
- `capture/.../capture/storage/CaptureSettings.kt`: `StoredSettings.zoom: Float = DEFAULT_ZOOM_RATIO`, line
  `zoom=` read and written with `ZoomSettingFormat`, `saveZoom(ratio)`; every write keeps the other values.
- `capture/.../capture/ui/CaptureUiState.kt`: `zoom: ZoomSelection`; `zoomEnabled` = idle (IDLE or ERROR) and
  `cameraReady` and permission GRANTED and `zoom.adjustable` (CP15).
- `CaptureViewModel.kt`: init reads the stored zoom; `onZoomRange(range: ZoomRange?)` (new range; when adjustable,
  effect `setZoomRatio(zoom.ratio)` and remember it as the last requested ratio; otherwise last requested = null);
  `onZoomChanged(position: Float)` (ignored unless `zoomEnabled`; `movedTo`, effect when the ratio changed);
  `onZoomChangeFinished()` (saves when the remembered ratio differs from the stored one; `IOException` swallowed,
  no error state, DZ7); `onStart` puts the last requested ratio in `setup.zoom.requestedRatio` (CP17).
- `capture/.../capture/CaptureActivity.kt`: in the bind callback, when bound, call
  `viewModel.onZoomRange(camera.zoomRange)` before `onCameraReady(true)`.
- Tests: `CaptureSettingsTest`, `CaptureViewModelTest`.

## Depends on
Z4.

## Rules
CP15, CP16, CP17, CP2. Decisions DZ4, DZ7.

## Tests
- CP16 settings: old file without `zoom` -> 1.0 and keeps quality and total; `zoom=abc`, `NaN`, `0`, `-1` -> 1.0;
  `saveZoom` keeps quality and total; `saveQuality` and `saveTotal` keep the zoom.
- CP16 view model: stored 2.4 -> state ratio 2.4 before any range; `onZoomRange(1..10)` -> one effect 2.4; stored 12
  -> effect 10.0, nothing saved; a second `onZoomRange` (rebinding) re-applies, also while a session runs;
  `onZoomRange(null)` or 1..1 -> no effect, `unavailable` for 1..1.
- CP16 save: three `onZoomChanged` then `onZoomChangeFinished` -> three effects, one save; finished without change ->
  no write; a settings write failing -> ratio kept, `error` stays null.
- CP15: `zoomEnabled` true in IDLE and ERROR with camera ready, permission granted, adjustable range; false while
  RUNNING, SAVING, camera not ready, permission ASK, range null or 1..1. `onZoomChanged` while running: no effect, no
  state change, no save.
- CP17: range 1..10, stored 2.4, Start -> truth `zoom.requestedRatio` 2.4 and `ratio` from the setup; range 1..1 ->
  `requestedRatio` null.

## Done when
`./gradlew :capture:testDebugUnitTest build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: state holder with JVM tests, behavior fully specified.

## Implementation notes
