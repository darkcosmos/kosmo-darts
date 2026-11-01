# Z3: Camera zoom port, CameraX and controller

## Goal
The camera layer can report its zoom range and current ratio and apply a ratio; the view model can ask for it.

## Files
- `capture/.../capture/camera/CaptureCamera.kt`: add `val zoomRange: ZoomRange?` (known after binding, null when the
  camera gave no `ZoomState`), `val zoomRatio: Float?` (current `ZoomState.zoomRatio`, null when unbound),
  `fun setZoomRatio(ratio: Float)` (no-op when unbound).
- `CameraXCaptureCamera.kt`: keep the bound `Camera` from `bindToLifecycle`; root plan "Approach", "Camera".
- `capture/.../capture/ui/CaptureEffects.kt`: add `fun setZoomRatio(ratio: Float)`.
- `CaptureController.kt`: implements it by calling `camera.setZoomRatio`; a `RuntimeException` is swallowed (zoom
  never reports a camera error, never ends a session).
- Tests: `FakeCaptureCamera` (configurable `zoomRange`, `zoomRatio`, records `setZoomRatio` calls, can throw),
  `CaptureControllerTest`; the fake effects of `CaptureViewModelTest` implement the new method (records calls).

## Depends on
Z2.

## Rules
CP16 (applying a ratio), CP9 (zoom failure is not a camera error). Decision DZ4.

## Tests
- `setZoomRatio(2.4f)` on the controller calls the camera once with 2.4.
- A camera whose `setZoomRatio` throws: no `onCameraError`, no `onVideoFinalized` reported.

## Done when
`./gradlew :capture:testDebugUnitTest build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: wiring of two CameraX calls behind an interface tested with a fake.

## Implementation notes
`zoomRange` and `zoomRatio` of `CameraXCaptureCamera` are read live from `camera.cameraInfo.zoomState.value` of the kept
`Camera` (no cached value; `ZoomRange.of` gives null on invalid bounds). `setZoomRatio` calls `cameraControl` and ignores
the returned future. Controller swallows `RuntimeException`. `FakeCaptureCamera` records `zoomCalls` apart from `calls`
and has `failZoom`; `NoopEffects` and the view model test effects (`zoomCalls`) implement the new method.
