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
- Model (`annotations.py`): `Annotations` gains `controls: tuple = ()` as its last field (JSON order stays
  `calibrations, controls, darts`); `Calibration` gains `snap: tuple = MANUAL_SNAP`, so existing callers (`flow.py`,
  `synth.py`) still build segments with `manual` statuses. T15n must pass the real statuses (`SNAPPED`/`UNSNAPPED`)
  when the tool snaps. `Control(dart, source, position_ms, width, height, points, snap)`; constants `PHOTO`, `VIDEO`,
  `SOURCES`, `SNAPPED`, `UNSNAPPED`, `MANUAL`, `MANUAL_SNAP`, `MAX_CONTROLS`, `VERSION_2`.
- Version 2 kept as its own model: `CalibrationV2`, `AnnotationsV2`, `_annotations_v2`, `_calibrations_v2`.
  `migrate_v1` now returns `AnnotationsV2` (was the v2 `Annotations`); `decode` runs `migrate_v2(migrate_v1(...))`.
- Point encoding of a control: `{x, y}` objects like segments and tips (root CV25 and the rule in `docs/rules/camera.md`
  say `{x, y}` too, to match the existing point encoding of the file).
- Deviation: `segments_sha256` hashes the segments WITHOUT `snap`, so the hash is identical before and after the v2 to
  v3 migration and Kosmo's existing `drift.json` flags (compared in `server.py`) stay current. `controls_sha256`
  includes `snap`. Covered by a test on the tool-written v2 fixture.
- `validate_against`: "volley has no video" is an empty `volley.video` string (truth v2 always names a video).
- `encode` writes `positionMs` when the source is video or a position is set, so a photo control with a position or
  a video control without one fails the decode check inside `encode`.
- Golden: `annotations-v3-golden.json` = v2 golden (segment 1 `manual`) plus a second segment from dart 3 `snapped`
  plus 3 controls; "v3 golden minus its controls" therefore also drops that second segment (test helper
  `_v3_golden_minus_its_controls`).
- Fixture `annotations-v2-tool-written.json`: generated before any code change with `git archive ab85842 tools/camera`
  extracted to the scratchpad, a script run with `python3.13 -I` that puts that export and its `tests/` on
  `sys.path`, `make_session(judged=1..9, voided=(10,), no_photo=(11,))`, 4 calibration clicks, darts 1 to 3, `recalibrate`
  on dart 4 with 4 clicks, darts 4 to 9, `write_atomic` after each action; asserts `VERSION == 2`, 2 segments and a
  complete cursor.
- Worst case measured: 807 716 bytes (64 segments, 1024 video/photo controls, 1000 visible darts, full-precision
  floats), under `MAX_BYTES`.
- Done-when: `python3 -I -S -m kosmo_camera.annotate --help` cannot find the package (`-I` drops the current folder,
  as T5b noted); checked with `-I -S` and the package root inserted on `sys.path`, and `python3 -S -m ... --help`.
