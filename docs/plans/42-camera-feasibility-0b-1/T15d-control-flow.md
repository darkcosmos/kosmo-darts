# T15d: Tool, control calibration mode (flow and server)

## Goal
Let Kosmo click the 4 calibration points of each sampled photo and video frame (CV25, CV26), saved at once,
resumable, undoable, with progress counts, in the Python flow and server so every rule is tested without a browser.

## Files
- Changed `tools/camera/kosmo_camera/annotate/flow.py`: cursor kind `CONTROL` with `number`, `source` (`photo` or
  `video`) and `pending`; `KINDS` gains `"control"`, `"hidden_point"`, `"control_next"`.
  - `control` (key K) is accepted from every cursor of a session, the "Terminé" screen (all tips done) included
    (U15): it opens the first sampled item of the session without a complete control (photos in dart order, then
    video frames in dart order), or the item of the current dart when it is sampled; with nothing left it answers
    the "Contrôle terminé" state, never an error.
  - `click` adds a pending point; `hidden_point` (key H) adds `null`; only the 4th item writes the control (real
    JPEG size, D8; `positionMs` from `frames.json` for a video frame), saves, moves to the next open item. The
    session view gives `pending` (0 to 3) so the page shows "Points posés : n / 4 (enregistrés au 4e point)" (U16).
  - `clear` (U) as T6c: removes the last item; on a saved control it removes the control from the file at once
    and keeps its first 3 items pending; with nothing pending, back to the previous item.
  - `next`, `previous`, `goto`, Escape: leave; pending items dropped, file unchanged (U16).
- No new write route for the list: its "Continuer" button (T15g) opens the session with the existing route, then
  sends the flow action carried by the session's `next_step` (T15e: `calibrate`, `goto N` or `control`), so it
  opens exactly the screen the instruction names (U15).
- Changed `annotate/server.py`, `annotate/__main__.py`: options `--split FILE` (with the committed lock
  `tools/camera/config/split-lock.json` by default, `--lock FILE`), `--frames DIR` (reads `DIR/frames.json` through
  `frames_file`, read-only). Without `--split` the control mode is off and the list says "Contrôle : indisponible
  (option --split absente)". Route `GET /api/frame/<session>/<file>` serves only files listed in `frames.json` for a
  listed session (same checks as the photo route). Session view adds `control`: counts done and total per source,
  the open item, `pending`, `zoomCentre` per point (previous control's point k, else the segment point k; never
  drawn, U), `frameMissing` reasons. Video items are only the darts with a frame in `frames.json`; a session listed
  with every frame missing has no video item (total 0) and its reasons are counted (S13).
- Changed `docs/rules/camera.md`: CV16 sentence on `--split`, `--frames` and the frame route.
- Changed tests `test_annotate_flow.py`, `test_annotate_server.py`; `annotate_support.py` (v3 fixtures).

## Depends on
T15b, T15c, T6c.

## Rules
CV25, CV26, CV16, D8. Control clicks never change tips, segments or `segments_sha256`.

## Tests
- K on a session: opens the first sampled photo; 4 clicks save a control and open the next; resume after restart
  opens the first item without a complete control.
- H twice and 2 clicks save 2 `null` points; 4 H save 4 `null` (counted done).
- U on a saved control removes it (file saved, 3 pending); U on 0 pending goes back.
- 2 points then leave (U16), once each by Escape, `next`, `previous` and `goto`: file byte-identical, `pending` 0
  after reopening; after 1, 2 and 3 points the file is unchanged and `pending` equals the count; the 4th writes.
- K from every cursor kind (darts, calibration, recalibration, "Terminé") opens the control item (U15); K with
  every control done gives the "Contrôle terminé" state.
- Session in `frames.json` with every frame missing: video total 0, reasons counted, K skips to nothing video.
- Video frame: control saved with `positionMs` and the frame size from `frames.json`; a frame absent from the
  manifest is never served; `../`, encoded `/`, NUL and an unlisted session refused on the frame route (as T6a).
- Counts: eval session all judged, dev session sample; reference photos counted; a tip change does not change them.
- No `--split`: control mode off, the rest of the tool unchanged; split not matching the lock: list warning, off.
- `MAX_CONTROLS` reached: 4th item answers 409 "Nombre maximal de contrôles atteint (1024)", file unchanged.

## Done when
Python checks of root "Test plan" green, `python3 -I -S -m kosmo_camera.annotate --help` works, `kosmo-detekt`
exit 0.

## Model
sonnet. Why: flow, undo and server rules specified, same patterns as T6c.

## Implementation notes
