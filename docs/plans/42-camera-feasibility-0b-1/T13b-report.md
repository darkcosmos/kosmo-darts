# T13b: Preliminary report

## Goal
Write the numbers-only preliminary report of decision K0 (Markdown to paste, JSON for the squad), with its limits
section, the verdict per threshold and either a path proposal or the confirmation campaign specification.

## Files
- Added `tools/camera/kosmo_camera/report.py`: `build_report(metrics, context) -> (markdown, json)` with sections:
  Set and split (mode, flag "plus optimiste" in block mode, units and judged darts per set); Results (every CV8 to
  CV12 rate with count, 95 % interval, target, pass or fail, verdict); Breakdowns (CV14 tables); Tip error (median,
  p95, intervals); Limits; Next step; Hashes and versions (model SHA-256 and size, `frozen.json` and split SHA-256,
  tool versions).
- Limits section: sample size and interval half-width per rate; overlap table of tripod groups and lighting classes
  (judged darts per group in dev and in eval, groups present on one side only marked); "removals not measurable on
  the existing captures (protocol mixed)"; block mode warning when used.
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
CV8 to CV15, CV17, D12. Interval at exactly the target counts as "close or above" (not clearly below).

## Tests
- Verdict: interval entirely below 95 % gives "clearly below"; upper bound exactly 95 % gives "close or above"; same
  for the 1 % bound from the other side.
- Next step: one clearly-below rate gives the path list; all close or above gives the campaign with the computed
  dart count (checked against a hand computation) and the missing groups.
- Limits: a group present only in dev is marked; block mode adds the warning; "removals not measurable" present for
  `captures/` and absent for a campaign report.
- 0 judged darts: report renders with "n/a" everywhere.
- Privacy (CV15): a report built from synthetic sessions contains no session name, file name, path separator from the
  inputs, or image data (scan for the input strings and for base64 or data URLs).

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: report content and verdict rule fully specified.

## Implementation notes
