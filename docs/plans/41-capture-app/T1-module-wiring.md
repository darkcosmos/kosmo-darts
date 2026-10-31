# T1: Module wiring, manifest, rules doc

## Goal
Create the empty `:capture` application module that builds, installs as "Kosmo Capture" and requests only `CAMERA`.
Write `docs/rules/capture.md` with rules CP1 to CP12 (root plan, section "Rules").

## Files
- Changed: `settings.gradle.kts`, `gradle/libs.versions.toml` (CameraX version, five libraries: core, camera2,
  lifecycle, video, view; latest stable from Google Maven, record the version and source in Implementation notes).
- Added: `capture/build.gradle.kts` (copy of the relevant parts of `app/build.gradle.kts`: SDK levels, buildTools,
  Java 17, testOptions, Roborazzi record block with `capture/build/outputs/roborazzi`, test dependencies; plus
  `:game-engine` and CameraX), `capture/src/main/AndroidManifest.xml` (only `CAMERA`, `uses-feature`
  `android.hardware.camera` required, one activity `sensorLandscape`, `exported` true for the launcher only),
  `CaptureActivity.kt` (placeholder content), `res/values/strings.xml`, `res/values-fr/strings.xml` (app name),
  launcher icon (reuse an adaptive vector, distinct background color), `docs/rules/capture.md`.
- Not changed: anything under `app/`, `.github/`.

## Depends on
None.

## Rules
CP1 to CP12 (documentation only in this task).

## Tests
- A Robolectric test that launches `CaptureActivity` without crashing.
- A JVM test reading the merged manifest is not required; check the permissions with
  `aapt2 dump permissions capture/build/outputs/apk/debug/capture-debug.apk` and report the output.

## Done when
`./gradlew build :capture:assembleDebug` green, `aapt2 dump permissions` shows only `CAMERA`, `kosmo-detekt` exit 0.

## Model
sonnet. Why: configuration and wiring copied from `:app`, plus checking a new dependency version.

## Implementation notes
