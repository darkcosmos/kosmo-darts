# T8b: Drift measure, compensation, report, flag file and guard

## Goal
Measure the tripod drift of every judged photo against the reference photo of its calibration segment, give each
photo its compensated reference calibration, and produce the numbers-only report and the local flag file that drive
Kosmo's recalibration loop (D15).

## Files
- Added `tools/camera/kosmo_camera/drift.py` (numpy, Pillow for JPEG to luma only): `DriftParams` from `config.py`;
  `phase_shift(a, b) -> (dx, dy, peak)`; `measure_photo(ref_luma, photo_luma, segment, params) -> PhotoDrift`
  (`status` `ok|beyond|unmeasurable`, `reason`, `shifts_px` x4, `drift_px`, `drift_mm`, `reference_points` or None);
  `measure_session(session_dir, annotations, params) -> SessionDrift` (judged darts only, CV5; the reference photo
  of each segment decoded once); `suggested_starts(session_drift)` (first dart of each run of consecutive beyond
  darts in number order, unmeasurable darts skipped); `require_clean(drifts, accept_drift) -> excluded_count`
  (raises `DriftError` with the French message of CV19 when a photo is beyond and `accept_drift` is false);
  `write_outputs(drifts, runs_dir)`; `main()` for `python -m kosmo_camera.drift --captures DIR --annotations DIR
  --runs DIR` (prints the report path and "Tout sous le seuil : oui|non"; exit 0 either way, 2 on bad input).
- Added `tools/camera/kosmo_camera/drift_file.py` (stdlib only): `encode`/`decode` of `drift.json`
  (`format` `"kosmo-camera-drift"`, `version` 1, `thresholdMm`, `sessions`: `session`, `label`, `segmentsSha256`,
  `maxDriftMm` or null, `beyond`, `suggested`, `unmeasurable`: ascending dart numbers); strict like T5 (unknown keys,
  duplicates, types, 1 MiB cap); `write_atomic` as `annotations.write_atomic`.
- Outputs, overwritten atomically each run (fixed path so the tool can point at it): `<runs>/drift/drift.json` and
  `<runs>/drift/drift-report.md`. Report: threshold and why (one line, CV19), parameters, then one row per session
  label `S01`... (session-name order): judged photos, segments, measured, unmeasurable per reason, max and median
  drift px and mm, darts beyond (count), suggested start darts (numbers); totals; "Tout sous le seuil : oui|non".
- Changed `tools/camera/config/defaults.json` (`DRIFT_MAX_MM` 1.0 unless Kosmo answers Q1 otherwise,
  `DRIFT_COARSE_PX` 512, `DRIFT_PATCH_FRACTION` 0.06, `DRIFT_MIN_PEAK` 0.2), `config.py` and `test_config.py`.
- Changed `docs/rules/camera.md`: adds CV18, CV19; amends CV3 (`drift` exempt from the lock), CV9, CV12, CV14,
  CV15 (root plan "Rules").
- Added tests `tools/camera/tests/test_drift.py`, `test_drift_file.py`.

## Depends on
T5b (segments, `segments_sha256`), T8 (`board_from_image`, `calibration_error_mm`).

## Rules
CV18, CV19, CV15, CV5, D8. Shift sign: content position in the photo minus in the reference. Reads every session
(dev and eval) without the split lock (CV18). Sources read-only (CV16 spirit).

## Tests
- `phase_shift` on a textured numpy image shifted by (2.5, -7.25) px: recovered within 0.1 px; brightness x1.3 still.
- T7 sessions with planned drift (none, 3 px translation, 0.5 degree roll, a later net move): each `d_i` within
  0.25 px of the truth; `drift_mm` within 0.05 mm of `calibration_error_mm` of the true views; reference photo 0.
- Each unmeasurable reason: no calibration, other photo size, patch past the border, occluder on one corner
  (`low_peak`), truncated JPEG. Status boundary: `drift_mm` exactly `DRIFT_MAX_MM` is ok, just above is beyond.
- Compensation: `reference_points` equal segment points plus shifts; none when beyond or unmeasurable.
- `suggested_starts`: beyond darts 12, 13, 14, 20 give 12, 20; an unmeasurable 13 does not split the run.
- `require_clean`: raises when beyond, returns the count with `accept_drift`; clean set returns 0.
- Report privacy (CV15): no session name, file name or path from the inputs; `drift.json` strict reader rejections.

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: algorithm, statuses and threshold fully specified in CV18 and CV19; derived, regenerable file.

## Implementation notes
