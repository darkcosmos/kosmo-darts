# T13: Metrics, tuning, frozen thresholds and eval guard

CANCELLED (2026-11-18): the RF-DETR chain is not built; this work is rewritten in phases 2 to 4 after their prototypes (root plan N12, N18).

HELD by D30: the drift guard and compensation parts (CV9, CV12, CV14 via CV19) are replaced at P2; CV9 becomes per photo.

## Goal
Aggregate per-dart, per-volley and per-event results into the rates, intervals and breakdowns the issue asks for,
tune the thresholds on out-of-training dev predictions, select the version by the paired McNemar test (D26), and
guard the eval run. The report itself is T13b.

## Files
- Added `tools/camera/kosmo_camera/metrics.py`: `wilson(k, n)`, `quantile_ci(values, q)` (order statistics, stdlib
  `math.comb`), `outcome(decision, truth, annotation)` (CV7), `aggregate(results) -> Metrics` with breakdowns (CV14)
  and the counts of every skipped case (null photo, missing annotation, no position, no region, no reference
  calibration per CV19 reason, photos excluded by `--accept-drift`). CV9, CV12 and the CV14 near/far, tripod and
  lighting groups use each photo's reference calibration from T8b (`PhotoDrift.reference_points`); CV7 and CV8 do
  not use it.
- Added `tools/camera/kosmo_camera/tune.py`: on dev results built from out-of-training predictions only (T9o; a
  result tagged with a `full` model or an eval or campaign result raises), grid over `tau_low < tau_sure`; pick the
  pair with unflagged errors <= 1 % that maximises correct; `NoFeasiblePair` carries the best reachable error rate
  and, for the McNemar outcomes, the pair with the fewest unflagged errors (ties to more correct). Writes a proposal
  `tuned.json` for the retained version: its tuned pair and the SHA-256 of that version's `full` `.tflite`
  (developer-fix commits it as `tools/camera/config/frozen.json`, thresholds and model SHA-256 only, A3); no
  feasible pair: no proposal file.
- Added `tools/camera/kosmo_camera/frozen.py`: the single reader of `frozen.json` (strict per CV4: `format`
  `"kosmo-camera-frozen"`, `version` 1, `tauLow`, `tauSure` in [0, 1] with `tauLow <= tauSure`, 64-hex
  `modelSha256`, no other key, 64 KiB cap) and `guard_eval(frozen_path, model_path, split_bytes, lock)` (CV4).
- Added `tools/camera/kosmo_camera/compare.py` (root "Approach, Version selection", CV22, D26):
  `mcnemar_one_sided(b, c) -> float` (exact, `math.comb`, 1.0 when `b + c = 0`); `select_version(versions,
  not_built, trainings) -> Choice` where `versions` maps `sans`, `A`, `B` (any subset, `sans` required) to the
  per-dart dev results from that version's out-of-training predictions plus, per fold, its manifest `version`,
  `kosmoPartSha256`, fold plan and hyperparameters (from `train-run.json`), public sets and pseudo-label figures
  (CV23, copied, never recomputed); `not_built` maps a missing version to its reason; `trainings` lists every
  `train-run.json` (version, fold, held-out role, images, duration). Tunes each version with `tune.py`, takes each
  dart's CV7 outcome at that version's pair, then runs the sequence `sans` -> `A` -> `B`: `b`, `c`, `p` against the
  version retained so far, candidate retained only when `p < MCNEMAR_ALPHA`, and never when it has no feasible pair
  while the retained version has one (Kosmo, Q5, D34: the 1 % unflagged-error cap comes before McNemar, the simpler
  version stays; reason `no-feasible-pair` in its step). Returns the retained version, one step per
  candidate (`b`, `c`, `p`, decision, reason), every version's figures (CV8 rates with Wilson intervals, tuned pair
  or `NoFeasiblePair`, CV9 successes, CV12 median), the trainings with count and total duration, and whether step
  13 is needed (B retained and `models/B/full` missing). Only `sans`: "sans jeux publics seulement".
- Changed `defaults.json` and `config.py`: `MCNEMAR_ALPHA` 0.05, pinned once in `test_config.py`.
- Changed `docs/adr/0016-camera-feasibility-tooling.md`, section "Public data and version selection" (T9): the
  sequential McNemar selection, the alpha and why paired darts (D26), the 1 % cap gate before McNemar (D34).
- Added tests `tools/camera/tests/test_metrics.py`, `test_tune.py`, `test_frozen.py`, `test_compare.py`.

## Depends on
T8b, T9o (predictions file), T12.

## Rules
CV4, CV7, CV8, CV9, CV12, CV13, CV14, CV17, CV19, CV22, CV24, D25, D26. Tuning and comparison read out-of-training
dev predictions only: an eval or campaign result, or a result from a `full` model, raises. All versions must share
the fold plan, the `kosmoPartSha256` of each fold and the hyperparameters, else `compare` raises. CV8 boundaries:
correct `>=` 95 % passes, unflagged errors `<=` 1 % passes. The former overlap rule and its tests are not built
(replaced by D26 in the plan, T3 of the design review).

## Tests
- Wilson: (0, 10), (10, 10), (95, 100) against reference values to 1e-4; n = 0 gives "n/a".
- Quantile CI: median of 1..99 and p95 intervals against hand-computed order statistics; too few values gives "n/a".
- Outcome table: every CV7 case (correct, flagged, wrong value, miss correct, miss wrong, hidden tip).
- Breakdowns: synthetic results land in the right near/far, ring, lighting, tripod and zoom rows.
- CV8 pass at exactly 95 % correct and exactly 1 % unflagged errors; fail just beyond each.
- 0 judged darts: every rate is "n/a", no division error.
- Tuning: on scripted scores, the chosen pair respects the 1 % bound; scores where no pair reaches it give
  `NoFeasiblePair` with its fallback pair (tie to more correct); eval input and a `full`-model result raise;
  `tuned.json` holds the retained version's pair and its `full` SHA-256.
- Frozen reader: valid file; unknown key, missing key, short or non-hex hash, `tauLow > tauSure`, other version each
  rejected.
- Guard: missing `frozen.json`, wrong model hash, missing or mismatched split lock, all matching.
- Drift: CV9 on a drifted photo uses the compensated reference (success where the stale segment would fail); a
  volley whose first photo is unmeasurable is not evaluated and counted; CV12 skips and counts such photos; the
  correct and unflagged-error rates are identical with and without drift data.
- McNemar (CV22): `(b, c)` = (0, 0) gives 1, (5, 0) gives 0.03125, (4, 0) gives 0.0625, (10, 3) gives 0.0461,
  (9, 3) gives 0.0730 (to 1e-4); `b` and `c` counted on the same darts from scripted outcomes.
- Sequence on scripted results: A not significant then B tested against `sans`; A retained then B tested against
  A; B significant against `sans` while A was not retained (B retained); B not built (listed with its reason);
  `sans` alone ("sans jeux publics seulement"); (5, 0) retains, (4, 0) does not; D34 gate: an infeasible candidate
  with `p < 0.05` does not replace a feasible retained version, while an infeasible retained version can be
  replaced; step 13 needed only when B is retained without `models/B/full`.
- Refusals: different fold plan, `kosmoPartSha256` of a fold or hyperparameters across versions, a duplicated
  version name, a missing `sans`, an eval or campaign result, a `full`-model result; pseudo figures and trainings
  (count, total duration) carried unchanged.

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: statistics, strict reader and the McNemar selection fully specified in CV4, CV8 to CV14, CV22 and
D26, with hand values.

## Implementation notes
