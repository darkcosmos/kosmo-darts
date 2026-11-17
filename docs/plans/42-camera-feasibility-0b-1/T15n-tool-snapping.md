# T15n: Tool snapping, venv requirements, lazy import, assisted calibration and recalibration

## Goal
Bring the T15m snap into the annotation tool for the first calibration and the recalibration (T6a, T6c flows),
keep the tool starting without numpy and Pillow, and prepare the assisted flow that T15d reuses for the controls
(Kosmo, D39; squad design D43, confirmed with three conditions in D44).

## Files
- Added `tools/camera/requirements-tool.txt` (D44 point 1): numpy 2.5.4 and pillow 13.0.0 only (the
  `requirements-cpu.txt` pins), two `--hash` lines each, for the wheels `<name>-<version>-cp313-cp313-` and
  `-cp314-cp314-manylinux_2_27_x86_64.manylinux_2_28_x86_64.whl` (no cp314t). Generate, never type: in a temp
  dir outside the repo, `.venv/bin/pip download --no-deps --only-binary=:all: --platform manylinux_2_28_x86_64
  --implementation cp --python-version 3.14 --abi cp314 -d <dir>/314 numpy==2.5.4 pillow==13.0.0`, the same with
  `3.13`/`cp313` into `<dir>/313`; `sha256sum` each wheel; check each digest equals `digests.sha256` of the same
  filename in `https://pypi.org/pypi/<name>/<version>/json` and each cp313 one equals the hash already in
  `requirements-cpu.txt`; stop and report on any mismatch or missing wheel. Header comment: purpose (WSL tool
  venv, K7.1b, Kosmo's Python 3.14; sandbox and image 3.13), the 4 wheel filenames, date, install command.
- Added `tools/camera/requirements-tool-test.txt` (D44 point 3): pytest, iniconfig, packaging, pluggy, pygments
  with the pins and hashes of `requirements-cpu.txt` copied by a script, not by hand (pure-Python wheels); header:
  Python 3.14 test runs only, never installed by Kosmo.
- Changed `kosmo_camera/synth.py`: `import av` moves into `_write_video` (comment: the snap tests run on 3.14
  without PyAV); behavior unchanged.
- Added `tools/camera/kosmo_camera/annotate/snapping.py` (stdlib at import time): `Snapper` with `available`
  (lazy `importlib.import_module("kosmo_camera.snap")` and `kosmo_camera.luma`; only `ModuleNotFoundError` whose
  name is numpy or PIL gives unavailable, any other import error propagates, A32),
  `snap(image_path, start)` and `propose(image_path, points)` (each point snapped from its old position; `null`
  stays `null`); images decoded by `luma.decode_luma` only, an unreadable one leaves the point at the click,
  `unsnapped` with reason `unreadable`, never an exception (S14); a luma cache keyed by resolved path and mtime, at
  most `SNAP_CACHE_IMAGES` images (LRU).
  Paths come only from the session listing (T6a resolution), never from the request.
- Changed `annotate/flow.py`, `annotate/server.py`: pending calibration and recalibration points become 4 slots
  with proposals and `snap` statuses as root "Screens and states", item "Assisted point screen": recalibration
  proposals = the segment in force; a click goes to the slot named by the pure function
  `flow.click_target(slots, click, selected)` (CV31 click rule, U27, constant `CLICK_REPLACE_RADIUS_PX` 100 in
  `defaults.json`, pinned once in `test_config.py`) and is snapped at once, or is refused with 409 and its text;
  keys 1 to 4 select, U undoes; `hide` refused with 409 on these screens (CV20; the CV31 hide rule is T15d's); Entrée (`save` action, carrying the open item) writes the segment with its `snap` only when 4 points are
  ready (snapped or clicked), else 409 « Points prêts : n / 4 », and 409 « Photo déjà enregistrée » when the item
  is no longer open (U29); leaving drops pending slots (U16). Snapper unavailable: `calibrate` and `save` refused
  with 409 "Recalage indisponible", tips unchanged; `--help` prints « Recalage : disponible » or « Recalage :
  indisponible (numpy ou Pillow absent) ».
- Changed `docs/adr/0016-camera-feasibility-tooling.md`: section "Assisted click" (ADR decision 2 and plan
  decision 5 amended, the options and their costs of root "Approach, Where the snapping runs", D43), and decision 2
  gains the line "Amended: see section Assisted click (D43, D44)" (A31). Changed `docs/rules/camera.md`: CV16 gains
  the "After D39 and D43" sentence of root "Rules" (A31).
- Changed tests `test_annotate_flow.py`, `test_annotate_server.py` (fake `Snapper`), `test_requirements.py`
  (the two new files as root "Test plan", T15n entry), `test_synth.py` (import and render with `av` blocked),
  `test_plan_docs.py` (A31 doc test), `test_drift.py` (static check adds `annotate/snapping.py`); added
  `test_annotate_snapping.py`.

## Depends on
T15b (`snap` in format v3), T15m (`snap.py`, `luma.py`), T6c (recalibration flow).

## Rules
CV16, CV25, CV31, S8, S14, D8, D45 (A31, A32, U27, U29). The page change (insets, keys, strings) is T15f; this task only serves the data
(`slots`: point, status, reason) and the actions. The page catches up in T15f; nothing is pushed before T15h.

## Tests
- Root plan "Test plan", T15n entry, every case (click rule boundaries, save refusals, A32 availability, decode
  failure with the real `luma.py`, A31 doc test failing when its line is removed, noted once by hand).
- `--help` and module import in a subprocess with `-I -S` and numpy made unimportable (`sys.modules` blocker in a
  `sitecustomize`-free child): tool starts, unavailable path.
- Real `snap.py` once end to end: calibration on a T7 photo with ring wires, rough clicks 5 px off, saved points
  within 0.1 px of truth, `snap` all `"snapped"`.
- Cache: third image evicts the first; mtime change reloads.

## Done when
Python checks of root "Test plan" green (CI command too), `python3 -I -S -m kosmo_camera.annotate --help` works,
`kosmo-detekt` exit 0, and the Python 3.14 run (system `python3` is 3.14.4; checked feasible on 2026-11-17):
`d=$(mktemp -d) && python3 -m venv "$d/v" && "$d/v/bin/pip" install --require-hashes --only-binary=:all:
-r tools/camera/requirements-tool.txt -r tools/camera/requirements-tool-test.txt && (cd tools/camera &&
"$d/v/bin/python" -m pytest -q -m "not slow" <TOOL_TESTS of root "Test plan">); rm -rf "$d"` green, from the
repository root, with its pytest count in the hand-back. A `TOOL_TESTS` file needing a module outside these two
files: make that import lazy if it is test-only plumbing, else stop and report; never drop a file from the list.

## Model
sonnet. Why: wiring and flow rules specified in CV31 and "Screens and states", fake snapper tests, same patterns
as T6a and T6c.

## Implementation notes
