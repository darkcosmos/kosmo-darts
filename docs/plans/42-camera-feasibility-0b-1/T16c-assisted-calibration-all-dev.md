# T16c (CONDITIONAL): Automatic per-photo calibration by chained snapping, exceptions only to Kosmo

Status: conditional. Built only if the photos moved and no automatic per-photo calibration passes the CV30 gate.
Finalized at P2. Reshaped at lot 3b (D48): the former manual clicking of every dev photo is withdrawn; Kosmo's
manual budget is at most 10 min per campaign (ADR 0018).

## Goal
Calibrate every dev photo automatically: snap the 4 calibration points of photo k from the snapped points of photo
k-1 (or the segment calibration), check them with the CV32 lattice, and flag only the photos the tool cannot
settle; Kosmo validates the flagged photos within the budget (D30 point 4, D48).

## Files
- Added `tools/camera/kosmo_camera/chain_calibration.py` (numpy, WSL tool venv): per dev photo in dart order, start
  = previous accepted points; snap (T17b), lattice (T17c), restarts; status `accepted` or `flagged` with reason;
  writes `<runs>/calibration/calibration-points.json` (format on the `snapped_file` pattern) and a numbers-only
  report with the flagged count and the estimated validation time from K7.6's real per-point time.
- Changed `annotate/server.py`, `annotate/flow.py`, `annotate/static/app.js`: option `--flagged FILE` shows only the
  flagged photos, the proposed points pre-placed; Kosmo accepts with Entrée or drags a point (snapped again).
- Changed `docs/camera/training.md`, `test_training_doc.py`: one command and the optional flagged review.
- Added `tests/test_chain_calibration.py`; changed `test_annotate_flow.py`, `test_annotate_static.py`.

## Depends on
T16b, T17c, T15g; plan update P2.

## Rules
CV30, CV31, CV32, D48: if the flagged count times the measured time per photo exceeds 10 min, the tool stops
before the review and the plan proposes another automated alternative.

## Tests
- Synthetic session with planned drift: every photo accepted, points within 0.25 px; a photo with a dart on a
  calibration crossing flagged; a jump larger than the snap radius flagged, never accepted on a wrong crossing.
- Budget: a flagged count above the 10-min estimate stops with its French line, no review started.
- Flow: only flagged photos are shown; acceptance with Entrée writes the proposed points.

## Done when
Python checks of root "Test plan" green, the Python 3.14 `TOOL_TESTS` run green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: chains T17b's snap and T17c's lattice check, both specified, with synthetic tests; the flagged review
reuses the T15d to T15g patterns.

## Implementation notes
