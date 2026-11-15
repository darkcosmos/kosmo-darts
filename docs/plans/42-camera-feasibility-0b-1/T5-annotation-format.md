# T5: Annotation format, reader and writer, doc

## Goal
Define and implement the stored annotation file that Kosmo's clicks produce, read by every later tool.

## Files
- Added `tools/camera/kosmo_camera/annotations.py` (stdlib only): `Annotations`, `Calibration`, `DartAnnotation`,
  `Status` (`VISIBLE`, `HIDDEN`, `OFF_BOARD`); `encode(a) -> str`, `decode(text) -> Annotations`, `read(path)` (refuses files over `MAX_BYTES` = 1 MiB
  without reading past it), `validate_against(a,
  session)`, `write_atomic(path, a)` (temp file in the same folder, `os.replace`), `AnnotationError`.
- Added `docs/camera/annotation-format.md`: fields, units (photo pixels, origin top left, float), CV2 order, version
  policy (same as the truth format: any change increases `version`), example, how `truthSha256` is used.
- Added tests `tools/camera/tests/test_annotations.py`.

## Depends on
T4.

## Rules
CV2, CV5, CV7, CV16 (atomic write). Outline: root plan "Annotation format outline". Decisions to keep: `tip` present
exactly when `VISIBLE`; at most one entry per dart number; only judged darts (CV5) may appear; partial files are valid
(annotation in progress); calibration photo must be a photo of the session; coordinates within the photo size;
coordinates in stored JPEG pixel order, EXIF orientation ignored (decision D8); the real JPEG size check is T6a's.
`truthSha256` mismatch is a warning returned by `validate_against`, not an error (Kosmo may re-copy the same session).

## Tests
- Round trip of a full file and of a partial one (no calibration, some darts).
- Golden text (key order, indentation, no trailing newline); the doc example equals the golden text.
- Rejections: file over 1 MiB, other format or version, unknown or duplicate key, `tip` with `HIDDEN`, missing `tip` with `VISIBLE`,
  3 or 5 calibration points, negative or out-of-photo coordinate, NaN, duplicate dart number.
- `validate_against`: unknown dart number, voided dart, dart with null photo, calibration photo not in the session,
  SHA mismatch warning.
- `write_atomic`: an exception during write leaves the previous file intact.

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
opus. Why: case "new stored format" (schema, strict reader, version policy used by every later tool).

## Implementation notes
