# T8: Homography, calibration metric, groups

## Goal
The geometry shared by measures and decisions: homography from the 4 calibration points, image to board mm, the
calibration error of CV9, and the derived groups of CV14.

## Files
- Added `tools/camera/kosmo_camera/geometry.py`: `homography(src, dst)` (normalised DLT, numpy, 4 or more points),
  `apply(H, pts)`, `board_from_image(cal_pixels)` (H image to board mm using `CALIBRATION_POINTS_MM`),
  `calibration_error_mm(H_true, H_pred) -> float` (CV9 sample of 41 points), `calibration_success(H_true,
  found_points) -> bool` (False with fewer than 4 points, else error `<=` `CAL_MAX_ERROR_MM` from `config.py`),
  `near_far(H_true, point_mm) -> "near" | "far"`.
- Added `tools/camera/kosmo_camera/groups.py`: `tripod_group(cal_pixels, image_size, zoom_ratio)` (angle, side,
  distance bins), `lighting_class(image, H_true)` (median luma of the pixels inside the 170 mm circle), `zoom_bin`.
  Bin limits: values of `docs/camera/training.md` section "Groups" (T2), added to `defaults.json` and read through
  `config.py` (D9).
- Added tests `tools/camera/tests/test_geometry.py`, `test_groups.py`.

## Depends on
T7.

## Rules
CV1, CV2, CV9, CV14. Camera direction for near and far: the image of the board centre and the vanishing direction of
H; the near half is the board side closer to the camera (larger in the image). Viewing angle from the ellipse of the
170 mm circle (ratio of axes, `acos(minor/major)`); `front` has no side; a null zoom ratio counts as 1.

## Tests
- DLT recovers a known H from 4 exact points (1e-9) and is invariant to point scaling; degenerate (3 collinear
  points) raises.
- `calibration_error_mm`: 0 for identical H; a 1 px shift of one point on a synthetic view gives a known error band;
  `calibration_success` true at exactly 2.0 mm and false just above (H built to give a known error), false with 3
  points found.
- Near and far: synthetic camera on the left gives near for x < 0; front view is split consistently.
- Tripod: synthetic views at yaw 0, 25, 45 degrees fall in `front`, `oblique`, `steep`, side `left` or `right` from
  the yaw sign; distance bins from the apparent size; zoom 2.0 halves the apparent distance ratio.
- Lighting: synthetic boards rendered dark, medium, bright land in their class.

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: formulas specified in CV9 and CV14.

## Implementation notes
