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
- `annotate/views.py`, the contract C1b relies on: list `{sessions: [{name, message, warning, label, progressLabel,
  calibrationLabel}], framesWarning}` where `progressLabel` is the server string « 12 / 24 pointes saisies » (T5); session adds `readOnly, photoSize, skipped, segments: [{fromDart, photo, points}],
  cursor: {kind, number, photo}, darts: [{number, photo, value, volley, status, tip, control, frame}]` where `control`
  is the photo control points or null and `frame` is `{file, control}` for a frame listed in `frames.json`, else null.
- `annotate/__main__.py`: `--drift`, `--split`, `--lock` and the snapping epilog removed; `--frames`, `--port` kept.
- `config/defaults.json`, `config.py`, `test_config.py`: `SNAP_*`, `DRIFT_*`, `CLICK_REPLACE_RADIUS_PX` removed (the
  tuple type and its two tests too if no key uses it).
- `annotations.py` (A8): readers, v1/v2 migrations, `encode`, `write_atomic`, `segment_for`, `control_for` stay (tip
  entry and views). Decision (T2): `segments_sha256` and `controls_sha256` are removed, their only callers being
  `drift.py`, `drift_file.py` and `views.drift_view` (deleted); in `test_annotations.py` the tests
  `test_segments_sha256_hashes_the_encoded_calibration_list_without_snap_cv20_cv25` and
  `test_controls_sha256_follows_controls_and_snap_only_and_segments_sha256_ignores_controls_cv25` go, and in
  `test_v2_file_written_by_the_batch_1_tool_migrates_without_loss_cv25` only its last line (the `segments_sha256`
  assert) goes; every other test of the file stays as is.
- Kept as they are: `luma.py` and `test_luma.py` (R needs it), `synth.py` (R unit tests), `control.py`, `split.py`,
  `frames_file.py`, `video_frames.py`, `jpeg_size.py`, `requirements-tool-test.txt` (pytest for CI 3.14; header only).
- Kept true, minimal edits: `test_requirements.py` (tool-file tests 65, 75 go), `test_dockerfile.py:58` and the
  `Dockerfile` drift header, `test_training_doc.py` (K7.1b test, snapping and journey texts go; K7.3 parses without
  `--split`) with the matching `training.md` command lines only; `test_annotate_static.py` 348, 394, 402, 429 go with
  the constants they compare (deleted journey and assisted texts), and `static/app.js` `KEY_ACTIONS` loses `r` and `k`
  so that test 162 still holds against the new `flow.KINDS`; the rest of the page is C1b.

## Depends on

P0 approved (done, N23). Between C1 and C1b the committed page still draws the old screens that the server no longer
serves: the tool page is not usable between the two tasks (A9). C1, C1b and C1c ship as one PR, the unit Kosmo
verifies; the PR opens only after C1c.

## Rules

CV16 (tool), CV20 and CV25 (segments and controls read, kept on write), CV27 (frames), CV18, CV19, CV31 (code gone).

## Tests

Tests are named as on `0005f0c` (T3; a `...` name is a unique substring); the notes repeat the final list by name.

- Kept in `test_annotate_flow.py`, adapted where they start on the calibration screen:
  `test_plan_lists_judged_darts_and_skipped_ones_cv5`, `test_click_on_a_dart_sets_the_tip_visible_and_moves_on`,
  `test_hidden_and_off_board_set_the_status_and_move_on`, `test_replace_a_tip_on_an_annotated_dart_and_move_on`,
  `test_replace_a_status_by_a_tip_and_a_tip_by_a_status`, `test_after_the_last_dart_the_cursor_goes_to_the_first_open_dart_then_complete`,
  `test_clear_removes_the_annotation_of_the_current_dart_and_the_cursor_stays`, `test_next_and_previous_only_move`,
  `test_previous_from_complete_goes_to_the_last_dart`, `test_goto_selects_a_judged_dart_only_after_calibration` (no
  segment needed), `test_click_outside_the_photo_or_not_a_number_is_refused`, `test_click_on_the_photo_border_is_accepted`,
  `test_click_without_a_known_photo_size_is_refused`, `test_unknown_action_is_refused`,
  `test_resume_cursor_on_the_first_dart_not_annotated_then_complete`, `test_session_without_judged_dart_has_no_flow`,
  `test_autosave_round_trip_apply_write_reload_gives_the_same_file_and_cursor`, `test_truth_dict_helper_gives_a_readable_truth`.
- Kept in `test_annotate_server.py`: every test from the `jpeg_size` tests through `test_help_works_with_the_stdlib_only`
  except `test_s14_a_click_on_an_unreadable_photo_answers_200_unsnapped_...` (removed), and the frame tests
  `test_a_corrupt_frame_manifest_warns_and_leaves_the_photo_controls_s13` (adapted: warning only),
  `test_frame_route_serves_only_listed_files_of_listed_sessions_s13`, `test_frame_route_without_the_frames_option_is_a_404_s13`,
  `test_the_source_of_an_action_is_checked_and_unknown_keys_are_refused` (adapted: `source` is now unknown). The
  rename (T4) updates every `control_state` import and monkeypatch of this file to `frames_state`.
- Removed with their code: in flow, the calibration, recalibration, select, save, control, hidden-point, snap, diagram
  and next-slot tests; in server, the recalibration, drift, snapping, without-numpy refusal, control, journey, badge
  and instruction tests. The notes list each removed test by name, so no kept test can disappear unseen.
- New: each removed kind (recalibrate, select, save, control, hidden_point) and a `source` key answer 400 and leave
  the annotations folder byte-identical (`tree_listing`); a tip write on a v3 file keeps its segments and controls
  equal on re-read (CV20, CV25); a new session without segment opens on its first dart and saves tips with no
  segment (N24), and `test_a_tips_only_file_reads_strictly_and_feeds_the_view_segment_for_and_control_sample_n24`
  (T1) reads that file back with `annotations.read`, builds the session view (`segments: []`), calls `segment_for`
  (None) and `control.sample` (the only downstream reader of `calibrations`; `split`, `frames_file`, `video_frames`
  and `export` never read them, grep in notes) without raising; the list view gives `progressLabel` as a string
  (« 0 / 24 pointes saisies », then « 24 / 24 ... » once all tips are in, T5); the session view carries each dart's photo control and listed frame with its video control, null
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
