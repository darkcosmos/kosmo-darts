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
- CameraX 1.6.2 (latest stable: Google Maven `androidx/camera/camera-core/maven-metadata.xml` lists 1.6.2 as the last
  non-pre-release; 1.7.0-rc01 and alphas ignored). One shared `camerax` version in the catalog.
- `capture/build.gradle.kts` is `app/build.gradle.kts` with namespace/applicationId `...kosmodarts.capture`, CameraX
  and `:game-engine`. `versionCode`/`versionName` copied. Sources are under `capture/src/main/kotlin`.
- Launcher icon: foreground/monochrome vectors copied from `:app`; background teal `#FF00695C`. Window theme
  `Theme.KosmoCapture`, colors `window_background`.
- `docs/rules/capture.md` copies CP1 to CP13 from the root plan.
- Manifest check on `capture-debug.apk` (aapt2 37.0.0): permissions `CAMERA`, plus `ACCESS_NETWORK_STATE` (added by
  CameraX, not INTERNET) and `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (AndroidX, own signature permission).
  `uses-feature android.hardware.camera`, `allowBackup=false`. Components: `CaptureActivity` exported=true (launcher);
  `androidx.camera.core.impl.MetadataHolderService` exported=false; `androidx.startup.InitializationProvider`
  exported=false; `androidx.profileinstaller.ProfileInstallReceiver` exported=true with permission `android.permission.DUMP`
  (AndroidX standard); `androidx.compose.ui.tooling.PreviewActivity` exported=true, debug builds only (ui-tooling,
  `debugImplementation`, same as `:app`). Every component declares `exported`.
- `:capture:dependencies --configuration debugRuntimeClasspath`: all five CameraX artifacts at 1.6.2 (no other version),
  `:game-engine` present, no `:app`.
