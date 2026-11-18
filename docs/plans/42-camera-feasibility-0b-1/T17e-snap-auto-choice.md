# T17e: Automatic choice of the snap candidate on the dev points, coarse-stage diagnosis in the report

## Goal
In the same `snap_report` command (K7.6), choose per source the snap candidate of T17b's grid on the dev control
points only, and print what the coarse stage sees, so that a refused acceptance already holds the data of the one
fix round without another Kosmo run (root "Approach, Snap fix after K7.4", D55).

## Files
- Changed `tools/camera/kosmo_camera/snap_report.py`: function `choose(points, times)` over
  `SNAP_SEARCH_SCALES` x `SNAP_SEARCH_MIN_CONTRAST`, one snap per candidate from the stored point, on dev control
  points of each source (photo; video without hidden points); ranking: highest snapped share, tie: smaller scale,
  then higher floor; returns `{source: [SnapParams, ...]}` ranked. The share is only a ranking (no ground truth
  on real images, D56): T17c retains the first-ranked candidate whose dev points show no lattice suspect and no
  unstable restart (at most `SNAP_SEARCH_FALLBACKS` 2 fallbacks), and fills those two columns of the section.
  Section « Choix automatique (dev) »: per source and candidate the snapped share and the stage counts, then the
  dev suspects and unstable restarts (« non évalué » for candidates T17c did not check), the retained candidate
  marked. Section « Étage grossier (dev) »: per source, scale and category the medians [p10-p90] n of
  the T17b coarse descriptors. No eval figure in either section (D54). `planned_minutes` reused (A1) with the
  candidate snaps added (dev control points x candidates).
- Changed `config/defaults.json`, `config.py`, `test_config.py`: `SNAP_SEARCH_FALLBACKS` 2.
- Changed `tests/test_snap_report.py`.

## Depends on
T17b.

## Rules
CV32 (independence, choice part, D55, D56), CV4, CV15 (numbers and roles only).

## Tests
- Choice: scripted outcomes per candidate give the expected ranking; ties broken by scale then floor; near tie
  (100 % beats 99 %); a source with a single dev point ranks on it; an eval point that would change the ranking
  does not (eval points never snapped by `choose`); hidden video points excluded.
- `SNAP_SEARCH_FALLBACKS` 2 pinned once in `test_config.py`.
- Sections: counts add up per source and candidate; no eval row; privacy scan (no session name, no path).
- Planned duration: the snaps `choose` runs on the test sessions equal the candidate term of `planned_minutes`.
- A source with no dev control point keeps the scale 1 / floor 4 candidate and says « aucun point dev ».

## Done when
Python checks of root "Test plan" green, the Python 3.14 `TOOL_TESTS` run green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: the selection rule, the sections and the counts are fully specified here and in CV32; wiring on
T17a's report with scripted-outcome tests.

## Implementation notes
