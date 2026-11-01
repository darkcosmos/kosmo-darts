# T6: View model and UI state

## Goal
`CaptureViewModel` and `CaptureUiState`: everything the screen shows is computed here, testable on the JVM.

## Files
- Added in `capture/.../capture/ui/`: `CaptureUiState` (permission state: granted, ask, permanently denied; camera
  ready; idle, running, saving, error; volley readout: number and 3 slots with display value or empty; `volleyFull`;
  counters; quality; enabled flags for keypad, Undo, Next volley, Start/Stop, quality, Delete all; dialogs: Stop
  confirm, Delete all confirm; transient photo failure message; `keepScreenOn`), `CaptureViewModel` (holds
  `CaptureSession` of T3 and `TruthWriter` of T4; emits effects to a `CaptureEffects` interface that T8 implements:
  start video, stop video, take photo; `onScreenStopped(changingConfigurations: Boolean)`), `DartLabel` (`T20`, `D5`,
  `5`, `25`, Bull, Miss as resource keys).
- Tests: `CaptureViewModelTest` with fake clock, fake storage, fake effects.

## Depends on
T3, T4.

## Rules
CP2, CP3, CP4, CP6, CP7, CP8, CP9, CP11, CP12, CP13. Root plan "Screens and states" (primary action per state).
Test names or comments cite the CP id.

## Tests
- Readout: after T20, 5, Miss the state holds "Volley 1" and the three labels; after Undo the third slot is empty;
  after Next volley "Volley 2" with three empty slots.
- CP3: three darts set `volleyFull` and disable the keypad; a fourth dart event changes nothing.
- CP2: enabled flags in idle, running, saving, error, camera not ready, permission missing.
- CP6: Stop opens the confirmation; Cancel leaves the session and its recording untouched; confirm ends it, shows
  saving, then idle when the last video is finalized.
- CP8: Delete all opens the confirmation; Cancel deletes nothing; confirm deletes and the total shows 0.
- CP9: `onScreenStopped(true)` keeps the session running and emits no stop; `onScreenStopped(false)` ends it with
  INTERRUPTED and emits stop video.
- CP11: quality toggle refused while running, persisted otherwise.
- CP13: photo failure shows the message once, the session keeps running, the dart stays counted.
- Failed truth write: error state, session ended, recording stopped.

## Done when
`./gradlew :capture:testDebugUnitTest build` green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: state holder with JVM tests, behavior fully specified.

## Implementation notes
- Package `capture.ui`: `CaptureViewModel(clock, storage, settings)` extends `androidx.lifecycle.ViewModel`
  (T8 builds it with a factory; the camera side is registered with `attachEffects(effects, setupProvider)` and removed with `detachEffects(owner)`, which interrupts a running session; without a port Start does nothing), exposes `state: StateFlow<CaptureUiState>`; no coroutines inside, all calls on the
  main thread. `CaptureEffects` has `startVideo(volley, target)`, `stopVideo(volley)`, `takePhoto(dart, target)`
  (targets from `CaptureStorage`, folder already right).
- T8 reports back with `onPermission`, `onCameraReady`, `onVideoStarted(volley)`, `onVideoFinalized(volley)` (must be
  called for every stopped video, also after a failure: Saving ends only then), `onPhotoSaved/onPhotoFailed(number)`,
  `onCameraError()`, `onScreenStopped(changingConfigurations)`. the `setupProvider` given to `attachEffects` gives the actual resolutions at Start.
- UI events: `onStart`, `onDart(dart, gravity)`, `onUndo`, `onNextVolley`, `onStopRequested/Cancelled/Confirmed`,
  `onDeleteAllRequested/Cancelled/Confirmed`, `onQuality`, `onErrorDismissed`, `onPhotoMessageShown` (clears
  `photoFailure`, the dart number, after the snackbar).
- Deviations: Delete all and quality are also disabled while saving. `CaptureError` (START, TRUTH_WRITE, CAMERA,
  DELETE_ALL) puts the phase in ERROR once nothing is left to finalize; Start works again and clears it. A failed
  `createTruthFile` at Start leaves no session. The readout shows volley 1 and empty slots outside a running session.
- `DartLabel.Text("T20")`, `Bull` and `Miss` (the last two need string resources in T7/T8). Test: `CaptureViewModelTest`.
