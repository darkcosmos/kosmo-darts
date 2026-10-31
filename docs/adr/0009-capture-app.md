# ADR 0009: Kosmo Capture, a separate data capture app

- Status: Proposed (design review of issue #41); accepted when its PR is merged
- Date: 2026-10-31
- Rules: `docs/rules/capture.md` (CP1 to CP13); format: `docs/camera/capture-format.md`

## Context

The camera milestone needs real data before any detection work: videos of volleys, a photo at each dart entry and
the true value of every dart, on the owner's phone (Pixel 7). Issue #41 asks for a small app that records them
(phase 0a); the phase 0b tools on the PC read them. Kosmo Darts itself must not change.

## Decisions

### A separate application module, independent of `:app`

`:capture` is a second `com.android.application` module (`io.github.darkcosmos.kosmodarts.capture`, label
"Kosmo Capture"), installed next to Kosmo Darts. It depends on `:game-engine` only, for the `Dart` type and its
validation (rule D2), and never on `:app`; `:app` does not depend on it. The keypad and the theme it needs are
copied, not shared (owner decision Q11): the copy will not follow later changes of the X01 keypad.

Rejected: a hidden screen inside Kosmo Darts (adds the camera permission and CameraX to the scoring app, and its
lifecycle to every release); a shared UI module for the keypad (a cross-module refactoring of `:app` for a throwaway
tool).

### Truth file: hand-written JSON, versioned, strict

The truth of a session is one `truth.json`, rewritten after every event (CP9). It is JSON because the phase 0b tools
read it with standard libraries, written and read by a small model, writer and strict reader in pure Kotlin (`capture.truth`), for the same
reasons as ADR 0001: no compiler plugin tied to the Kotlin version, no extra runtime. `org.json` is rejected: it is
lenient (duplicate keys, number coercion) and stubbed in plain JVM tests.

The file carries `format` and an integer `version`, checked before anything else. Version 1 has no optional field:
every field is always written (`null` when absent) and unknown fields are rejected, so any change to the file
increases the version and is documented in `docs/camera/capture-format.md`. The reader is strict (types, duplicate
keys, trailing text, 10 MiB limit before parsing, nesting limit) and builds every dart through the engine `Dart`
constructor. Two clocks per event: UTC for people, `elapsedRealtimeNanos` for durations, plus the position in the
volley video.

### CameraX rather than Camera2 or the system camera app

CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-video`, `camera-view`, one version from the
catalog) binds a preview, a video recording and a still capture to the activity lifecycle, with quality selection
and actual resolutions exposed. Camera2 directly would need several hundred lines of session and surface handling
for the same result. The system camera app (intent) cannot take a photo while it records a video and gives no
timestamps, so it cannot produce the data at all.

### Shared `Download/KosmoCapture/` through MediaStore

Sessions are written with MediaStore under `Download/KosmoCapture/<session>/`, without any storage permission
(own entries only). The owner copies them to the PC over USB (MTP). Rejected: app-specific external storage (hard to
find over USB, deleted on uninstall, so a reinstall would lose uncopied sessions); the Storage Access Framework
(needs a folder pick and a persisted grant for a tool used by one person). Delete all deletes only the entries this
installation created (CP8).

### No sound, no microphone permission

Videos are recorded without audio (owner decision Q2): the detection does not use sound, and the app asks only for
`CAMERA`.

### The total counter is a private counter

The total number of darts (CP7) is a counter in `filesDir`, persisted after every entry and every Undo, not derived
from the session folders: MediaStore queries return only this installation's entries, a folder may be copied and
deleted on the PC at any time, and reading every truth file at launch would be slow and fragile. It restarts after a
reinstall (owner decision Q8). Backup is disabled, like `:app`.

## Consequences

- Two APKs from one repository; the CI workflow needs an owner change to build and publish `capture-debug.apk`.
- The truth format is a contract with the phase 0b tools: a change needs a new version, a document update and a
  reader that knows it.
- Sessions in `Download/` are visible to any app with storage access and over USB; the owner accepts this (local
  data, no network).
- The copied keypad may drift from the X01 keypad; acceptable for a capture tool.
