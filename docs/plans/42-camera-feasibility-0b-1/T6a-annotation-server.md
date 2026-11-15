# T6a: Annotation server and per-dart flow

## Goal
The stdlib server of the annotation tool (decision 5) and the per-dart flow in Python, so that Kosmo annotates about
10 s per dart and every rule is tested without a browser. This file is the single description of the flow and states.

## Files
- Added in `tools/camera/kosmo_camera/annotate/`: `__init__.py`, `__main__.py` (`python3 -m kosmo_camera.annotate
  --captures DIR --annotations DIR [--port 8765]`), `flow.py` (pure state transitions), `jpeg_size.py` (stdlib SOF
  parser), `server.py` (`ThreadingHTTPServer` on `127.0.0.1`).
- Added tests `tools/camera/tests/test_annotate_flow.py`, `test_annotate_server.py`.

## Depends on
T5.

## Rules
CV2, CV5, CV16, D8. Flow (`flow.apply(annotations, session, action) -> (annotations, cursor)`):
- Order: calibration (points 1 to 4 on the first photo of the session) then judged darts by number (CV5); voided
  entries and null photos are skipped and listed as skipped. Opening a session sets the cursor on the first missing
  calibration point, else the first dart not annotated, else "complete".
- `click(x, y)` on calibration sets the next point; on a dart sets the tip (VISIBLE) and moves to the next dart. On an
  already annotated dart (reached with "Précédente" or from the list) a click replaces the tip and moves on.
- `hidden` (H) and `off_board` (O) set the status and move on. `clear` (U, "Effacer") removes the current dart's
  annotation or, in calibration, the last point; the cursor stays. `next` (N) and `previous` (P) only move.
- Every action that changes the file is saved at once through the single write route; the response carries the
  saved time shown as "Enregistré à HH:MM:SS", or the French error.
Server, one function `resolve_session(name)` used by every route: `name` must equal a name of the current session
list (exact string after URL decoding, else 404); file paths are built from the listed name only, never from input.
- Routes: `GET /`, `GET /static/<file>` (fixed list), `GET /api/sessions`, `GET /api/sessions/<name>`, `GET
  /photo/<name>/<file>` (file named in the truth file and matching `dart-\d{3,}\.jpg`), `POST
  /api/sessions/<name>/actions` (the only write route, `clear` included; no DELETE or PUT).
- Write route: `Content-Length` required and at most 64 KiB (411 or 413 before reading; chunked refused 411);
  `Content-Type: application/json` (415); `Origin` present and equal to `http://127.0.0.1:<port>` or
  `http://localhost:<port>` (403); `Host` checked on every route (403). Click coordinates checked against the real
  JPEG size read with `jpeg_size.py` (400 otherwise).
- Headers: CSP `default-src 'self'`, `X-Content-Type-Options: nosniff`, `Cache-Control: no-store`.
- Every handler catches errors and answers JSON 4xx/500; the thread never dies.
States with French messages, shown in the session list and the session screen: unreadable or invalid `truth.json`
(cause), annotation file rejected by the strict reader (cause; the session is read-only and the file never
overwritten), annotation file over 1 MiB, stale annotation (`truthSha256` differs: warning, editing allowed), no
calibration photo (no judged dart with a photo), nothing to annotate (every entry voided or without photo: the session ignored by the split, CV3; listed, never
hidden, with the label "Ignorée : aucune fléchette jugée" instead of a progress count, not openable), port in
use (message on the terminal and exit code 2), save error (nothing lost on screen, retry on the next action).

## Tests
- Flow: every transition above, including replace, clear in both modes, skip of voided and null-photo entries,
  resume cursor, complete state; autosave round trip (apply, write, reload gives the same file and cursor).
- Write route attacks, each answering 4xx and leaving nothing outside the annotations folder (directory listing of
  the temp root before and after): `..%2f..%2fx`, `%2e%2e`, a `/`, a backslash, `%00`, an unlisted name.
- Missing or oversized `Content-Length`, chunked body, wrong `Content-Type`, missing or foreign `Origin`, bad `Host`.
- Coordinates outside the real JPEG size (fixture JPEG headers of known size) refused.
- Each error state above reaches the session list with its message; a rejected annotation file is unchanged after a
  write attempt; captures folder unchanged (hashes); the socket is bound to `127.0.0.1`.
- Session with no judged dart (all voided, or all `photo: null`): present in the session list with the "Ignorée"
  label, no "0/0" progress, and its open and write routes answer 4xx without creating a file (CV3).
- `python3 -I -S -m kosmo_camera.annotate --help` works (stdlib only).

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0. Notes record a manual run (`curl` on a T4 golden
session copied to a temp folder).

## Model
sonnet. Why: flow, states and security rules specified, Python tests.

## Implementation notes
- Code in `tools/camera/kosmo_camera/annotate/`: `flow.py` (pure), `jpeg_size.py`, `server.py` (`SessionStore` holds every
  rule and state, the handler only routes and checks headers), `__main__.py`. Test helpers: `tests/annotate_support.py`.
- Flow details not in the task text: the format cannot hold a partial calibration, so points 1 to 3 live in
  `Cursor.pending`, kept in server memory (lost on restart, not a file change). "Précédente" on the first dart reopens
  the calibration (cursor `calibration`, four pending points); "Effacer" then removes the last point (calibration back
  to null in the file) and a click sets it again; a click with four points set is ignored. `goto` (list) needs a
  complete calibration. After a click or status the cursor goes to the next dart in order (annotated or not), after
  the last one to the first dart not annotated, else `complete`.
- "No calibration photo" and "nothing to annotate" are the same condition (no judged dart with a photo): one state
  `ignored`, label "Ignorée : aucune fléchette jugée", not openable (view without cursor), write answers 409.
- Other states: `truth_invalid` (also when the truth session differs from the folder name), `annotation_rejected`,
  `annotation_too_large`: all read-only (409 on write, file untouched); the stale warning keeps the stored `truthSha256`.
- D8: the click size is the real JPEG size of the cursor photo (`jpeg_size`); calibration width/height are that real
  size; `validate_against` then checks tips against the truth photo size (400 if the JPEG is larger than the truth says).
- Save error: the new annotations stay in memory (`SessionStore.unsaved`), the answer carries `error` (French) and the
  session view, and the next action is applied on top and written. Action answers: `{saved|error, session}`.
- `make_server` refuses an annotations folder inside the captures folder. Static files are served from
  `annotate/static/` (fixed list `index.html`, `app.js`, `style.css`, added by T6b; absent: 404 "Page absente").
- Manual run: golden-like session in a temp folder, server on port 18765, `curl` calibration 4 clicks and one tip
  gave `Enregistré à ...` and the file `<session>.json`; POST without `Origin` gave 403, `..%2f..%2fx` gave 404.
