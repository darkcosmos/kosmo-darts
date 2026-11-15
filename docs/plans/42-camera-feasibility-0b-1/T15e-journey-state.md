# T15e: Tool, journey state: per-session progress and one active instruction (Python)

## Goal
Compute in Python what is done and what is left per session, and exactly one active instruction per screen, never
a stale one (D30 point 5), so the page only displays it.

## Files
- Added `tools/camera/kosmo_camera/annotate/journey.py` (stdlib): `session_state(loaded, control, drift, frames)`
  -> `SessionState(steps, next_step, info)`; `steps`: ordered rows `calibration`, `tips`, `control_photo`,
  `control_video`, each `done`, `total`, `status` (`todo`, `in_progress`, `done`, `unavailable` with a reason);
  `next_step`: the first row not done in that order, with its French instruction (root plan "Screens and states",
  table "Instructions"); `global_instruction(states)`: the next step of the first session, in list order, that has
  one, else "Tout est fait pour cette étape". `info`: lines that are information only (drift, stale files).
- Changed `annotate/server.py`: `/api/sessions` adds `instruction` (one string) and per session `steps` and
  `state` (`À faire`, `En cours`, `Terminée`, `Ignorée`); the session view adds `instruction` (the session's next
  step, or the screen's own instruction in calibration, recalibration and control screens).
- Drift flags (CV18 not validated, CV30): `drift.json` lines move to `info` as "Mesure de dérive non validée :
  information seulement" plus the existing label; never an instruction, never the "!" mark of a dart, the
  "Dérive au-delà du seuil" dart text is removed. A `frames.json` whose session entry is missing gives the
  `control_video` row `unavailable` "Images vidéo absentes : lancer l'extraction (K7.2)".
- Changed `docs/rules/camera.md`: CV16 sentence (one active instruction; drift flags informational).
- Changed tests `test_annotate_server.py`; added `test_annotate_journey.py`.

## Depends on
T15d.

## Rules
CV16, CV26, CV30. An instruction is derived from the current annotation file and the presence of frames only;
`drift.json` is information, never the active instruction. Rows are taken in the order of the root table
"Instructions"; an `unavailable` video row gives that table's "no frames" row, after every photo control.

## Tests
- Fresh session: each row of the root table "Instructions" in order: calibration, tips "Pointer les fléchettes :
  prochaine fléchette N", control photos, video (frames present, then absent); session done with others left,
  then everything done: `Terminée`.
- Global instruction: first session in list order with a step left; ignored sessions skipped.
- After the last control of a session is saved, its instruction changes at the next call (no cached text).
- Drift file with beyond darts: no instruction, no dart flag, one info line; stale drift file: same.
- No `--split`: control rows `unavailable` with the reason; no `--frames`: video row `unavailable`.
- Every instruction string comes from the table of root "Screens and states" (exact French text).

## Done when
Python checks of root "Test plan" green, `python3 -I -S -m kosmo_camera.annotate --help` works, `kosmo-detekt`
exit 0.

## Model
sonnet. Why: states and strings fully specified in the root plan; pure Python with tests.

## Implementation notes
