# T15d: Tool, control calibration mode (flow and server)

## Goal
Let Kosmo confirm or click the 4 snapped calibration points of each sampled photo and video frame (CV25, CV26,
CV31), saved by Entrée,
resumable, undoable, with progress counts, in the Python flow and server so every rule is tested without a browser.

## Files
- Changed `tools/camera/kosmo_camera/annotate/flow.py`: cursor kind `CONTROL` with `number`, `source` (`photo` or
  `video`) and its slots; `KINDS` gains `"control"`, `"hidden_point"`, `"select"`, `"save"` (shared with T15n).
  - `control` (key K) is accepted from every cursor of a session, the "Terminé" screen (all tips done) included
    (U15): it opens the first sampled item of the session without a complete control (photos in dart order, then
    video frames in dart order), or the item of the current dart when it is sampled; with nothing left it answers
    the "Contrôle terminé" state, never an error.
  - The item opens with 4 slots from the T15n assisted flow: proposals = the previous item's saved points of the
    same session and source, else the segment of that dart for the first photo item, none for the first video
    item, each snapped on the new image (root "Screens and states", items "Assisted point screen" and "Control
    screen"; CV31, D39). `click` goes to the slot of `flow.click_target` (T15n, CV31 click rule, U27), snapped
    at once, or is refused with 409 and its text; `select k`; `hidden_point` (H) follows the CV31 hide rule (U25):
    the selected slot, then the selection is cleared; else the first slot `à cliquer`; else 409 « Choisir le point
    à cacher : touche 1 à 4, puis H »; `save` (Entrée, carrying `dart` and `source`) writes the control with its
    `snap` (real JPEG size, D8; `positionMs` from `frames.json` for a video frame) only when the 4 slots are
    snapped, clicked or hidden (else 409 « Points prêts : n / 4 »; 409 « Photo déjà enregistrée » when that item is
    no longer the open one, U29), then opens the next open item with its proposals. A frame or photo that
    `luma.decode_luma` cannot read: slots `à cliquer`, a click stays `unsnapped` with reason `unreadable`, banner
    text « Photo illisible : le recalage est impossible pour cette photo » (S14). The view gives the slots (point,
    status, reason) and `ready` (0 to 4) for "Points prêts : n / 4 (Entrée pour enregistrer)" (U16).
  - `clear` (U): undoes the last slot change of the item; with no change, back to the previous item, whose saved
    control is removed from the file at once and shown again as proposals.
  - `next`, `previous`, `goto`, Escape: leave; pending slots dropped, file unchanged (U16).
  - Snapper unavailable (T15n): `control` and `save` refused with 409 "Recalage indisponible".
- No new write route for the list: its "Continuer" button (T15g) opens the session with the existing route, then
  sends the flow action carried by the session's `next_step` (T15e: `calibrate`, `goto N` or `control`), so it
  opens exactly the screen the instruction names (U15).
- Changed `annotate/server.py`, `annotate/__main__.py`: options `--split FILE` (with the committed lock
  `tools/camera/config/split-lock.json` by default, `--lock FILE`), `--frames DIR` (reads `DIR/frames.json` through
  `frames_file`, read-only). Without `--split` the control mode is off and the list says "Contrôle : indisponible
  (option --split absente)". Route `GET /api/frame/<session>/<file>` serves only files listed in `frames.json` for a
  listed session (same checks as the photo route). Session view adds `control`: counts done and total per source,
  the open item, its slots and `ready`, `frameMissing` reasons. Snapping reads the frame through the T15n cache. Video items are only the darts with a frame in `frames.json`; a session listed
  with every frame missing has no video item (total 0) and its reasons are counted (S13).
- Changed `docs/rules/camera.md`: CV16 sentence on `--split`, `--frames` and the frame route.
- Changed tests `test_annotate_flow.py`, `test_annotate_server.py`; `annotate_support.py` (v3 fixtures).

## Depends on
T15b, T15c, T15n (assisted flow and `Snapper`, which builds on T6c).

## Rules
CV25, CV26, CV31, CV16, D8, S14, D45 (U25, U27, U29). Control clicks never change tips, segments or `segments_sha256`.

## Tests
All flow tests use a fake `Snapper` (T15n) scripted per point; one test uses the real `snap.py` on a T7 board.
- K on a session: opens the first sampled photo with the segment as proposals; Entrée saves the control with
  `snap` from the snapper and opens the next item with the saved points as proposals; resume after restart opens
  the first item without a complete control.
- Proposal that does not snap: slot "à cliquer", Entrée refused until clicked or hidden; a click 2 px from slot 3
  replaces slot 3, not the nearest empty slot; a click that does not snap is stored `"unsnapped"`.
- H with a selection hides it and clears the selection; H with no selection and no slot `à cliquer`: 409, slots
  unchanged; two `save` for the same item: the second 409 « Photo déjà enregistrée », one control written (U25,
  U29); an unreadable frame (truncated JPEG): click stored `unsnapped` `unreadable`, next request served (S14).
- First video item: no proposal; 4 clicks then Entrée. H twice and 2 clicks save 2 `null` points with `snap`
  `null`; 4 H save 4 `null` (counted done).
- U undoes the last slot change; U with no change goes back and removes the previous control (file saved, its
  points shown as proposals).
- Changes then leave (U16), once each by Escape, `next`, `previous` and `goto`: file byte-identical; nothing is
  written before Entrée.
- Snapper unavailable: K and `save` give 409 "Recalage indisponible", tips and file unchanged.
- K from every cursor kind (darts, calibration, recalibration, "Terminé") opens the control item (U15); K with
  every control done gives the "Contrôle terminé" state.
- Session in `frames.json` with every frame missing: video total 0, reasons counted, K skips to nothing video.
- Video frame: control saved with `positionMs` and the frame size from `frames.json`; a frame absent from the
  manifest is never served; `../`, encoded `/`, NUL and an unlisted session refused on the frame route (as T6a).
- Counts: eval session all judged, dev session sample; reference photos counted; a tip change does not change them.
- No `--split`: control mode off, the rest of the tool unchanged; split not matching the lock: list warning, off.
- `MAX_CONTROLS` reached: Entrée answers 409 "Nombre maximal de contrôles atteint (1024)", file unchanged.

## Done when
Python checks of root "Test plan" green, `python3 -I -S -m kosmo_camera.annotate --help` works, `kosmo-detekt`
exit 0.

## Model
sonnet. Why: flow, undo and server rules specified, same patterns as T6c.

## Implementation notes
