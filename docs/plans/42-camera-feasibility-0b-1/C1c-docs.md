# C1c: Documents and rules after the removal of the stopped code

## Goal

Bring the rules, ADRs and Kosmo's guides in line with the code left by C1 and C1b (Q1 B, root plan N23, N24): the tool
enters tips and shows the rest read-only; snapping, drift, calibration and control entry are gone.

## Files

- `docs/rules/camera.md`: status note (line 9: "frozen until task C1" becomes "removed by C1 to C1c"); CV16 rewritten
  from the code (stdlib only, `127.0.0.1`, captures read-only, atomic writes in the annotations folder, tip entry only,
  a tip write keeps segments and controls, `--frames` and the frame route; the drift, "After D39 and D43", control mode
  and journey sentences removed); CV20 and CV25: one line "no longer entered in the tool (C1, Q1 B); read and kept";
  CV18, CV19, CV31 keep their "Stopped" line plus "code removed by C1".
- `docs/adr/0016-camera-feasibility-tooling.md`: decision 2 stdlib core without `drift_file` and without the "Amended"
  pointer; "Tripod drift" and "Assisted click" status lines say "code removed by task C1" instead of "frozen".
- `docs/camera/training.md`: "Annotate" section (python3 command from C1, no calibration first, tip keys from C1b, V
  for the video frame); "Contrôle de calibration": K7.1b removed, K7.3 and K7.C texts match the tool, stop line kept.
- `tools/camera/README.md`: tool description and command.
- `tools/camera/tests/test_plan_docs.py` (ADR 0016 statuses, the CV16 sentence tests replaced by the new CV16
  sentences), `tools/camera/tests/test_training_doc.py` (expected texts taken from the Python constants left by C1).

## Depends on

C1b.

## Rules

CV16, CV18, CV19, CV20, CV25, CV31 (text only, no rule number reused, N20).

## Tests

- Doc tests pin the new CV16 sentences, the two ADR 0016 status lines and the core list; `training.md` names no
  removed option (`--drift`, `--split`, `--lock`), no `kosmo-tool-venv`, no « Recalage » text; every command in it
  still parses with the real parser.
- `grep -rn "snap.py\|drift.py\|requirements-tool.txt" docs tools/camera --include=*.md` lists only history (done or
  cancelled task files, ADR status lines); the result goes in the notes.

## Done when

`python -m pytest -q -m "not slow"` in `tools/camera` green; `kosmo-detekt` exit 0; `git diff --check` clean.

## Model and Why this model

haiku: documentation copied from the code and the task files of C1 and C1b, no design choice.

## Implementation notes

Changed: `docs/rules/camera.md` (CV16 rewritten from the code; CV18, CV19, CV31 "Code removed by C1."; CV20 and CV25
"No longer entered in the tool (C1, Q1 B); read and kept."; status note), `docs/adr/0016-camera-feasibility-tooling.md`
(decision 2 without `drift_file` and without the "Amended" pointer; the two status lines say "code removed by task C1"),
`docs/camera/training.md`, `tools/camera/README.md` (one command block), `tools/camera/tests/test_plan_docs.py`,
`tools/camera/tests/test_training_doc.py`, `tools/camera/tests/test_annotate_flow.py` (T3 rename), and the C1 task file
(T3 rename in Tests and notes; T2 owner text lists `tests/test_strict_json.py`, stdlib only).

Deviations from the Files list, all for the points of the reviews:
- K1 (split) block removed from `training.md`: the step is done, the split is committed in
  `tools/camera/config/split-lock.json`, and its only command printed `--lock` (U16).
- Section renamed `## Visionneuse (K7)` (was "Contrôle de calibration"); K7.1b (venv) removed; the K7.3 command is
  `python3 -m kosmo_camera.annotate ... --frames ~/kosmo-runs/frames`; K7.C is the 2-minute visual check (T1 of C1b).
- K7.0 switches to `docs/camera-registration-strategy`, the branch of this mission (was the old feat branch). Main must
  confirm the branch name once the PR is open.
- K7.2 keeps `kosmo_camera.video_frames --split /runs/split.json`: `--split` is an option of that module, not of the
  annotation tool (the removed options of the tool are `--split`, `--lock`, `--drift`, exit 2).
- Test T3 of C1: `test_goto_selects_a_judged_dart_only_after_calibration` renamed
  `test_goto_selects_a_judged_dart_with_no_calibration_needed` (only reference: `test_annotate_flow.py` and the C1 file).
- Architecture point: the only doc-test string citing a deleted module was the ADR 0016 core list with `drift_file`
  and the CV16 "After D39 and D43" sentence with `snap.py`; both replaced, no doc test cites a deleted module now.

Tests (new or changed): `test_plan_docs.py`: `test_camera_rules_mark_the_stopped_rules_and_their_removed_code` (x3),
`test_adr_0016_decision_2_has_no_amended_pointer_and_keeps_the_assisted_click_section_as_history`,
`test_adr_0016_decision_2_lists_the_stdlib_core_without_drift_file`, `test_cv16_says_the_tool_is_stdlib_local_and_enters_the_tips_only`,
`test_cv16_says_calibration_control_snapping_drift_and_journey_are_not_in_the_tool`,
`test_cv16_says_the_frame_route_serves_only_a_listed_file_of_a_listed_session`, `test_cv16_says_the_removed_options_exit_with_status_2`,
`test_cv20_and_cv25_are_read_and_kept_and_no_longer_entered_in_the_tool`; the two ADR 0016 status constants updated.
`test_training_doc.py`: `test_the_k73_annotate_command_is_started_by_python3_and_parses_with_the_annotate_parser` (renamed),
`test_the_section_names_the_steps_the_expected_texts_and_retires_k5_and_k7` (no K7.1b step, new branch name, no venv, no
« Recalage »), `test_every_command_names_a_parser_that_accepts_it_and_no_removed_option[training.md|README.md]` (every
command of the guide and the README with its real parser, `--split`, `--lock`, `--drift` refused for the tool; `gpu_check`
has no parser and is skipped; `docker build` names no module), `test_the_visual_checklist_quotes_the_page_strings_word_for_word_and_names_the_removed_keys`
(each checklist string is in `static/app.js` and in the guide).

Greps (`grep -rn "snap.py\|drift.py\|requirements-tool.txt" docs tools/camera --include=*.md`): nothing left in the guide,
the rules or the tool README. Remaining hits are history only: done or cancelled task files (T8b, T9, T15h, T15m, T15n,
T16a, T15i), C1 (deletion list), ADR 0016 "Assisted click" and "Tripod drift" sections, ADR 0018 and 0020 (stopped code),
root plan lines 33, 131-132 (stopped list).

Checks, head at C1c start `ff5c33c`:
- `.venv/bin/python -m pytest -q -m "not slow"` in `tools/camera` (Python 3.13.18): 886 passed, 4 deselected.
- Python 3.14 bare venv (`/usr/bin/python3` 3.14.4, only `requirements-tool-test.txt`, no numpy nor Pillow), the owner's
  five files `test_annotate_flow`, `test_annotate_server`, `test_annotate_static`, `test_requirements`, `test_strict_json`:
  194 passed; `test_plan_docs.py`: 61 passed. `test_training_doc.py` is not in that list: its video-frames tests import
  `kosmo_camera.video_frames`, which needs PyAV, so they fail with `ModuleNotFoundError` in a bare venv (as the pre-existing
  video-frames test already did).
- `kosmo-detekt`: exit 0 ("detekt passed").
- `git diff --check`: clean.
