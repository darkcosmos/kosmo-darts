# T17c: Real-data acceptance of the snap and automatic re-snap of every stored click

## Goal
Measure the fixed snap on Kosmo's real clicks against the acceptance criterion of CV32 and, when it passes, snap
every stored click into the pixel-level truth file read by the diagnosis; a point not snapped keeps the click and
its human precision, counted apart (D49, root "Approach, Automatic re-snap").

## Files
- Changed `tools/camera/kosmo_camera/snap_report.py` (same command as K7.4): section « Acceptation » (share of photo
  control points snapped from the stored click with its Wilson interval against `SNAP_ACCEPT_MIN_SHARE`, lattice
  consistency, restart stability, per role and source) and section « Précision des clics » (click-to-snap distance
  px and mm, median and p95 with CV13 intervals: Kosmo's hand measured by the machine). The verdict line is
  « Recalage accepté » or « Recalage non accepté : <cause> »; `snapped-points.json` is written only when accepted.
- Added `tools/camera/kosmo_camera/lattice.py` (numpy): the consistency check of CV32: the homography of the 4
  snapped points predicts the other outer-double corners in the frame; each is snapped from its prediction; a
  calibration point is « croisement suspect » when its residual against the lattice fitted on the others exceeds
  `SNAP_LATTICE_MAX_PX`; fewer than `SNAP_LATTICE_MIN_CORNERS` snapped corners: « non vérifiable », counted.
- Added `tools/camera/kosmo_camera/snapped_file.py` (stdlib, strict, `MAX_BYTES` 4 MiB): format
  `"kosmo-camera-snapped"`, version 1, per session `session`, `segmentsSha256`, `controlsSha256`; per control and
  per segment the stored points, `points` (snapped or click), `snap`, `reasons`, `dispersionPx` per point.
- Changed `defaults.json`, `config.py`, `test_config.py`: `SNAP_ACCEPT_MIN_SHARE` 0.90, `SNAP_LATTICE_MAX_PX`,
  `SNAP_LATTICE_MIN_CORNERS` (values chosen on synthetic boards, pinned once).
- Changed `docs/rules/camera.md`: CV32 (acceptance and re-snap parts), CV15 sentence on `snapped-points.json`.
- Changed `tests/test_snap_report.py`; added `test_lattice.py`, `test_snapped_file.py` (both in `TOOL_TESTS`).

## Depends on
T17b, T17a.

## Rules
CV32, CV31, CV28 (`D` per point stored for T15i), CV13, CV15, D48.

## Tests
- Acceptance at the boundary: 0.90 accepted, one point fewer refused; one suspect point refuses with its cause line;
  a restart converging elsewhere refuses; failed restarts counted, not suspect.
- Lattice: a calibration point moved onto a dart-shaft crossing 4 px away flagged; lens-like smooth distortion of
  the synthetic board (radial, a few px at the corners) never flagged; a video frame cropped 16:9 with 2 visible
  points « non vérifiable » when below the corner minimum.
- Re-snap file: points equal the snapped ones, clicks kept, `unsnapped` points keep the click; strict reader (cap,
  cap + 1, unknown key, stale hashes detected by the reader helper); not written when refused.
- Click precision on synthetic clicks offset by a known random spread: p95 within its interval.

## Done when
Python checks of root "Test plan" green, the Python 3.14 `TOOL_TESTS` run green, `kosmo-detekt` exit 0.

## Model
opus. Why: case 3, "no point on another crossing" has no ground truth on real images; the lattice check and its
thresholds against lens distortion are not specified by any earlier rule and gate the truth.

## Implementation notes
