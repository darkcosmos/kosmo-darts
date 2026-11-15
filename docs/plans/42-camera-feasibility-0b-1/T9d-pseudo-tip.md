# T9d: Constrained tip pseudo-labels of v17 (CV23)

## Goal
Turn each whole-dart box of `dart-tip-detection-v17i-coco` into a tip label predicted by model A (MA) under
Kosmo's constraint, drop the whole image otherwise, report the figures, and build dataset version B (root
"Approach, Constrained tip pseudo-labels of v17", D23, D24).

## Files
- Changed `tools/camera/kosmo_camera/pseudo_label.py`: `tip_threshold(points, references) -> float | None`
  (`tauTip`); `label_tips(detector, images, tau_tip) -> PseudoResult`; `run_pseudo_tip(model, a_dataset_dir,
  public_root, pseudo_cal, out)` (refuses a manifest whose `version` is not `A`); `tip_labels_from(path, manifest)`
  returning the T9 `tip_labels` callable (same refusals as `cal_labels_from`).
- Uses `pseudo_file.py` (T9c) with `kind` `tip`; `figures`: boxes total and accepted, images total and kept, drops
  per reason, `medianPx`, `p95Px` (distance from the retained tip to the box corner `(x + w, y + h)`, source px).
- Changed ADR 0016 "Public data and version selection": the v17 constraint and drop rules.
- Added tests `tools/camera/tests/test_pseudo_tip.py`.

## Depends on
T9c.

## Rules
CV15, CV21, CV23, D23, D24. `tauTip` on the valid subset of the `A` dataset: MA tip points matched one-to-one to
labelled tips by decreasing score (duplicates within half a box side merged), correct within `PSEUDO_MAX_MM`;
lowest score whose set at or above has support >= `PSEUDO_MIN_SUPPORT` and precision >= `PSEUDO_MIN_PRECISION`.
Per v17 image (tip points >= `tauTip`, duplicates merged): each source box needs a point inside it with
`x + w/2 <= px <= x + w` and `y + h/2 <= py <= y + h` (edges inclusive; the best-scoring such point is retained);
else the box is rejected (`tip-outside-box`, `tip-outside-quarter`, `tip-below-threshold`, by the best point found)
and the image dropped; also dropped: one point retained for two boxes (`tip-shared`), a point >= `tauTip` inside no
box (`tip-unassigned`), or no calibration labels for the image in `pseudo-cal.json` (its drop reason). No `tauTip`,
no v17 folder or no image kept: `notBuilt` with the reason, B not built. Box rebuilt with `BOX_FRACTION` (T9).
Percentiles: p95 by the same quantile rule as T13 (`numpy.percentile`, linear), stated in the notes.

## Tests
Fake detector, synthetic COCO export with whole-dart boxes:
- `tauTip`: precision exactly 99 % and just below, support 29 and 30, one-to-one matching, merged duplicates.
- Per box: point in the lower-right quarter accepted (each quarter edge inclusive), in another quarter, outside the
  box, below `tauTip` each rejected with its reason and the whole image dropped (no partial labels kept).
- Shared point, unassigned confident point, missing calibration labels each drop the image.
- Figures against a hand computation (rates, per-reason counts, median and p95 px on 20 scripted distances).
- Fallbacks: no `tauTip`, no v17 folder, no image kept give `notBuilt`; `run_pseudo_tip` on a `sans` manifest
  raises; dataset B (T9) contains A plus the kept v17 tips and calibration labels; no `file_name` in stdout.

## Done when
Python checks of root "Test plan" green (including `python -m pytest -q -m "not slow"` from `tools/camera`),
`kosmo-detekt` exit 0.

## Model
sonnet. Why: constraint, drop rules and figures specified by Kosmo (D23) and CV23; same patterns as T9c.

## Implementation notes
