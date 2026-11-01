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
  Really leaving the screen or a camera/recording error ends the session as Stop does (CP9).
- **Configuration changes** (owner decision Q17): the session state lives in `CaptureViewModel`, which survives
  recreation. The manifest also declares `configChanges` for `orientation|screenSize|screenLayout|smallestScreenSize|
  uiMode|locale|layoutDirection|fontScale|density|keyboard|keyboardHidden|navigation`, so CameraX stays bound and the
  recording is not cut (Compose recomposes with the new configuration). Only `onStop` with
  `!isChangingConfigurations` sends Interrupt; this decision lives in the Activity, tested through the view model.
- **Edge to edge**: `enableEdgeToEdge`, content padded by `WindowInsets.safeDrawing` on all sides (with
  `sensorLandscape` the cutout and the navigation bar can be on either side).
- **Keep screen on**: `FLAG_KEEP_SCREEN_ON` while a session runs only.

## Rules (new `docs/rules/capture.md`, prefix CP; T1 writes them, later tasks cite them)

- CP1 Start creates session folder `YYYYMMDD-HHMMSS` (UTC wall clock of the start, from the clock only, never from
  input), volley 1 and video `volley-001.mp4`. If a folder of this installation already has that name (two starts in
  the same second), the suffix `-2`, `-3`... is appended.
- CP2 Outside a session the keypad, Undo and Next volley are disabled. The quality setting and Delete all are enabled
  only outside a session. Start is disabled until the camera is bound and while the previous session is saving.
- CP3 A dart entry is accepted while the current volley has fewer than `DARTS_PER_VOLLEY` (3, named constant in
  `capture.session`) non-voided darts. It is appended with its entry timestamps and triggers one photo `dart-NNN.jpg`,
  NNN = session-wide entry number (voided entries included), zero-padded to 3 digits, wider beyond 999. Volley files
  `volley-NNN.mp4` follow the same padding. When the volley is full the keypad is disabled and the hint "Volley full:
  press Next volley" is shown (no refusal on tap).
- CP4 Undo voids the last non-voided dart of the current volley only: the dart stays in the file with `voided: true`,
  its void timestamps and its photo. Disabled when the current volley has no non-voided dart.
- CP5 Next volley ends the current volley (end timestamps, video position), closes its video and starts the next volley
  and its video. Allowed with no dart; a volley with no non-voided dart is marked `empty: true`.
- CP6 Stop asks for confirmation (Cancel keeps the session untouched). Confirmed, it ends the current volley as CP5
  without starting a new one (an empty last volley keeps its video, marked empty), then ends the session
  (`endReason: STOP`). The screen shows "Saving..." until the last video is finalized and the truth file written.
- CP7 Counters: session = non-voided darts of the running (or last) session; total = non-voided darts of every session
  of this installation. The total is a private counter (not derived from folders), persisted after every dart entry
  and every undo (so it is right after Stop, after an interruption and after a crash), reset by Delete all, restarts
  after reinstall; a missing or corrupt counter file reads as 0 without crashing.
- CP8 Delete all asks for confirmation, deletes every session folder created by this installation and resets the total.
  The screen states that sessions of an earlier installation are not deleted.
  Delete all queries only own MediaStore entries whose `RELATIVE_PATH` starts with `Download/KosmoCapture/`, pending
  entries included, and deletes by the returned URIs only.
- CP9 The truth file is rewritten after every event. Really leaving the screen (activity stopped and not changing
  configurations) or a camera/recording error ends the session as CP6 without confirmation (`endReason:
  INTERRUPTED`), and the open recording is stopped. A configuration change (rotation, dark mode, locale, font scale)
  never interrupts the session.
- CP10 Timestamps: `utc` ISO 8601 UTC with milliseconds (`2026-10-31T14:03:22.123Z`, format H1); `monotonicNanos` =
  `SystemClock.elapsedRealtimeNanos()`; `videoPositionMs` = event monotonic minus the volley video start monotonic
  (recorded at the CameraX `Start` event), floored at 0; `null` for an event recorded before that `Start` event.
- CP11 Quality setting 4K or 1080p, default 4K, kept across launches; the actual video and photo resolutions are stored.
- CP12 The screen stays on during a session only.
- CP13 Photo failure: if the photo of a dart cannot be taken or saved, the dart is kept with `photo: null`, the
  session continues and a non-blocking message "Photo failed for this dart" is shown for a few seconds.

## Files and types added or changed

- Changed: `settings.gradle.kts` (`include(":capture")`), `gradle/libs.versions.toml` (CameraX version and libraries).
- Added: `capture/build.gradle.kts`, `capture/src/main/AndroidManifest.xml`, `capture/src/main/res/values{,-fr}/`,
  `capture/src/main/kotlin/io/github/darkcosmos/kosmodarts/capture/` packages `truth`, `session`, `storage`, `camera`,
  `ui`, `ui/theme`, plus `CaptureActivity`; tests under `capture/src/test/kotlin/...`.
- Added: `capture/src/main/res/xml/data_extraction_rules.xml` and `full_backup_content.xml` (exclude every domain,
  same as `app/`).
- Added docs: `docs/rules/capture.md`, `docs/camera/capture-format.md`, `docs/adr/0009-capture-app.md` (T2): module
  and its independence from `:app`; truth format and versioning; CameraX dependency versus Camera2 directly (and
  versus the system camera app intent, which cannot take a photo during a recording); shared `Download/` storage via
  MediaStore versus app-specific external storage (not visible as easily over USB, deleted on uninstall) and SAF
  (needs a folder pick); no sound and no microphone permission; the total counter as a private counter (A4).
- Not changed: anything under `app/`, `game-engine/src/main`, `.github/` (`git diff origin/main -- app` stays empty).

## Data read, stored or exposed

- Stored in shared `Download/KosmoCapture/<session>/`: MP4 videos without sound, JPEG photos, `truth.json`. Visible
  over USB (MTP) and to any app with storage access: the owner accepts this (local data, never pushed).
- Stored privately in `filesDir`: quality setting and total counter. Backup disabled (`allowBackup="false"`,
  extraction rules excluding everything). No network, no `INTERNET` permission. Only the launcher activity is
  exported; the merged manifest (CameraX included) adds no permission, service, receiver or provider beyond what T1
  records.
- Truth file content: dart values, timestamps, file names, video positions, gravity vector, resolutions, device model,
  app version. No location, no account, no image metadata added by the app.

## Truth file outline (finalized by T2 in `docs/camera/capture-format.md`)

`{ format: "kosmo-capture-truth", version: 1, session, app {id, version}, device {manufacturer, model, sdk},
clock: "elapsedRealtimeNanos", startedAt {utc, monotonicNanos}, endedAt | null, endReason ("STOP"|"INTERRUPTED") | null,
video {requestedQuality "UHD"|"FHD", width, height}, photo {width, height, timestampSource}, volleys: [ { index, video,
start {utc, monotonicNanos}, end {utc, monotonicNanos, videoPositionMs} | null, empty, darts: [ { number, segment,
multiplier "SINGLE"|"DOUBLE"|"TRIPLE", voided, entry {utc, monotonicNanos, videoPositionMs}, voidedAt | null,
photo {file, utc, monotonicNanos, videoPositionMs} | null, gravity {x, y, z} | null } ] } ] }`. Miss is segment 0,
outer bull 25, bull 50, always `SINGLE` (engine rule D2); the decoder builds each value through the engine's `Dart`
constructor, so its validation applies. `photo` is null after a photo failure (CP13). A session killed before Stop
reads back with `endedAt` and `endReason` null and its last volley `end` null. Unknown `format` or `version` is
rejected on read, as is any text larger than 10 MB.

## Screens and states

Single landscape screen, edge to edge, padded by the safe drawing insets. Two columns:

- **Left column (preview side, about 55% width)**: status line on top (REC dot with the text label "REC" while
  recording, session counter, total counter; a `FlowRow`, so it wraps onto two lines at large font scale); camera
  preview filling the rest (placeholder "Starting camera..." until the first frame); control row at the bottom:
  **Delete all** at the far left (outlined, away from the keypad), the **4K / 1080p** toggle in the middle, and
  **Start session / Stop session** at the right end of the column.
- **Right column (keypad side)**: volley readout on top, keypad, action row (Undo, Next volley).
- **Volley readout** (comes from `CaptureUiState`, computed by the view model, never in the composable): "Volley 3"
  and three dart slots showing the value (`T20`, `D5`, `5`, `25`, `Bull`, `Miss`) or an empty dash, at least 20sp
  bold; when the volley is full, the hint "Volley full: press Next volley" below the slots.
- **Primary action**: idle = Start session (filled); running = Next volley (filled), Stop session becomes an outlined
  red button, and Stop asks for confirmation (CP6). Delete all is never primary.

Height budget at 412dp landscape height (Pixel 7), minus about 24dp top and 24dp bottom insets, so about 364dp:
right column = readout 56 + keypad 5 rows x 42 + 4 gaps x 4 = 226 + action row 42 + 3 gaps x 8 = 24, total 348dp.
Buttons and keys are 42dp tall in landscape (CLAUDE.md exception, owner decision Q14), at least 48dp wide. Keypad
labels keep the copied `fixedSp` sizing of `:app`; other texts scale, and at font scale 200 % the status line wraps
and the preview shrinks, while buttons grow in height rather than clip.

States: camera permission missing (message and "Allow camera"; after a permanent refusal the button opens the app
settings); camera starting; idle; session running; volley full; Stop confirmation dialog; saving (after Stop, Start
disabled); photo failure snackbar (CP13, session continues); saving error (session ended, message says files up to
that point are kept); Delete all confirmation dialog with the reinstall note. Disabled controls use the design
system disabled style (reduced alpha and no fill), never color alone. Content descriptions on every icon, on the REC
dot, on the preview and on the toggle. Dark theme colors copied from the design system.

| Strings | English | French |
|---|---|---|
| App name | "Kosmo Capture" | identical |
| Start / Stop | "Start session" / "Stop session" | « Démarrer la session » / « Arrêter la session » |
| Next volley | "Next volley" | « Volée suivante » |
| Undo | "Undo" | « Annuler » |
| Miss key | "Miss" | « Raté » |
| Counters (plurals; French `one` covers 0 and 1) | one: "Session: %1$d dart", other: "Session: %1$d darts"; same for "Total" | one: « Session : %1$d fléchette », other: « Session : %1$d fléchettes » ; idem « Total » |
| REC label / description | "REC" / "Recording" | « REC » / « Enregistrement en cours » |
| Volley readout | "Volley %1$d"; empty slot "-"; values `T20`, `D5`, `25`, "Bull", "Miss" | « Volée %1$d » ; « - » ; `T20`, `D5`, `25`, « Bull », « Raté » |
| Readout description | "Volley %1$d: %2$s" | « Volée %1$d : %2$s » |
| Volley full | "Volley full: press Next volley" | « Volée complète : touchez Volée suivante » |
| Quality | "4K", "1080p"; description "Video quality: %1$s" | identiques ; « Qualité vidéo : %1$s » |
| Preview description | "Camera preview" | « Aperçu de la caméra » |
| Camera starting | "Starting camera..." | « Démarrage de la caméra... » |
| Delete all | "Delete all"; description "Delete all sessions" | « Tout supprimer » ; « Supprimer toutes les sessions » |
| Delete confirm | "Delete every session of this installation? Sessions from an earlier installation stay on the phone." / "Delete" / "Cancel" | « Supprimer toutes les sessions de cette installation ? Celles d'une installation précédente restent sur le téléphone. » / « Supprimer » / « Annuler » |
| Stop confirm | "Stop the session? The current volley is saved." / "Stop" / "Continue" | « Arrêter la session ? La volée en cours est enregistrée. » / « Arrêter » / « Continuer » |
| Saving | "Saving..." | « Enregistrement... » |
| Permission | "Kosmo Capture needs the camera." / "Allow camera" / "Open settings" | « Kosmo Capture a besoin de la caméra. » / « Autoriser la caméra » / « Ouvrir les réglages » |
| Photo failure | "Photo failed for this dart. The entry is kept." | « La photo de cette fléchette a échoué. La saisie est conservée. » |
| Error | "Saving failed: the session was stopped. Files saved until now are kept." | « L'enregistrement a échoué : la session est arrêtée. Les fichiers déjà enregistrés sont conservés. » |

## Test plan

Every test name or comment cites its CP rule id. Task files list the cases in full; summary:

- T2 (JVM): round trip of a full session (voided dart with photo, null photo, empty volley, miss, 25, bull); round
  trip of a killed session (CP9); golden text; strict rejections (version, missing field, bad multiplier, triple
  bull, truncated text, text over 10 MB); UTC millis.
- T3 (JVM, fake clock): CP1 naming and collision suffix; CP2; CP3 limit through `DARTS_PER_VOLLEY`, numbering and
  padding; CP4; CP5; CP6; CP7 counters; CP10 position null before the Start event and floored at 0; CP13 photo null.
- T4 (JVM with `FakeCaptureStorage` that models ownership and pending entries, plus Robolectric where it can):
  truth rewrite per event, last write of a killed session decodes; CP7 total after Stop, Interrupt, Undo; CP8 delete
  all removes own sessions only (a foreign file in the same folder survives) and resets the total.
- T6 (JVM view model): readout field, enabled flags, volley full hint, Stop confirm and cancel, Delete all flow,
  photo failure message, saving and error states, CP9 configuration change versus real stop.
- T7 (Robolectric + Roborazzi, placeholder preview): readout text assertion; captures EN and FR of idle, running with
  2 darts, volley full, Stop confirmation, Delete all confirmation, permission missing, photo failure, error, at
  915x412dp and 640x360dp, and at font scale 2.0; no clipping and 42dp landscape targets at 412dp and font scale 2.0.
- T8 (JVM, fake camera and storage): recording per volley, photo per dart, photo failure (CP13), interruption and
  recording error stop the recording (CP9), actual resolutions copied.
- Manifest, permissions and dependency checks are proven by command output recorded in the task notes and in
  "Local checks" of the PR, not by tests.

## How Kosmo verifies on the phone (Pixel 7)

Needs the owner CI change below to get `capture-debug.apk`. Then:
1. Install, refuse the camera: the message and "Allow camera" show; refuse again: the button opens the settings;
   allow the camera there, come back: the preview shows.
2. Start a session in 4K: REC shows, "Volley 1" readout empty. Enter T20, 5, Miss: the readout shows them, the
   keypad is disabled with "Volley full: press Next volley". Undo: Miss disappears, the keypad is enabled again.
3. Next volley: "Volley 2" empty. Next volley with no dart: "Volley 3". Rotate the phone 180 degrees: the session
   goes on (REC still shown, same volley).
4. Stop: the confirmation shows; Continue keeps the session; Stop again and confirm: "Saving..." then idle.
5. Copy `Download/KosmoCapture/<session>/` to the PC: 3 videos without sound, one photo per entry, `truth.json`
   with the voided dart and the empty volley. Same in 1080p.
6. Start a session and press the Home key: the session ends (truth file `endReason` INTERRUPTED).
7. Delete all: the dialog text (check in French and English); confirm: folders gone, total back to 0.
8. The saving error state is hard to trigger on purpose (full storage); it is covered by tests and captures only.
9. Kosmo Darts is unchanged.
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
- Versions table for CameraX (one version for the five artifacts).
- Local checks also list the outputs of `aapt2 dump permissions`, the merged manifest check and
  `./gradlew :capture:dependencies --configuration debugRuntimeClasspath` summary (T1, T8).

## Tasks

| Id | Title | Task file | Depends on | Model | Why this model |
|---|---|---|---|---|---|
| T1 | Module wiring, manifest, rules doc | `41-capture-app/T1-module-wiring.md` | - | sonnet | Wiring and configuration copied from `:app`, plus a new dependency to check |
| T2 | Truth file model and JSON codec, format doc, ADR | `41-capture-app/T2-truth-format.md` | T1 | opus | New stored format (case 1): schema, strict decoder and its versioning |
| T3 | Session reducer, naming and counters | `41-capture-app/T3-session-rules.md` | T2 | sonnet | Rules fully specified (CP1 to CP7, CP10, CP13) with listed tests |
| T4 | Storage: MediaStore, settings, delete all | `41-capture-app/T4-storage.md` | T3 | sonnet | Platform code behind an interface, tests listed |
| T5 | Copy theme and keypad into the module | `41-capture-app/T5-keypad-theme.md` | T1 | sonnet | Copy with small adaptations (event type, resources) |
| T6 | View model and UI state | `41-capture-app/T6-view-model.md` | T3, T4 | sonnet | State holder with JVM tests, behavior specified |
| T7 | Capture screen and captures | `41-capture-app/T7-screen.md` | T5, T6 | sonnet | Screen from a specified layout and height budget |
| T8 | CameraX controller, sensor, lifecycle | `41-capture-app/T8-camera.md` | T7 | sonnet | Wiring of CameraX behind a tested interface |

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
| D2 | Confirmed by Kosmo: counters exclude voided darts. | CP7 |
| D3 | Confirmed by Kosmo: really leaving the screen or a camera error ends the session like Stop (`endReason: INTERRUPTED`). | CP9 |
| Q14 | Landscape buttons and keys 42dp tall, as in Kosmo Darts (CLAUDE.md exception). | - |
| Q15 | Photo failure: entry kept, session continues, non-blocking message, `photo: null`. | CP13 |
| Q16 | Stop needs a confirmation; Delete all is separated from Start/Stop (opposite end of the control row). | CP6 |
| Q17 | A configuration change (rotation, dark mode, locale) never interrupts the session. | CP9 |
| D4 | Design review: ADR `docs/adr/0009-capture-app.md` in T2 (A1). | - |
| D5 | Design review: volley readout from `CaptureUiState`; volley full = keypad disabled plus hint (U1, U2). | CP3 |
| D6 | Design review: backup disabled, merged manifest and dependency tree checked by command output (S1, S2, S5). | - |
| D7 | Design review: folder collision suffix `-2`; position null before the CameraX Start event; truth text limit 10 MB; Delete all by own URIs under `Download/KosmoCapture/`, pending included. | CP1, CP8, CP10 |
| D8 | Design review: T6 split into view model (T6) and screen (T7); camera becomes T8. | - |
| D9 | Design review (U9, U10): photo failure snackbar announced by TalkBack, at least 4 s, dismiss target reachable at font scale 2.0; the volley full hint is part of the readout's content description. | CP3, CP13 |

## Open questions

None.
