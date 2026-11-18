# T15e: Tool, journey state: per-session progress and one active instruction (Python)

## Goal
Compute in Python what is done and what is left per session, and exactly one active instruction per screen, never
a stale one (D30 point 5), so the page only displays it.

## Files
- Added `tools/camera/kosmo_camera/annotate/journey.py` (stdlib): `session_state(loaded, control, drift, frames,
  snap_available)`
  -> `SessionState(steps, next_step, info)`; `steps`: ordered rows `calibration`, `tips`, `control_photo`,
  `control_video`, each `done`, `total`, `status` (`todo`, `in_progress`, `done`, `unavailable` with a reason);
  `next_step`: the first row not done in that order, with its French text, its flow `action` (`calibrate`, `goto
  N`, `control`, or none) and its hint per screen (root plan "Screens and states", table "Instructions": list hint
  names the "Continuer" button, session hint names the key); `global_instruction(states)`: the focus session (first
  in list order with a step left, ignored sessions skipped), its text and list hint; when no session has a step
  left, the table's final row. No default text claims completion while a row is `unavailable` (U17).
  `info`: lines that are information only (drift, stale files, missing frames reasons).
  Snapping unavailable (`Snapper.available` false, T15n; D43): when the matching row's action is `calibrate` or
  `control`, the table row "Recalage indisponible : relancer l'outil avec ~/kosmo-tool-venv/bin/python (K7.1b)"
  replaces it (no action, no hint); a `goto N` tips row is unchanged.
- Changed `annotate/server.py`: `/api/sessions` adds `instruction` (one string), `focus` (the focus session's name)
  and per session `steps`, `state` (`À faire`, `En cours`, `Terminée`, `Ignorée`) and `role` (`dev 1`, `eval 2`,
  from `control.roles` when `--split` is given, absent otherwise; Kosmo, Q9, D38; never a set name inferred from
  the session name); the session view adds
  `instruction` (the session's next step with its session hint, or the screen's own title in calibration,
  recalibration and control screens).
- Drift flags (CV18 not validated, CV30): `drift.json` lines move to `info` as "Mesure de dérive non validée :
  information seulement" plus the existing label; never an instruction, never the "!" mark of a dart, the
  "Dérive au-delà du seuil" dart text is removed. No `--split`: control rows `unavailable` (table row "Contrôle
  indisponible"). No `--frames`, or a `frames.json` without the session: `control_video` row `unavailable` (table
  row "Images vidéo absentes"). A session listed with every frame missing: `control_video` `done` with total 0 and
  an info line "Aucune image vidéo utilisable pour cette session (N manquantes)" (S13).
- Changed `docs/rules/camera.md`: CV16 sentence (one active instruction; drift flags informational).
- Changed tests `test_annotate_server.py`; added `test_annotate_journey.py`.

## Depends on
T15d (and through it T15n's `Snapper.available`, T15c's `control.roles`).

## Rules
CV16, CV26, CV30, CV31. An instruction is derived from the current annotation file and the presence of frames only;
`drift.json` is information, never the active instruction. Rows are taken in the order of the root table
"Instructions"; an `unavailable` video row gives that table's "no frames" row, after every photo control.

## Tests
- Each row of the root table "Instructions" reached once, in order, on a scripted session: calibration, tips,
  control photos, video frames, no `--split`, no `--frames`, session done with others left, final row; `Terminée`.
- Global instruction: the focus session is the first in list order with a step left; ignored sessions skipped.
- After the last control of a session is saved, its instruction changes at the next call (no cached text).
- No `--split` or no `--frames` with everything else done: the "indisponible" row, never the final row (U17).
- Snapping unavailable: the calibration row and each control row are replaced by "Recalage indisponible ..." with
  no action; the tips row is unchanged; never the final row while a calibration or control is left; the same states
  with snapping available give the normal rows (D43).
- Role (Q9, D38): with `--split` each listed session carries its role in CV3 key order (`dev 1`, `dev 2`,
  `eval 1`, `eval 2`), an ignored session none; without `--split` no `role` field.
- Session with every frame missing: video row done with total 0, info line, the next row is reached (S13).
- Drift never instructs (A30): for every journey state above crossed with every drift input (none, valid with
  beyond darts, stale, invalid, oversized), `instruction`, `next_step` and every session view `instruction` are
  identical to the no-drift case; drift text appears only in `info`; no response field read by the list banner or
  the dart screen banner contains "Dérive" (the batch 1 page showed "Dérive au-delà du seuil" there).
- Hints name what is bound (U15): for every state, the list hint names "Continuer" (bound on the focus card by
  T15g) and never a key; the session hint names K only for control rows, and `control` is accepted from that
  state (checked through `flow.py`), the "Terminé" cursor included; the `action` of each row, applied to the flow,
  opens the screen whose title matches the row.
- Every instruction string comes from the table of root "Screens and states" (exact French text).

## Done when
Python checks of root "Test plan" green, `python3 -I -S -m kosmo_camera.annotate --help` works, `kosmo-detekt`
exit 0.

## Model
sonnet. Why: states and strings fully specified in the root plan; pure Python with tests.

## Implementation notes
Done (sonnet). Where: `annotate/journey.py` (rows, `session_state`, `global_instruction`, `session_instruction`, the
JSON field builders), `server.py` (`session_list` replaces `sessions_with_warning`; `_states`; `view` adds journey
fields and `instruction`), tests `test_annotate_journey.py` (pure) and the end of `test_annotate_server.py`.
- Deviations: (1) the French badge is the field `badge`, not `state`: `state` is still the load state (`ok`,
  `ignored`...) read by the current page; T15g renames or reads `badge`. (2) `session_state(loaded, control_info,
  drift, snap_available)` has no `frames` argument: `ControlInfo` already carries `frames_present` and the missing
  reasons. (3) An unreadable session (`truth_invalid`, rejected, too large) has `blocked`: never the focus, its message
  is its only info line, and while one exists with nothing else left `instruction` is null (never the final row, U17).
  Product question left to Kosmo if he wants a text there.
- Session `instruction` is the screen's title on the calibration ("Calibration : point k sur 4"), recalibration and
  control screens; else the next step with its key hint; else `Session terminée...` or the final row. The list
  instruction ends with `(bouton Continuer)` when its step has an action. `next_step` fields (`session_fields` and
  `next_step_fields`) are helpers for T15g; the list answer has `focus` and `instruction` at its root.
- `driftLabel`, `driftStale` and the per-dart `drift` field are unchanged for the current page; drift lines also go to
  `info` ("Mesure de dérive non validée : information seulement" then the label). T15g removes the page's drift
  banner and mark ("Dérive au-delà du seuil") and may then drop these fields.
- `test_annotate_journey.py` is not in `TOOL_TESTS` (stdlib only, runs in the normal suite).
- Carry-over minors done in their own commits: CV16 control-mode doc test (fails when the line is altered, checked by
  hand), French split warning (`control_state.split_reason`, generic fallback), mixed `control_progress` test,
  `control.Role(set_name, number)` and `control_info` parameter names. Earlier minors found already done: reasons
  `residual`, `no_convergence`, `too_far` are each provoked and pinned in `test_snap.py`, plain-sector and single-wire
  tests pin `CONTRAST`, `test_video_frames.py` uses seeded bytes.
