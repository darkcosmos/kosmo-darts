# T15j: Diagnosis, video side and verdict: photo versus untouched phone, decision rule

## Goal
Measure whether the photo differs from the video frame of the untouched phone about 1 s before the entry (H2),
whether the board moves durably between darts (H3), and print the verdict of the decision rule written in advance
(D30 point 2 (b), CV28 video part, CV29).

## Files
- Changed `tools/camera/kosmo_camera/diagnose.py`:
  - Session mapping `M` (photo px to video px): least-squares similarity (scale, rotation, translation) over the
    clicked points of every dart that has both a complete photo control and a complete video control; median and
    p95 residual px reported; not usable when fewer than `MAP_MIN_PAIRS` (3) pairs or residual p95 above
    `MAP_MAX_RESIDUAL_PX` (2.0), reason shown.
  - `O` per dart with both controls (CV28); automatic `O` for other video darts only when the CV18 method,
    applied video frame against the session's first video control, passes the CV30 gate on the clicked video
    frames (else "méthode non validée sur la vidéo").
  - `V` (untouched frame versus the first video control of the same segment) and `W` (motion during the capture
    window: frames every `VIDEO_WINDOW_STEP_MS` from `entry` to `photo` `videoPositionMs`, registered against the
    CV27 frame; maximum and settling time) only with the validated video method.
  - Report sections "(b) Photo et vidéo", "Verdict" (CV29 lines and the "Suite proposée" row of root plan
    "Approach, Diagnosis decision rule").
- Changed `diagnostic_file.py`: optional per photo `videoMm` (`O`) and per session `mapping` residuals.
- Changed `defaults.json`, `config.py`, `test_config.py`: `VIDEO_WINDOW_STEP_MS` 100, `DIAG_MOVE_MM` 1.0,
  `DIAG_MIN_SHARE` 0.10, `DIAG_MIN_VIDEO` 10, `MAP_MIN_PAIRS` 3, `MAP_MAX_RESIDUAL_PX` 2.0.
- Changed `docs/rules/camera.md`: CV28 video part, adds CV29.
- Changed tests `test_diagnose.py`.

## Depends on
T15i, T15c (frames, `frame_at`).

## Rules
CV28, CV29, CV30, CV27, CV15. Every threshold is read from `config.py`; none is chosen from the data.

## Tests
- Synthetic session (T7 boards, generated video at a known scale and crop): photos equal to the frames: `O` about
  0, `M` recovered (scale, offset within 0.1 px residual); photos shifted by a random tilt on 30 % of darts: share
  of `O` above `DIAG_MOVE_MM` matches, H2 "oui"; a durable shift from dart 20 in the video and photos: H3 "oui",
  H2 "non".
- Mapping refused: 2 pairs; residual above the cap (a non-similar warp).
- Video method not validated (frames made unregistrable): automatic `O`, `V`, `W` reported "non mesurable".
- Verdict: each CV29 branch on scripted values, thresholds at the boundary (exactly `DIAG_MIN_SHARE`,
  exactly `VALIDATION_MIN_SHARE`), "non concluant" for `R` p95 above `CLICK_REPEAT_MAX_MM` and for 29 dev controls.
- Report privacy scan as T15i.

## Done when
Python checks of root "Test plan" green (CI command too), `kosmo-detekt` exit 0.

## Model
opus. Why: algorithm with an open design: the photo to video mapping, its validity test and the video
registration are not specified by any earlier rule and decide the H2 verdict.

## Implementation notes
