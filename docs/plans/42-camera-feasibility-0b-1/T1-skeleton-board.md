# T1: Tooling skeleton, pins, board geometry, rules doc

## Goal
Create `tools/camera/` with the pinned CPU environment and pytest, the stdlib board geometry, the CV rules and the ADR.

## Files
- Added `tools/camera/requirements-cpu.txt`: full transitive set, `==` pins each with `--hash=sha256:...`, header
  comment with the install command (`--require-hashes --index-url https://download.pytorch.org/whl/cpu
  --extra-index-url https://pypi.org/simple`). Generate it from `pip install --dry-run --report` in a fresh
  `python3.13` venv for `rfdetr==1.12.1 litert-torch==0.10.0 ai-edge-litert==2.3.0 torch==2.14.1 pytest`, latest
  stable of each (root plan "Approach"); hashes from the report's `download_info` (Linux x86_64 cp313 wheels).
- Added `tools/camera/config/defaults.json` with the keys known now (split: target 0.30, accepted 0.25 to 0.35, at
  least 2 sessions per set and 100 eval judged darts, `SPLIT_BLOCK_VOLLEYS` 5; `BOX_FRACTION` 0.025, valid ratio
  0.15, `MATCH_RADIUS_MM` 6.0, `CAL_MAX_ERROR_MM` 2.0, initial `TAU_LOW` 0.2 and `TAU_SURE` 0.5); T8 adds the group
  bins and T12 the event parameters, each with a test. And
  `tools/camera/kosmo_camera/config.py` (stdlib, strict: unknown or missing key, wrong type raise; values exposed as
  module constants). Other modules read constants only from `config.py` (decision D9).
- Added `tools/camera/pytest.ini` (`pythonpath = .`, `testpaths = tests`, marker `slow`, `addopts = -m "not slow"`).
- Added `tools/camera/README.md` (one paragraph, points to `docs/camera/training.md`).
- Added `tools/camera/kosmo_camera/__init__.py` (empty: imports nothing) and `board.py`.
- Added `tools/camera/tests/test_board.py`, `test_config.py`.
- Added `docs/rules/camera.md` (CV1 to CV17, wording from the root plan "Rules"), `docs/adr/0016-camera-feasibility-
  tooling.md` (two environments on 3.13, stdlib core, keypoints as objects, measures on the `.tflite` only, no OpenCV,
  no model in the repo; the training image delivered in PR A for the GPU check and rebuilt in PR B with the
  training code and entry point, D11; alternatives from root "Approach").
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
- Config: the committed `defaults.json` loads; an unknown key, a missing key and a wrong type each raise (A1).

## Done when
`python3.13 -m venv .venv && .venv/bin/pip install --require-hashes -r tools/camera/requirements-cpu.txt <indexes>`
succeeds; Python checks of root "Test plan" green; `kosmo-detekt` exit 0; `git status` shows no `.venv`.

## Model
sonnet. Why: configuration plus geometry mirrored from existing Kotlin, tests listed.

## Implementation notes
- `requirements-cpu.txt`: 89 pins, one hash each, generated from the dry-run report. Torch family pinned as
  `torch==2.14.1+cpu`, `torchvision==0.29.2+cpu`, `torchao==0.18.0+cpu` (local version of the PyTorch CPU index);
  `python-dateutil` resolved to 4.0.0.
- `config.py`: `SCHEMA` (key to type), `load_defaults(path)`, `ConfigError`; constants `BOX_FRACTION`, `VALID_RATIO`,
  `MATCH_RADIUS_MM`, `CAL_MAX_ERROR_MM`, `TAU_LOW`, `TAU_SURE`, `SPLIT_*`. Later tasks add keys to `SCHEMA` and the
  JSON together. Integer keys refuse floats and booleans.
- `board.py`: `zone_at`, `SECTOR_ORDER`, radii, `CALIBRATION_POINTS_MM` (tuples, math angles 99, 9, 279, 189).
  Segment 0 returns multiplier "SINGLE". Tests run from `tools/camera/` (`pytest.ini` lives there).
- ADR and rules written in the same commit; `requirements-gpu.txt` is for the image task (T10a).
