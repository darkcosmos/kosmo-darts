# T13b: Preliminary report

## Goal
Write the numbers-only preliminary report of decision K0 (Markdown to paste, JSON for the squad), with its limits
section, the verdict per threshold and either a path proposal or the confirmation campaign specification.

## Files
- Added `tools/camera/kosmo_camera/report.py`: `build_report(metrics, context) -> (markdown, json)` with sections:
  Set and split (mode, flag "plus optimiste" in block mode, units and judged darts per set); Results (every CV8 to
  CV12 rate with count, 95 % interval, target, pass or fail, verdict); Breakdowns (CV14 tables); Tip error (median,
  p95, intervals); Public data; Limits; Next step; Hashes and versions (model SHA-256 and size, `frozen.json` and split SHA-256,
  tool versions). `build_comparison(choice) -> (markdown, json)` for the `compare` run of T14.
- Public data section (CV15, CV21, CV22): sets used by the measured model (slug, licence, images kept and dropped,
  "attribution : voir docs/camera/datasets.md") or "aucun jeu public"; then the comparison of T13 `compare`: one row
  per version (sans / avec jeux publics) with correct and unflagged rates and intervals, tuned pair or "aucune paire",
  CV9 successes, CV12 median, comparison set size; the retained version, its model SHA-256, the deciding step in
  French and "écart dans le bruit" when flagged. The same section is the whole output of `build_comparison`.
- Limits section: sample size and interval half-width per rate; overlap table of tripod groups and lighting classes
  (judged darts per group in dev and in eval, groups present on one side only marked); "removals not measurable on
  the existing captures (protocol mixed)"; block mode warning when used; drift summary of the measured set (CV19:
  threshold, calibration segments, photos compensated, max compensated drift mm, photos without reference
  calibration per reason, photos excluded by `--accept-drift`), with the line "critère principal indépendant de la
  calibration cliquée".
- Next step section (D12): any threshold "clearly below" (whole interval on the wrong side of the target) lists the
  candidate paths of the issue (more data, larger model, keypoint model, classic detection) with the numbers that
  point to each (tip error versus calibration failures versus missed tips); otherwise the campaign: judged darts
  needed so the Wilson half-width is at most 2 points at the observed rate, tripod groups and lightings absent or
  under 50 darts in eval, the 3 weakest zones of the breakdowns, the removal protocol of `training.md`, folder
  `captures-eval/` (CV17).
- Added tests `tools/camera/tests/test_report.py`.

## Depends on
T13.

## Rules
CV8 to CV15, CV17, CV19, CV21, CV22, D12, D21. Interval at exactly the target counts as "close or above" (not clearly below).

## Tests
- Verdict: interval entirely below 95 % gives "clearly below"; upper bound exactly 95 % gives "close or above"; same
  for the 1 % bound from the other side.
- Next step: one clearly-below rate gives the path list; all close or above gives the campaign with the computed
  dart count (checked against a hand computation) and the missing groups.
- Limits: a group present only in dev is marked; block mode adds the warning; "removals not measurable" present for
  `captures/` and absent for a campaign report.
- 0 judged darts: report renders with "n/a" everywhere.
- Drift summary present with its counts; an `--accept-drift` run shows the excluded count.
- Public data: a model trained with two synthetic slugs lists both with licence and counts; without, "aucun jeu
  public"; a comparison choice renders both rows, the retained version and the deciding step; overlap flag shown,
  with the fail-closed French message of root CV22 while Q2 is open.
- Privacy scan also run on the comparison output; slugs are allowed, session names are not.
- Privacy (CV15): a report built from synthetic sessions contains no session name, file name, path separator from the
  inputs, or image data (scan for the input strings and for base64 or data URLs).

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: report content and verdict rule fully specified.

## Implementation notes
