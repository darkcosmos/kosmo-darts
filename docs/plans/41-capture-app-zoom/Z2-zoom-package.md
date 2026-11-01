# Z2: Portable zoom package and its tests

## Goal
Pure Kotlin zoom logic, reusable later by Kosmo Darts, with no dependency on the rest of `:capture`.

## Files
- Added in `capture/src/main/kotlin/io/github/darkcosmos/kosmodarts/camera/zoom/`: `ZoomRange.kt` (`ZoomRange`,
  `ZOOM_STEP`, `DEFAULT_ZOOM_RATIO`, `quantizeZoom`), `ZoomSelection.kt`, `ZoomSettingFormat.kt`. API and behavior:
  root plan "Approach", "Portable zoom package" (including `steppedBy`, `canStepDown`, `canStepUp`). KDoc on each type says it must stay free of Android and capture types.
- Added tests in `capture/src/test/kotlin/io/github/darkcosmos/kosmodarts/camera/zoom/`: `ZoomRangeTest`,
  `ZoomSelectionTest`, `ZoomSettingFormatTest`, `ZoomPackageIsolationTest`.

## Depends on
Z1.

## Rules
CP14 (slider and buttons), CP16 (parse fallbacks). Decisions DZ1, DZ2, DZ3, DZ10, DZ11.

## Tests
- CP14 range 1..10: position 0 -> 1.0, 1 -> 10.0, 0.5 -> 3.2 (sqrt 10 rounded); positions -1, 2, NaN -> 1.0, 10.0,
  1.0; `ratioAt(positionOf(r))` gives back `r` for every 0.1 ratio `r` from 1.0 to 10.0.
- CP14 rounding on the shortest decimal form (DZ10): 2.44 -> 2.4, 2.45 -> 2.5, 2.46 -> 2.5, 1.05 -> 1.1,
  2.35 -> 2.4, 1.04999 -> 1.0. Range 0.6..8.37: position 1 -> 8.37 (rounded 8.4 kept inside).
- CP14 buttons (`steppedBy`), range 1..10: 2.4 +1 -> 2.5, 2.4 -1 -> 2.3; ten +1 steps from 1.0 -> exactly 2.0
  (no float drift); 10.0 +1 -> 10.0 and 1.0 -1 -> 1.0; range 0.6..8.37: 8.37 +1 -> 8.37, 8.37 -1 -> 8.3, 0.6 -1
  -> 0.6; range 1..1: unchanged. `canStepDown` false at 1.0, `canStepUp` false at 10.0 and at 8.37 in 0.6..8.37,
  both true at 2.4, both false when not adjustable or range null.
- `ZoomRange` factory: null for NaN, infinity, 0, negative min, max < min; `adjustable` false for 1..1 and 1..1.05,
  true for 1..1.1; `positionOf` 0 when not adjustable.
- `ZoomSelection`: default ratio 1.0 and range null; remembered 12 with range 1..10 -> ratio 10.0, remembered still 12;
  `movedTo(0.5)` -> remembered 3.2; `movedTo` on a non-adjustable range changes nothing; `withRange(null)` keeps the
  remembered ratio; `unavailable` only when a range is known and not adjustable.
- CP16 `parse`: null, "", "abc", "NaN", "Infinity", "0", "-1" -> 1.0; "2.4" -> 2.4; `format(2.4f)` -> "2.4".
- Isolation (DZ3, A1, T2), over every `.kt` file under `src/main/kotlin/io/github/darkcosmos/kosmodarts/camera/zoom/`
  (relative to the module directory, the Gradle test working directory). Only lines whose trimmed text starts with
  `package ` or `import ` are checked (comments and KDoc ignored); matches are case-sensitive, lowercase. Asserts:
  at least 3 files; each file's `package` line is exactly `package io.github.darkcosmos.kosmodarts.camera.zoom`;
  every import starts with `kotlin.` or `java.math.`, and these two prefixes are the whole allow-list (an import
  of `java.util.*` or `kotlinx.*` fails); no package or import line contains `capture`; no import
  starts with `android.`, `androidx.` or `kotlinx.coroutines`. Each assert names the file and line on failure.

## Done when
`./gradlew :capture:testDebugUnitTest build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: pure logic fully specified, tests listed.

## Implementation notes
Package in `capture/src/main/kotlin/io/github/darkcosmos/kosmodarts/camera/zoom/`. Deviations: `ZoomRange` is built with
`ZoomRange.of(min, max)` (private constructor, plain class with equals); `adjustable` is computed in `BigDecimal` on
the shortest decimal form; `ZoomSettingFormat` is an object; non-finite ratios quantize to 1.0. `ZoomSelection.steppedBy`
starts from `quantizeZoom(ratio)`. Later tasks use `ZoomSelection` as the state holder and keep the lock rule outside.
