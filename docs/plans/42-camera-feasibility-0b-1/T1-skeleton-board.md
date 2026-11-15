# T1: Tooling skeleton, pins, board geometry, rules doc

## Goal
Create `tools/camera/` with the pinned CPU environment and pytest, the stdlib board geometry, the CV rules and the ADR.

## Files
- Added `tools/camera/requirements-cpu.txt`: full transitive set, `==` pins, header comment with the install command
  (`--index-url https://download.pytorch.org/whl/cpu --extra-index-url https://pypi.org/simple`). Generate it with
  `pip install --dry-run --report` in a fresh `python3.13` venv for `rfdetr==1.12.1 litert-torch==0.10.0
  ai-edge-litert==2.3.0 torch==2.14.1 pytest`, latest stable of each (root plan "Approach").
- Added `tools/camera/pytest.ini` (`pythonpath = .`, `testpaths = tests`, marker `slow`, `addopts = -m "not slow"`).
- Added `tools/camera/README.md` (one paragraph, points to `docs/camera/training.md`).
- Added `tools/camera/kosmo_camera/__init__.py` (empty: imports nothing) and `board.py`.
- Added `tools/camera/tests/test_board.py`.
- Added `docs/rules/camera.md` (CV1 to CV16, wording from the root plan "Rules"), `docs/adr/0016-camera-feasibility-
  tooling.md` (two environments on 3.13, stdlib core, keypoints as objects, measures on the `.tflite` only, no OpenCV,
  no model in the repo; alternatives from root "Approach").
- Changed `.gitignore`: `.venv/`, `__pycache__/`, `.pytest_cache/`, `*.tflite`, `*.pth`.

## Depends on
-

## Rules
CV1, CV2. `board.py` (stdlib only): radii and sector order equal to
`game-engine/.../engine/board/BoardGeometry.kt`; `zone_at(x_mm, y_mm)` returns `(segment, multiplier)` with segment 0
outside the double ring, 25 and 50 for the bulls (truth convention of `docs/camera/capture-format.md`); border
ownership as BC5; `CALIBRATION_POINTS_MM` (4 points, CV2 order).

## Tests
- Kotlin sync: parse the `const val` radii and `SECTOR_ORDER` from `BoardGeometry.kt`, compare with `board.py` (CV1).
- Zone at the centre of each of the 82 zones; points exactly on every ring radius and on sector edges (BC5);
  170 mm inclusive, 170.0001 mm is a miss.
- Calibration points: radius 170 mm, angles 99, 9, 279, 189 degrees, each on the wire between its two sectors (CV2).

## Done when
`python3.13 -m venv .venv && .venv/bin/pip install -r tools/camera/requirements-cpu.txt <indexes>` succeeds;
`.venv/bin/python -m pytest tools/camera -q` green; `kosmo-detekt` exit 0; `git status` shows no `.venv`.

## Model
sonnet. Why: configuration plus geometry mirrored from existing Kotlin, tests listed.

## Implementation notes
