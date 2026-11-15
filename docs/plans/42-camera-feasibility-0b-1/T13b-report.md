# T13b: Preliminary report

Status: on hold (D30): the report gains the per-photo calibration measure (ADR 0017); revised at plan update P2.

## Goal
Write the numbers-only preliminary report of decision K0 (Markdown to paste, JSON for the squad), with its limits
section, the verdict per threshold and either a path proposal or the confirmation campaign specification.

## Files
- Added `tools/camera/kosmo_camera/report.py`: `build_report(metrics, context) -> (markdown, json)` with sections:
  Set and split (mode, flag "plus optimiste" in block mode, units and judged darts per set, and "Validation croisée
  par session" with judged darts of dev 1 and dev 2, sessions named by role only, CV15); Results (every CV8 to
  CV12 rate with count, 95 % interval, target, pass or fail, verdict); Breakdowns (CV14 tables); Tip error (median,
  p95, intervals); Public data; Entraînements; Limits; Next step; Hashes and versions (model SHA-256 and size,
  `frozen.json` and split SHA-256, tool versions). `build_comparison(choice) -> (markdown, json)` for the `compare`
  run of T14: Public data plus Entraînements.
- Public data section (CV15, CV21, CV22, CV23): sets used by the measured model (slug, status, licence, images kept
  and dropped per reason, "attribution : voir docs/camera/datasets.md") or "aucun jeu public"; pseudo-labels:
  calibration (`tauCalHigh`, `tauCalLow` or "aucun", support and precision on the out-of-training dev predictions,
  public images labelled, dropped per rule) and v17 tips (`tauTip`, boxes accepted / total and rate, images kept /
  total and rate, images dropped per rule of D27, median and p95 distance in px to the box's lower-right corner);
  then the comparison of T13 `compare`, headed by the glosses "A : photos de Kosmo + jeux publics de détection ;
  B : A + pointes v17 pseudo-étiquetées": one row per version (sans jeux publics / A / B) with correct and unflagged
  rates and intervals on the out-of-training dev darts, tuned pair or "aucune paire", CV9 successes, CV12 median,
  training images (Kosmo, public); one row per McNemar step ("A contre sans", "B contre <version retenue>") with
  `b`, `c`, `p` to 4 decimals, alpha 0,05 and the decision ("retenue" / "non retenue : écart non significatif" /
  "non retenue : aucune paire faisable"); versions not built with their reason; the retained version and its
  `full` model SHA-256.
- Entraînements section: number of trainings, one row per training (version, fold, held-out role or "aucune" for
  `full`, Kosmo and public images, duration in minutes), total duration in hours and minutes.
- Limits lines (CV22, CV24): the same out-of-training predictions serve the tuning and the comparison; the
  pseudo-label thresholds come from the dev photos and the labelling models (`full`) saw both dev sessions; the
  thresholds tuned on fold models are applied to the final `full` model, trained on twice the dev photos.
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
T13, T9d (manifest pseudo-label figures).

## Rules
CV8 to CV15, CV17, CV19, CV21 to CV24, D12, D23, D25 to D27. Interval at exactly the target counts as "close or
above" (not clearly below). The dev report (K5.7) takes its photo metrics from the out-of-training predictions and
its events from the fold model of the held-out session (T14), never from a `full` model.

## Tests
- Verdict: interval entirely below 95 % gives "clearly below"; upper bound exactly 95 % gives "close or above"; same
  for the 1 % bound from the other side.
- Next step: one clearly-below rate gives the path list; all close or above gives the campaign with the computed
  dart count (checked against a hand computation) and the missing groups.
- Limits: a group present only in dev is marked; block mode adds the warning; "removals not measurable" present for
  `captures/` and absent for a campaign report.
- 0 judged darts: report renders with "n/a" everywhere.
- Drift summary present with its counts; an `--accept-drift` run shows the excluded count.
- Public data: a model trained with two synthetic slugs lists both with status, licence and counts; without,
  "aucun jeu public"; pseudo-label figures rendered from a scripted manifest (rates and px distances to one decimal,
  "aucun" for a missing threshold, drops per rule); a three-version choice renders three version rows, the glosses,
  two McNemar step rows with `b`, `c`, `p` (0.0461 rendered "0,0461") and their decisions, and the retained
  version; B not built renders two version rows, one step plus its reason; only `sans` renders "sans jeux publics
  seulement"; the Q5 decision text for an infeasible candidate.
- Entraînements: 8 scripted trainings render 8 rows, the count and the total (hand-computed); a `full` row shows
  "aucune"; the three CV22/CV24 limit lines present.
- Privacy scan also run on the comparison output; slugs and the roles `dev 1`/`dev 2` are allowed, session names
  and public `file_name` values (from the scripted pseudo files) are not.
- Privacy (CV15): a report built from synthetic sessions contains no session name, file name, path separator from the
  inputs, or image data (scan for the input strings and for base64 or data URLs).

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: report content, comparison rows and verdict rule fully specified (CV22, D26).

## Implementation notes
