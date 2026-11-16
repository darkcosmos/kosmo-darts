# T15e: Tool, journey state: per-session progress and one active instruction (Python)

## Goal
Compute in Python what is done and what is left per session, and exactly one active instruction per screen, never
a stale one (D30 point 5), so the page only displays it.

## Files
- Added `tools/camera/kosmo_camera/annotate/journey.py` (stdlib): `session_state(loaded, control, drift, frames)`
  -> `SessionState(steps, next_step, info)`; `steps`: ordered rows `calibration`, `tips`, `control_photo`,
  `control_video`, each `done`, `total`, `status` (`todo`, `in_progress`, `done`, `unavailable` with a reason);
  `next_step`: the first row not done in that order, with its French text, its flow `action` (`calibrate`, `goto
  N`, `control`, or none) and its hint per screen (root plan "Screens and states", table "Instructions": list hint
  names the "Continuer" button, session hint names the key); `global_instruction(states)`: the focus session (first
  in list order with a step left, ignored sessions skipped), its text and list hint; when no session has a step
  left, the table's final row. No default text claims completion while a row is `unavailable` (U17).
  `info`: lines that are information only (drift, stale files, missing frames reasons).
- Changed `annotate/server.py`: `/api/sessions` adds `instruction` (one string), `focus` (the focus session's name)
  and per session `steps` and `state` (`À faire`, `En cours`, `Terminée`, `Ignorée`); the session view adds
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
T15d.

## Rules
CV16, CV26, CV30. An instruction is derived from the current annotation file and the presence of frames only;
`drift.json` is information, never the active instruction. Rows are taken in the order of the root table
"Instructions"; an `unavailable` video row gives that table's "no frames" row, after every photo control.

## Tests
- Each row of the root table "Instructions" reached once, in order, on a scripted session: calibration, tips,
  control photos, video frames, no `--split`, no `--frames`, session done with others left, final row; `Terminée`.
- Global instruction: the focus session is the first in list order with a step left; ignored sessions skipped.
- After the last control of a session is saved, its instruction changes at the next call (no cached text).
- No `--split` or no `--frames` with everything else done: the "indisponible" row, never the final row (U17).
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
