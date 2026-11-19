# C1b: Tool page for tip entry and read-only display of segments and controls

## Goal

Rebuild the annotation tool page on the view contract fixed by C1 (see `C1-remove-stopped-code.md`, `views.py`
bullet): the page enters tips as today and shows the rest read-only (root plan, "Screens and states", N24).

## Files

- `kosmo_camera/annotate/static/app.js`, `index.html`, `style.css`.
- `tests/test_annotate_static.py`.

## Depends on

C1.

## Screens

- List: one card per session with name, the server `progressLabel`, calibration label, message and warning; the
  frames warning on top. No journey, badge, focus card or « Continuer » button.
- Session: the dart photo with its tip; tip keys and buttons as today (click, H caché, O hors cible, U annuler, N/P,
  picker); zoom, pan and magnifier kept. Read-only overlays: the 4 segment points when the photo is that segment's
  photo (as today) and the dart's photo control. Key V (with its button « Image vidéo (V) ») toggles the dart's listed
  video frame from `/api/frame/...` with its video control. The page sends only the actions of `flow.KINDS`.
- Frame view (U13): a visible label « Image vidéo (lecture seule) » above the image, in the text style (not a colour
  tint), so the frame is never taken for the photo.
- Legend (U13), always visible beside the image, text plus shape, never colour alone: « + pointe », « □ 1 à 4 :
  points du segment (lecture seule) », « ○ C : contrôle (lecture seule) »; the segment points carry their number and
  the control points the letter C on the image. Legend text contrast >= 4.5 on the panel colour.
- Gone: assisted panel, slots, insets, diagram, Entrée save, keys R, K, 1 to 4, recalibration help, the
  « recalé » and « Recalage indisponible » texts, the step rows and the « Contrôle de calibration (K) » button.

## States (U12)

Every message is one line, in French, in the single message place, cleared at the next action.

| State | Text (French) | Effect on the page |
|---|---|---|
| Dart without listed frame (no `--frames`, or `frame` null) | « Pas d'image vidéo pour cette fléchette. » when V is pressed | V button disabled (`disabled`, still visible); photo stays |
| Dart without saved photo control | none | no C points; legend line « ○ C : contrôle — aucun pour cette fléchette » |
| All tips entered (cursor `complete`) | « Toutes les pointes sont saisies. Précédente (P) ou la liste pour corriger. » | opens on the last dart's photo; N/P and picker work; list card shows the server `progressLabel` (« 24 / 24 pointes saisies ») |
| Click or H, O, U on the frame view | « Image vidéo en lecture seule : V pour revenir à la photo. » | no request sent; frame stays shown |
| Frame fails to load | « Image vidéo illisible. Retour à la photo. » | photo shown again; V stays enabled |
| Photo fails to load | « Photo illisible : saisie impossible sur cette photo. » | clicks ignored; H, O, U, N/P still work |

## Rules

CV16 (stdlib page, no external resource, same origin only), D5 (French only), D8 (stored pixel order).

## Tests

Test names are those on `0005f0c`; the notes list each kept, adapted, removed and new test by name (T3).

- Kept: `test_files_exist_and_are_utf8`, `test_no_external_resource_no_forbidden_call`,
  `test_no_inline_handler_nor_inline_script_nor_style_in_html`, `test_fetch_goes_to_the_same_origin_only`,
  `test_each_action_button_label_ends_with_its_key_letter`, `test_the_key_handler_maps_the_same_letters_to_the_same_actions`,
  `test_every_visible_string_is_french_and_not_empty`, `test_html_has_no_hardcoded_text_outside_french_or_symbols`,
  `test_declared_text_colours_have_a_contrast_of_at_least_4_5`, `test_buttons_are_at_least_48px_high_and_wide`,
  `test_system_fonts_only`, `test_double_click_guard_and_view_reset_are_present`,
  `test_key_actions_match_the_flow_action_names_and_no_letter_is_used_twice`,
  `test_warning_colour_has_a_contrast_of_at_least_4_5_on_background_and_panel`,
  `test_a_message_has_one_place_and_is_cleared_at_the_next_action_u36`, `test_no_dérive_text_nor_drift_field_remains_in_the_page_cv30_a7`.
- Adapted: `test_the_page_shows_the_server_progress_badge_and_calibration_label_without_counting_a3` (reads
  `progressLabel`, no badge), `test_list_reads_the_sessions_object_and_the_drift_warning_and_uses_text_content_only`
  (frames warning), `test_video_frames_load_from_the_frame_route_and_skip_the_truth_size_check` (V toggle),
  `test_picker_label_is_the_number_ticked_when_annotated_without_any_drift_mark_a7`.
- New: V calls the frame route only when the dart's view has `frame`, else the button is disabled and the U12 text
  shows; each States row's text is a constant of the page used at its trigger; the frame view sets the label
  « Image vidéo (lecture seule) » and sends no request on click or H, O, U; the legend has the three text lines and
  its colour passes 4.5 on the panel; overlays read `segments`, `control` and `frame.control` from the view; no text
  « recalé », « Recalage », « Points prêts », « Continuer », « Contrôle de calibration » remains; no key R, K or 1 to 4.
- Counts (T5): the page shows `progressLabel` as given; the server side is proved by C1's view test, not by this scan.
- Removed with the assisted screen, journey and list focus: `..._recalibration_strings_and_no_drift_string_a7`,
  `test_old_points_are_drawn_as_hollow_crosses_labelled_ancien_k`, `test_the_recalibration_row_is_full_width_...`,
  `test_each_assisted_screen_string_is_a_constant_written_once`, `test_the_inset_zoom_is_60_source_px_...`,
  `test_the_obsolete_strings_are_gone_and_the_panel_is_400px_...`, `test_the_keys_of_the_assisted_screen_are_bound_...`,
  `test_entree_always_saves_...u25_u29`, `test_save_sends_the_open_item_and_the_control_source`,
  `test_every_assisted_button_blurs_after_its_click_u25`, `test_insets_are_drawn_from_the_loaded_image_..._u31`,
  `test_the_unreadable_and_unavailable_banners_and_the_ignored_clicks_are_wired` (replaced by the States tests),
  `test_the_page_builds_text_with_textcontent_only_on_the_assisted_screen`, `test_the_reopened_notice_..._a4`,
  `test_the_page_draws_the_next_slot_and_the_diagram_flag_of_the_server_a5`, `test_entree_with_fewer_than_four_points_says_why_u37`,
  `test_the_diagram_follows_the_action_buttons_..._u38_u39`, `test_the_step_row_templates_and_card_strings_are_defined_once`,
  `test_the_page_restates_no_instruction_of_the_server_u35`, `test_journey_help_and_banner_are_three_distinct_elements_..._u19`,
  `test_the_focus_card_carries_the_mark_..._u15`, `test_entree_on_the_list_runs_continuer_..._u23_d42`,
  `test_the_card_shows_information_apart_and_skips_nothing_else_cv30`, `..._is_an_argument_of_assisted_button_t1`,
  `test_the_save_time_is_stamped_only_by_a_save_action_t2`. C1 already removed `..._equal_the_python_constants_a6`,
  `test_the_badge_values_..._t3`, `test_the_step_statuses_and_keys_..._t3`, `test_the_recalibration_explanation_..._u19`.

## Done when

`python -m pytest -q -m "not slow"` in `tools/camera` green; `kosmo-detekt` exit 0; `git diff --check` clean.

## Model and Why this model

sonnet: a screen rebuilt from a fixed JSON contract with its static tests; no stored format, no cross-module change.

## Implementation notes

Deviations: none on behavior. Choices a reader needs: (1) the cursor `complete` carries no photo, so the page shows the
last dart of `view.darts` (display only; N/P and the picker work, click answers « Toutes les pointes sont saisies... »).
(2) One message place = the `#status` line; `#banner` keeps only the persistent server `warning` of the session; the old
`notice` is gone with the server (C1). The server warnings are shown as given, no « Attention : » prefix added.
(3) A dart photo whose size differs from the truth file blocks clicks only (like an unreadable photo), with its own
French text; H, O, U, N/P still work. (4) A listed frame whose file is missing is still listed by the view (the server
never checks the disk); the page meets it as « Image vidéo illisible. Retour à la photo. » (server test
`..._whose_file_is_missing_is_listed_and_its_route_answers_404_cv27_t1`). (5) U15: the V button is disabled without a
listed frame; it carries `title` and `aria-label` « Image vidéo (V) : Pas d'image vidéo pour cette fléchette. »; the
message itself shows only through the key V. (6) `drawCross`, hollow crosses, insets, diagram and the assisted panel are
gone; segment points are squares with their number (`#7fdbff`), control points circles with the letter C (`#ffa94d`).
The legend colour is `--legend-text` (#dddddd), checked at >= 4.5 on `--panel` and `--bg`.

Where: `static/app.js` (`shownDart`, `imageUrl`, `loadImage`, `toggleFrame`, `perform` with the frame guard `EDIT_ACTIONS`,
`renderLegend`, `drawStage`), `index.html` (`#stage-box` with `#frame-label`, `#legend`), `style.css`. Python 3.14 bare
venv (only `requirements-tool-test.txt`, no numpy nor Pillow), the four files `test_annotate_flow.py`,
`test_annotate_server.py`, `test_annotate_static.py`, `test_requirements.py`: 185 passed (208 before: the removed
assisted-screen static tests outnumber the new ones). Full run `tools/camera`, `-m "not slow"`: 881 passed.

`test_annotate_static.py`, final lists. Kept as on C1 (19): `test_files_exist_and_are_utf8`,
`test_no_external_resource_no_forbidden_call`, `test_no_inline_handler_nor_inline_script_nor_style_in_html`,
`test_fetch_goes_to_the_same_origin_only`, `test_each_action_button_label_ends_with_its_key_letter`,
`test_the_key_handler_maps_the_same_letters_to_the_same_actions`, `test_every_visible_string_is_french_and_not_empty`,
`test_html_has_no_hardcoded_text_outside_french_or_symbols`, `test_declared_text_colours_have_a_contrast_of_at_least_4_5`
(two pairs added for `--legend-text`), `test_buttons_are_at_least_48px_high_and_wide`, `test_system_fonts_only`,
`test_double_click_guard_and_view_reset_are_present`, `test_key_actions_match_the_flow_action_names_and_no_letter_is_used_twice`,
`test_warning_colour_has_a_contrast_of_at_least_4_5_on_background_and_panel`, `test_no_dérive_text_nor_drift_field_remains_in_the_page_cv30_a7`;
adapted, same name: `test_the_page_shows_the_server_progress_badge_and_calibration_label_without_counting_a3`,
`test_video_frames_load_from_the_frame_route_and_skip_the_truth_size_check`,
`test_picker_label_is_the_number_ticked_when_annotated_without_any_drift_mark_a7` (unchanged body),
`test_a_message_has_one_place_and_is_cleared_at_the_next_action_u36`.
Renamed (T3): `test_list_reads_the_sessions_object_and_the_drift_warning_and_uses_text_content_only` is now
`test_list_reads_the_sessions_object_and_the_frames_warning_and_uses_text_content_only`.
New: `test_v_calls_the_frame_route_only_when_the_dart_has_a_frame_else_the_button_is_disabled_and_says_why` (U15),
`test_each_states_row_text_is_a_constant_of_the_page_used_at_its_trigger_u12` (6 cases),
`test_the_frame_view_sets_the_label_and_sends_no_request_on_click_or_h_o_u_u13`,
`test_the_legend_has_the_three_text_lines_with_their_shapes_and_passes_4_5_on_the_panel_u13`,
`test_overlays_read_segments_control_and_frame_control_from_the_view`,
`test_no_recalibration_nor_assisted_text_remains_and_no_key_r_k_or_1_to_4`, `test_the_page_sends_only_the_actions_of_flow_kinds`,
`test_an_unreadable_photo_ignores_clicks_and_keeps_h_o_u_n_p_and_a_failed_frame_goes_back_to_the_photo`,
`test_when_all_tips_are_entered_the_page_opens_on_the_last_dart_and_n_p_and_the_picker_work`.
Removed, all names of the task's removed list (the `..._is_an_argument_of_assisted_button_t1` family included), nothing
more: `test_the_obsolete_strings_are_gone_and_the_panel_is_400px_on_the_assisted_screen`,
`test_the_save_time_is_stamped_only_by_a_save_action_t2`, `test_the_card_shows_information_apart_and_skips_nothing_else_cv30`,
`test_each_assisted_button_label_is_an_argument_of_assisted_button_t1`,
`test_each_assisted_screen_string_is_a_constant_written_once`, `test_entree_always_saves_with_the_repeat_and_save_guards_u25_u29`,
`test_entree_on_the_list_runs_continuer_only_when_no_button_or_link_has_the_focus_u23_d42`,
`test_entree_with_fewer_than_four_points_says_why_u37`, `test_every_assisted_button_blurs_after_its_click_u25`,
`test_insets_are_drawn_from_the_loaded_image_with_an_aria_label_u31`,
`test_journey_help_and_banner_are_three_distinct_elements_and_only_journey_has_the_instruction_style_u19`,
`test_old_points_are_drawn_as_hollow_crosses_labelled_ancien_k`, `test_save_sends_the_open_item_and_the_control_source`,
`test_string_table_has_the_recalibration_strings_and_no_drift_string_a7`,
`test_the_diagram_follows_the_action_buttons_and_marks_done_points_without_colour_alone_u38_u39`,
`test_the_focus_card_carries_the_mark_and_continuer_sends_the_server_payload_u15`,
`test_the_inset_zoom_is_60_source_px_shown_at_180_css_px_and_the_save_guard_is_300_ms`,
`test_the_keys_of_the_assisted_screen_are_bound_in_the_document_handler`,
`test_the_page_builds_text_with_textcontent_only_on_the_assisted_screen`,
`test_the_page_draws_the_next_slot_and_the_diagram_flag_of_the_server_a5`, `test_the_page_restates_no_instruction_of_the_server_u35`,
`test_the_recalibration_row_is_full_width_and_at_least_48px_high`,
`test_the_reopened_notice_comes_from_the_server_and_the_page_infers_no_flow_event_a4`,
`test_the_step_row_templates_and_card_strings_are_defined_once`,
`test_the_unreadable_and_unavailable_banners_and_the_ignored_clicks_are_wired` (replaced by the States tests).

`test_annotate_server.py` (T1): new `test_a_dart_without_a_usable_frame_has_frame_null_and_the_right_warning_cv27_t1`
(frame not listed: null and no warning; manifest missing: null everywhere and the fixed warning) and
`test_a_listed_frame_whose_file_is_missing_is_listed_and_its_route_answers_404_cv27_t1`. All other tests of the file kept.
JavaScript cannot run here (no node): the page is proved by these static tests only; Kosmo's check on the real page
stays the final proof.
