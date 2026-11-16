# T15c: Control sample rule, session roles, video frames and their manifest

## Goal
Decide deterministically which photos and video frames Kosmo control-clicks (CV26), name sessions by role, and
extract the video frame of each sampled dart about 1 s before its entry (CV27), so the tool can show it.

## Files
- Added `tools/camera/kosmo_camera/control.py` (stdlib only): `roles(split)` -> session name to `dev 1`, `dev 2`,
  `eval 1`, `eval 2`... (ascending CV3 unit key per set, CV24 extended to eval); `sample(session, annotations,
  set_name) -> Sample(photo_darts, video_darts)` by the CV26 rule; `read_split(split_path, lock_path)` through
  `split.decode` and `split.check_lock` (refuses a mismatch with the CV3 message).
- Added `tools/camera/kosmo_camera/frames_file.py` (stdlib only): strict reader and atomic writer of
  `frames.json` (`format` `"kosmo-camera-frames"`, `version` 1, `beforeEntryMs`, `sessions`: `session`,
  `frames` [`dart`, `positionMs`, `width`, `height`, `file`, `sha256`], `missing` [`dart`, `reason`]); cap
  `MAX_BYTES` = 4 MiB, same strictness as `drift_file.py`.
- Added `tools/camera/kosmo_camera/video_frames.py` (PyAV, Pillow): `frame_at(video_path, target_ms)` -> last
  decoded frame whose time is at or before `target_ms`, its exact `positionMs` (floored), RGB array in stored
  order (rotation metadata ignored, D8); `main()` for `python -m kosmo_camera.video_frames --captures DIR
  --annotations DIR --split FILE --out DIR`: for the video darts of every session writes
  `<out>/<session>/video-NNN.jpg` (Pillow, quality 95) and `<out>/frames.json`; prints counts only (frames
  written, missing per reason, the line "Images vidéo absentes : N" when N > 0).
- Bounded work on Kosmo's videos (S13): `frame_at` stops after `VIDEO_MAX_DECODED_FRAMES` decoded frames per call
  and refuses a video file above `VIDEO_MAX_BYTES`; either gives `video_unreadable`. Any PyAV or Pillow error while
  opening, seeking or decoding (`av.error.FFmpegError`, `OSError`, `ValueError`, `MemoryError`) is caught per dart
  and becomes `video_unreadable`; the run always goes on with the next dart and session and ends with exit 0
  (non-zero only for a bad argument, split or lock).
- Changed `tools/camera/config/defaults.json`, `config.py`, `test_config.py`: `CONTROL_DEV_STRIDE` 10,
  `CONTROL_EVAL_VIDEO_FIRST` 55, `CONTROL_EVAL_VIDEO_LAST` 60, `VIDEO_BEFORE_ENTRY_MS` 1000,
  `VIDEO_MAX_DECODED_FRAMES` 20000 (11 min at 30 fps, far above a volley), `VIDEO_MAX_BYTES` 512 MiB; each pinned
  once in `test_config.py`.
- Changed `docs/rules/camera.md`: adds CV26, CV27; CV15 sentence on `frames.json`.
- Added tests `test_control.py`, `test_frames_file.py`, `test_video_frames.py` (videos generated with PyAV).

## Depends on
T15b (v3 model), T4b (split reader and lock).

## Rules
CV26, CV27, CV15, CV3, D8. Missing-frame reasons: `no_position`, `before_start`, `too_close`, `past_end`,
`video_missing`, `video_unreadable`, `orientation_mismatch` (frame and photo orientations differ: never rotated).

## Tests
- Sample: eval session: every judged dart; dev session of 25 judged darts: ranks 1, 11, 21 and the last (25);
  segment reference photos added (a segment from dart 14 adds 14); voided and photo-less entries never sampled;
  video darts: the dev photo darts, and in eval darts 55 to 60 that are judged. Same output twice (deterministic).
- Roles: 2 dev and 2 eval sessions named in an order different from their keys get roles by key order.
- `frame_at` on a generated 10 fps video: target between two frames picks the earlier, its `positionMs`; each
  missing reason on purpose-built inputs; `too_close` when the target precedes the previous entry's photo stamp.
- `frames.json` strict reader (unknown key, cap and cap + 1, bad file name); output folder holds only listed files.
- `main()` in a subprocess on a T7 session with a generated video: files and manifest written, counts printed,
  no session name printed.
- Bounds and corrupt input (S13): a truncated video and a file of random bytes give `video_unreadable` for their
  darts while the next session's frames are still written, exit 0; `VIDEO_MAX_DECODED_FRAMES` patched to 5 on a
  20-frame video gives `video_unreadable`; a session where every frame is missing is listed with only `missing`.

## Done when
Python checks of root "Test plan" green (CI command too), `python3 -I -S -c "import kosmo_camera.control,
kosmo_camera.frames_file"` from `tools/camera` works, `kosmo-detekt` exit 0.

## Model
sonnet. Why: sample rule, frame choice and reasons fully specified in CV26 and CV27; derived, regenerable file.

## Implementation notes
