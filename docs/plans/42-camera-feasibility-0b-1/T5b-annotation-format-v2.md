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
  `fromDart` integer >= 1, first item exactly 1, strictly ascending; `photo`, `width`, `height`, `points` as v1.
  `validate_against`: every `photo` named in the truth; for items after the first, `fromDart` is a judged dart (CV5)
  and `photo` is that dart's photo. Version policy as v1 (any other version rejected, any change bumps it).
- Changed `tools/camera/kosmo_camera/annotate/flow.py` and `server.py`: mechanical port to `calibrations`: the
  existing calibration screen edits segment 0 (from dart 1); no new action (T6c adds it). A v1 file is never
  rewritten by opening or reading; the first saved change writes v2 (CV16).
- Changed `docs/camera/annotation-format.md`: title and policy for versions 1 and 2, new section "Version 2"
  (fields, segment semantics, migration table v1 to v2), example as v2; the v1 section kept as reference.
- Changed `docs/rules/camera.md`: adds CV20 and the v1 sentence of CV16 (root plan "Rules").
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
