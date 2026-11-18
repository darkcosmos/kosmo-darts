# T5b: Annotation format v2 (calibration segments), v1 reader and migration

## Goal
Change the annotation file to version 2 so a session holds several calibrations, each from a start dart (CV20, D15),
read Kosmo's existing v1 files without loss, and keep the annotation tool working unchanged with one segment.

## Files
- Changed `tools/camera/kosmo_camera/annotations.py`: `VERSION = 2`, `Calibration` gains `from_dart: int`,
  `Annotations.calibrations: tuple[Calibration, ...]` replaces `calibration`. `decode` checks `format`, then
  `version` in (1, 2): v1 body parsed by the kept v1 rules then `migrate_v1` (null gives `()`, else one segment with
  `from_dart=1`, every other field copied); v2 body by the v2 rules. `encode`/`write_atomic` write v2 only. New
  `segment_for(annotations, dart_number) -> (index, Calibration) | None` (last segment with `from_dart <= number`)
  and `segments_sha256(annotations)` (SHA-256 of `json.dumps(<encoded calibrations list>, sort_keys=True,
  separators=(",", ":"))`, used by T8b and T6c).
- v2 strict rules: top-level keys exactly `format, version, session, truthSha256, calibrations, darts`;
  `calibrations` an array (may be empty); each item keys exactly `fromDart, photo, width, height, points`;
  `fromDart` integer >= 1, first item exactly 1, strictly ascending; `photo`, `width`, `height`, `points` as v1;
  at most `MAX_SEGMENTS` = 64 items (named constant next to `MAX_BYTES`; the tool's recalibration answers 409 with a
  French message at the cap).
  `validate_against`: every `photo` named in the truth; for items after the first, `fromDart` is a judged dart (CV5)
  and `photo` is that dart's photo. Version policy as v1 (any other version rejected, any change bumps it).
- Changed `tools/camera/kosmo_camera/annotate/flow.py` and `server.py`: mechanical port to `calibrations`: the
  existing calibration screen edits segment 0 (from dart 1); no new action (T6c adds it). A v1 file is never
  rewritten by opening or reading; the first saved change writes v2 (CV16).
- Changed `docs/camera/annotation-format.md`: title and policy for versions 1 and 2, new section "Version 2"
  (fields, segment semantics, migration table v1 to v2), example as v2; the v1 section kept as reference.
- Changed `docs/rules/camera.md`: adds CV20 and the v1 sentence of CV16 (root plan "Rules").
- Changed `docs/adr/0016-camera-feasibility-tooling.md`: new section "Annotation format v2 and migration" with the
  content listed in root plan "Files and types added or changed" (ADR bullet, A13).
- Added `tools/camera/tests/data/annotations-v1-tool-written.json`: written, before any code change, by the PR A
  tool path (`flow.apply` plus `annotations.write_atomic` at 8d69f64, on a golden-like session: calibration and
  three darts of each status); its generation is described in the notes.
- Added `tools/camera/tests/data/annotations-v2-golden.json` (the doc's v2 example); tests in `test_annotations.py`,
  `test_annotate_flow.py`, `test_annotate_server.py`.

## Depends on
T5, T6a (both merged in PR A).

## Rules
CV20, CV16, CV2, CV5, D8. `truthSha256` is copied unchanged by the migration; the stale warning keeps working.

## Tests
- v1 golden decoded then encoded equals the v2 golden byte for byte (lossless); v2 golden equals its doc block.
- 50 seeded random valid v1 files: migrate, encode, decode, then a test-only inverse gives the original v1 dict.
- v1 with `calibration: null` gives `calibrations: []` and encodes back.
- The tool-written v1 fixture decodes, migrates and round-trips to the same v1 dict (T2 of the design review).
- 64 segments accepted, 65 rejected (`MAX_SEGMENTS` read from the module, not hardcoded).
- v2 rejections, each with its JSON path: first `fromDart` 2, equal or descending `fromDart`, `fromDart` 0 or float,
  unknown key in a segment, `calibration` key in v2, `calibrations` key in v1, version 3, version 0.
- `validate_against`: second segment on a voided dart or with another dart's photo rejected.
- `segment_for`: dart before, at and after a `fromDart`; empty list gives None.
- Tool: a v1 file opened and listed stays byte-identical; one click saves v2 with every tip kept; all T6a tests pass.

## Done when
Python checks of root "Test plan" green, `python3 -I -S -m kosmo_camera.annotate --help` works, `kosmo-detekt`
exit 0.

## Model
opus. Why: changed stored format with its migration (case 1), applied to Kosmo's real annotation files.

## Implementation notes
- Model (`annotations.py`): `Calibration(from_dart, photo, width, height, points)` (field first, no default);
  `Annotations(session, truth_sha256, calibrations, darts)`; v1 kept as `CalibrationV1` / `AnnotationsV1`, parsed by
  `_annotations_v1` (unchanged v1 rules) then `migrate_v1`. Constants `VERSION = 2`, `VERSION_1 = 1`,
  `MAX_SEGMENTS = 64`. Shared field rules in `_calibration_fields`; v2 list rules in `_calibrations` (cap checked
  before parsing items). `encode` validates by decoding, so the cap and the order are also enforced on write.
- `segment_for(annotations, n) -> (index, Calibration) | None`; `segments_sha256` hashes `_calibrations_json`
  (exactly the encoded list) with `sort_keys`, compact separators; `[]` for no segment. T8b/T6c import both.
- `validate_against`: errors at `$.calibrations[i].photo` / `.fromDart`; first segment may use any session photo.
- Flow: `_calibrated()` = segment 0 exists; the calibration click replaces segment 0 and keeps the others. Deviation
  for T6c: `clear` on the calibration screen while later segments exist raises `FlowError("Supprimez d'abord les
  recalibrations suivantes.")` (removing segment 0 would leave no segment from dart 1); unreachable in this task
  (the tool cannot create a second segment yet). T6c may replace this when it adds segment removal.
- Server: the page API is unchanged: `view["calibration"]` is segment 0; T6c/T6d add segments to the view. The
  409 at the cap belongs to T6c's recalibration action (no such action here).
- Fixture `tests/data/annotations-v1-tool-written.json`: generated before any code change with `git archive 8d69f64
  tools/camera` extracted to /tmp, then a script run with `python3.13 -I` that puts that export and its `tests/` on
  `sys.path`, builds a session with `annotate_support.make_session(judged=1..9, voided=(10,), no_photo=(11,))`,
  applies 4 calibration clicks then click, hidden, off_board x3 through `flow.apply` and calls
  `annotations.write_atomic` after each action (autosave, as the PR A server); it asserts `VERSION == 1` and a
  complete cursor. `annotations-v2-golden.json` = the doc's v2 example (v1 golden migrated).
- Done-when deviation: `python3 -I -S -m kosmo_camera.annotate --help` cannot find the package (`-I` drops the
  current folder from `sys.path`); checked instead with the form of `test_help_works_with_the_stdlib_only`
  (`-I -S -c` with the package root inserted, 3.14 and 3.13) and with `python3 -S -m ... --help` from `tools/camera`.
