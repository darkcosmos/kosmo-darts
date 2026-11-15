# T9d: Constrained tip pseudo-labels of v17 (CV23)

Status: on hold (D30): depends on T9o; revised at plan update P2 (root plan "Tasks").

## Goal
Turn each whole-dart box of `dart-tip-detection-v17i-coco` into a tip label predicted by MA (the `A/full` export,
trained on all dev photos, D25) under Kosmo's constraint, drop the whole image otherwise, report the figures per
rule, and build dataset version B (root "Approach, Constrained tip pseudo-labels of v17", D23, D24, D27).

## Files
- Changed `tools/camera/kosmo_camera/pseudo_label.py`: `tip_threshold(points, references) -> float | None`
  (`tauTip`); `label_tips(detector, images, tau_tip) -> PseudoResult`; `run_pseudo_tip(run, public_root,
  detector_factory)`: `tauTip` from `predictions/A.json` (T9o `load_for`), tips from `models/A/full` (MA), checks
  `pseudo-cal.json` (`StaleStep`, steps 6 to 8); refuses predictions of another version; `A` not built: prints
  "Version B non construite : version A non construite" and writes `not-built/B.json`; `tip_labels_from(run)`
  returning the T9 `tip_labels` callable (same refusals as `cal_labels_from`, against `A/full`).
- Uses `pseudo_file.py` (T9c) with `kind` `tip`; `figures`: boxes total and accepted, images total and kept, drops
  per reason, `medianPx`, `p95Px` (distance from the retained tip to the box corner `(x + w, y + h)`, source px).
- Changed ADR 0016 "Public data and version selection": the v17 constraint and drop rules.
- Added tests `tools/camera/tests/test_pseudo_tip.py`.

## Depends on
T9c.

## Rules
CV4, CV15, CV21, CV23, CV24, D23, D24, D25, D27 (strict rules final). `tauTip` on the out-of-training `A`
predictions of every judged dev photo (never MA's output on dev photos): tip points matched one-to-one to labelled
tips by decreasing score (duplicates within half a box side merged), correct within `PSEUDO_MAX_MM`;
lowest score whose set at or above has support >= `PSEUDO_MIN_SUPPORT` and precision >= `PSEUDO_MIN_PRECISION`.
Per v17 image (tip points >= `tauTip`, duplicates merged): each source box needs a point inside it with
`x + w/2 <= px <= x + w` and `y + h/2 <= py <= y + h` (edges inclusive; the best-scoring such point is retained);
else the box is rejected (`tip-outside-box`, `tip-outside-quarter`, `tip-below-threshold`, by the best point found)
and the image dropped; also dropped: one point retained for two boxes (`tip-shared`), a point >= `tauTip` inside no
box (`tip-unassigned`), or no calibration labels for the image in `pseudo-cal.json` (its drop reason). No `tauTip`,
no v17 folder or no image kept: `notBuilt` with the reason, B not built. Box rebuilt with `BOX_FRACTION` (T9).
`figures` count dropped images per rule (each image under the first rule it breaks, in the order above). The
calibration labels of v17 images come from `pseudo-cal.json` (M0), pending Q4 (root "Open questions"): if Kosmo
picks A, main amends this task before it is built. Percentiles: p95 by the same quantile rule as T13
(`numpy.percentile`, linear), stated in the notes.

## Tests
Fake detector factory (records which export was loaded), synthetic chain and COCO export with whole-dart boxes:
- `tauTip` from a scripted `predictions/A.json`: precision exactly 99 % and just below, support 29 and 30,
  one-to-one matching, merged duplicates; a `sans` predictions file refused; tips come from `A/full` only.
- Per box: point in the lower-right quarter accepted (each quarter edge inclusive), in another quarter, outside the
  box, below `tauTip` each rejected with its reason and the whole image dropped (no partial labels kept).
- Shared point (T2 of the design review): two overlapping boxes whose lower-right quarters both contain one
  confident point, no other point: `tip-shared`, the whole image dropped, no label kept for either box.
- Unassigned confident point, missing calibration labels each drop the image.
- Figures against a hand computation (rates, per-rule counts, median and p95 px on 20 scripted distances).
- Fallbacks: no `tauTip`, no v17 folder, no image kept give `notBuilt`; `not-built/A.json` gives B not built with
  its line; `pseudo-cal.json` changed after step 4: `StaleStep`; dataset B (T9) contains A plus the kept v17 tips
  and calibration labels; no `file_name` in stdout.

## Done when
Python checks of root "Test plan" green (including `python -m pytest -q -m "not slow"` from `tools/camera`),
`kosmo-detekt` exit 0.

## Model
sonnet. Why: constraint, drop rules and figures specified by Kosmo (D23, D27) and CV23; same patterns as T9c.

## Implementation notes
