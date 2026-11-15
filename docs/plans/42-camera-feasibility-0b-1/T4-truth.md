# T4: Truth v2 reader

## Goal
Read Kosmo Capture `truth.json` version 2 in Python (stdlib only).

## Files
- Added `tools/camera/kosmo_camera/truth.py`: frozen dataclasses `Session`, `Volley`, `Dart`, `Stamp`, `Zoom`;
  `read_truth(path) -> Session`; `TruthError` with a JSON path in the message. Helpers: `Dart.judged` (non-voided and
  photo not null, CV5), `Session.sha256` of the file bytes.
- Added tests `tools/camera/tests/test_truth.py`, fixture `tests/data/truth-v2-golden.json` (the
  example of `docs/camera/capture-format.md`, copied as is).

## Depends on
T1.

## Rules
CV5. Format: `docs/camera/capture-format.md` (sections "Encoding", "Version policy", "Fields"): reject size over
10 MiB before parsing, duplicate keys (`object_pairs_hook`), unknown fields, other `format` or `version`, missing
fields, segment and multiplier outside rule D2 of `docs/rules/x01.md`. Stdlib only.

## Tests
- Golden file reads; values spot-checked (a voided dart, a null photo if present, zoom).
- Rejections: version 1 and 3, other format, duplicate key, unknown field, missing field, triple bull, segment 21,
  text over 10 MiB (the file is not read past the cap), truncated text.
- Killed session (`endedAt` null, last volley `end` null) reads.
- Import closure: `python3 -I -S -c "import kosmo_camera.truth, kosmo_camera.config"` with `PYTHONPATH` succeeds.

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: reader of an existing documented format.

## Implementation notes
- `read_truth(path)` in `tools/camera/kosmo_camera/truth.py`; frozen dataclasses `Session` (flat: app, device, video and photo fields are prefixed), `Volley`, `Dart`, `Stamp`, `Photo` (a `Stamp` plus `file`), `Zoom`. `Dart.gravity` is an `(x, y, z)` tuple or None.
- Reads at most 10 MiB + 1 bytes; nesting depth is checked iteratively after parsing; NaN/Infinity are refused at parse.
- Deviation: the import-closure test puts the package root on `sys.path` inside the `-I -S -c` command, because `-I` ignores `PYTHONPATH`.
- Extra checks beyond the task list: `voided` iff `voidedAt`, `endReason` null iff `endedAt` null, UTC string shape, `clock`, enumerations.
