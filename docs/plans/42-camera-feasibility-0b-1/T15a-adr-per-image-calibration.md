# T15a: ADR 0017 (per-image calibration in the game app) and K5 suspended

## Goal
Record now Kosmo's product requirement for the camera phases after 0b (D30 point 6) as ADR 0017, and mark the K5
drift loop of `training.md` as suspended so Kosmo never follows a stale instruction (D30, D31).

## Files
- Added `docs/adr/0017-per-image-calibration.md`: the text of root plan "ADR 0017 text", copied as is.
- Changed `docs/camera/training.md`, section "Dérive et recalibration": insert at the top of the section, before
  K5.0, the paragraph of root plan "How Kosmo verifies", item "K5 (suspended)", in French, as given there. The K5
  steps below stay unchanged (their doc test still parses them; T15h replaces the section).
- Changed `docs/rules/camera.md`: CV18 and CV19 each gain the sentence of root plan "Rules", items "CV18 status"
  and "CV19 status" (copied).

## Depends on
- (none: docs only; first task of batch 3)

## Rules
CV18, CV19 (status sentences), D30, D31. No code, no test change.

## Tests
- No new test. `test_training_doc.py` stays green unchanged (the K5 blocks are kept).

## Done when
`.venv/bin/python -m pytest tools/camera -q` and `.venv/bin/python -m compileall -q tools/camera` green (unchanged
count), `kosmo-detekt` exit 0.

## Model
haiku. Why: text copied from the root plan, no design choice.

## Implementation notes
