# T15i: Diagnosis, photo side: true drift, error of the automatic measure, where it fails

## Goal
From the pixel-level truth of the automatic re-snap (`snapped-points.json`, T17c, D49), measure per photo the true
drift and the error of the current automatic measure (CV18), the machine-measured snapping dispersion `D` (Kosmo,
Q10, D39), and where the automatic measure fails (coarse search or patches, peak ratios), as numbers only (D30
point 2 (a) and (c), CV28, CV31, CV32).

## Change of lot 3b (D49, D52)
- Truth source: control and segment points and their `snap` statuses come from `snapped-points.json` (option
  `--snapped FILE`, required; read with `snapped_file`; absent, refused or stale against the annotation hashes: the
  run stops with one French line « Recalage automatique absent ou périmé : relancer K7.6 », exit 2). The
  annotation files keep Kosmo's clicks and are never written.
- `D` per snapped point comes from the file's `dispersionPx` (`"failed"` read as infinite, T17c), in mm through the control homography (4 points);
  controls with fewer visible points report `D` in px, apart; no restart is rerun.
- « Recalage des clics » also gives the share of snapped and non-snapped points per role and source and the click
  precision `C` (p95 click-to-snap mm of T17c). Controls with a non-snapped point stay out of `T`, `E` and the
  `DIAG_MIN_CONTROLS` count (CV28); they are listed apart with `E` compared with `VALIDATION_MAX_MM` + `C`
  (information line « précision humaine »), never in a verdict.

## Files
- Changed `tools/camera/kosmo_camera/drift.py`: `phase_shift_detail(a, b)` -> `(dx, dy, peak, second_peak)`
  (second peak = highest correlation value outside the square of half side `PEAK_EXCLUSION` cells around the
  peak); `measure_photo_detail(ref, photo, segment, params, seed_points=None)` -> `t0`, coarse peak and ratio, per
  point shift, peak and ratio, status and reason, the shifted points even when beyond; with `seed_points` the
  patches are centred on them in the photo (truth-seeded run, coarse step skipped). `measure_photo` and
  `phase_shift` results unchanged (same values on the T8b test sessions). The `drift` report and its printed line
  gain the first line "Mesure de dérive non validée (diagnostic en cours) : ne pas recalibrer d'après ce rapport".
- Added `tools/camera/kosmo_camera/diagnose.py` (numpy; images decoded by `luma.decode_luma` only, T15m, A33):
  per session (roles from `control.roles`, split
  checked against the lock, CV3) and per complete photo control, the CV28 values `T`, `E`, `E_seeded`, coarse
  error px, peak ratios; `R` on segment reference photos (agreement of the segment clicks with the snapped
  controls, information only, decides nothing); `D` per `snapped` point of photo and video controls through
  `snap.restart_dispersion` (T15m) on the luma of that photo or listed frame, p95 with its CV13 interval, a failed
  restart counted infinite; partial controls (a `null` point): visible points' px errors only, counted apart;
  controls with an `unsnapped` point: excluded from `T`, `E` and from the `DIAG_MIN_CONTROLS` count, counted per
  role and source, like a partial control; a control image the decoder cannot read: counted per role and source
  with reason `unreadable`, excluded like a partial control (CV28); the number of complete snapped dev photo controls is exported for
  CV29. `build_report(...)` sections "Échantillon", "(a) Dérive vraie et erreur de la mesure automatique", "(c) Où
  la mesure échoue", "Recalage des clics" (points recalés and non recalés per role and source with reasons, `D`
  median and p95 with interval against the target `SNAP_REPEAT_MAX_MM`, `R` as information), per role and
  overall: counts, median and p95 with CV13 intervals, share of `E` at most `VALIDATION_MAX_MM` with a Wilson
  interval, unmeasurable per reason; section « Limites » with the CV28 sentence on `D` (repeatability, not
  accuracy; A35, T2), T15j adds the video limit. T15l documents that section for Kosmo. `diagnostic.json` (local, CV15) through `diagnostic_file.py`. `main()` for `python -m
  kosmo_camera.diagnose --captures DIR --annotations DIR --split FILE --runs DIR [--frames DIR]`: writes
  `<runs>/diagnostic/diagnostic-report.md` and `diagnostic.json`, prints the report path.
- Added `tools/camera/kosmo_camera/diagnostic_file.py` (stdlib only, strict, `MAX_BYTES` 4 MiB): `format`
  `"kosmo-camera-diagnostic"`, `version` 1, per session `session`, `role`, `segmentsSha256`, `controlsSha256`,
  per photo `dart`, `referencePhoto`, `autoPoints` or null with `reason`, `controlPoints`, `controlSnap` (the
  stored `snap` statuses), `autoPx`, `autoMm`, `trueMm`, `errorMm`, `dispersionMm` (per point, null when not
  snapped; JSON has no infinity, a failed restart is the string `"failed"`).
- Changed `defaults.json`, `config.py`, `test_config.py`: `VALIDATION_MAX_MM` 0.5, `VALIDATION_MIN_SHARE` 0.95,
  `PEAK_EXCLUSION` 3, `DIAG_MIN_CONTROLS` 30; each value pinned once, in `test_config.py` only; every other test
  reads it from `config` (T3, as A16). The `SNAP_*` constants are T15m's, read from `config`.
- Changed `docs/rules/camera.md`: adds CV28 (photo part, with `D` and the `unsnapped` exclusion); CV15 sentence on
  `diagnostic.json`.
- Added tests `test_diagnose.py`, `test_diagnostic_file.py`; changed `test_drift.py` (static check adds
  `diagnose.py`).

## Depends on
T15c (roles, sample), T8b, T15m (`snap.restart_dispersion`, `luma.decode_luma`), T17c (`snapped_file`).
Where the Files list above says `restart_dispersion` on the luma, read the stored `dispersionPx` instead (section
"Change of lot 3b"); the synthetic `D` tests below build the file with T17c's writer.

## Rules
CV28, CV31, CV32, CV9 metric, CV13, CV15, CV4 (reads every session like `drift`; nothing is tuned). There is no click
threshold on Kosmo's hand (Q10 option 3).

## Tests
- T7 session with planned drift and exact "control clicks" (true points): `T` equals the planned drift mm within
  0.05; `E` small for small shifts. Periodic trap: a synthetic board shifted by one wire spacing near a corner,
  built so the coarse step locks on the wrong wire: `E` large, `E_seeded` small, location "recherche grossière".
- K5 regression (T2), named `test_cv28_k5_wrong_shift_flags_measure`: a synthetic session where the board does
  not move (true `T` 0) but the CV18 measure locks onto the neighbouring wire on most photos (periodic trap, darts
  added during the volley), as in K5 (almost every photo "beyond", the real move not seen); a real 2 mm move from
  dart 20 also planted: the diagnosis gives `E` above `VALIDATION_MAX_MM` on those photos, `T` about 0 before dart
  20 and about 2 mm after, and the share feeding CV29 makes H1 "mesure fausse" (verdict line checked in T15o).
- Second peak on a two-peak correlation: ratio equals the hand value.
- `R` on reference photos with clicks offset by 1 px: equals the CV9 error of that offset; reported under
  "information", feeds no verdict field.
- `D` on rendered controls (T7 boards with ring wires, T15m): a clean crossing gives p95 under 0.05 mm; a stored
  point between two crossings gives a dispersion above `SNAP_REPEAT_MAX_MM`; a restart that fails counts infinite
  (`"failed"` in `diagnostic.json`, the p95 then infinite when it is in the top 5 %); video controls included.
- Unsnapped point: a control with one `unsnapped` point is excluded from `T` and `E` and from the snapped dev
  count, counted per role and source with its reason; its snapped points keep their `D` entries, the unsnapped one
  has none.
- Partial control: excluded from `T` and `E`, counted, visible points listed. Unreadable control image (truncated
  JPEG): counted `unreadable`, excluded, the run continues. Report holds the « Limites » sentence on `D` verbatim.
- Report: numbers only, roles only (scan for every input session name, file name and path), CV13 intervals at a
  hand-checked case; `diagnostic.json` strict reader (cap, cap + 1, unknown key).
- `drift` report first line present; `measure_photo` outputs unchanged on the T8b fixtures.
- `main()` in a subprocess on T7 sessions with a split and lock written by the test.
- Lot 3b: missing, over-cap or stale `--snapped` file stops with the French line and exit 2; a control whose click
  differs from its snapped point uses the snapped point; the « précision humaine » line uses `C` from the file and
  changes no verdict field; a `"failed"` `dispersionPx` in the file gives an infinite `D` (and `"failed"` in
  `diagnostic.json`).

## Done when
Python checks of root "Test plan" green (CI command too), `kosmo-detekt` exit 0.

## Model
sonnet. Why: every quantity is defined in CV28 and CV31 with formulas reusing T8, T8b and T15m; tests on
synthetic data.

## Implementation notes
