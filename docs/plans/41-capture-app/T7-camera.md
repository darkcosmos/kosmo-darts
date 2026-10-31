# T7: CameraX controller, sensor, lifecycle

## Goal
Real capture: preview, one video per volley, one full-resolution photo per dart entry, gravity vector, keep screen
on, and interruption handling, wired to the view model of T6 and the storage of T4.

## Files
- Added in `capture/.../capture/camera/`: `CaptureCamera` (interface: bind, startVideo(target), stopVideo,
  takePhoto(target), actual resolutions, timestamp source), `CameraXCaptureCamera` (Preview + `VideoCapture<Recorder>`
  with `QualitySelector` UHD or FHD and lower fallback, no audio; `ImageCapture` highest resolution, minimize latency
  off; outputs through `MediaStoreOutputOptions` / `ImageCapture.OutputFileOptions` from T4), `GravityReader`
  (`Sensor.TYPE_GRAVITY`, latest vector, null if absent), `CaptureController` (turns view model effects into camera
  and storage calls, records the CameraX `Start` event monotonic time per volley).
- Changed: `CaptureActivity` (camera permission request, `PreviewView` in the preview slot, `FLAG_KEEP_SCREEN_ON` from
  state, `onStop` sends Interrupt), `CaptureViewModel` if an effect is missing.
- Tests: `FakeCaptureCamera` in the test source set.

## Depends on
T4, T6.

## Rules
CP1, CP3, CP5, CP6, CP9, CP10, CP11, CP12. Decisions Q1, Q2, D3.

## Tests
- `CaptureController` with fakes: Start opens `volley-001.mp4`; each dart takes `dart-NNN.jpg`; Next volley stops
  then starts the next video; Stop stops without restart; a recording error or Interrupt ends the session
  (INTERRUPTED) and writes the truth file; actual resolutions and timestamp source copied into the session; video
  position uses the Start event time.
- Manual only (no device here): real recording on the Pixel 7, listed in the root plan "How Kosmo verifies".

## Done when
`./gradlew build :capture:assembleDebug` green, `aapt2 dump permissions` still only `CAMERA`, `kosmo-detekt` exit 0.

## Model
sonnet. Why: CameraX wiring behind a tested interface, behavior fully specified.

## Implementation notes
