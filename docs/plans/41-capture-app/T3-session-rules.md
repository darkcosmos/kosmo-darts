# T3: Session reducer, naming and counters

## Goal
Pure Kotlin `CaptureSession` state and reducer that applies Start, Dart, Undo, NextVolley, Stop and Interrupt and
produces the `TruthSession` of T2, plus file naming and counters.

## Files
- Added in `capture/src/main/kotlin/io/github/darkcosmos/kosmodarts/capture/session/`: `CaptureClock` (interface:
  `nowUtc(): Instant`, `monotonicNanos(): Long`), `CaptureSession` (immutable state + reducer functions returning
  the new state and the side effects to perform: start video N, stop video N, take photo N), `CaptureNaming`
  (session folder, `volley-NNN.mp4`, `dart-NNN.jpg`, `truth.json`), `CaptureCounters`.
- Tests in `capture/src/test/kotlin/.../capture/session/` with a fake clock.

## Depends on
T2.

## Rules
CP1, CP2, CP3, CP4, CP5, CP6, CP7, CP9 (Interrupt), CP10, CP13 (PhotoFailed, PhotoSaved events). Decisions Q3
to Q9, Q15, D2, D3, D7 of the root plan. `DARTS_PER_VOLLEY = 3` is a named constant here (the engine's
`DARTS_PER_TURN` is internal), used by tests too. Test names or comments cite the CP id.

## Tests
- CP1: folder name from UTC (`20261031-140322`), at midnight, on 31 December, from a non-UTC default zone; name
  collision with an existing own folder gives `-2`, then `-3`.
- CP2: Dart, Undo, NextVolley refused outside a session.
- CP3: fourth non-voided dart refused (`volleyFull` true); after an undo a third dart is accepted; photo numbers keep
  counting voided entries; padding 999 then 1000.
- CP13: PhotoFailed for dart N leaves the dart with `photo` null and the session running.
- CP4: undo voids the last non-voided dart, keeps its photo name, records the void stamp; refused on an empty volley
  and on a previous volley after NextVolley.
- CP5/CP6: NextVolley with no dart marks `empty`; Stop with an empty last volley keeps it, marked empty, `endReason`
  STOP; Interrupt gives INTERRUPTED.
- CP7: counters ignore voided darts. CP10: video position is event minus video start, floored at 0, null for an
  event before the video Start event is known (and stays null once it arrives).

## Done when
`./gradlew :capture:testDebugUnitTest build` green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: rules fully specified with their tests.

## Implementation notes
- Package `capture.session`. `CaptureSession(truth: TruthSession?, videoStartNanos: Map<Int, Long>)` is immutable;
  every function takes a `CaptureClock` and returns `CaptureOutcome(session, effects, refusal)`: `start(clock, setup,
  existingFolders)`, `videoStarted(clock, volley)`, `dart(clock, dart, gravity)`, `photoSaved(clock, number)`,
  `photoFailed(number)`, `undo`, `nextVolley`, `stop`, `interrupt`. Properties `running`, `volleyFull`,
  `currentVolleyDarts`. Effects: `StartVideo(volley, file)`, `StopVideo(volley)`, `TakePhoto(dart, file)`.
  Refusals: `NO_SESSION`, `ALREADY_RUNNING`, `VOLLEY_FULL`, `NOTHING_TO_UNDO` (state unchanged).
- `CaptureSetup` (app, device, video, photo settings) is given at Start; T8 must pass the actual resolutions then.
- Video positions use `videoStartNanos` per volley: T8 calls `videoStarted(clock, volley)` on the CameraX Start event.
  A photo saved late is placed in the video of its own dart's volley. Stop/Interrupt/NextVolley end stamps carry the
  position of the ended volley; the session end and volley start have none.
- `photoFailed` changes no state (the photo is already null) and sets `CaptureOutcome.failedPhoto` for the message.
- `CaptureCounters(session, total)`: `forNewSession()` then `after(truth)` moves the total by the change of the session
  counter; `cleared()` for Delete all. Persisting the total is for the storage task.
- `DARTS_PER_VOLLEY = 3` is a top-level public constant. Tests: `CaptureSessionTest`, `CaptureNamingTest`,
  `CaptureCountersTest`, `FakeClock`. Padding 999/1000 is tested on `CaptureNaming`.
