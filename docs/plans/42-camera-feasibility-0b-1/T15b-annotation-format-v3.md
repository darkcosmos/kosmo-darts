# T15b: Annotation format v3 (control clicks, snap status), v1 and v2 readers, lossless migration

## Goal
Store Kosmo's control calibrations (CV25) in the annotation file, independent of the calibration segments, without
losing or changing anything in his version 1 and version 2 files (D30 point 1).

## Files
- Changed `tools/camera/kosmo_camera/annotations.py` (stdlib only): `VERSION = 3`; v3 root keys `format`,
  `version`, `session`, `truthSha256`, `calibrations`, `controls`, `darts`. `Control(dart, source, position_ms,
  width, height, points)`, `source` `"photo"` or `"video"`; `points` exactly 4 items in CV2 order, each a point
  inside `width` x `height` or `null` (point hidden); `positionMs` present exactly when `source` is `"video"`
  (integer >= 0); `snap` exactly 4 items aligned with `points`: `"snapped"` or `"unsnapped"` for a point, `null`
  for a `null` point (CV25, CV31, root D43); `controls` sorted by `dart` then `photo` before `video`, `(dart, source)` unique; at most
  `MAX_CONTROLS` = 1024 (cap next to `MAX_SEGMENTS`). v2 rules kept unchanged as `_annotations_v2`, then
  `migrate_v2` adds `controls: []` and `snap: ["manual" x4]` on every segment (segments of v3 hold
  `"snapped"`, `"unsnapped"` or `"manual"`, never `null`); v1 goes `migrate_v1` then `migrate_v2`. Writer writes v3 only. New
  `controls_sha256(annotations)` (canonical JSON of `controls`, like `segments_sha256`) and `control_for(annotations,
  dart, source)`. `validate_against`: a control on a dart that is not judged (CV5), or a video control whose volley
  has no video or whose entry has `videoPositionMs` null, is reported like a tip on an unknown dart.
- Changed `docs/camera/annotation-format.md`: "Version 3" section, migration v2 to v3, v3 example; v1 and v2 kept
  as reference.
- Changed `docs/adr/0016-camera-feasibility-tooling.md`: new section "Annotation format v3: control clicks" (text of
  root plan "Files and types added or changed", ADR bullet, with the `snap` field).
- Changed `docs/rules/camera.md`: adds CV25.
- Added `tools/camera/tests/data/annotations-v3-golden.json` (from the v2 golden plus 2 photo controls, one with a
  hidden point and one with an `"unsnapped"` point, and 1 video control; one segment `"snapped"`); changed `test_annotations.py`, and any test asserting `version: 2` on write.

## Depends on
T5b (v2 reader and writer).

## Rules
CV25, CV31 (`snap` values only), CV20, CV16, D8. Controls never change `calibrations`, `segment_for` or `segments_sha256`; a v1 or v2 file is
rewritten as v3 only when the tool saves a change (CV16).

## Tests
- v2 golden migrates to the v3 golden minus its controls byte for byte; v1 golden to v3 likewise.
- The v2 file written by the batch 1 tool (`flow.apply` plus `write_atomic` at ab85842, added as test data) and 50
  seeded random v2 files: decode, encode as v3, drop the empty `controls` and the `"manual"` x4 `snap` of each segment, equal the v2
  text; every migrated segment has `snap` `"manual"` x4.
- v3 strict rejections: unknown key, `positionMs` on a photo control or missing on a video one, 3 or 5 points, a
  point outside the size, unsorted or duplicated `(dart, source)`, `MAX_CONTROLS` + 1, version 4, v2 body with a
  `controls` key. All 4 points `null` accepted. `snap`: missing, 3 or 5 entries, a status on a `null` point, `null` on a
  point, an unknown value, `"manual"` on a control, `null` on a segment.
- Worst case: `MAX_SEGMENTS`, `MAX_CONTROLS` with 4 points each and 1000 darts encodes under `MAX_BYTES`.
- `controls_sha256` changes with a control or its `snap`, not with a tip; `segments_sha256` unchanged by a control.
- `validate_against`: control on a voided entry, on a photo-less entry, video control with null position.

## Done when
Python checks of root "Test plan" green, `python3 -I -S -m kosmo_camera.annotate --help` works, `kosmo-detekt`
exit 0.

## Model
opus. Why: changed stored format with its migration (case 1): v3 schema, strict reader, lossless migration of
Kosmo's real v1 and v2 files.

## Implementation notes
