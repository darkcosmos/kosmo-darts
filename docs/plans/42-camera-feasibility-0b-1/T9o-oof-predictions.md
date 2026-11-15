# T9o: Out-of-training predictions of the cross-validation (CV24)

## Goal
Give every judged dev photo exactly one prediction made by the fold model that did not train on it, and store them
in a strict local file read by T9c, T9d and T13 (root "Approach, Cross-validation by session", D25).

## Files
- Added `tools/camera/kosmo_camera/predictions.py`: `predict_out_of_fold(run, version, detector_factory)` runs the
  `cv1` export on the dev 2 judged photos and the `cv2` export on the dev 1 judged photos (fold plan of
  `chain.json`, T9), through the T3 `Detector`; writes `predictions/<version>.json` atomically; `read(path)` strict
  reader; `load_for(run, version)` checks the file against the current `cv` exports (T9 `chain.check_inputs`).
- File format (root "Data read, stored or exposed"): `format` `"kosmo-camera-oof-predictions"`, `version` 1,
  `chainSha256`, `modelVersion` (`sans`|`A`|`B`), `folds` [{`fold` `cv1`|`cv2`, `heldOutRole`, `tfliteSha256`,
  `manifestSha256`}], `photos` [{`role` (`dev 1`|`dev 2`), `dart` (number in the CV3 order of the role),
  `detections` [{`class`, `score`, `x`, `y`} source px]}]; unknown or missing key, wrong type, class outside
  `model_spec.CLASS_NAMES`, a photo twice, cap `MAX_BYTES` (16 MiB) rejected. No image, path or session name.
- Changed `docs/rules/camera.md`: CV24 names the predictions file. Changed ADR 0016 "Public data and version selection":
  one prediction per dev photo, fold-to-model rule.
- Added tests `tools/camera/tests/test_predictions.py`.

## Depends on
T3 (`Detector`, `model_spec`), T9 (`chain.py`, fold plan, run folder paths).

## Rules
CV4, CV15 (roles only), CV24, D25, A19. A `full` export or the export of the wrong fold is refused (the fold's
`heldOutRole` must equal the photo's role). An export whose `export.json` does not match its `train-run.json` and
manifest raises `StaleStep` "Étape 3 à refaire …" (step numbers of root "Approach, Three trainings, run chain"; 7
for A, 11 for B). A version with `not-built/<version>.json` prints "Version <A|B> non construite : <raison>",
writes nothing and returns normally (the CLI exits 0, T14).

## Tests
Fake detector factory (scripted detections per photo, records which model saw which photo), synthetic chain from
the T9 helpers:
- Each judged dev photo predicted exactly once, by the fold that held it out; voided entries and unjudged photos
  absent; `full` never loaded.
- Swapped fold exports (cv1 model on dev 1) refused; a `full` path refused; an export changed after training:
  `StaleStep` with the step numbers.
- Strict reader: round trip, each rejection above, cap; no synthetic session name or file name in the file.
- `not-built/A.json` present: no file written, the French line printed.

## Done when
Python checks of root "Test plan" green (including `python -m pytest -q -m "not slow"` from `tools/camera`),
`kosmo-detekt` exit 0.

## Model
sonnet. Why: fold-to-model rule of CV24 and a strict local file regenerable by the chain (no migration), tested
with a fake detector.

## Implementation notes
