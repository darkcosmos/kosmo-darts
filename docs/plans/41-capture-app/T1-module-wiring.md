# T1: Module wiring, manifest, rules doc

## Goal
Create the empty `:capture` application module that builds, installs as "Kosmo Capture" and requests only `CAMERA`.
Write `docs/rules/capture.md` with rules CP1 to CP13 (root plan, section "Rules").

## Files
- Changed: `settings.gradle.kts`, `gradle/libs.versions.toml` (CameraX version, five libraries: core, camera2,
  lifecycle, video, view; latest stable from Google Maven, record the version and source in Implementation notes).
- Added: `capture/build.gradle.kts` (copy of the relevant parts of `app/build.gradle.kts`: SDK levels, buildTools,
  Java 17, testOptions, Roborazzi record block with `capture/build/outputs/roborazzi`, test dependencies; plus
  `:game-engine` and CameraX, never `:app`), `capture/src/main/AndroidManifest.xml` (only `CAMERA`, `uses-feature`
  `android.hardware.camera` required, `allowBackup="false"`, `dataExtractionRules` and `fullBackupContent` pointing to
  `res/xml/` files that exclude every domain as in `app/`, one activity `sensorLandscape` with the `configChanges`
  list of the root plan "Approach", `exported` true for the launcher only),
  `CaptureActivity.kt` (placeholder content), `res/values/strings.xml`, `res/values-fr/strings.xml` (app name),
  launcher icon (reuse an adaptive vector, distinct background color), `docs/rules/capture.md`.
- Not changed: anything under `app/`, `.github/`.

## Depends on
None.

## Rules
CP1 to CP13 (documentation only in this task).

## Tests
- A Robolectric test that launches `CaptureActivity` without crashing.
- Manifest checks are command output recorded in Implementation notes, not tests:
  `aapt2 dump permissions` and `aapt2 dump xmltree --file AndroidManifest.xml` on
  `capture/build/outputs/apk/debug/capture-debug.apk`: only `CAMERA` (plus any `DYNAMIC_RECEIVER_NOT_EXPORTED`
  permission added by AndroidX, named), only the launcher activity exported, every component declares `exported`,
  no service, receiver or provider other than those CameraX/AndroidX add (each listed with its `exported` value),
  `allowBackup` false; `./gradlew :capture:dependencies --configuration debugRuntimeClasspath` shows one CameraX
  version and no `:app`.

## Done when
`./gradlew build :capture:assembleDebug` green, the manifest checks above pass, `git diff origin/main -- app` empty,
`kosmo-detekt` exit 0.

## Model
sonnet. Why: configuration and wiring copied from `:app`, plus checking a new dependency version.

## Implementation notes
