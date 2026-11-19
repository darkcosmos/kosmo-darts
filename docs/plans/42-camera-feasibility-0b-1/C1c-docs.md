# C1c: Documents and rules after the removal of the stopped code

## Goal

Bring the rules, ADRs and Kosmo's guides in line with the code left by C1 and C1b (Q1 B, root plan N23, N24): the tool
enters tips and shows the rest read-only; snapping, drift, calibration and control entry are gone.

## Files

- `docs/rules/camera.md`: status note (line 9: "frozen until task C1" becomes "removed by C1 to C1c"); CV16 rewritten
  from the code (stdlib only, `127.0.0.1`, captures read-only, atomic writes in the annotations folder, tip entry only,
  a tip write keeps segments and controls, `--frames` and the frame route; the drift, "After D39 and D43", control mode
  and journey sentences removed); CV20 and CV25: one line "no longer entered in the tool (C1, Q1 B); read and kept";
  CV18, CV19, CV31 keep their "Stopped" line plus "code removed by C1".
- `docs/adr/0016-camera-feasibility-tooling.md`: decision 2 stdlib core without `drift_file` and without the "Amended"
  pointer; "Tripod drift" and "Assisted click" status lines say "code removed by task C1" instead of "frozen".
- `docs/camera/training.md`: "Annotate" section (python3 command from C1, no calibration first, tip keys from C1b, V
  for the video frame); "Contrôle de calibration": K7.1b removed, K7.3 and K7.C texts match the tool, stop line kept.
- `tools/camera/README.md`: tool description and command.
- `tools/camera/tests/test_plan_docs.py` (ADR 0016 statuses, the CV16 sentence tests replaced by the new CV16
  sentences), `tools/camera/tests/test_training_doc.py` (expected texts taken from the Python constants left by C1).

## Depends on

C1b.

## Rules

CV16, CV18, CV19, CV20, CV25, CV31 (text only, no rule number reused, N20).

## Tests

- Doc tests pin the new CV16 sentences, the two ADR 0016 status lines and the core list; `training.md` names no
  removed option (`--drift`, `--split`, `--lock`), no `kosmo-tool-venv`, no « Recalage » text; every command in it
  still parses with the real parser.
- `grep -rn "snap.py\|drift.py\|requirements-tool.txt" docs tools/camera --include=*.md` lists only history (done or
  cancelled task files, ADR status lines); the result goes in the notes.

## Done when

`python -m pytest -q -m "not slow"` in `tools/camera` green; `kosmo-detekt` exit 0; `git diff --check` clean.

## Model and Why this model

haiku: documentation copied from the code and the task files of C1 and C1b, no design choice.

## Implementation notes
