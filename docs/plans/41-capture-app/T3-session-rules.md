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
CP1, CP2, CP3, CP4, CP5, CP6, CP7, CP9 (Interrupt), CP10. Decisions Q3 to Q9, D2, D3 of the root plan.

## Tests
- CP1: folder name from UTC (`20261031-140322`), at midnight, on 31 December, from a non-UTC default zone.
- CP2: Dart, Undo, NextVolley refused outside a session.
- CP3: fourth non-voided dart refused; after an undo a third dart is accepted; photo numbers keep counting voided
  entries; padding 999 then 1000.
- CP4: undo voids the last non-voided dart, keeps its photo name, records the void stamp; refused on an empty volley
  and on a previous volley after NextVolley.
- CP5/CP6: NextVolley with no dart marks `empty`; Stop with an empty last volley keeps it, marked empty, `endReason`
  STOP; Interrupt gives INTERRUPTED.
- CP7: counters ignore voided darts. CP10: video position is event minus video start, floored at 0, null before the
  video start event is known.

## Done when
`./gradlew :capture:testDebugUnitTest build` green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: rules fully specified with their tests.

## Implementation notes
