# T6: Local annotation tool

## Goal
A local web page, served by the Python standard library in WSL, where Kosmo clicks the 4 calibration points once per
session and the tip of each dart (decision 5).

## Files
- Added `tools/camera/kosmo_camera/annotate/__init__.py`, `__main__.py` (`python3.13 -m kosmo_camera.annotate
  --captures DIR --annotations DIR [--port 8765]`), `server.py` (`ThreadingHTTPServer` bound to `127.0.0.1`).
- Added `tools/camera/kosmo_camera/annotate/static/index.html`, `app.js`, `style.css` (French UI, no external URL).
- Added tests `tools/camera/tests/test_annotate_server.py`, `test_annotate_static.py`.

## Depends on
T5.

## Rules
CV16, CV2, CV5. Screens and states: root plan "Screens and states". Endpoints: `GET /` and `/static/<file>` (fixed
list), `GET /api/sessions`, `GET /api/sessions/<name>` (truth summary, split, annotations), `GET /photo/<name>/<file>`
(name must be a listed session, file must match `dart-\d{3,}\.jpg` and be named in its truth file), `PUT
/api/sessions/<name>/annotations` (JSON body up to 1 MiB, validated with T5, written with `write_atomic`). Headers:
CSP `default-src 'self'`, `X-Content-Type-Options: nosniff`, `Cache-Control: no-store`. Reject a `Host` other than
`127.0.0.1:<port>` or `localhost:<port>` and a `PUT` whose `Origin` differs (403). The captures folder is never
written. Imports: stdlib and `kosmo_camera.{truth,split,annotations,board}` only.

## Tests
- Server in a thread on port 0 with temp captures and annotations folders (fake JPEG bytes): session list, photo
  bytes, save then reload, invalid body 400 with nothing written, captures folder unchanged (hashes before and after).
- Path traversal (`..`, encoded `%2e%2e`, absolute path, file not in truth, other extension) gives 404.
- Bad `Host` and cross-origin `PUT` give 403; the socket is bound to `127.0.0.1`.
- `python3.13 -I -S -m kosmo_camera.annotate --help` works (stdlib only).
- Static files: no `http://` or `https://` URL, no `@import`, no inline event handler; every visible string French.

## Done when
`.venv/bin/python -m pytest tools/camera -q` green, `kosmo-detekt` exit 0. Notes record a manual run in the sandbox
(`curl` of `/api/sessions` on a synthetic folder).

## Model
sonnet. Why: small stdlib server and page with behavior and security rules specified.

## Implementation notes
