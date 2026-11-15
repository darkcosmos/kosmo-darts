# T13: Metrics, tuning, frozen thresholds and eval guard

## Goal
Aggregate per-dart, per-volley and per-event results into the rates, intervals and breakdowns the issue asks for,
tune the thresholds on dev, and guard the eval run. The report itself is T13b.

## Files
- Added `tools/camera/kosmo_camera/metrics.py`: `wilson(k, n)`, `quantile_ci(values, q)` (order statistics, stdlib
  `math.comb`), `outcome(decision, truth, annotation)` (CV7), `aggregate(results) -> Metrics` with breakdowns (CV14)
  and the counts of every skipped case (null photo, missing annotation, no position, no region, no reference
  calibration per CV19 reason, photos excluded by `--accept-drift`). CV9, CV12 and the CV14 near/far, tripod and
  lighting groups use each photo's reference calibration from T8b (`PhotoDrift.reference_points`); CV7 and CV8 do
  not use it.
- Added `tools/camera/kosmo_camera/tune.py`: on dev results only, grid over `tau_low < tau_sure`; pick the pair with
  unflagged errors <= 1 % that maximises correct; writes a proposal `tuned.json` in the run folder (developer-fix
  commits it as `tools/camera/config/frozen.json`: thresholds and model SHA-256 only, A3); no feasible pair gives an
  explicit `NoFeasiblePair` result with the best reachable error rate, and no proposal file.
- Added `tools/camera/kosmo_camera/frozen.py`: the single reader of `frozen.json` (strict per CV4: `format`
  `"kosmo-camera-frozen"`, `version` 1, `tauLow`, `tauSure` in [0, 1] with `tauLow <= tauSure`, 64-hex
  `modelSha256`, no other key, 64 KiB cap) and `guard_eval(frozen_path, model_path, split_bytes, lock)` (CV4).
- Added tests `tools/camera/tests/test_metrics.py`, `test_tune.py`, `test_frozen.py`.

## Depends on
T8b, T12.

## Rules
CV4, CV7, CV8, CV9, CV12, CV13, CV14, CV17, CV19. Tuning reads dev only: passing an eval or campaign result raises.
CV8 boundaries: correct `>=` 95 % passes, unflagged errors `<=` 1 % passes.

## Tests
- Wilson: (0, 10), (10, 10), (95, 100) against reference values to 1e-4; n = 0 gives "n/a".
- Quantile CI: median of 1..99 and p95 intervals against hand-computed order statistics; too few values gives "n/a".
- Outcome table: every CV7 case (correct, flagged, wrong value, miss correct, miss wrong, hidden tip).
- Breakdowns: synthetic results land in the right near/far, ring, lighting, tripod and zoom rows.
- CV8 pass at exactly 95 % correct and exactly 1 % unflagged errors; fail just beyond each.
- 0 judged darts: every rate is "n/a", no division error.
- Tuning: on scripted scores, the chosen pair respects the 1 % bound; scores where no pair reaches it give
  `NoFeasiblePair`; eval input raises.
- Frozen reader: valid file; unknown key, missing key, short or non-hex hash, `tauLow > tauSure`, other version each
  rejected.
- Guard: missing `frozen.json`, wrong model hash, missing or mismatched split lock, all matching.
- Drift: CV9 on a drifted photo uses the compensated reference (success where the stale segment would fail); a
  volley whose first photo is unmeasurable is not evaluated and counted; CV12 skips and counts such photos; the
  correct and unflagged-error rates are identical with and without drift data.

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: statistics and strict reader specified in CV4, CV8 to CV14.

## Implementation notes
