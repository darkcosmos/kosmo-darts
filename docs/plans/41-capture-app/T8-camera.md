# T8: CameraX controller, sensor, lifecycle

## Goal
Real capture: preview, one video per volley, one full-resolution photo per dart entry, gravity vector, keep screen
on, and interruption handling, wired to the view model of T6 (its `CaptureEffects`) and the storage of T4.

## Files
- Added in `capture/.../capture/camera/`: `CaptureCamera` (interface: bind, startVideo(target), stopVideo,
  takePhoto(target), actual resolutions, timestamp source), `CameraXCaptureCamera` (Preview + `VideoCapture<Recorder>`
  with `QualitySelector` UHD or FHD and lower fallback, no audio; `ImageCapture` highest resolution, minimize latency
  off; outputs through `MediaStoreOutputOptions` / `ImageCapture.OutputFileOptions` from T4), `GravityReader`
  (`Sensor.TYPE_GRAVITY`, latest vector, null if absent), `CaptureController` (turns view model effects into camera
  and storage calls, records the CameraX `Start` event monotonic time per volley, reports photo success or failure
  back to the view model as events; it never decides session rules itself).
- Changed: `CaptureActivity` (camera permission request; after a permanent refusal "Open settings" starts
  `ACTION_APPLICATION_DETAILS_SETTINGS`; `PreviewView` in the preview slot; `FLAG_KEEP_SCREEN_ON` from state;
  `onStop` calls `onScreenStopped(isChangingConfigurations)`; display rotation changes update the use cases'
  `targetRotation` without rebinding), `CaptureViewModel` if an effect is missing.
- Tests: `FakeCaptureCamera` and the `FakeCaptureStorage` of T4 in the test source set.

## Depends on
T7.

## Rules
CP1, CP3, CP5, CP6, CP9, CP10, CP11, CP12, CP13. Decisions Q1, Q2, Q15, Q17, D3. Test names or comments cite the
CP id.

## Tests
- `CaptureController` with fakes: Start opens `volley-001.mp4`; each dart takes `dart-NNN.jpg`; Next volley stops
  then starts the next video; Stop stops without restart; a recording error or Interrupt stops the open recording,
  ends the session (INTERRUPTED) and writes the truth file; actual resolutions and timestamp source copied into the
  session; video position uses the Start event time, null for a dart entered before it.
- CP13: the fake camera fails the photo of dart 2: dart 2 kept with `photo` null, photo failure event sent, dart 3
  still photographed, recording untouched.
- CP9: a configuration change (`onScreenStopped(true)`) keeps the recording open; a real stop closes it.
- Manual only (no device here): real recording, rotation and Home key on the Pixel 7, root plan "How Kosmo verifies".

## Done when
`./gradlew build :capture:assembleDebug` green; the T1 manifest checks and `:capture:dependencies` re-run with their
output in Implementation notes; `git diff origin/main -- app` empty; `kosmo-detekt` exit 0.

## Model
sonnet. Why: CameraX wiring behind a tested interface, behavior fully specified.

## Implementation notes
- `capture.camera`: `CaptureCamera` + `CaptureCameraListener` (Android-free), `CameraXCaptureCamera` (Preview,
  `VideoCapture<Recorder>` without audio, `ImageCapture` max quality and highest resolution; CameraX itself inserts the
  MediaStore rows, pending flag included, from `MediaStoreCaptureStorage.contentValues(target)` and
  `MediaStore.Downloads.EXTERNAL_CONTENT_URI`), `GravityReader`, `CaptureController` (is the `CaptureEffects` and the
  camera listener; `attach(vm)`; `setup(app, device, quality)` builds the `CaptureSetup`). `capture.ui.CaptureReports` is
  the interface the view model now implements (only `override` added, no logic change). `session.SystemCaptureClock`.
- Deviations: no `bind` in the `CaptureCamera` interface (it needs Android types; it is on `CameraXCaptureCamera`).
  The `Start` event time is the clock read by the view model when the controller forwards the event on the main thread.
  The activity rebinds when quality or permission changes and after each `onStop`; `onStop` calls `onScreenStopped`
  then `onCameraReady(false)`. Display rotation uses a `DisplayListener` (a 180 degree flip raises no configuration change).
  Camera `stopVideo` with no open recording reports finalized at once; a recording error reports `onCameraError` then
  finalized.
- Not provable here (no device, Robolectric has no CameraX/MediaStore): `MediaStoreOutputOptions` on the Downloads
  collection, real resolutions (video from `QualitySelector.getResolution`, photo from `ImageCapture.resolutionInfo`; if
  either is null the camera reports not ready), timestamp source via `Camera2CameraInfo`. Kosmo checks on the Pixel 7.
- Manifest check on `capture-debug.apk` unchanged from T1: permissions `CAMERA`, `ACCESS_NETWORK_STATE`,
  `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; `allowBackup=false`; activities (`CaptureActivity`, ui-tooling
  `PreviewActivity` debug) exported=true, service exported=false, provider exported=false, `ProfileInstallReceiver`
  exported=true. `:capture:dependencies --configuration debugRuntimeClasspath`: five CameraX artifacts at 1.6.2, `:game-engine`,
  no `:app`. `git diff origin/main -- app` empty.
- Tests: `CaptureControllerTest` with `FakeCaptureCamera` (real view model, `FakeCaptureStorage`).
