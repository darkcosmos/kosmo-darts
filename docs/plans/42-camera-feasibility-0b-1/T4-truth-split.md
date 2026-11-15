# T4: Truth v2 reader and dev/eval split

## Goal
Read Kosmo Capture `truth.json` version 2 in Python (stdlib only) and assign each session to dev or eval.

## Files
- Added `tools/camera/kosmo_camera/truth.py`: frozen dataclasses `Session`, `Volley`, `Dart`, `Stamp`, `Zoom`;
  `read_truth(path) -> Session`; `TruthError` with a JSON path in the message. Helpers: `Dart.judged` (non-voided and
  photo not null, CV5), `Session.sha256` of the file bytes.
- Added `tools/camera/kosmo_camera/split.py`: `EVAL_CUTOFF`, `split_of(session_name) -> "dev" | "eval"`,
  `list_sessions(captures_dir, split)` (sorted, folders with a `truth.json` only).
- Added tests `tools/camera/tests/test_truth.py`, `test_split.py`, fixture `tests/data/truth-v2-golden.json` (the
  example of `docs/camera/capture-format.md`, copied as is).

## Depends on
T1.

## Rules
CV3, CV5. Format: `docs/camera/capture-format.md` (sections "Encoding", "Version policy", "Fields"): reject size over
10 MiB before parsing, duplicate keys (`object_pairs_hook`), unknown fields, other `format` or `version`, missing
fields, segment and multiplier outside rule D2 of `docs/rules/x01.md`. Stdlib only.

## Tests
- Golden file reads; values spot-checked (a voided dart, a null photo if present, zoom).
- Rejections: version 1 and 3, other format, duplicate key, unknown field, missing field, triple bull, segment 21,
  text over 10 MiB, truncated text.
- Killed session (`endedAt` null, last volley `end` null) reads.
- Split: `20261115-235959` dev, `20261116-000000` eval, `20261116-000000-2` eval, bad name rejected (CV3).
- Import closure: `python3.13 -I -S -c "import kosmo_camera.truth, kosmo_camera.split"` with `PYTHONPATH` succeeds.

## Done when
`.venv/bin/python -m pytest tools/camera -q` green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: reader of an existing documented format and a specified rule.

## Implementation notes
