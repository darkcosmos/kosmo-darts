# T7: Synthetic boards and sessions for tests

## Goal
Generate, from a seed, dartboard images seen in perspective with darts at known board positions, and whole synthetic
sessions (truth file, photos, short videos, annotations) that every later test uses. No real image ever.

## Files
- Added `tools/camera/kosmo_camera/synth.py`: `render_board(view, darts, size, lighting, seed) -> (image, truth)`
  where `view` is a homography from board mm to pixels built from yaw, pitch, distance and roll; the board is drawn
  in board mm with `board.py` (sector colours, wires, numbers optional) then warped; each dart is a barrel and flight
  drawn from its tip; returns the tip pixels and the 4 calibration pixels (CV2).
  `make_session(dir, spec, seed)` writes `truth.json` v2 (a small writer local to `synth.py`, golden-compatible with
  `truth.py`), `dart-NNN.jpg`, `volley-NNN.mp4` (PyAV, mpeg4 codec, 10 fps, 640x360, a dart appearing between two
  entries, an optional person-sized occluder, an empty board after removal) and the matching annotations (format
  v2, T5b). The spec may give a per-dart camera drift (image translation in px and roll in degrees applied after
  the warp, from a dart number on) and the calibration segments to write; the truth view of every photo is returned
  so T8b compares measured and true drift. An optional occluder over one calibration corner on a chosen photo.
- Added `tools/camera/tests/conftest.py` (fixtures `synthetic_session`, `synthetic_board`), `test_synth.py`.

## Depends on
T4, T5b.

## Rules
CV1, CV2, CV20 (segments in generated annotations), CV5 (voided entries and a null photo in the generated sessions), CV10 and CV11 timing (entries and
`videoPositionMs` consistent with the video frames).

## Tests
- Same seed gives identical bytes; different seeds differ.
- Generated session reads with `truth.py` and its annotations validate with T5 against it.
- A dart placed at the centre of zone Z, mapped back with the true homography, scores Z for 20 random zones.
- Calibration pixels equal the homography applied to `CALIBRATION_POINTS_MM`.
- Video: frame count and duration match the spec; the dart appears at the planned frame (pixel difference in its box).
- Drift spec: calibration pixels of a drifted photo equal the drifted view applied to `CALIBRATION_POINTS_MM`; with
  no drift every photo has the same view; a session with two segments validates (T5b).

## Done when
Python checks of root "Test plan" green (generation fast enough: whole suite under 2 minutes),
`kosmo-detekt` exit 0.

## Model
sonnet. Why: test fixture generator from fixed geometry.

## Implementation notes
- Code in `tools/camera/kosmo_camera/synth.py`; fixtures in `tests/conftest.py` (`synthetic_session`: session-scoped, 2 volleys of 3,
  dart 2 voided, dart 5 without photo, segments (1, 4), drift from dart 4; `synthetic_board`: callable).
- No OpenCV in the environment: the warp is Pillow's perspective transform; the homography is built with numpy.
  Tips and calibration pixels come from `View.homography(size)`; `project` / `unproject` map board mm and pixels.
- `View` (yaw, pitch, distance, roll in degrees and mm; drift_dx, drift_dy, drift_roll applied after the warp);
  `Drift(from_dart, dx, dy, roll)` is absolute, the last drift with `from_dart <= n` applies (not cumulative).
- `SessionSpec(darts=(DartSpec(segment, multiplier, voided, photo), ...), segments=(1, 4), ...)`; darts are split in
  volleys of `darts_per_volley`; every dart sits at `zone_centre_mm`. Photo n shows the darts of its volley up to n.
- Timeline per volley (video ms): dart j arrives at 500+1000j (frame = ms/100), entry 300 ms later, photo entry+42,
  voidedAt entry+400, removal last entry+800 (`video_empty_from` = first empty frame), end = removal+700.
- `make_session` writes annotations to `<dir>/annotations/<name>.json` (not in the session folder). The first segment
  uses the first judged dart's photo, later ones the photo of their `fromDart` (CV20).
- Returned `SyntheticSession` holds per-dart true views, homographies, tips, calibration pixels, positions_mm,
  arrival frames and frame counts. A "Done when" note: `python3 -I -S` is not usable, the venv python runs the tests.
- Not done: no empty volley, no HIDDEN status; mp4 bytes are deterministic with thread_count 1 (tested).
