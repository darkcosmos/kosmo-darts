# T9c: Calibration-point pseudo-labels of public images (CV23)

## Goal
Give every public image of versions A and B the `cal1`..`cal4` labels predicted by M0 (the exported model of the
`sans` dataset), or drop it, so public images never teach that calibration points are background (root "Approach,
Calibration points on public images", option (a), D23, D24). Also the shared pseudo file used by T9d.

## Files
- Added `tools/camera/kosmo_camera/pseudo_file.py`: strict reader and atomic writer of `pseudo-cal.json` and
  `pseudo-tip.json` (root CV23): `format` `"kosmo-camera-pseudo-labels"`, `version` 1, `kind` (`cal`|`tip`),
  `modelSha256`, `sourceVersion`, `kosmoPartSha256`, `mapSha256`, `thresholds`, `figures`, `notBuilt` (reason or
  null), `images` (slug, subset, `file_name`, `imageSha256`, `labels` [{class, x, y} source px] or `dropReason`);
  unknown or missing key, wrong types, cap `MAX_BYTES` (16 MiB) rejected.
- Added `tools/camera/kosmo_camera/pseudo_label.py`: `cal_thresholds(points, references) -> CalThresholds`
  (`tauCalHigh`, `tauCalLow`, either None, support, precision); `label_calibration(detector, images, thresholds)`;
  `run_pseudo_cal(model, sans_dataset_dir, public_root, out)` (refuses a dataset manifest whose `version` is not
  `sans`); `cal_labels_from(path, manifest)` returning the T9 `cal_labels` callable (refuses another
  `kosmoPartSha256`, map SHA-256 or `sourceVersion`; image SHA-256 changed: drop `pseudo-stale`).
- Changed `tools/camera/config/defaults.json` and `config.py`: `PSEUDO_MAX_MM` 2.0, `PSEUDO_MIN_PRECISION` 0.99,
  `PSEUDO_MIN_SUPPORT` 30, `PSEUDO_LOW_QUANTILE` 0.01; pinned once in `test_config.py` (as A16).
- Changed `docs/rules/camera.md`: writes CV23. Changed ADR 0016 "Public data and version selection": options (a) to
  (d), why (a), the `rfdetr` 1.12.1 finding (no per-class or per-image mask in `loss_labels`, `iscrowd=1` dropped
  by `ConvertCoco`).
- Added tests `tools/camera/tests/test_pseudo_file.py`, `test_pseudo_cal.py`.

## Depends on
T3 (`Detector`, `Point`), T8 (`geometry.board_from_image`, `apply`), T8b (compensated reference), T9.

## Rules
CV2, CV15, CV21, CV23, D23, D24. Thresholds (root Approach): on the valid subset of the `sans` dataset, best M0
point per class per photo, correct when within `PSEUDO_MAX_MM` of the reference point in board mm (photo's
compensated reference homography); `tauCalHigh` lowest score whose set at or above has support >= 30 and precision
>= 99 %; `tauCalLow` = 1 % quantile of correct scores, capped at `tauCalHigh`. Per public image (every mapped set,
v17 included): best point per class >= high is a label; any calibration point in [low, high) drops
`cal-uncertain`; two same-class points >= high farther apart than half a box side drop `cal-duplicate`; 3 or 4
labels not clockwise in `cal1`..`cal4` order drop `cal-order`. No high: no label, any point >= low drops. No low:
`notBuilt` "seuil de calibration introuvable", A and B not built. Detector runs on images from T9 `iter_images`.

## Tests
Fake detector (scripted `Point` lists), synthetic sessions (T7) and synthetic COCO exports (T9 helpers):
- Thresholds: precision exactly 99 % and just below, support 29 and 30, `tauCalLow` capped, no correct point.
- Labelling: label at `tauCalHigh` exactly, band drop at `tauCalLow` exactly, duplicate, clockwise with 3 and 4
  points, a swapped pair dropped, fewer than 3 points unchecked; no-high and no-low fallbacks.
- Property: no kept image has a calibration point >= `tauCalLow` without its label.
- Pseudo file: round trip, each rejection, cap; `cal_labels_from` refusals and `pseudo-stale`; dataset A (T9)
  carries the labels as `BOX_FRACTION` boxes; `run_pseudo_cal` on an `A` manifest raises; no `file_name` in stdout.

## Done when
Python checks of root "Test plan" green (including `python -m pytest -q -m "not slow"` from `tools/camera`),
`kosmo-detekt` exit 0.

## Model
sonnet. Why: thresholds, band, order and drop rules fully specified in CV23; fake-detector tests; the pseudo file is
local and regenerable (no migration), so not an `opus` case.

## Implementation notes
