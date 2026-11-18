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
  duplicates, types, `MAX_BYTES` = 1 MiB named in the module); `write_atomic` as `annotations.write_atomic`.
- Segment of a dart: `annotations.segment_for` only (A15). JPEG decoding: `Image.MAX_IMAGE_PIXELS` kept as is;
  `DecompressionBombError`, `OSError`/truncated data and a decoded size different from the segment give the
  `unreadable` or `size_mismatch` status, never an exception out of `measure_session` (S8).
- Outputs, overwritten atomically each run (fixed path so the tool can point at it): `<runs>/drift/drift.json` and
  `<runs>/drift/drift-report.md`. Report: threshold and why (one line, CV19), parameters, then one row per session
  label `S01`... (session-name order): judged photos, segments, measured, unmeasurable per reason, max and median
  drift px and mm, darts beyond (count), suggested start darts (numbers); totals; a table "Seuils candidats" with,
  for each value of `DRIFT_CANDIDATES_MM`, the sessions and darts beyond (U11, for information); "Tout sous le seuil : oui|non".
- Changed `tools/camera/config/defaults.json` (`DRIFT_MAX_MM` 1.0, Kosmo's answer, root D18;
  `DRIFT_CANDIDATES_MM` [0.5, 1.0, 2.0], `DRIFT_COARSE_PX` 512, `DRIFT_PATCH_FRACTION` 0.06,
  `DRIFT_MIN_PEAK` 0.2), `config.py` and `test_config.py` (the one test that pins each value, so a change is
  intentional; every other test reads them through `config.py`, A16).
- Changed `docs/rules/camera.md`: adds CV18, CV19; amends CV3 (`drift` exempt from the lock), CV9, CV12, CV14,
  CV15 (root plan "Rules").
- Changed `docs/adr/0016-camera-feasibility-tooling.md`: new section "Tripod drift" with the content listed in root
  plan "Files and types added or changed" (ADR bullet, A13).
- Changed `tools/camera/Dockerfile` (batch 1, root D19): `COPY config/ /app/config/` after the package copy, since
  `config.py` reads `config/defaults.json` at import and the image has no `config/` yet; header comment says the
  image also runs `python -m kosmo_camera.drift` with `-v ~/kosmo-data:/data:ro` (root "How Kosmo verifies", K5.2).
  No entry point (T14). `tests/test_dockerfile.py`: asserts the `config/` copy; the no-entry-point test stays.
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
  (`low_peak`), truncated JPEG, and a decompression bomb (`Image.MAX_IMAGE_PIXELS` patched low in the test only):
  each a status, the run continues. Status boundary, with the threshold read from `config.py` (no literal):
  `drift_mm` exactly `DRIFT_MAX_MM` is ok, just above is beyond.
- Report: "Seuils candidats" table on a synthetic session with known drifts gives the expected counts per value.
- `drift.json` of exactly `MAX_BYTES` read, one byte more rejected.
- Compensation: `reference_points` equal segment points plus shifts; none when beyond or unmeasurable.
- `suggested_starts`: beyond darts 12, 13, 14, 20 give 12, 20; an unmeasurable 13 does not split the run.
- `require_clean`: raises when beyond, returns the count with `accept_drift`; clean set returns 0.
- Report privacy (CV15): no session name, file name or path from the inputs; `drift.json` strict reader rejections.
- `main()` as Kosmo runs it (K5.2): `python -m kosmo_camera.drift` in a subprocess with `cwd` outside
  `tools/camera` and the package on `PYTHONPATH`, on a T7 session, writes both outputs and prints the two lines.

## Done when
Python checks of root "Test plan" green (including the CI command `python -m pytest -q -m "not slow"` from
`tools/camera`), `kosmo-detekt` exit 0.

## Model
sonnet. Why: algorithm, statuses and threshold fully specified in CV18 and CV19; derived, regenerable file.

## Implementation notes
- Deviation (accuracy): the whitened cross-power spectrum carries a Gaussian low-pass, `_SPECTRAL_SIGMA` = 0.08
  cycles per pixel, in `drift.phase_shift`. A bare whitened peak is a sinc and the 3-point parabola then biased the
  shift by about 0.1 px (measured: 2.5 px recovered as 2.58). With the weight the textured test is within 0.1 px.
  CV18 and the ADR section say so. Peak height is normalised by the mean weight, so `DRIFT_MIN_PEAK` keeps its meaning.
- Where: `drift.py` (`phase_shift`, `measure_photo`, `measure_session`, `suggested_starts`, `require_clean`,
  `build_report`, `write_outputs`, `main`; `DriftParams` defaults come from `config.py`), `drift_file.py`
  (`DriftFile`, `SessionFlags`, `read`, `encode`, `write_atomic`, `replace_atomic` also used for the report).
  `config.py` schema gained the `tuple` type (non-empty list of numbers) for `DRIFT_CANDIDATES_MM`.
- Later tasks: T6c reads `drift_file.read(path)` (camelCase keys only on disk; `SessionFlags` has `beyond`,
  `suggested`, `unmeasurable`, `segments_sha256`, `max_drift_mm`); a flag is stale when `segments_sha256` differs from
  `annotations.segments_sha256`. T9/T13 call `drift.measure_session` then use `PhotoDrift.reference_points`
  (None means no reference calibration; `reason` tells why) and `drift.require_clean(drifts, accept_drift)`.
- Reasons: a degenerate (collinear) segment gives `uncalibrated`. An unreadable reference photo gives `unreadable`
  to the other photos of its segment; the reference photo itself stays drift 0.
- `suggested_starts` works on the judged darts in number order: a dart under the threshold ends a run, an
  unmeasurable one is skipped. Voided or photo-less darts are not in the annotations and do not split a run.
- Test photo size: the planned-drift session uses 2400x1800 photos (3.7 px/mm, finer than the 3 px/mm synthetic
  canvas) because at 640x480 the aliasing of the canvas limits the shift to about 0.15 px, above the 0.05 mm budget.
  Its 12 photos are written once (module fixture, about 3 s). Measured: shifts within 0.17 px, mm within 0.045.
- `main()` exits 2 on a missing folder, an annotation file the strict reader rejects (the path is on stderr), or a
  session name that differs from its file name; sessions are the `*.json` files of `--annotations`, their photos
  are read in `<captures>/<session>/`; a missing photo folder gives `unreadable` statuses, not an error.
