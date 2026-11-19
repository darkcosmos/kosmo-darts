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
