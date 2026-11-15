# T15i: Diagnosis, photo side: true drift, error of the automatic measure, where it fails

## Goal
From Kosmo's control clicks, measure per photo the true drift and the error of the current automatic measure
(CV18), the click repeatability, and where the automatic measure fails (coarse search or patches, peak ratios),
as numbers only (D30 point 2 (a) and (c), CV28).

## Files
- Changed `tools/camera/kosmo_camera/drift.py`: `phase_shift_detail(a, b)` -> `(dx, dy, peak, second_peak)`
  (second peak = highest correlation value outside the square of half side `PEAK_EXCLUSION` cells around the
  peak); `measure_photo_detail(ref, photo, segment, params, seed_points=None)` -> `t0`, coarse peak and ratio, per
  point shift, peak and ratio, status and reason, the shifted points even when beyond; with `seed_points` the
  patches are centred on them in the photo (truth-seeded run, coarse step skipped). `measure_photo` and
  `phase_shift` results unchanged (same values on the T8b test sessions). The `drift` report and its printed line
  gain the first line "Mesure de dérive non validée (diagnostic en cours) : ne pas recalibrer d'après ce rapport".
- Added `tools/camera/kosmo_camera/diagnose.py` (numpy, Pillow): per session (roles from `control.roles`, split
  checked against the lock, CV3) and per complete photo control, the CV28 values `T`, `E`, `E_seeded`, coarse
  error px, peak ratios; repeatability `R` on segment reference photos; partial controls (a `null` point): visible
  points' px errors only, counted apart. `build_report(...)` sections "Échantillon", "(a) Dérive vraie et erreur
  de la mesure automatique", "(c) Où la mesure échoue", "Répétabilité des clics", per role and overall: counts,
  median and p95 with CV13 intervals, share of `E` at most `VALIDATION_MAX_MM` with a Wilson interval,
  unmeasurable per reason. `diagnostic.json` (local, CV15) through `diagnostic_file.py`. `main()` for `python -m
  kosmo_camera.diagnose --captures DIR --annotations DIR --split FILE --runs DIR [--frames DIR]`: writes
  `<runs>/diagnostic/diagnostic-report.md` and `diagnostic.json`, prints the report path.
- Added `tools/camera/kosmo_camera/diagnostic_file.py` (stdlib only, strict, `MAX_BYTES` 4 MiB): `format`
  `"kosmo-camera-diagnostic"`, `version` 1, per session `session`, `role`, `segmentsSha256`, `controlsSha256`,
  per photo `dart`, `referencePhoto`, `autoPoints` or null with `reason`, `controlPoints`, `autoPx`, `autoMm`,
  `trueMm`, `errorMm`.
- Changed `defaults.json`, `config.py`, `test_config.py`: `VALIDATION_MAX_MM` 0.5, `VALIDATION_MIN_SHARE` 0.95,
  `PEAK_EXCLUSION` 3, `CLICK_REPEAT_MAX_MM` 0.35, `DIAG_MIN_CONTROLS` 30.
- Changed `docs/rules/camera.md`: adds CV28 (photo part); CV15 sentence on `diagnostic.json`.
- Added tests `test_diagnose.py`, `test_diagnostic_file.py`; changed `test_drift.py`.

## Depends on
T15c (roles, sample), T8b.

## Rules
CV28, CV9 metric, CV13, CV15, CV4 (reads every session like `drift`; nothing is tuned).

## Tests
- T7 session with planned drift and exact "control clicks" (true points): `T` equals the planned drift mm within
  0.05; `E` small for small shifts. Periodic trap: a synthetic board shifted by one wire spacing near a corner,
  built so the coarse step locks on the wrong wire: `E` large, `E_seeded` small, location "recherche grossière".
- Second peak on a two-peak correlation: ratio equals the hand value.
- `R` on reference photos with clicks offset by 1 px: equals the CV9 error of that offset.
- Partial control: excluded from `T` and `E`, counted, visible points listed.
- Report: numbers only, roles only (scan for every input session name, file name and path), CV13 intervals at a
  hand-checked case; `diagnostic.json` strict reader (cap, cap + 1, unknown key).
- `drift` report first line present; `measure_photo` outputs unchanged on the T8b fixtures.
- `main()` in a subprocess on T7 sessions with a split and lock written by the test.

## Done when
Python checks of root "Test plan" green (CI command too), `kosmo-detekt` exit 0.

## Model
sonnet. Why: every quantity is defined in CV28 with formulas reusing T8 and T8b; tests on synthetic data.

## Implementation notes
