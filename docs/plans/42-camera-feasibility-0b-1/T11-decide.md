# T11: Per-dart decision

## Goal
Turn detector points on each dart photo into what the app would announce for that dart: a value, a flag "to check",
or a miss, plus the predicted calibration, as the input of every measure.

## Files
- Added `tools/camera/kosmo_camera/decide.py`: `Thresholds(tau_low, tau_sure, match_radius_mm=6.0)`;
  `calibrate(points) -> Calibration | None` (best point per `calN` class with score `>=` `tau_low`, `None` if one is missing,
  H from T8); `decide_volley(photos, detector, thresholds) -> list[DartDecision]` where `DartDecision` holds the dart
  number, announced `(segment, multiplier)` or miss, `flagged`, the chosen tip pixel and mm, score, calibration
  result. Old darts: candidates at a distance `<=` `match_radius_mm` of a tip announced earlier in the volley.
  Scores compare with `>=` (`tau_low` and `tau_sure` included). Several candidates of one class: highest score wins;
  equal scores: first in detector output order (stable sort). Default values from `config.py`.
- Added tests `tools/camera/tests/test_decide.py` with a `FakeDetector` returning scripted points.

## Depends on
T3, T8.

## Rules
CV5, CV6, CV7, D16. Detector contract (T3): `detect` returns one point per query in image pixels (best of the 5
classes, the no-object slot ignored), sorted by score, unthresholded; `decide` applies `tau_low` itself. The
decision never reads the clicked calibration or the drift (CV19): it uses the predicted calibration only, so CV7
does not depend on Kosmo's calibration clicks. Order of judgement: by entry number inside the volley; voided entries and null photos skipped (CV5).
Flagged when the best new candidate score is in `[tau_low, tau_sure)` or when the photo's calibration is `None`
(then the announced value is the one from the previous successful calibration of the volley, or none).

## Tests
- Three darts in a volley: each photo shows the earlier tips; each decision picks the new one.
- Candidate at 5.9 mm and at exactly 6.0 mm of an old tip is old, at 6.1 mm is new.
- Score exactly `tau_low` gives a flagged candidate (not ignored).
- Two `cal2` points on one photo: the higher score is used; two new tips with equal scores: the first one wins.
- Score at `tau_sure` announced unflagged; just below flagged; below `tau_low` ignored, so a miss is announced.
- Truth miss with no new candidate gives a miss (CV7 correct case); with a candidate above `tau_sure`, an announced
  value (the error case is counted in T13).
- Missing `cal3` on one photo: flagged, value from the previous calibration; on the first photo: flagged, no value.
- Voided entry between two darts and a dart with null photo are skipped.
- On a synthetic session with a perfect fake detector (truth points), every dart is correct and unflagged.
- Same session with its annotation calibration segments removed or shifted: identical decisions (CV7 independent
  of the clicked calibration).

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: rules CV5 to CV7 specified, fake detector tests.

## Implementation notes
