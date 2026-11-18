# T16c (CONDITIONAL): Assisted manual calibration of every dev photo

CANCELLED (2026-11-18): stopped; global per-image registration R replaces it (ADR 0020).

Status: conditional. Built only if the photos moved and no automatic per-photo calibration passes the CV30 gate,
and Kosmo accepts the clicking time at plan update P2. Finalized at P2.

## Goal
Let Kosmo click the 4 calibration points of every dev photo quickly, so that dev labels are right without a
validated automatic measure (D30 point 4, "manuelle assistée").

## Files
- Changed `tools/camera/kosmo_camera/control.py`: sample option `--control-sample all-dev` (every judged dev
  photo); default unchanged (CV26).
- Changed `annotate/server.py`, `annotate/flow.py`: the option passed through; the zoom centre of each point is
  the previous photo's clicked point (already the T15d rule); progress counts the new sample.
- Changed `annotate/static/app.js`: progress line shows the time left estimate "Reste environ … min".
- Changed `docs/camera/training.md` and `test_training_doc.py`: one step for the option.
- Changed tests `test_control.py`, `test_annotate_flow.py`, `test_annotate_static.py`.

## Depends on
T16b, T15g; plan update P2.

## Rules
CV25, CV26 (option added), CV16.

## Tests
- Sample `all-dev`: every judged dev photo, no eval photo, order by dart.
- Flow: the next control item after the last sampled one is the first item of the option sample.
- Static: the time-left string present once; T6b scan.

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: an extra sample option and a progress string on the T15c to T15f patterns.

## Implementation notes
