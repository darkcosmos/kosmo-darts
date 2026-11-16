# T9c: Calibration-point pseudo-labels of public images (CV23)

HELD by D30: the compensated-calibration parts are replaced at P2; depends on T9o (root plan "Tasks").

## Goal
Give every public image of versions A and B the `cal1`..`cal4` labels predicted by M0 (the `sans/full` export,
trained on both dev sessions; thresholds from the `sans` out-of-training predictions, D25), or drop it, so public images never teach that calibration points are background (root "Approach,
Calibration points on public images", option (a), D23, D24). Also the shared pseudo file used by T9d.

## Files
- Added `tools/camera/kosmo_camera/pseudo_file.py`: strict reader and atomic writer of `pseudo-cal.json` and
  `pseudo-tip.json` (root CV23): `format` `"kosmo-camera-pseudo-labels"`, `version` 1, `kind` (`cal`|`tip`),
  `chainSha256`, `modelSha256` (the labelling `full` export), `sourceVersion` (`sans` for `cal`, `A` for `tip`),
  `predictionsSha256` (the predictions file the thresholds came from), `mapSha256`, `thresholds`, `figures`,
  `notBuilt` (reason or null), `images` (slug, subset, `file_name`, `imageSha256`, `labels` [{class, x, y} source
  px] or `dropReason`); unknown or missing key, wrong types, cap `MAX_BYTES` (16 MiB) rejected. (`kosmoPartSha256`
  is not used: the Kosmo part differs per fold; the chain SHA-256 ties the file to one fold plan.)
- Added `tools/camera/kosmo_camera/pseudo_label.py`: `cal_thresholds(points, references) -> CalThresholds`
  (`tauCalHigh`, `tauCalLow`, either None, support, precision); `label_calibration(detector, images, thresholds)`;
  `run_pseudo_cal(run, public_root, detector_factory)`: thresholds from `predictions/sans.json` (T9o `load_for`),
  labels from `models/sans/full` (M0); refuses predictions of another version or whose fold exports are not the
  current `sans` ones, and a `full` export not matching its training (`StaleStep`, steps 2 to 4);
  `cal_labels_from(run)` returning the T9 `cal_labels` callable (refuses another `chainSha256`, map SHA-256 or
  `sourceVersion`, or a `modelSha256` that is not the current `sans/full` export; image SHA-256 changed: drop
  `pseudo-stale`).
- Changed `tools/camera/config/defaults.json` and `config.py`: `PSEUDO_MAX_MM` 2.0, `PSEUDO_MIN_PRECISION` 0.99,
  `PSEUDO_MIN_SUPPORT` 30, `PSEUDO_LOW_QUANTILE` 0.01; pinned once in `test_config.py` (as A16).
- Changed `docs/rules/camera.md`: writes CV23. Changed ADR 0016 "Public data and version selection": options (a) to
  (d), why (a), the `rfdetr` 1.12.1 finding (no per-class or per-image mask in `loss_labels`, `iscrowd=1` dropped
  by `ConvertCoco`).
- Added tests `tools/camera/tests/test_pseudo_file.py`, `test_pseudo_cal.py`.

## Depends on
T3 (`Detector`, `Point`), T8 (`geometry.board_from_image`, `apply`), T8b (compensated reference), T9 (chain,
run folder), T9o (`predictions/sans.json`).

## Rules
CV2, CV4, CV15, CV21, CV23, CV24, D23, D24, D25. Thresholds (root Approach): on the out-of-training `sans`
predictions of every judged dev photo (never a `full` model's output, never an image seen in training), best point
per class per photo, correct when within `PSEUDO_MAX_MM` of the reference point in board mm (photo's
compensated reference homography); `tauCalHigh` lowest score whose set at or above has support >= 30 and precision
>= 99 %; `tauCalLow` = 1 % quantile of correct scores, capped at `tauCalHigh`. Per public image (every mapped set,
v17 included): best point per class >= high is a label; any calibration point in [low, high) drops
`cal-uncertain`; two same-class points >= high farther apart than half a box side drop `cal-duplicate`; 3 or 4
labels not clockwise in `cal1`..`cal4` order drop `cal-order`. No high: no label, any point >= low drops. No low:
`notBuilt` "seuil de calibration introuvable", A and B not built. M0 runs on images from T9p `iter_images`. Limit
(stated by T13b): thresholds measured on fold models are applied to M0.

## Tests
Fake detector factory (scripted `Point` lists, records which export was loaded), synthetic chain (T9 helpers),
sessions (T7) and COCO exports (T9p helpers):
- Thresholds from a scripted `predictions/sans.json`: precision exactly 99 % and just below, support 29 and 30,
  `tauCalLow` capped, no correct point; a predictions file of version `A`, or whose fold hashes are not the current
  `sans` exports, refused; labels come from `sans/full` only (load recorded).
- Labelling: label at `tauCalHigh` exactly, band drop at `tauCalLow` exactly, duplicate, clockwise with 3 and 4
  points, a swapped pair dropped, fewer than 3 points unchecked; no-high and no-low fallbacks.
- Property: no kept image has a calibration point >= `tauCalLow` without its label.
- Pseudo file: round trip, each rejection, cap; `cal_labels_from` refusals (other chain, other map, other
  `sourceVersion`, `sans/full` retrained after the file) and `pseudo-stale`; dataset A (T9) carries the labels as
  `BOX_FRACTION` boxes; no `file_name` in stdout.

## Done when
Python checks of root "Test plan" green (including `python -m pytest -q -m "not slow"` from `tools/camera`),
`kosmo-detekt` exit 0.

## Model
sonnet. Why: thresholds, band, order and drop rules fully specified in CV23 and D25; fake-detector tests; the pseudo file is
local and regenerable (no migration), so not an `opus` case.

## Implementation notes
