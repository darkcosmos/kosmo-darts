# T17e: Automatic choice of the snap candidate on the dev points, coarse-stage diagnosis in the report

## Goal
In the same `snap_report` command (K7.6), choose per source the snap candidate of T17b's grid on the dev control
points only, and print what the coarse stage sees, so that a refused acceptance already holds the data of the one
fix round without another Kosmo run (root "Approach, Snap fix after K7.4", D55).

## Files
- Changed `tools/camera/kosmo_camera/snap_report.py`: function `choose(points, times)` over
  `SNAP_SEARCH_SCALES` x `SNAP_SEARCH_MIN_CONTRAST`, one snap per candidate from the stored point, on dev control
  points of each source (photo; video without hidden points); rule: highest snapped share, tie: smaller scale,
  then higher floor; returns `{source: SnapParams}`, used by T17c's acceptance and by the existing sections.
  Section « Choix automatique (dev) »: per source and candidate the snapped share and the stage counts, the chosen
  candidate marked. Section « Étage grossier (dev) »: per source, scale and category the medians [p10-p90] n of
  the T17b coarse descriptors. No eval figure in either section (D54). `planned_minutes` reused (A1) with the
  candidate snaps added (dev control points x candidates).
- Changed `tests/test_snap_report.py`.

## Depends on
T17b.

## Rules
CV32 (independence, choice part, D55), CV4, CV15 (numbers and roles only).

## Tests
- Choice: scripted outcomes per candidate give the expected winner; ties broken by scale then floor; an eval point
  that would change the winner does not (eval points never snapped by `choose`); hidden video points excluded.
- Sections: counts add up per source and candidate; no eval row; privacy scan (no session name, no path).
- Planned duration: the snaps `choose` runs on the test sessions equal the candidate term of `planned_minutes`.
- A source with no dev control point keeps the scale 1 / floor 4 candidate and says « aucun point dev ».

## Done when
Python checks of root "Test plan" green, the Python 3.14 `TOOL_TESTS` run green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: the selection rule, the sections and the counts are fully specified here and in CV32; wiring on
T17a's report with scripted-outcome tests.

## Implementation notes
