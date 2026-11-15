# T13: Metrics, tuning, eval guard, report

## Goal
Aggregate per-dart, per-volley and per-event results into the rates, intervals and breakdowns the issue asks for,
tune the thresholds on dev, guard the eval run and write the numbers-only report.

## Files
- Added `tools/camera/kosmo_camera/metrics.py`: `wilson(k, n)`, `quantile_ci(values, q)` (order statistics, stdlib
  `math.comb`), `outcome(decision, truth, annotation)` (CV7), `aggregate(results) -> Metrics` with breakdowns (CV14)
  and the counts of every skipped case (null photo, missing annotation, no position, no region).
- Added `tools/camera/kosmo_camera/tune.py`: on dev results only, grid over `tau_low < tau_sure`; pick the pair with
  unflagged errors <= 1 % that maximises correct; writes a proposal `tuned.json` in the run folder (developer-fix
  commits it as `tools/camera/config/frozen.json` with the model SHA-256).
- Added `tools/camera/kosmo_camera/report.py`: Markdown and JSON reports: set, counts, every CV8 to CV12 rate with
  its interval and target, pass or fail per threshold, breakdown tables, tip error median and p95, model SHA-256 and
  size, `frozen.json` SHA-256, tool versions. `guard_eval(frozen_path, model_path)` per CV4.
- Added tests `tools/camera/tests/test_metrics.py`, `test_tune.py`, `test_report.py`.

## Depends on
T12.

## Rules
CV4, CV7, CV8, CV9, CV12, CV13, CV14, CV15. Tuning reads dev only: passing an eval result raises.

## Tests
- Wilson: (0, 10), (10, 10), (95, 100) against reference values to 1e-4; n = 0 gives "n/a".
- Quantile CI: median of 1..99 and p95 intervals against hand-computed order statistics; too few values gives "n/a".
- Outcome table: every CV7 case (correct, flagged, wrong value, miss correct, miss wrong, hidden tip).
- Breakdowns: synthetic results land in the right near/far, ring, lighting, tripod and zoom rows.
- Tuning: on scripted scores, the chosen pair respects the 1 % bound; eval input raises.
- Guard: missing `frozen.json`, wrong model hash, matching hash.
- Privacy (CV15): a report built from synthetic sessions contains no session name, file name, path separator from the
  inputs, or image data (scan for the input strings and for base64 or data URLs).

## Done when
`.venv/bin/python -m pytest tools/camera -q` green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: statistics specified in CV8 to CV15.

## Implementation notes
