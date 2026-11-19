# C1: Remove the stopped code; the tool server keeps tip entry and becomes a viewer for the rest

## Goal

Q1 B (root plan N23, N24): delete the code stopped by ADR 0020 ("Kept and stopped") and the entry of calibrations and
controls. The tool server keeps the session list, photos, frames listed in `frames.json` and the entry of dart tips;
segments and controls are only sent for display. The page is C1b, the prose and rules are C1c.

## Files (buckets checked on `0005f0c`)

- Deleted: `kosmo_camera/snap.py`, `drift.py`, `drift_file.py`, `annotate/snapping.py`, `annotate/journey.py`,
  `requirements-tool.txt`; `tests/snap_support.py`, `test_snap.py`, `test_drift.py`, `test_drift_file.py`,
  `test_annotate_snapping.py`, `test_annotate_journey.py`.
- `strict_json.py`: receives `replace_atomic` and its error (name without "drift") from `drift_file.py`; callers
  `frames_file.py:212`, `video_frames.py:124`; its tests move to `test_strict_json.py` (drop `drift_file` lines 23, 34).
- `annotate/flow.py`: `KINDS` = click, hidden, off_board, clear, next, previous, goto; cursor kinds `DART`, `COMPLETE`;
  `resume` opens the first dart without annotation; `goto` needs no segment. Deleted: calibration, recalibration and
  control kinds, `Slot`, `ControlPlan`, select, save, recalibrate, control, hidden_point, `click_target` and their texts.
- `annotate/server.py`: the one write route `POST /api/sessions/<name>/actions` keeps every header, Host, Origin, size
  and session check; `_ACTION_KEYS` = action, x, y, number; `_BoundSnapping`, `read_drift`, journey and control info go.
- `annotate/control_state.py` renamed `frames_state.py`: only the `frames.json` reading (listed frames, fixed warning)
  for `GET /api/frame/...`, kept as is; split, lock, roles and sample go (`control.py` stays: `video_frames.py` uses it).
- `annotate/views.py`, the contract C1b relies on: list `{sessions: [{name, message, warning, label, progress,
  calibrationLabel}], framesWarning}`; session adds `readOnly, photoSize, skipped, segments: [{fromDart, photo, points}],
  cursor: {kind, number, photo}, darts: [{number, photo, value, volley, status, tip, control, frame}]` where `control`
  is the photo control points or null and `frame` is `{file, control}` for a frame listed in `frames.json`, else null.
- `annotate/__main__.py`: `--drift`, `--split`, `--lock` and the snapping epilog removed; `--frames`, `--port` kept.
- `config/defaults.json`, `config.py`, `test_config.py`: `SNAP_*`, `DRIFT_*`, `CLICK_REPLACE_RADIUS_PX` removed (the
  tuple type and its two tests too if no key uses it).
- `annotations.py` (A8): readers, v1/v2 migrations, `encode`, `write_atomic`, `segment_for`, `control_for` stay (tip
  entry and views); `segments_sha256`, `controls_sha256` and their tests go if no kept module calls them (grep in notes).
- Kept as they are: `luma.py` and `test_luma.py` (R needs it), `synth.py` (R unit tests), `control.py`, `split.py`,
  `frames_file.py`, `video_frames.py`, `jpeg_size.py`, `requirements-tool-test.txt` (pytest for CI 3.14; header only).
- Kept true, minimal edits: `test_requirements.py` (tool-file tests 65, 75 go), `test_dockerfile.py:58` and the
  `Dockerfile` drift header, `test_training_doc.py` (K7.1b test, snapping and journey texts go; K7.3 parses without
  `--split`) with the matching `training.md` command lines only; `test_annotate_static.py` 348, 394, 402, 429 go with
  the constants they compare (deleted journey and assisted texts), and `static/app.js` `KEY_ACTIONS` loses `r` and `k`
  so that test 162 still holds against the new `flow.KINDS`; the rest of the page is C1b.

## Depends on

P0 approved (done, N23).

## Rules

CV16 (tool), CV20 and CV25 (segments and controls read, kept on write), CV27 (frames), CV18, CV19, CV31 (code gone).

## Tests

- Kept, adapted where they start on the calibration screen: `test_annotate_flow.py` 68, 102-152, 203, 224-270, 284, 291, 336 (plan,
  tips, statuses, replace, clear, moves, goto, click bounds and size, unknown action, resume, autosave round trip);
  `test_annotate_server.py` 98-621 except 265 (jpeg size, loopback, headers, photo route, path attacks, Host, Origin,
  Content-Type and body checks, read-only rejected, oversized and stale files, v1 byte-identical, v1 to v3 rewrite,
  save retry, JSON 500, rescan, folder checks, exit codes, stdlib help), 1176, 1187, 1201 (frames), 1230 (keys).
- Removed with their code: flow 75-100, 164-195, 215, 278, 310-332, 358-970; server 265, 654-1162, 1206,
  1237-1443.
- New: each removed kind (recalibrate, select, save, control, hidden_point) and a `source` key answer 400 and leave
  the annotations folder byte-identical (`tree_listing`); a tip write on a v3 file keeps its segments and controls
  equal on re-read (CV20, CV25); a new session without segment opens on its first dart and saves tips with no
  segment (N24); the session view carries each dart's photo control and listed frame with its video control, null
  when absent; `--drift`, `--split`, `--lock` exit 2; the tool starts from a bare `python3` with numpy and PIL
  blocked (`stdlib_pythons` subprocess), lists sessions and accepts a tip.
- `frames_file` and `video_frames` atomic-write tests pass through `strict_json.replace_atomic`; `test_luma.py` runs.
- Notes give the grep proving no import of a deleted module and no unread key in `defaults.json`.

## Done when

`python -m pytest -q -m "not slow"` in `tools/camera` green; `kosmo-detekt` exit 0; `git diff --check` clean. The
hand-back gives main this "Proposed owner changes" text (the squad cannot edit `.github/**`): « Job `camera-tools` de
`.github/workflows/android.yml` : les deux étapes Python 3.14 sont désormais sautées, car
`tools/camera/requirements-tool.txt` est supprimé (plus de numpy ni de Pillow pour l'outil). Proposition : condition
`hashFiles('tools/camera/requirements-tool-test.txt') != ''`, installation de `-r requirements-tool-test.txt` seul
(pytest, empreintes exigées, wheels seules), puis `pytest -q -m "not slow" tests/test_annotate_flow.py
tests/test_annotate_server.py tests/test_annotate_static.py tests/test_requirements.py` sous un Python 3.14 nu ;
commentaire : outil d'annotation (saisie des pointes et visionneuse) sur le Python 3.14 de WSL. »

## Model and Why this model

opus: refactoring across modules (case 2): flow, server, views, frames input and entry point, the shared writer moved
out of `drift_file`, config and requirement files, with their tests and the CI step coupling. Estimate about 55 tool
calls, mostly deletions; past 60, stop after a green flow and server, commit, and report what is left to main.

## Implementation notes
