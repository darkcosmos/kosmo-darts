# T17c: Real-data acceptance of the snap and automatic re-snap of every stored click

## Goal
Measure the fixed snap on Kosmo's real clicks against the acceptance criterion of CV32 and, when it passes, snap
every stored click into the pixel-level truth file read by the diagnosis; a point not snapped keeps the click and
its human precision, counted apart (D49, root "Approach, Automatic re-snap").

## Files
- Retention (D56): per source, the first T17e-ranked candidate is checked on its dev control points (lattice and
  restarts); any « croisement suspect » or unstable restart excludes it and the next is checked, at most
  `SNAP_SEARCH_FALLBACKS` times; none clean: « Recalage non accepté : aucun réglage sans croisement suspect sur
  dev ». Every snap of this task (acceptance, lattice, restarts, re-snap) uses the retained candidate for the
  point's source (D55); segment points are re-snapped and counted apart, never in the acceptance share (D52). The run
  prints « Durée prévue d'une relance de K7.6 : environ N min » from `planned_minutes` (reused, A1) with the median
  time per snap measured in this run (real post-fix timing, ADR 0018).
- Changed `tools/camera/kosmo_camera/snap_report.py` (same command as K7.4): section « Acceptation » (share of photo
  control points snapped from the stored click with its Wilson interval against `SNAP_ACCEPT_MIN_SHARE`, lattice
  consistency, restart stability, per role and source; decided on the eval photo control points only, the dev
  values printed « pour information », CV32 independence, D54) and section « Précision des clics » (click-to-snap distance
  px and mm, median and p95 with CV13 intervals: Kosmo's hand measured by the machine). The verdict line is
  « Recalage accepté » or « Recalage non accepté : <cause> »; `snapped-points.json` is written only when accepted.
- Added `tools/camera/kosmo_camera/lattice.py` (numpy): the consistency check of CV32: the homography of the 4
  snapped points predicts the other outer-double corners in the frame; each is snapped from its prediction; a
  calibration point is « croisement suspect » when its residual against the lattice fitted on the others exceeds
  `SNAP_LATTICE_MAX_PX`; fewer than `SNAP_LATTICE_MIN_CORNERS` snapped corners: « non vérifiable », counted.
- Added `tools/camera/kosmo_camera/snapped_file.py` (stdlib, strict, `MAX_BYTES` 4 MiB): format
  `"kosmo-camera-snapped"`, version 1, top-level `snapParams` per source (`scale`, `minContrast`: the chosen
  candidates, so T15j re-snaps with them; the strict reader accepts only values of `SNAP_SEARCH_SCALES` and
  `SNAP_SEARCH_MIN_CONTRAST`, both sources required, anything else refused like any invalid file, S3), per session `session`, `segmentsSha256`, `controlsSha256`; per control and
  per segment the stored points, `points` (snapped or click), `snap`, `reasons`, `dispersionPx` per point: a finite
  number, the string `"failed"` for a failed restart (infinite `D`, as `diagnostic_file`), or null when not snapped;
  the writer never emits `Infinity` or `NaN`, the strict reader refuses them (`parse_constant` raises). The file is
  written to a temporary name then renamed; a refused run deletes any earlier `snapped-points.json` in `--out`.
  Its format, hash linkage and "written only when accepted" rule are recorded in ADR 0018 (section "Re-snap file"),
  plus one line added there: the file records the candidate used (`snapParams`) and the reader validates it
  against the grids (A5).
  If the build grows past the task size, split into T17c-1 (lattice and file) and T17c-2 (acceptance report).
- Changed `defaults.json`, `config.py`, `test_config.py`: `SNAP_ACCEPT_MIN_SHARE` 0.90, `SNAP_LATTICE_MAX_PX`,
  `SNAP_LATTICE_MIN_CORNERS` (values chosen on synthetic boards, pinned once).
- Changed `docs/rules/camera.md`: CV32 (acceptance and re-snap parts), CV15 sentence on `snapped-points.json`.
- Changed `tests/test_snap_report.py`; added `test_lattice.py`, `test_snapped_file.py` (both in `TOOL_TESTS`).

## Depends on
T17e, T17b, T17a.

## Rules
CV32, CV31, CV28 (`D` per point stored for T15i), CV13, CV15, D48, D52, D55, D56.

## Tests
- Acceptance at the boundary: 0.90 accepted, one point fewer refused; one suspect point refuses with its cause line;
  a restart converging elsewhere refuses; failed restarts counted, not suspect.
- Independence (D54): dev at 0.50 and eval at 0.95 accepted; dev at 0.95 and eval at 0.85 refused; a suspect point
  or an unstable restart on dev only does not refuse; the dev line is marked « pour information ».
- Planned duration: on the test sessions, the number of snaps the acceptance run performs equals the starts of the
  K7.4 « Durée prévue de K7.6 » formula plus T17e's candidate snaps (CV32), so the planned duration is not
  underestimated; the rerun line uses this run's median (injected in the test).
- Chosen candidate: acceptance snaps use the scale T17e chose (a scale-2 photo case accepted only with it);
  `snapParams` round-trips; segment points counted apart and absent from the share (D52).
- Retention (D56): a 100 % candidate with one dev lattice suspect, and one with an unstable dev restart, each lose
  to a 97 % clean one; three unclean candidates in a row stop after 2 fallbacks with the refusal line; eval
  suspects never change the retained candidate; the fallback snaps equal the planned-duration term.
- `snapParams` reader (S3): scale 0, negative, NaN, a value outside the grid, a string, a missing source: each
  refused on the usual path.
- Lattice: radial lens distortion k1 giving 3 px at the image corners (4080 px wide) never flagged, over 20 seeded
  boards (seeds 0 to 19); the chosen `SNAP_LATTICE_MAX_PX` lies above the largest distortion residual of those
  boards and below 4 px (a calibration point moved onto a dart-shaft crossing 4 px away is flagged); boundary: a
  point offset so its residual is exactly `SNAP_LATTICE_MAX_PX` is not suspect, + 0.1 px is; a video frame cropped
  16:9 with 2 visible points « non vérifiable » when below the corner minimum.
- Re-snap file: points equal the snapped ones, clicks kept, `unsnapped` points keep the click; strict reader (cap,
  cap + 1, unknown key, stale hashes detected by the reader helper); not written when refused; a refused run after
  an accepted one leaves no `snapped-points.json`.
- Failed restart (T1): round trip of a point with a failed restart writes `"failed"`, reads back as `math.inf`;
  the reader refuses a file holding `Infinity`, `-Infinity` or `NaN`; T15i's reader helper returns infinite `D`.
- Worst case size (S2): 1024 controls (`MAX_CONTROLS`) and 64 segments per session file, every point with a
  `dispersionPx` and a reason, written under the 4 MiB cap.
- Click precision: 400 synthetic clicks (seed 7) offset by a Gaussian spread of 2 px: p95 within its CV13 interval.

## Done when
Python checks of root "Test plan" green, the Python 3.14 `TOOL_TESTS` run green, `kosmo-detekt` exit 0.

## Model
opus. Why: case 3, "no point on another crossing" has no ground truth on real images; the lattice check and its
thresholds against lens distortion are not specified by any earlier rule and gate the truth.

## Implementation notes
