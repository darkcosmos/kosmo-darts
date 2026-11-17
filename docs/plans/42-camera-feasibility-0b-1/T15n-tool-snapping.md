# T15n: Tool snapping, venv requirements, lazy import, assisted calibration and recalibration

## Goal
Bring the T15m snap into the annotation tool for the first calibration and the recalibration (T6a, T6c flows),
keep the tool starting without numpy and Pillow, and prepare the assisted flow that T15d reuses for the controls
(Kosmo, D39; squad design D43, pending Kosmo's confirmation Q14).

## Files
- Added `tools/camera/requirements-tool.txt`: numpy and Pillow only, same versions as `requirements-cpu.txt`
  (numpy 2.5.4, Pillow 13.0.0), `--hash` for the cp313 and cp314 manylinux x86_64 wheels (hashes read from PyPI's
  JSON, never typed by hand); header comment: for the WSL tool venv, K7.1b.
- Added `tools/camera/kosmo_camera/annotate/snapping.py` (stdlib at import time): `Snapper` with `available`
  (lazy `importlib.import_module("kosmo_camera.snap")`, `ImportError` gives unavailable, never a crash),
  `snap(image_path, start)` and `propose(image_path, points)` (each point snapped from its old position; `null`
  stays `null`); a luma cache keyed by resolved path and mtime, at most `SNAP_CACHE_IMAGES` images (LRU).
  Paths come only from the session listing (T6a resolution), never from the request.
- Changed `annotate/flow.py`, `annotate/server.py`: pending calibration and recalibration points become 4 slots
  with proposals and `snap` statuses as root "Screens and states", item "Assisted point screen": recalibration
  proposals = the segment in force; a click replaces the nearest slot or fills the first empty one and is snapped
  at once; keys 1 to 4 select, H hides, U undoes; Entrée (`save` action) writes the segment with its `snap` only
  when 4 points are ready (snapped or clicked; `hide` refused with 409 on these screens, CV20); leaving drops pending
  slots (U16). Snapper unavailable: `calibrate` and `save` refused with 409 "Recalage indisponible", tips
  unchanged; `--help` prints « Recalage : disponible » or « Recalage : indisponible (numpy ou Pillow absent) ».
- Changed `docs/adr/0016-camera-feasibility-tooling.md`: section "Assisted click" (decision 5 amended, the options
  and their costs of root "Approach, Where the snapping runs", D43).
- Changed tests `test_annotate_flow.py`, `test_annotate_server.py` (fake `Snapper`), `test_requirements.py`;
  added `test_annotate_snapping.py`.

## Depends on
T15b (`snap` in format v3), T15m (`snap.py`), T6c (recalibration flow).

## Rules
CV16, CV25, CV31, S8, D8. The page change (insets, keys, strings) is T15f; this task only serves the data
(`slots`: point, status, reason) and the actions. The page catches up in T15f; nothing is pushed before T15h.

## Tests
- Root plan "Test plan", T15n entry, every case.
- `--help` and module import in a subprocess with `-I -S` and numpy made unimportable (`sys.modules` blocker in a
  `sitecustomize`-free child): tool starts, unavailable path.
- Real `snap.py` once end to end: calibration on a T7 photo with ring wires, rough clicks 5 px off, saved points
  within 0.1 px of truth, `snap` all `"snapped"`.
- Cache: third image evicts the first; mtime change reloads.

## Done when
Python checks of root "Test plan" green (CI command too), `python3 -I -S -m kosmo_camera.annotate --help` works,
`kosmo-detekt` exit 0.

## Model
sonnet. Why: wiring and flow rules specified in CV31 and "Screens and states", fake snapper tests, same patterns
as T6a and T6c.

## Implementation notes
