# Z4: Truth format version 2 with the zoom record

## Goal
`truth.json` version 2 records the zoom of the session (CP17); the strict decoder accepts version 2 only.

## Files
- `capture/.../capture/truth/TruthSession.kt`: `zoom: ZoomRecord` after `photo`; new
  `data class ZoomRecord(val requestedRatio: Float?, val ratio: Float?)`, init: each value null or finite and > 0.
  The truth package does not import `camera.zoom` (plain floats).
- `TruthEncoder.kt`, `TruthDecoder.kt` (object `zoom` after `photo`, `finish()` rejects unknown members),
  `JsonFields.kt` (`floatOrNull`), `TruthJson.kt` (`VERSION = 2`, KDoc).
- `capture/.../capture/session/CaptureSession.kt`: `CaptureSetup.zoom: ZoomRecord`, passed to `TruthSession` at Start.
- `capture/.../capture/camera/CaptureController.kt`: `setup` fills `ZoomRecord(requestedRatio = null,
  ratio = camera.zoomRatio)`; Z5 sets `requestedRatio` in the view model.
- `docs/camera/capture-format.md`: title and status version 2, field table row `zoom`, version policy "this reader
  knows version 2 only", section "Version 1" (identical without `zoom`, recorded at zoom 1.0, see root plan "Truth
  file version 2"), example regenerated from the golden test; the `zoom` row says the values have no upper bound
  and phase 0b tools must not assume a plausible range (S9, root "Truth file version 2").
- Tests: `TruthFixtures`, `TruthJsonGoldenTest`, `TruthJsonRoundTripTest`, `TruthJsonRejectTest`,
  `CaptureControllerTest`; fixtures building `CaptureSetup` in `CaptureSessionTest` and `CaptureViewModelTest`.

## Depends on
Z3.

## Rules
CP17, CP9 (killed session reads back), format rules of `docs/camera/capture-format.md`. Decisions DZ5, DZ6.

## Tests
- Golden text: the document example with `"version": 2` and `zoom` 2.4 / 2.4 after `photo`.
- Round trips: zoom 2.4 / 2.3999999; both null; killed session with zoom.
- Rejections: `"version": 1` (otherwise valid v1 text: "unsupported version 1"); missing `zoom`; `zoom` not an object;
  unknown member in `zoom`; missing `ratio`; `ratio` 0, -1, `"2.4"`; `requestedRatio` 0.
- `ZoomRecord` init refuses NaN, infinity, 0 and negatives.
- `CaptureController.setup` copies the fake camera's `zoomRatio` (2.4 and null) with `requestedRatio` null.

## Done when
`./gradlew :capture:testDebugUnitTest build` green; `kosmo-detekt` exit 0.

## Model
opus. Why: new stored format version (case 1): schema change, strict decoder, version 1 rejected and documented.

## Implementation notes
- `ZoomRecord` lives in `TruthSession.kt`; `TruthSession.zoom` sits between `photo` and `volleys` (positional
  constructor order changed). Invalid values fail with "requestedRatio must be null or finite and greater than 0" /
  "ratio must be ...", reported by the decoder at path `$.zoom`; type errors at `$.zoom.ratio` ("expected a float").
- `CaptureSetup.zoom` is required (no default). `CaptureController.setup` returns `ZoomRecord(null, camera.zoomRatio)`;
  Z5 must replace `requestedRatio` (for example `setup.copy(zoom = setup.zoom.copy(requestedRatio = ...))` in the
  view model's setup provider path) so that Start records the last ratio asked.
- Extra tests beyond the list: exponent forms and no upper bound (1500, 1e10, `Float.MIN_VALUE`, `Float.MAX_VALUE`)
  round trip; `-0.0` rejected; Start copies `setup.zoom` (`CaptureSessionTest`); the zoom at Start reaches
  `truth.json` through the controller (`CaptureControllerTest`). `CaptureCountersTest` fixture also updated.
- The format document example equals `TruthJsonGoldenTest.GOLDEN` (checked by diff before commit).
