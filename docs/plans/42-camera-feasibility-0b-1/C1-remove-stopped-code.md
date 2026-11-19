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
  `test_previous_from_complete_goes_to_the_last_dart`, `test_goto_selects_a_judged_dart_with_no_calibration_needed` (renamed by C1c, T3; no
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
tests/test_annotate_server.py tests/test_annotate_static.py tests/test_requirements.py tests/test_strict_json.py` sous un Python 3.14 nu (stdlib seul pour ce dernier) ;
commentaire : outil d'annotation (saisie des pointes et visionneuse) sur le Python 3.14 de WSL. »

## Model and Why this model

opus: refactoring across modules (case 2): flow, server, views, frames input and entry point, the shared writer moved
out of `drift_file`, config and requirement files, with their tests and the CI step coupling. Estimate about 55 tool
calls, mostly deletions; past 60, stop after a green flow and server, commit, and report what is left to main.

## Implementation notes

Commits `d28b211` (writer moved), `7dd2596` (flow, server, views, `frames_state`, entry point), `2a7ede5` (snap, config
keys, tool requirements). Deviations: none; `test_goto_selects_a_judged_dart_only_after_calibration` kept its name at C1
(T3), renamed `test_goto_selects_a_judged_dart_with_no_calibration_needed` by C1c; `static/app.js` still holds the old screens (`ASSISTED_KINDS`, snap insets),
C1b rewrites them.

- Writer: `strict_json.replace_atomic` and `AtomicWriteError`; new tests in `test_strict_json.py`
  (`test_replace_atomic_replaces_the_file_and_leaves_no_temporary_file`, `..._keeps_the_old_file_when_the_replace_fails`,
  `..._in_a_missing_folder_is_an_atomic_write_error`, `test_import_closure_is_stdlib_only`).
- View contract: `views.PROGRESS_LABEL` = "{done} / {total} pointes saisies", `progressLabel` null when unread.
- Greps (head `2a7ede5`): `grep -rnE "import (snap|drift|drift_file|journey|snapping|control_state)|from \.(snap|drift|journey|snapping|control_state)" kosmo_camera tests`
  gives nothing. `calibrations` is read only by `control.py:56` (`sample`) among `split`, `frames_file`,
  `video_frames`, `export`, `control`. Keys of `defaults.json` unused outside `config.py`: `MATCH_RADIUS_MM`, `TAU_LOW`,
  `TAU_SURE`, `VALID_RATIO`, all present before C1 (evaluation keys of a later step, tested in `test_config.py`); no
  removed key is left.
- Python 3.14 run: fresh venv of `/usr/bin/python3` (3.14.4) with only `requirements-tool-test.txt`
  (`--require-hashes --only-binary=:all:`; `pip list` shows no numpy nor Pillow), then the four files of the owner text:
  208 passed.

Kept, `test_annotate_flow.py` (18, as listed in Tests) plus new: `test_the_removed_kinds_are_unknown_actions_and_change_nothing_n24`,
`test_a_new_session_without_segment_opens_on_its_first_dart_and_saves_tips_with_no_segment_n24`,
`test_every_action_keeps_the_segments_and_controls_of_the_file_cv20_cv25`.

Kept, `test_annotate_server.py`: every test of `0005f0c` from `test_jpeg_size_reads_the_start_of_frame_after_a_large_exif_segment`
through `test_help_works_with_the_stdlib_only` (42 kept, the S14 photo test removed), and the four frame and source tests of
Tests; new: `test_the_removed_options_exit_2`, `test_the_removed_actions_and_the_source_key_answer_400_and_leave_the_folder_byte_identical_n24`,
`test_a_tip_write_on_a_v3_file_keeps_its_segments_and_controls_cv20_cv25`,
`test_a_tips_only_file_reads_strictly_and_feeds_the_view_segment_for_and_control_sample_n24`,
`test_the_progress_label_counts_the_entered_tips_of_24_darts`,
`test_the_session_view_carries_the_photo_control_and_the_listed_frame_of_each_dart_cv25_cv27`,
`test_the_tool_starts_lists_and_saves_a_tip_with_numpy_and_pillow_blocked`.

Removed, flow (61): `test_a_click_that_does_not_snap_is_stored_unsnapped_with_its_reason_at_the_click_cv31`,
`test_a_new_segment_goes_in_order_between_existing_ones`, `test_a_proposal_that_does_not_snap_is_clicked_and_a_click_replaces_the_near_slot_not_the_empty_one_u27`,
`test_a_proposal_that_does_not_snap_stays_to_be_clicked_cv31`, `test_a_refused_click_keeps_the_selection_and_the_snapper_is_not_asked`,
`test_a_repeated_save_of_the_same_item_is_a_conflict_and_writes_one_segment_u29`, `test_a_save_that_names_the_open_calibration_item_writes_segment_0_once_u29`,
`test_a_saved_control_replaces_the_one_of_the_same_item_and_stays_sorted_photo_before_video_cv25`,
`test_an_accepted_click_consumes_the_selection_like_hide_u27`, `test_an_unreadable_image_leaves_the_proposals_to_click_and_remembers_the_reason_s14`,
`test_calibration_points_one_to_four_then_save_then_first_dart_cv2`, `test_calibration_screen_edits_segment_0_and_keeps_later_segments_cv20`,
`test_calibration_takes_the_real_jpeg_size_given_with_the_click`, `test_clear_in_calibration_removes_the_last_point_and_the_cursor_stays`,
`test_clear_of_segment_0_with_later_segments_is_refused_and_changes_nothing_cv20`, `test_clear_on_empty_calibration_changes_nothing`,
`test_clear_undoes_the_last_change_of_the_item_selection_included_u27`, `test_click_far_from_four_set_points_is_refused_in_calibration_u27`,
`test_click_on_a_full_recalibration_far_from_the_points_is_refused_u27`, `test_control_from_a_dart_cursor_opens_that_dart_when_it_is_sampled_and_open`,
`test_control_is_accepted_from_every_cursor_and_done_when_nothing_is_left_u15`, `test_control_opens_the_first_sampled_photo_with_the_segment_snapped_then_saves_and_proposes_the_next_cv31`,
`test_control_progress_counts_each_source_on_its_own_when_photos_and_video_are_partly_done_cv26`, `test_control_without_a_plan_is_off_and_nothing_changes_cv16`,
`test_every_slot_positioned_and_none_within_the_radius_is_refused_with_its_text_u27`, `test_first_video_item_has_no_proposal_four_clicks_then_save_stores_position_and_frame_size_cv25`,
`test_flow_saves_version_3_with_one_snapped_segment_from_dart_1_cv20_cv25`, `test_hidden_points_are_saved_as_null_with_a_null_snap_and_four_hidden_count_done_u18`,
`test_hide_is_refused_outside_the_control_screen`, `test_hide_takes_the_selected_slot_then_clears_the_selection_else_the_first_to_click_else_409_u25`,
`test_leaving_the_control_screen_drops_the_unsaved_points_and_the_file_is_unchanged_u16`, `test_leaving_with_unsaved_points_drops_them_and_changes_nothing`,
`test_marks_are_refused_with_a_conflict_on_the_calibration_screens_cv20_cv31`, `test_next_is_refused_to_leave_calibration_before_it_is_complete`,
`test_next_slot_is_what_a_far_click_does_u27`, `test_none_within_the_radius_fills_the_first_empty_slot_in_order_u27`,
`test_of_two_points_within_the_radius_the_nearer_wins_u27`, `test_only_the_clear_that_goes_back_to_the_previous_control_sets_the_reopened_flag_u28`,
`test_previous_from_the_first_dart_reopens_the_calibration_which_clear_then_redoes`, `test_recalibrate_from_a_dart_saves_the_segment_with_that_photo_and_its_snap_cv20_cv31`,
`test_recalibrate_needs_a_dart_cursor_and_the_first_dart_opens_segment_0`, `test_recalibrate_on_an_existing_start_shows_its_points_and_clear_removes_the_saved_segment_cv20`,
`test_recalibrate_proposes_the_segment_in_force_snapped_on_the_new_photo_cv31`, `test_resume_after_a_restart_opens_the_first_item_without_a_control_cv26`,
`test_resume_keeps_the_points_clicked_so_far`, `test_resume_starts_with_calibration_cv2`, `test_save_below_four_ready_points_is_a_conflict_and_the_file_is_unchanged_u29`,
`test_save_needs_the_size_of_the_photo`, `test_saving_again_replaces_the_segment_at_the_same_dart_cv20`, `test_select_is_refused_outside_the_calibration_screens`,
`test_select_refuses_a_point_that_is_not_1_to_4`, `test_the_control_cap_answers_409_and_the_file_is_unchanged_cv25`,
`test_the_diagram_stays_for_an_item_opened_without_proposals_and_never_for_one_with_proposals_u27`, `test_the_nearest_positioned_point_within_the_radius_is_replaced_u27`,
`test_the_save_at_the_segment_cap_is_a_conflict_and_changes_nothing_cv20`, `test_the_selected_point_takes_the_click_wherever_it_is_u27`,
`test_two_points_at_the_same_distance_give_the_lowest_index_u27`, `test_two_saves_for_the_same_item_write_one_control_and_the_second_is_409_u29`,
`test_undo_takes_back_the_last_change_then_the_previous_control_which_comes_back_as_proposals_u28`,
`test_without_a_snapper_clicks_saves_and_recalibration_are_refused_and_nothing_changes_a32`, `test_without_a_snapper_control_and_save_give_409_and_nothing_changes_a32`.

Removed, server (49): `test_s14_a_click_on_an_unreadable_photo_answers_200_unsnapped_unreadable_and_the_next_request_is_served`,
`test_the_cursor_says_can_save_and_sends_the_previous_points_of_a_recalibration_a8_a9`, `test_previous_points_are_sent_on_a_recalibration_cursor_only_not_on_a_control_cv31`,
`test_recalibration_from_a_dart_is_saved_with_its_photo_and_real_jpeg_size_cv20`, `test_clear_on_a_saved_recalibration_removes_it_at_once_cv20`,
`test_clear_of_the_calibration_screen_is_refused_while_later_segments_exist_cv20`, `test_the_save_at_the_segment_cap_answers_409_and_the_file_is_unchanged_cv20`,
`test_session_list_labels_come_from_the_drift_file_cv16`, `test_session_absent_from_the_drift_file_is_not_measured_and_unlisted_ones_are_ignored`,
`test_stale_flags_keep_the_suggested_darts_with_the_stale_mention_and_mark_each_dart_u7`, `test_the_views_carry_no_drift_field_nor_the_old_state_a7`,
`test_unreadable_oversized_or_missing_drift_file_warns_once_and_the_tool_serves_cv16`, `test_the_flag_file_is_read_again_at_every_list_and_never_written_cv16`,
`test_drift_option_is_passed_to_the_server`, `test_a_click_is_snapped_on_the_photo_of_the_session_and_the_view_shows_the_slots_cv31`,
`test_select_then_click_goes_to_that_slot_and_a_far_click_is_refused_u27`, `test_save_below_four_ready_points_answers_409_and_writes_nothing_u29`,
`test_a_repeated_save_answers_409_and_writes_one_segment_u29`, `test_hide_is_refused_with_409_on_the_calibration_screen_cv20`,
`test_the_saved_segment_keeps_the_snap_statuses_of_the_clicks_cv31`, `test_without_numpy_or_pillow_the_tool_serves_and_refuses_the_calibration_a32`,
`test_without_numpy_or_pillow_a_new_session_refuses_clicks_and_save_and_writes_nothing_a32`, `test_without_split_the_control_is_off_with_its_text_and_nothing_changes_cv16`,
`test_control_photos_then_video_are_saved_with_snap_size_and_position_cv25_cv26`, `test_a_repeated_save_answers_409_and_writes_one_control_u29`,
`test_a_proposal_that_does_not_snap_must_be_clicked_before_save_and_a_hide_follows_the_rule_cv31`, `test_leaving_the_control_screen_by_reopening_the_session_changes_nothing_u16`,
`test_resume_after_a_restart_opens_the_first_item_without_a_control_cv26`, `test_undo_with_no_change_reopens_the_previous_item_and_removes_its_control_u28`,
`test_counts_follow_the_sample_eval_all_judged_dev_sample_reference_photos_counted_cv26`, `test_a_refused_split_is_explained_in_french_with_a_generic_fallback_cv16`,
`test_a_split_that_does_not_match_its_lock_warns_in_the_list_and_switches_control_off_cv16`, `test_a_session_absent_from_the_split_has_no_control_and_is_still_annotated`,
`test_a_session_with_every_frame_missing_has_no_video_item_and_its_reasons_are_counted_s13`, `test_s14_a_click_on_an_unreadable_video_frame_is_unsnapped_unreadable_and_the_tool_keeps_serving`,
`test_without_numpy_or_pillow_control_is_refused_with_409_and_nothing_changes_a32`, `test_the_list_gives_the_roles_the_focus_session_and_its_instruction_q9_cv16`,
`test_the_list_sends_the_payload_of_the_continue_button_and_none_without_a_button_u15_u23`, `test_the_list_has_no_continue_payload_when_the_instruction_has_no_action_u35`,
`test_a_blocked_session_alone_gives_its_instruction_and_no_focus_u35`, `test_the_badge_values_are_pinned_for_the_list_page_t15g`,
`test_without_split_the_list_has_no_role_and_says_control_is_unavailable_u17`, `test_the_instruction_follows_every_save_with_no_cached_text_cv16`,
`test_drift_flags_never_change_the_instruction_and_stay_in_the_information_lines_cv30`, `test_the_view_carries_the_calibration_label_progress_and_badge_so_the_page_counts_nothing_a3`,
`test_a_session_with_nothing_left_has_the_terminee_badge_in_the_view_and_the_list_a3`, `test_the_undo_that_reopens_the_previous_control_answers_with_the_fixed_notice_and_no_other_action_does_u28`,
`test_an_eval_session_shorter_than_the_video_range_ends_with_no_missing_frames_message_cv26`, `test_planned_video_frames_absent_from_the_manifest_keep_the_missing_frames_message_cv27`.

Removed elsewhere: static `test_the_badge_values_of_the_page_are_the_server_constants_t3`,
`test_the_page_strings_that_the_server_also_knows_equal_the_python_constants_a6`,
`test_the_recalibration_explanation_is_the_server_text_in_the_side_panel_u19`,
`test_the_step_statuses_and_keys_of_the_page_are_the_server_constants_t3`; training doc
`test_k71b_is_one_command_that_creates_the_venv_installs_the_hashed_file_and_prints_the_pins`; Dockerfile
`test_header_documents_the_drift_run`; requirements `test_tool_requirements_hold_only_numpy_and_pillow_at_the_cpu_versions_with_two_hashes_each`,
`test_tool_requirements_name_the_four_wheels_and_the_install_command`; annotations the two `*_sha256` tests of Files
(and the last assert of the v2 migration test); config `test_candidate_list_is_read_as_a_tuple_of_floats`,
`test_candidate_list_must_be_a_non_empty_list_of_numbers`. Whole files deleted (names at `19d0d3b`): `test_snap.py`
(23), `test_drift.py` (49), `test_drift_file.py` (23, the `replace_atomic` cases moved to `test_strict_json.py`),
`test_annotate_snapping.py` (17), `test_annotate_journey.py` (20).

Proposed owner changes (French, for the PR): see "Done when", text unchanged.
