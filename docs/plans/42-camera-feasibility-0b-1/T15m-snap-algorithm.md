# T15m: Snapping algorithm, sub-pixel wire crossing and restart dispersion

## Goal
Locate the exact wire crossing near a rough click of Kosmo, sub-pixel, or say "non recalé", and measure by machine
how repeatable that location is (Kosmo, D39; squad design D43).

## Files
- Added `tools/camera/kosmo_camera/luma.py` (numpy, Pillow; A33, S14, D45): the one JPEG-to-luma decoder, moved
  from `drift.py` `_decode_luma` (same output), `decode_luma(path, expected_size=None)` -> `(luma, None)` or
  `(None, reason)`, never an exception; `MAX_IMAGE_BYTES` 32 MiB checked before reading, never read past;
  `Image.MAX_IMAGE_PIXELS` kept, bomb warning raised as an error, no `LOAD_TRUNCATED_IMAGES` (root "Approach, One
  image decoder"). Changed `drift.py`: calls `luma.decode_luma`, `_decode_luma` removed.
- Added `tools/camera/kosmo_camera/snap.py` (numpy only, works on a luma array, no image decoding):
  `snap_point(luma, start, params)` -> `SnapResult(point,
  status, reason)`, status `"snapped"` or `"unsnapped"`, reason one of `support`, `contrast`, `residual`, `angle`,
  `no_convergence`, `too_far`, `border`; `restart_dispersion(luma, point, homography, params)` -> mm or `inf`.
  Algorithm as root plan "Approach, Assisted click" (window, Hessian ridge and gradient scales, line A, line B,
  intersection, iteration, the "no clear crossing" tests). Pure functions, no file or network access; never
  imported by the stdlib modules at import time.
- Changed `tools/camera/kosmo_camera/synth.py`: option `ring_wires=True` drawing the double and treble ring wires
  (`_WIRE` colour, same width as the radial wires) from `board.py` geometry; default off so every existing T7
  output stays byte-identical.
- Changed `defaults.json`, `config.py`, `test_config.py`: the `SNAP_*` constants of root "Files and types added or
  changed" (`SNAP_RADIUS_PX` to `SNAP_REPEAT_MAX_MM`), each pinned once in `test_config.py` only (T3, A16). T15m may
  change the initial values, on synthetic boards only, and writes the final values and why in Implementation notes.
- Changed `docs/rules/camera.md`: adds CV31 (snap part: statuses, reasons, dispersion).
- Added `tools/camera/tests/test_snap.py`, `test_luma.py`; changed `test_synth.py` (ring option, default output
  unchanged) and the T8b static check in `test_drift.py` (parametrized over `drift.py`, `luma.py`, `snap.py`).

## Depends on
T7 (synthetic boards; built). Board-plane mm use the existing `geometry.homography` and `board.py`.

## Rules
CV31, CV2 (the 4 points are double-ring outer edge on radial wires), CV4 (no tuning on Kosmo's photos), S8, S14
(D45).

## Tests
- Root plan "Test plan", T15m entry, every case, with the rule identifier in each test name.
- Truth from `board.py` geometry projected by the T7 homography, never from the snap itself; a converged wrong
  crossing is caught by its distance to that truth, not by the dispersion (T2); one board in a seed and style not
  used for tuning meets the 0.1 px bound (T1); `luma.py` cases (bomb, truncated, random, wrong size, cap and
  cap + 1, T8b drift tests unchanged) as root "Test plan".
- Determinism: same input, same output bit for bit; no random state without the T7 seed.
- Speed: one snap on a 12 MP luma under 50 ms and `luma.decode_luma` of a 12 MP JPEG under 1 s on the sandbox CPU,
  marked `slow` if above the CI budget.

## Done when
Python checks of root "Test plan" green (CI command too), `python3 -I -S -m kosmo_camera.annotate --help` works,
`kosmo-detekt` exit 0.

## Model
opus. Why: rule still open after design review (case 3): the detector, the "no clear crossing" tests and their
thresholds are outlined, not specified, and their quality bounds the reference truth of the whole diagnosis.

## Implementation notes
Status: PARTIAL (two build agents). Done and committed: `luma.py` with `test_luma.py` (no RGB copy besides
`convert("L")`; `Image.draft` not used because it changes the luma values, see the module docstring; the 12 MP timing
test is `slow`), `drift.py` on `luma.decode_luma` (T8b drift tests unchanged and green), the T8b static check
parametrized over `drift.py`, `luma.py`, `snap.py`; `synth.py` `ring_wires` and `BoardStyle` (analytic renderer from
`board.py` geometry, edges supersampled 4x4; default output byte-identical, pinned by sha256 in `test_synth.py`);
`SNAP_*` in `defaults.json` and `config.py`, pinned once in `test_config.py`, initial values unchanged.

Not done: `snap.py` is committed as work in progress, without `test_snap.py`, CV31 in `docs/rules/camera.md`, or
tuned values. It does NOT meet the Tests section. Measured on `render_board(..., ring_wires=True)` with noise 2,
luma by `convert("L")`, starts at 0, 3, 6 and 9.5 px around each of the 4 `truth.calibration` pixels (100 starts per
board): when it snaps it is usually within 0.02 to 0.07 px of the `board.py` truth, but 20 to 75 % of starts end
`unsnapped` (mostly `support` from `_detect_line` on the second line, the stem: too few sub-pixel ridge points
between the two double-ring wires, about 16 to 20 px at 1600 x 1200), and some converge 2 to 17 px away (inner
double wire crossing, or a wrong second line) on a wide-wire board and on an unseen style (yaw -30, pitch -10, roll
8, wire 1.2 mm). Tried without a clear gain: scale with the most points instead of the strongest response, a
narrower first-line band, thresholds from white noise only, unlimited stem reach, window 24 or 32, edges as a
fallback for the stem, every pixel above threshold instead of sub-pixel points. The profile refinement
(`_refine`, `_Profile`) is the accurate part; the detection (`_detect_line`, `_nearest_parallel`, `_away`) needs a
redesign: e.g. a coarse junction stage that gets within 1 to 2 px first, then the window centred on it.
