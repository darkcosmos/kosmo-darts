# T15o: Diagnosis verdict: decision rule written in advance (CV29)

Split from T15j at lot 3b (D52) to keep one concern per task.

## Goal
Print the verdict of the decision rule written in advance from the T15i and T15j quantities (D30 point 2, CV29 as
amended by D48: "too few controls" leads to an automated alternative, never to more clicks).

## Files
- Changed `tools/camera/kosmo_camera/diagnose.py`: report section "Verdict" (CV29 lines and the "Suite proposée"
  row of root plan "Approach, Diagnosis decision rule"); the two "non concluant" causes read `D` p95 and the
  complete snapped dev photo control count from T15i, never `R` (information since D39).
- Changed `defaults.json`, `config.py`, `test_config.py`: `DIAG_MIN_SHARE` 0.10, `DIAG_MIN_VIDEO` 10 (pinned once, T3).
- Changed `docs/rules/camera.md`: adds CV29 (as in root "Rules").
- Changed tests `test_diagnose.py`.

## Depends on
T15j.

## Rules
CV29, CV28, CV32, D48. Every threshold is read from `config.py`; none is chosen from the data.

## Tests
- Each CV29 branch on scripted values, thresholds at the boundary (exactly `DIAG_MIN_SHARE`, exactly
  `VALIDATION_MIN_SHARE`, exactly `DIAG_MIN_VIDEO` darts with `O`).
- The two "non concluant" causes (CV29, Kosmo Q10, D39), each with its own French line and "Suite proposée":
  `D` p95 just above `SNAP_REPEAT_MAX_MM` gives "Non concluant : dispersion du recalage p95 X mm, au-delà de
  0,25 mm", exactly at it does not; an infinite p95 gives the same line with "infinie"; 29 complete snapped dev
  photo controls give "Non concluant : N contrôles photo dev complets et recalés, il en faut 30", 30 do not; 30
  complete dev controls of which 2 have an `unsnapped` point count 28 and give that line; both causes at once print
  both lines and nothing else. No "Suite proposée" asks Kosmo for a click (scan for « cliquer », « clic »).
- `R` and `C` are information: scripted values from 0 to 10 mm never change any verdict line.
- Combinations (T1, independent lines): H1 with "photos déplacées"; H2 and H3 together; H1, H2 and H3 together;
  "non concluant" with values that would also give H1 and H2: only "non concluant" is printed.
- The T15i K5 regression session gives the H1 line; the T15j synthetic sessions give H2 "oui" and H3 "oui", H2 "non".

## Done when
Python checks of root "Test plan" green (CI command too), `kosmo-detekt` exit 0.

## Model
sonnet. Why: the rule is fully written in CV29 and root "Approach, Diagnosis decision rule"; scripted-value tests.

## Implementation notes
