# T15j: Diagnosis, video side: movement at the tap tracked inside the video stream (`O`, `V`, `W`)

## Goal
Measure whether the phone moves at the tap (H2) inside the video stream at constant framing, whether the board
moves durably between darts (H3), and print the verdict of the decision rule written in advance (D30 point 2 (b),
D50, CV28 video part, CV29). A photo is never compared with a video frame: their framing and resolution differ
(photo 4:3, video 16:9 cropped, ADR 0019).

## Files
- Changed `tools/camera/kosmo_camera/diagnose.py` (container, PyAV):
  - Tracking (D50, root "Approach, H2 inside the video stream"): for each dart with a video control, start = its
    visible snapped points on the CV27 frame A (about 1 s before `entry`; unsnapped points start from the click,
    flagged); frames decoded from A to frame B, the last frame at or before `photo.videoPositionMs`; each point
    tracked frame to frame by local patch registration (numpy, sub-pixel), then re-snapped on B from its tracked
    position. Validation per point: forward-backward return to A within `TRACK_MAX_RETURN_PX`, and tracked versus
    re-snapped on B within `TRACK_MAX_SNAP_PX`; a failed point is dropped with its reason, counted.
  - `O` per dart from 2 or more validated points: board-plane move of the visible points between A and B in mm,
    through the control homography with 4 points, else through the scale given by the known board distances of the
    visible calibration points (translation with 2 points, similarity with 3 or 4). Fewer than 2: « non mesurable »
    with reason (`hors_cadre` for points marked hidden because out of frame, `suivi` for tracking failures).
  - `W` = the largest move along the tracked path (maximum and settling time); `V` = move of frame A of dart k
    against the first video control of its segment, both snapped controls, same rule for 2 to 4 points.
  - Report section « Limites » (T15i) gains the video limits of CV28; report section "(b) Mouvement dans la vidéo"
    (darts with `O`, points out of frame, tracking failures by reason, return and re-snap residuals). The
    "Verdict" section is T15o.
- Changed `diagnostic_file.py`: optional per photo `videoMm` (`O`), `videoPoints` (validated count) and
  `videoReason`.
- Changed `defaults.json`, `config.py`, `test_config.py`: `DIAG_MOVE_MM` 1.0, `TRACK_PATCH_PX`, `TRACK_MAX_RETURN_PX`, `TRACK_MAX_SNAP_PX` (values chosen on synthetic
  videos, pinned once in `test_config.py`, T3); `MAP_MIN_PAIRS`, `MAP_MAX_RESIDUAL_PX`, `VIDEO_WINDOW_STEP_MS` dropped.
- Changed `docs/rules/camera.md`: CV28 video part (as in root "Rules").
- Changed tests `test_diagnose.py`.

## Depends on
T15i, T15c (frames, `frame_at`), T17c (snapped video controls; every re-snap on B uses the video `snapParams` of
`snapped-points.json`, D55).

## Rules
CV28, CV31, CV32, CV27, CV15, D50. Every threshold is read from `config.py`; none is chosen from the data.

## Tests
- Chosen scale (D56): a synthetic wide-wire video (T17b video style, wires widened) re-snaps on B with the video
  `snapParams` scale read from the file, and fails `TRACK_MAX_SNAP_PX` with scale 1.
- Synthetic session (T7 ring-wire boards, generated 16:9 video cropped top and bottom): no move between A and B:
  `O` about 0; the board moved by a planned amount on 30 % of darts between A and B: `O` within 0.1 mm of the
  plan, share above `DIAG_MOVE_MM` matches; a durable shift from dart 20 in the video: `V` above it from dart 20,
  `O` about 0.
- Points out of frame: 2 visible points give `O` (translation), 3 a similarity, 1 « non mesurable » `hors_cadre`;
  counts per reason.
- Tracking: a frame sequence with a fast blur or a person crossing the point fails the forward-backward return
  and is dropped with `suivi`; a tracked point that re-snaps on a neighbouring crossing fails `TRACK_MAX_SNAP_PX`.
- Frame B at or before frame A (photo stamp not after the CV27 target): `O` = 0 from the single frame, no division
  by zero; a missing or undecodable frame between A and B: dart « non mesurable » `video_unreadable`, counted.
- Synthetic videos are deterministic: fixed seeds, encoder settings fixed in the test (codec, CRF, GOP, pixel format).
- A photo is never read by the H2 code (static check: no photo path in the tracking functions).
- A start point stored `unsnapped` is tracked from the click and flagged; `O` from it is counted apart (CV28).
- Report privacy scan as T15i. The CV29 verdict and its tests are T15o.

## Done when
Python checks of root "Test plan" green (CI command too), `kosmo-detekt` exit 0.

## Model
opus. Why: case 3, the in-video tracking (D50), its validation and the mm scale from 2 or 3 visible points are not
specified by any earlier rule and decide the H2 verdict.

## Implementation notes
