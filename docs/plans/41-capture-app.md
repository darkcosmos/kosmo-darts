# Plan: Kosmo Capture, camera phase 0a data capture app (issue #41)

## Goal

Issue [#41](https://github.com/darkcosmos/kosmo-darts/issues/41): a separate test app, "Kosmo Capture", that records
video, photos and the true value of every dart, to build the real dataset that phase 0b uses to measure whether
detection is feasible. Kosmo Darts itself never records images.

## Scope and out of scope

In scope:
- New Android application module `:capture`, independent of `:app` (no dependency in either direction). It may depend
  on `:game-engine` for `Dart` and `Multiplier`.
- One landscape screen: back camera preview, X01 keypad copy, Undo, Next volley, Start/Stop session, 4K/1080p setting,
  counters, Delete all with confirmation.
- Video per volley, photo per dart entry, truth file per session, in `Download/KosmoCapture/<session>/` via MediaStore.
- `docs/camera/capture-format.md` (format and schema), `docs/rules/capture.md` (rules CP1 to CP12 below).

Out of scope: detection, calibration, any image analysis; any change to `:app`, to `:game-engine` behavior or to
`.github/**` (the CI change is proposed to the owner, see "Proposed owner changes"); detekt on `capture/src` (owner
decision R2: not handled in this mission); sound in videos; publishing the app.

## Approach

- **Module**: `capture/` with `com.android.application` and `kotlin.compose`, the same SDK levels, `buildToolsVersion`,
  Java 17 target, Robolectric test options and Roborazzi recording block as `app/build.gradle.kts` (screenshot dir
  `capture/build/outputs/roborazzi`). `applicationId` and namespace `io.github.darkcosmos.kosmodarts.capture`.
  Manifest: only `android.permission.CAMERA`, one activity locked to `sensorLandscape`, label "Kosmo Capture".
- **Layers inside `:capture`** (logic stays free of Android classes and is tested with plain JUnit 4 on the JVM):
  - `capture.truth`: immutable truth model and its JSON codec (pure Kotlin).
  - `capture.session`: `CaptureSession` reducer (start, dart, undo, next volley, stop), file naming, counters, behind a
    `CaptureClock` interface (wall clock as `java.time.Instant`, monotonic as nanoseconds).
  - `capture.storage`: `CaptureStorage` interface (create session folder, open video and photo outputs, write the truth
    file, list and delete own sessions) with a MediaStore implementation and an in-memory fake for tests; a small
    private settings store (quality, total counter) under `filesDir`.
  - `capture.camera`: CameraX binding (Preview, VideoCapture with `Recorder`, ImageCapture), gravity sensor, behind a
    `CaptureCamera` interface so the controller is tested with a fake.
  - `capture.ui`: copied theme and keypad, `CaptureScreen` (renders state, forwards events), `CaptureViewModel`.
- **JSON**: hand-written writer and strict reader in pure Kotlin, like the save format choice of ADR 0001 (no new
  dependency). Alternative: kotlinx.serialization (rejected for the same reasons as ADR 0001: compiler plugin tied to
  the Kotlin version, extra runtime). `org.json` is rejected: stubbed in plain JVM tests and lenient.
- **Keypad and theme**: copied from `app/.../ui/game/Keypad*.kt` and `app/.../ui/theme/` (only the files the keypad
  needs), with `AppEvent` replaced by a capture-local event type and strings moved to capture resources. Owner decision
  11: the copy will not follow later changes of the X01 keypad.
- **Camera**: CameraX `camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-video`, `camera-view` (latest
  stable, one shared version in the catalog). `QualitySelector` with UHD or FHD from the setting and a lower-quality
  fallback; no audio (`withAudioEnabled` never called). One `Recording` per volley, output with
  `MediaStoreOutputOptions` (`RELATIVE_PATH` `Download/KosmoCapture/<session>/`). `ImageCapture` at the highest
  available resolution, saved through MediaStore. Actual video and photo resolutions are read from the bound use cases'
  `resolutionInfo` and stored in the truth file, with the camera's `SENSOR_INFO_TIMESTAMP_SOURCE`.
- **Durability**: the truth file is rewritten after every event (owner gets a valid file even if the app dies).
  Leaving the screen (stop of the activity) or a camera/recording error ends the session as Stop does (CP9).
- **Keep screen on**: `FLAG_KEEP_SCREEN_ON` while a session runs only.

## Rules (new `docs/rules/capture.md`, prefix CP; T1 writes them, later tasks cite them)

- CP1 Start creates session folder `YYYYMMDD-HHMMSS` (UTC wall clock of the start), volley 1 and video `volley-001.mp4`.
- CP2 Outside a session the keypad, Undo and Next volley are disabled. The quality setting and Delete all are enabled
  only outside a session.
- CP3 A dart entry is accepted while the current volley has fewer than 3 non-voided darts. It is appended with its
  entry timestamps and triggers one photo `dart-NNN.jpg`, NNN = session-wide entry number (voided entries included),
  zero-padded to 3 digits, wider beyond 999. Volley files `volley-NNN.mp4` follow the same padding.
- CP4 Undo voids the last non-voided dart of the current volley only: the dart stays in the file with `voided: true`,
  its void timestamps and its photo. Disabled when the current volley has no non-voided dart.
- CP5 Next volley ends the current volley (end timestamps, video position), closes its video and starts the next volley
  and its video. Allowed with no dart; a volley with no non-voided dart is marked `empty: true`.
- CP6 Stop ends the current volley as CP5 without starting a new one (an empty last volley keeps its video, marked
  empty), then ends the session.
- CP7 Counters: session = non-voided darts of the running (or last) session; total = non-voided darts of every session
  of this installation (stored privately, restarts after reinstall).
- CP8 Delete all asks for confirmation, deletes every session folder created by this installation and resets the total.
  The screen states that sessions of an earlier installation are not deleted.
- CP9 The truth file is rewritten after every event. Leaving the screen or a camera error ends the session as CP6.
- CP10 Timestamps: `utc` ISO 8601 UTC with milliseconds (`2026-10-31T14:03:22.123Z`, format H1); `monotonicNanos` =
  `SystemClock.elapsedRealtimeNanos()`; `videoPositionMs` = event monotonic minus the volley video start monotonic
  (recorded at the CameraX `Start` event), floored at 0.
- CP11 Quality setting 4K or 1080p, default 4K, kept across launches; the actual video and photo resolutions are stored.
- CP12 The screen stays on during a session only.

## Files and types added or changed

- Changed: `settings.gradle.kts` (`include(":capture")`), `gradle/libs.versions.toml` (CameraX version and libraries).
- Added: `capture/build.gradle.kts`, `capture/src/main/AndroidManifest.xml`, `capture/src/main/res/values{,-fr}/`,
  `capture/src/main/kotlin/io/github/darkcosmos/kosmodarts/capture/` packages `truth`, `session`, `storage`, `camera`,
  `ui`, `ui/theme`, plus `CaptureActivity`; tests under `capture/src/test/kotlin/...`.
- Added docs: `docs/rules/capture.md`, `docs/camera/capture-format.md`.
- Not changed: anything under `app/`, `game-engine/src/main`, `.github/`.

## Data read, stored or exposed

- Stored in shared `Download/KosmoCapture/<session>/`: MP4 videos without sound, JPEG photos, `truth.json`. Visible
  over USB (MTP) and to any app with storage access: the owner accepts this (local data, never pushed).
- Stored privately in `filesDir`: quality setting and total counter. No network, no `INTERNET` permission.
- Truth file content: dart values, timestamps, file names, video positions, gravity vector, resolutions, device model,
  app version. No location, no account, no image metadata added by the app.

## Truth file outline (finalized by T2 in `docs/camera/capture-format.md`)

`{ format: "kosmo-capture-truth", version: 1, session, app {id, version}, device {manufacturer, model, sdk},
clock: "elapsedRealtimeNanos", startedAt {utc, monotonicNanos}, endedAt | null, endReason ("STOP"|"INTERRUPTED") | null,
video {requestedQuality "UHD"|"FHD", width, height}, photo {width, height, timestampSource}, volleys: [ { index, video,
start {utc, monotonicNanos}, end {utc, monotonicNanos, videoPositionMs} | null, empty, darts: [ { number, segment,
multiplier "SINGLE"|"DOUBLE"|"TRIPLE", voided, entry {utc, monotonicNanos, videoPositionMs}, voidedAt | null,
photo {file, utc, monotonicNanos, videoPositionMs} | null, gravity {x, y, z} | null } ] } ] }`. Miss is segment 0,
outer bull 25, bull 50, always `SINGLE` (engine rule D2). Unknown `version` is rejected on read.

## Screens and states

Single landscape screen: preview (left, about 55% width), keypad (right), top bar with counters, quality toggle,
Start/Stop; bottom row Undo and Next volley; Delete all in the top bar. States: camera permission missing (message and
"Allow camera" button, rest disabled); idle; session running (red recording dot, Stop); saving error (message, session
ended); Delete all confirmation dialog with the reinstall note. Touch targets at least 48dp, content descriptions on
icons, dark theme colors copied from the design system.

| Strings | English | French |
|---|---|---|
| App name | "Kosmo Capture" | identical |
| Start / Stop | "Start session" / "Stop session" | « Démarrer la session » / « Arrêter la session » |
| Next volley | "Next volley" | « Volée suivante » |
| Undo | "Undo" | « Annuler » |
| Miss key | "Miss" | « Raté » |
| Counters | "Session: %1$d darts", "Total: %1$d darts" (plurals) | « Session : %1$d fléchettes », « Total : %1$d fléchettes » (plurals) |
| Quality | "4K", "1080p" | identical |
| Delete all | "Delete all" | « Tout supprimer » |
| Confirm | "Delete every session of this installation? Sessions from an earlier installation stay on the phone." | « Supprimer toutes les sessions de cette installation ? Celles d'une installation précédente restent sur le téléphone. » |
| Permission | "Kosmo Capture needs the camera." / "Allow camera" | « Kosmo Capture a besoin de la caméra. » / « Autoriser la caméra » |
| Error | "Saving failed: the session was stopped." | « L'enregistrement a échoué : la session est arrêtée. » |

## Test plan

- T2 (JVM): encode then decode round trip of a full session (3 volleys, voided dart, empty volley, miss, 25, bull);
  golden JSON text for a small session; voided dart keeps photo; decode rejects unknown version, missing field, bad
  multiplier, triple bull; timestamp format with milliseconds, zero millis kept (`.000Z`).
- T3 (JVM, fake clock): CP1 folder name at midnight and year boundary; CP3 fourth dart refused, numbering continues
  after undo, padding 999 to 1000; CP4 undo only in current volley, disabled after Next volley; CP5 empty volley; CP6
  stop with empty last volley; CP7 counters ignore voided darts; CP10 video position floor at 0.
- T4 (JVM with fake storage + Robolectric for the MediaStore implementation where Robolectric supports it): truth file
  rewritten after each event; delete all removes every own session and resets the total; settings round trip.
- T6 (Robolectric + Roborazzi, fake camera, no real preview): captures of idle, running with 2 darts, 3 darts (keypad
  disabled), permission missing, delete confirmation, error; in English and French.
- T7 (JVM, fake camera and storage): controller starts a recording per volley, takes a photo per dart, ends the session
  on interruption and on error; actual resolutions copied into the session.

## How Kosmo verifies on the phone (Pixel 7)

Needs the owner CI change below to get `capture-debug.apk`. Then: install, allow camera; start a session in 4K; enter
3 darts, check the 4th is refused; undo one; Next volley; Next volley with no dart; Stop; copy
`Download/KosmoCapture/<session>/` to the PC and check 3 videos without sound, one photo per entry, `truth.json` with the
voided dart and the empty volley; same in 1080p; Delete all and check the folder is gone; check Kosmo Darts unchanged.
The full step list and the collection protocol go in the PR description (see below).

## PR description material (for developer-fix)

- Collection protocol: at least 500 darts; whole board including the far side, tight groups, bull, off-board darts;
  daylight and evening; 2 or 3 tripod positions; one session per lighting and position; copy to PC then Delete all.
- Storage estimate on the Pixel 7, assuming about one minute per volley and 170 volleys for 500 darts: 4K about
  350 MB per minute, about 60 GB; 1080p about 130 MB per minute, about 22 GB; photos about 4 MB each, about 2.5 GB.
  Advise copying and deleting between sessions.
- Copy: Pixel in file transfer mode, Windows Explorer to `\\wsl$\<distro>\home\<user>\kosmo-data\captures\`.
- Proposed owner changes (`.github/workflows/android.yml`): build `:capture:assembleDebug` too; upload
  `capture/build/outputs/apk/debug/capture-debug.apk` as an artifact; run the aapt2 permission check on that APK too
  (no `INTERNET`, and only `CAMERA` expected); upload `capture/build/outputs/roborazzi/`. Detekt input unchanged (R2).
- Versions table for CameraX.

## Tasks

| Id | Title | Task file | Depends on | Model | Why this model |
|---|---|---|---|---|---|
| T1 | Module wiring, manifest, rules doc | `41-capture-app/T1-module-wiring.md` | - | sonnet | Wiring and configuration copied from `:app`, plus a new dependency to check |
| T2 | Truth file model and JSON codec, format doc | `41-capture-app/T2-truth-format.md` | T1 | opus | New stored format (case 1): schema, strict decoder and its versioning |
| T3 | Session reducer, naming and counters | `41-capture-app/T3-session-rules.md` | T2 | sonnet | Rules fully specified (CP1 to CP7, CP10) with listed tests |
| T4 | Storage: MediaStore, settings, delete all | `41-capture-app/T4-storage.md` | T3 | sonnet | Platform code behind an interface, tests listed |
| T5 | Copy theme and keypad into the module | `41-capture-app/T5-keypad-theme.md` | T1 | sonnet | Copy with small adaptations (event type, resources) |
| T6 | Capture screen, view model, captures | `41-capture-app/T6-screen.md` | T3, T5 | sonnet | Screen from a specified layout, state holder with JVM tests |
| T7 | CameraX controller, sensor, lifecycle | `41-capture-app/T7-camera.md` | T4, T6 | sonnet | Wiring of CameraX behind a tested interface |

One `opus` task (T2).

## Decisions

| Id | Decision | Rules |
|---|---|---|
| Q1 | Quality is a 4K/1080p setting on the screen; the actual resolution is recorded. Phone: Pixel 7. | CP11 |
| Q2 | Video without sound, no microphone permission. | - |
| Q3 | Undo only within the current volley. | CP4 |
| Q4 | At most 3 darts per volley. | CP3 |
| Q5 | Stop ends the current volley like Next volley; an empty last volley keeps its video, marked empty. | CP6 |
| Q6 | Outside a session, Next volley, keypad and Undo are disabled. | CP2 |
| Q7 | A volley with no dart is allowed. | CP5 |
| Q8 | Total restarts after reinstall; Delete all deletes only this installation's sessions; the screen says so. | CP7, CP8 |
| Q9 | `Download/KosmoCapture/YYYYMMDD-HHMMSS/` (UTC), `volley-001.mp4`, `dart-001.jpg`, `truth.json`. | CP1, CP3 |
| Q10 | Timestamps ISO 8601 UTC with milliseconds, e.g. `2026-10-31T14:03:22.123Z`. | CP10 |
| Q11 | Keypad and dark theme copied into `:capture`; no change to `:app`. | - |
| Q12 | CI: Kosmo updates the workflow himself; the change is described under "Proposed owner changes". | - |
| Q13 | Detekt on the new module (R2) is out of scope. | - |
| D1 | Hand-written JSON codec, no new serialization dependency (same reasoning as ADR 0001). | - |
| D2 | Developer default, to confirm: counters exclude voided darts. | CP7 |
| D3 | Developer default, to confirm: leaving the screen or a camera error ends the session like Stop (`endReason: INTERRUPTED`). | CP9 |

## Open questions

- D2 and D3 are defaults chosen by the developer; Kosmo may confirm or change them.
