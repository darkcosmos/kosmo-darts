# C1: Remove the stopped code (snapping, assisted click, drift); the annotation tool becomes a read-only viewer

Not started before Kosmo answers Q1 (root plan, "Open questions"). Written for option A; option B keeps the tip
annotation routes and screens of the tool (main amends this Goal before launch); option C cancels this task.

## Goal

Delete the code of the work stopped by ADR 0020 (root plan, "Kept and stopped") so that only kept code is tested:
`snap.py`, the assisted click of the tool, `drift.py`, the drift flags and the `drift.json` reader. The tool keeps
listing sessions and showing photos, video frames, tips, calibration segments and controls, and writes nothing.

## Files

- Deleted: `kosmo_camera/snap.py`, `kosmo_camera/drift.py`, `kosmo_camera/annotate/snapping.py`,
  `tests/snap_support.py`, `tests/test_snap.py`, `tests/test_drift.py`, `tests/test_annotate_snapping.py`,
  `requirements-tool.txt`, `requirements-tool-test.txt`.
- `kosmo_camera/drift_file.py`: `replace_atomic` and its error move to `strict_json.py` (used by `frames_file.py` and
  `video_frames.py`); the rest of the module and `tests/test_drift_file.py` are deleted.
- Tool: `annotate/server.py`, `flow.py`, `journey.py`, `views.py`, `control_state.py`, `__main__.py`, `static/*`: the
  write routes (tips, calibrate, recalibrate, control, undo), `--drift`, the drift flags and the assisted-click states
  are removed; the page shows « Visionneuse : lecture seule » and no edit key. Their tests are cut to match.
- `config/defaults.json`, `config.py`, `tests/test_config.py`: `SNAP_*`, `DRIFT_*`, `CLICK_REPLACE_RADIUS_PX` removed.
- `synth.py`: ring-wire boards stay only if a kept test uses them. `luma.py` stays (R needs the safe decoder) with
  `tests/test_luma.py`. `annotations.py`: the readers and migrations stay (truth for R); the v3 writer and its tests
  are deleted when no kept module calls it (`grep` in the hand-back), else the caller is named in the notes.
- Docs: `docs/rules/camera.md` (CV16 viewer rule, CV18, CV19, CV31 deleted with a one-line "removed by C1" each),
  `docs/camera/training.md` (K7.1b and the K7.3 assisted-click text removed, the tool command uses `python3`),
  `docs/adr/0016-camera-feasibility-tooling.md` decision 2 line (stdlib core restored), `test_plan_docs.py`,
  `test_training_doc.py`, `test_requirements.py`, `test_dockerfile.py`.

## Depends on

P0 approved by Kosmo, Q1 answered A.

## Rules

CV15, CV16 (rewritten: viewer, stdlib only, `127.0.0.1`, read-only), CV18, CV19, CV31 (deleted), CV25 (unchanged).

## Tests

- Every write route of the tool answers 405 and leaves the annotations folder byte-identical (one test per route).
- The tool starts with a bare `python3` with numpy and Pillow absent (subprocess with an isolated `sys.path`).
- The page and the journey show no « recalé », « Recalage indisponible » or drift text (static and journey tests).
- `frames_file` and `video_frames` atomic-write tests still pass through `strict_json.replace_atomic`.
- No remaining import of the deleted modules (`grep` in the hand-back) and no orphan constant in `defaults.json`.
- `test_luma.py` still runs and passes once `test_snap.py` and `test_annotate_snapping.py` are gone.
- The hand-back carries the owner-change text replacing the CI Python 3.14 step (checked by main against the
  workflow, which the squad cannot edit; no repository test can read a PR body).

## Done when

`python -m pytest -q -m "not slow"` in `tools/camera` green; `kosmo-detekt` exit 0; `git diff --check` clean. The
hand-back gives main the "Proposed owner changes" text: the CI Python 3.14 step (now skipped, no `requirements-tool.txt`)
runs the tool tests with a bare Python 3.14 instead.

## Model and Why this model

opus: refactoring across modules (case 2): tool server, flow, journey and page, shared writer moved out of
`drift_file`, config, requirement files, rules and docs, with the CI step coupling. Mostly deletions; if the estimate
exceeds about 60 tool calls, the developer stops after the code and tests and main gives the docs to a `haiku` C1b.

## Implementation notes
