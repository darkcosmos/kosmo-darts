# T15j: Diagnosis, video side and verdict: photo versus untouched phone, decision rule

CANCELLED (2026-11-18): the H1/H2 diagnosis is stopped (ADR 0020, root plan "Kept and stopped").

## Goal
Measure whether the photo differs from the video frame of the untouched phone about 1 s before the entry (H2),
whether the board moves durably between darts (H3), and print the verdict of the decision rule written in advance
(D30 point 2 (b), CV28 video part, CV29).

## Files
- Changed `tools/camera/kosmo_camera/diagnose.py`:
  - Session mapping `M` (photo px to video px): least-squares similarity (scale, rotation, translation) over the
    clicked points of every dart that has both a complete photo control and a complete video control, none of
    their points `unsnapped` (CV28); median and
    p95 residual px reported; not usable when fewer than `MAP_MIN_PAIRS` (3) pairs or residual p95 above
    `MAP_MAX_RESIDUAL_PX` (2.0), reason shown.
  - `O` per dart with both controls (CV28); automatic `O` for other video darts only when the CV18 method,
    applied video frame against the session's first video control, passes the CV30 gate on the clicked video
    frames (else "méthode non validée sur la vidéo").
  - `V` (untouched frame versus the first video control of the same segment) and `W` (motion during the capture
    window: frames every `VIDEO_WINDOW_STEP_MS` from `entry` to `photo` `videoPositionMs`, registered against the
    CV27 frame; maximum and settling time) only with the validated video method.
  - Report section « Limites » (T15i) gains the CV28 video limit (a constant tilt in every photo is absorbed by
    `M`). Report sections "(b) Photo et vidéo", "Verdict" (CV29 lines and the "Suite proposée" row of root plan
    "Approach, Diagnosis decision rule"); the two "non concluant" causes read `D` p95 and the complete snapped dev
    photo control count from T15i, never `R` (information since D39).
- Changed `diagnostic_file.py`: optional per photo `videoMm` (`O`) and per session `mapping` residuals.
- Changed `defaults.json`, `config.py`, `test_config.py`: `VIDEO_WINDOW_STEP_MS` 100, `DIAG_MOVE_MM` 1.0,
  `DIAG_MIN_SHARE` 0.10, `DIAG_MIN_VIDEO` 10, `MAP_MIN_PAIRS` 3, `MAP_MAX_RESIDUAL_PX` 2.0; each pinned once, in
  `test_config.py` only (T3).
- Changed `docs/rules/camera.md`: CV28 video part, adds CV29.
- Changed tests `test_diagnose.py`.

## Depends on
T15i, T15c (frames, `frame_at`).

## Rules
CV28, CV29, CV30, CV31, CV27, CV15. Every threshold is read from `config.py`; none is chosen from the data.

## Tests
- Synthetic session (T7 boards, generated video at a known scale and crop): photos equal to the frames: `O` about
  0, `M` recovered (scale, offset within 0.1 px residual); photos shifted by a random tilt on 30 % of darts: share
  of `O` above `DIAG_MOVE_MM` matches, H2 "oui"; a durable shift from dart 20 in the video and photos: H3 "oui",
  H2 "non".
- Mapping refused: 2 pairs; residual above the cap (a non-similar warp).
- Video method not validated (frames made unregistrable): automatic `O`, `V`, `W` reported "non mesurable".
- Verdict: each CV29 branch on scripted values, thresholds at the boundary (exactly `DIAG_MIN_SHARE`,
  exactly `VALIDATION_MIN_SHARE`).
- The two "non concluant" causes (CV29, Kosmo Q10, D39), each with its own French line and "Suite proposée":
  `D` p95 just above `SNAP_REPEAT_MAX_MM` gives "Non concluant : dispersion du recalage p95 X mm, au-delà de
  0,25 mm", exactly at it does not; an infinite p95 (failed restarts) gives the same line with "infinie"; 29
  complete snapped dev photo controls give "Non concluant : N contrôles photo dev complets et recalés, il en faut
  30", 30 do not; 30 complete dev controls of which 2 have an `unsnapped` point count 28 and give that line; both
  causes at once print both lines and nothing else.
- `R` is information: scripted `R` values from 0 to 10 mm never change any verdict line.
- `O` and `M` use only controls without an `unsnapped` point (CV28).
- Combinations (T1, the CV29 lines are independent): H1 with "photos déplacées" (both lines, "Suite proposée" =
  T16a used as per-photo calibration); H2 and H3 together; H1, H2 and H3 together; "non concluant" with values
  that would also give H1 and H2: only "non concluant" is printed and no other line.
- The T15i K5 regression session gives the H1 line.
- Report privacy scan as T15i.

## Done when
Python checks of root "Test plan" green (CI command too), `kosmo-detekt` exit 0.

## Model
opus. Why: algorithm with an open design: the photo to video mapping, its validity test and the video
registration are not specified by any earlier rule and decide the H2 verdict.

## Implementation notes
