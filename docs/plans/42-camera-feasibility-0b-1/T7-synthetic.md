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
  entries, an optional person-sized occluder, an empty board after removal) and the matching annotations (T5).
- Added `tools/camera/tests/conftest.py` (fixtures `synthetic_session`, `synthetic_board`), `test_synth.py`.

## Depends on
T4, T5.

## Rules
CV1, CV2, CV5 (voided entries and a null photo in the generated sessions), CV10 and CV11 timing (entries and
`videoPositionMs` consistent with the video frames).

## Tests
- Same seed gives identical bytes; different seeds differ.
- Generated session reads with `truth.py` and its annotations validate with T5 against it.
- A dart placed at the centre of zone Z, mapped back with the true homography, scores Z for 20 random zones.
- Calibration pixels equal the homography applied to `CALIBRATION_POINTS_MM`.
- Video: frame count and duration match the spec; the dart appears at the planned frame (pixel difference in its box).

## Done when
`.venv/bin/python -m pytest tools/camera -q` green (generation fast enough: whole suite under 2 minutes),
`kosmo-detekt` exit 0.

## Model
sonnet. Why: test fixture generator from fixed geometry.

## Implementation notes
