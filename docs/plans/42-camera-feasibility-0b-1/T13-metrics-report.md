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
- Added `tools/camera/kosmo_camera/compare.py` (root CV22, D21, D23): `select_version(versions, not_built) ->
  Choice` where `versions` maps `sans`, `A`, `B` (any subset, `sans` required) to the dev results of that version on
  the comparison set plus its manifest `version`, `kosmoPartSha256`, public sets and pseudo-label figures (CV23,
  copied from the manifest, never recomputed), and `not_built` maps a missing version to its reason; tunes each with
  `tune.py`, ranks them by the CV22 order (pairwise first difference, ties to the simpler version, simplicity order
  `sans` < `A` < `B`) and returns the retained version, the leader, the deciding step against each other version,
  every version's figures (CV8 rates with Wilson intervals, tuned pair or `NoFeasiblePair`, CV9 successes, CV12
  median) and the overlap flags. Only `sans`: "sans jeux publics seulement". Overlap branch: unless main's
  assignment carries Kosmo's answer to Q2, it fails closed (root CV22): the simplest version whose correct-rate
  interval overlaps the leader's is kept and `Choice` carries the French message of CV22 naming it, shown by T13b
  and printed by `compare` (T14), not only logged. Q2's answer later replaces this branch and its test in one commit.
- Changed `docs/adr/0016-camera-feasibility-tooling.md`, section "Public data and version selection" (T9): adds the
  three-version CV22 order and the overlap case.
- Added tests `tools/camera/tests/test_metrics.py`, `test_tune.py`, `test_frozen.py`, `test_compare.py`.

## Depends on
T8b, T12.

## Rules
CV4, CV7, CV8, CV9, CV12, CV13, CV14, CV17, CV19, CV22. Tuning and comparison read dev only: passing an eval or campaign result raises.
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
- Comparison (CV22) on scripted fake-detector results, two and three versions: one case per decision step
  (feasible beats infeasible, lower best reachable rate, more correct, fewer unflagged, more CV9, lower CV12
  median), exact tie keeps the simpler version (`sans` over `A`, `A` over `B`, three-way tie keeps `sans`), one case
  where two steps disagree (one version ahead on correct darts, the other ahead on CV9 and CV12: the first wins, step
  2 reported; T3), three versions with B leading: deciding step reported against `sans` and against `A`; overlap
  fails closed until Q2: B leads and overlaps A only (A kept, message names A), B leads and overlaps both (`sans`
  kept), A leads and overlaps `sans` (`sans` kept), no overlap (leader kept, no message); different
  `kosmoPartSha256`, a duplicated version name, a missing `sans`, or an eval or campaign result raises; only `sans`
  gives "sans jeux publics seulement"; `not_built` B listed with its reason; pseudo figures carried unchanged.

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: statistics, strict reader and selection rule (three versions) specified in CV4, CV8 to CV14, CV22.

## Implementation notes
