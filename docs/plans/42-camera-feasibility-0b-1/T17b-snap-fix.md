# T17b: Snapping fix for the cause found on the real clicks, with realistic synthetic cases

Status: "Cause and fix" rewritten at plan update P3 (D55) from the dev and segment figures of Kosmo's K7.4 report.

## Independence (CV32, D54)
P3 and this task read only the dev figures of the K7.4 report and the segment-point figures; the eval control
figures are never used to choose a cause, a fix or a parameter (they decide the acceptance in T17c).

## Goal
Make the snap scale-aware so that it sees the wires of 4080x3072 photos and of video frames, never ends on a wrong
crossing (CV31 criterion 2), and expose what the coarse stage sees so that one K7.6 run both accepts and explains.

## Cause and fix (D55)
- Confirmed (dev): 0 of 161 photo control points snapped; 132 stop at `coarse_no_ridge` (no wire line at all in
  the coarse region), 9 of 12 segment points too; 0 recovered at 2x and 4x radius (so not the radius); T share 0 %
  and stem about 22 px where measured (so not the T junction); 0 ambiguity on photos; noise 2.1 to 3.15 (low);
  bright wires 100 %. Video: 7 of 111 snapped, width 3.26 px, blur 1.41 px where snapped.
- Open hypotheses, in order: (1) scale: if frames are 1920 px wide with the photo's horizontal field (D50 crop top
  and bottom), photo wires are about 2.1 x the video ones, about 7 px wide with about 3 px blur, outside the regime
  the detector was tuned for (2 to 3.3 px wires, `SNAP_SIGMAS_PX` up to 3, absolute px constants such as
  `_SHARP_BLUR_PX` 2, `_DISTINCT_PX` 3, `_GAP_PX` 3, `_BAND_MARGIN_PX` 2.5); (2) the `_is_wire` floor: the wire not
  above both sides by `SNAP_MIN_CONTRAST` x noise (cream sides, shadow, reflections); (3) other (to be read from
  the coarse descriptors below). The report has no width or contrast for stages before the fine fit, so (1) is
  inferred, not measured.
- Chosen fix: `SnapParams.scale` (integer binning factor, area average, cached per image and factor): the
  unchanged detector runs on the binned luma in its validated regime, start and radius divided by the factor
  (the radius stays `SNAP_RADIUS_PX` in full-resolution px), the result mapped back to full-resolution coordinates.
  The values tried are the grid `SNAP_SEARCH_SCALES` x `SNAP_SEARCH_MIN_CONTRAST` (at most 8 candidates, chosen here
  so that every candidate passes the safety tests below); T17e picks one candidate per source on the dev points.
- Coarse descriptors (filled even when the coarse stage fails): `coarse_sigma`, `ridge_points`, `ridge_lines`,
  `wire_lines` (lines passing `_is_wire`), `wire_excess` (best median excess over the floor), `ridge_width_px`
  (half-maximum width of the mean profile across the strongest ridge line, full-resolution px).
- Rejected: shape-check change (nothing reaches the shape checks: 18 « forme » of 161); larger radius (0 at 2x and
  4x); larger sigmas and window only (every absolute px constant of the fine stage would need rescaling and
  retesting; binning reuses them unchanged and costs less time); polarity change (bright 100 %, consistent with
  metal wires); lower floor alone (a guess without the coarse descriptors; kept as a grid axis, safety-tested).

## Files
- Changed `tools/camera/kosmo_camera/snap.py` (`scale`, binning, coarse descriptors); `config/defaults.json`,
  `config.py`, `test_config.py`: `SNAP_SEARCH_SCALES`, `SNAP_SEARCH_MIN_CONTRAST` pinned once (T3).
- Changed `tools/camera/kosmo_camera/synth.py`: `BoardStyle` options for a realistic photo (4080x3072, wire 7 px,
  blur 3 px, bright metal wire, JPEG quality 90) and video frame (1920x1080 cut from the 4:3 field, wire 3.3 px,
  blur 1.4 px).
- Changed `docs/rules/camera.md` CV31 (scale and grid, D55); ADR 0016 "Assisted click" one line.
- Changed `tests/test_snap.py`.

## Depends on
T17a; plan update P3 (D55).

## Rules
CV31, CV32 (independence, D54), CV4 (eval never used; the grid is fixed here on synthetic boards, the choice
inside it is made on dev only, D55).

## Tests
- Every T15m case keeps its bounds at scale 1 (0.25 px, at least 90 % snapped, never a wrong crossing, noise 5).
- One case per cause, named after it, 200 starts over seeds 0 to 9: `wide_photo_wire` (photo style) snaps at
  least 90 % with the scale 2 or 3 candidate, at most 0.25 x scale px; `video_wire` (video style) at scale 1;
  `low_wire_contrast` (wire 6 grey levels above one side) with the lower floor; each also fails at scale 1 / floor
  4 (the case reproduces the cause).
- Safety, every grid candidate at its own style: dart shaft across the radial wire, T junction next to a
  crossing, halo ridge parallel to the wire: refused or on the true crossing; never a wrong crossing.
- Mapping: a crossing at a known full-resolution point is returned within the bound for scales 1 to 4, the
  start near the image border at scale 4 gives `border`; descriptors present on a `coarse_no_ridge` failure.
- Timing: median success-path time per snap of each realistic case printed in Implementation notes (T15l).

## Done when
Python checks of root "Test plan" green, the Python 3.14 `TOOL_TESTS` run green, `kosmo-detekt` exit 0.

## Model
opus. Why: case 3, the CV31 domain changes for a cause inferred, not measured, from the real report, and the
candidates bound the reference truth.

## Implementation notes
