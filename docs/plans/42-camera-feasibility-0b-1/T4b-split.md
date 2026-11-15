# T4b: Dev/eval split, split file and lock

## Goal
Compute once, on Kosmo's real list of existing sessions, the about 70/30 dev/eval split of CV3, report its balance
with counts only, and let every later tool check that the locked split is used (decision K0).

## Files
- Added `tools/camera/kosmo_camera/split.py` (stdlib only): `unit_key(session, block=None)`, `compute_split(sessions)
  -> Split` (session mode, then block mode if the CV3 criteria fail), `encode(split) -> bytes` (canonical JSON:
  sorted keys, 2-space indent, `format` `"kosmo-camera-split"`, `version` 1, `mode`, `units` with session name, block
  volley range and set, `counts` with `ignoredSessions`), `decode(bytes)` (strict), `read_lock(path)` and `check_lock(split_bytes, lock)`
  (`split-lock.json`: format, version, `mode`, 64-hex `splitSha256`, unit and judged-dart counts per set; strict),
  `set_of(session, volley_index, split) -> "dev" | "eval"`, `list_sessions(root)` (folders with a `truth.json`).
  Command `python3 -m kosmo_camera.split --captures DIR --out FILE` writes the split file (default
  `~/kosmo-runs/split.json`) and prints mode, flag "more optimistic" in block mode, units, judged darts and eval
  share per set, the number of ignored sessions (0 judged darts, CV3), the criteria met or failed, and the SHA-256. No session name printed.
- Added tests `tools/camera/tests/test_split.py`.

## Depends on
T4.

## Rules
CV3 (including ignored sessions), CV4, CV5 (judged darts from the truth file only, so the split runs before annotation), CV15, CV17 (a session
under `captures-eval/` passed to `compute_split` raises). Constants from `config.py`.

## Tests
- Determinism: same list in any order gives identical bytes; the key of a session does not depend on the others.
- Session mode on 10 synthetic sessions of 50 darts: eval share in [0.25, 0.35], at least 2 sessions per set.
- Fallback to block mode for 3 sessions, for one big session plus small ones (share out of range), for eval under
  100 darts; block mode flagged; blocks of 5 volleys, last block shorter; a volley belongs to exactly one block.
- Boundaries: eval share exactly 0.25 and 0.35 accepted; exactly 2 sessions per set accepted.
- Block mode leakage: no volley appears in both sets; every volley's darts are all in one set (a block never
  splits a volley between dev and eval).
- Sessions with 0 judged darts (CV5) are ignored (CV3): 10 judged sessions plus 3 empty ones give the same `units`
  and sets as the 10 alone, and `counts.ignoredSessions` = 3; with 3 judged sessions plus 2 empty ones, session mode
  still fails "at least 2 sessions per set" exactly as with the 3 alone (empty sessions never count), so the
  encoded and printed mode is "block" with the "more optimistic" flag set; `set_of` on an
  ignored session raises; printed output shows the ignored count, no name.
- Lock: matching hash passes; one changed byte, wrong mode, malformed lock (unknown key, short hash) fail.
- Printed output contains no session name (CV15).

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: algorithm and criteria fully specified in CV3; the split file is derived and regenerable (no migration).

## Implementation notes
- `split.py` works on `SessionInput(name, volleys=((volley index, judged darts), ...), path)`; `from_truth` and
  `list_sessions` build them from `read_truth`. Tests use generated inputs only.
- Unit keys in block mode use the block ordinal (position of the volley in the session, divided by 5). A block
  without judged dart in a judged session is still a unit (0 darts) so `set_of` covers every volley.
- `Split.session_criteria` / `block_criteria` are not encoded; they feed the printed summary only.
- Added beyond the task text: `make_lock(split_bytes)` and `--lock FILE` (writes `split-lock.json`); the command
  refuses to overwrite an existing split or lock (computed once). `check_lock` returns the decoded split.
- Lock format string: `kosmo-camera-split-lock`; lock `counts` = units and darts per set.
