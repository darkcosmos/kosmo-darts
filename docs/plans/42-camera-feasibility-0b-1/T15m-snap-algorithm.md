# T15m: Snapping algorithm, sub-pixel wire crossing and restart dispersion

## Goal
Locate the exact wire crossing near a rough click of Kosmo, sub-pixel, or say "non recalé", and measure by machine
how repeatable that location is (Kosmo, D39; squad design D43).

## Files
- Added `tools/camera/kosmo_camera/snap.py` (numpy; Pillow only in `load_luma(path)`, which keeps
  `Image.MAX_IMAGE_PIXELS` (S8) and returns a float32 luma array): `snap_point(luma, start, params)` -> `SnapResult(point,
  status, reason)`, status `"snapped"` or `"unsnapped"`, reason one of `support`, `contrast`, `residual`, `angle`,
  `no_convergence`, `too_far`, `border`; `restart_dispersion(luma, point, homography, params)` -> mm or `inf`.
  Algorithm as root plan "Approach, Assisted click" (window, Hessian ridge and gradient scales, line A, line B,
  intersection, iteration, the "no clear crossing" tests). Pure functions, no file or network access besides
  `load_luma`; never imported by the stdlib modules at import time.
- Changed `tools/camera/kosmo_camera/synth.py`: option `ring_wires=True` drawing the double and treble ring wires
  (`_WIRE` colour, same width as the radial wires) from `board.py` geometry; default off so every existing T7
  output stays byte-identical.
- Changed `defaults.json`, `config.py`, `test_config.py`: the `SNAP_*` constants of root "Files and types added or
  changed" (`SNAP_RADIUS_PX` to `SNAP_REPEAT_MAX_MM`), each pinned once in `test_config.py` only (T3, A16). T15m may
  change the initial values, on synthetic boards only, and writes the final values and why in Implementation notes.
- Changed `docs/rules/camera.md`: adds CV31 (snap part: statuses, reasons, dispersion).
- Added `tools/camera/tests/test_snap.py`; changed `test_synth.py` (ring option, default output unchanged).

## Depends on
T7 (synthetic boards; built). Board-plane mm use the existing `geometry.homography` and `board.py`.

## Rules
CV31, CV2 (the 4 points are double-ring outer edge on radial wires), CV4 (no tuning on Kosmo's photos), S8.

## Tests
- Root plan "Test plan", T15m entry, every case, with the rule identifier in each test name.
- Truth from `board.py` geometry projected by the T7 homography, never from the snap itself.
- Determinism: same input, same output bit for bit; no random state without the T7 seed.
- Speed: one snap on a 12 MP luma under 50 ms and `load_luma` of a 12 MP JPEG under 1 s on the sandbox CPU,
  marked `slow` if above the CI budget.

## Done when
Python checks of root "Test plan" green (CI command too), `python3 -I -S -m kosmo_camera.annotate --help` works,
`kosmo-detekt` exit 0.

## Model
opus. Why: rule still open after design review (case 3): the detector, the "no clear crossing" tests and their
thresholds are outlined, not specified, and their quality bounds the reference truth of the whole diagnosis.

## Implementation notes
