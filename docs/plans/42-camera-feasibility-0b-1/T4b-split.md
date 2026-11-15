# T4b: Dev/eval split, split file and lock

## Goal
Compute once, on Kosmo's real list of existing sessions, the about 70/30 dev/eval split of CV3, report its balance
with counts only, and let every later tool check that the locked split is used (decision K0).

## Files
- Added `tools/camera/kosmo_camera/split.py` (stdlib only): `unit_key(session, block=None)`, `compute_split(sessions)
  -> Split` (session mode, then block mode if the CV3 criteria fail), `encode(split) -> bytes` (canonical JSON:
  sorted keys, 2-space indent, `format` `"kosmo-camera-split"`, `version` 1, `mode`, `units` with session name, block
  volley range and set, `counts`), `decode(bytes)` (strict), `read_lock(path)` and `check_lock(split_bytes, lock)`
  (`split-lock.json`: format, version, `mode`, 64-hex `splitSha256`, unit and judged-dart counts per set; strict),
  `set_of(session, volley_index, split) -> "dev" | "eval"`, `list_sessions(root)` (folders with a `truth.json`).
  Command `python3.13 -m kosmo_camera.split --captures DIR --out FILE` writes the split file (default
  `~/kosmo-runs/split.json`) and prints mode, flag "more optimistic" in block mode, units, judged darts and eval
  share per set, the criteria met or failed, and the SHA-256. No session name printed.
- Added tests `tools/camera/tests/test_split.py`.

## Depends on
T4.

## Rules
CV3, CV4, CV5 (judged darts from the truth file only, so the split runs before annotation), CV15, CV17 (a session
under `captures-eval/` passed to `compute_split` raises). Constants from `config.py`.

## Tests
- Determinism: same list in any order gives identical bytes; the key of a session does not depend on the others.
- Session mode on 10 synthetic sessions of 50 darts: eval share in [0.25, 0.35], at least 2 sessions per set.
- Fallback to block mode for 3 sessions, for one big session plus small ones (share out of range), for eval under
  100 darts; block mode flagged; blocks of 5 volleys, last block shorter; a volley belongs to exactly one block.
- Boundaries: eval share exactly 0.25 and 0.35 accepted; exactly 2 sessions per set accepted.
- Sessions with 0 judged darts are kept in dev and counted.
- Lock: matching hash passes; one changed byte, wrong mode, malformed lock (unknown key, short hash) fail.
- Printed output contains no session name (CV15).

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: algorithm and criteria fully specified in CV3; the split file is derived and regenerable (no migration).

## Implementation notes
