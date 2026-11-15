# T9: COCO dataset builder and public import

## Goal
Build the RF-DETR training dataset (COCO layout) from annotated dev photos and accepted public sets, never from eval.

## Files
- Added `tools/camera/kosmo_camera/dataset.py`: `build_dataset(captures, annotations, public_dirs, out_dir, seed)`:
  dev units of the locked split only (T4b `check_lock`, block mode honoured); one image per judged dart photo with a VISIBLE tip or an OFF_BOARD/HIDDEN status (negatives keep
  earlier tips of the volley), boxes for every tip known in that photo (earlier VISIBLE tips of the volley included)
  and the 4 calibration points of that photo's reference calibration (T8b `measure_session`, CV19: segment points
  plus the measured drift); photos without reference calibration are dropped, counted per reason; before building,
  `drift.require_clean(dev drifts, accept_drift)` (beyond photos refuse the build, or are dropped and counted with
  `accept_drift=True`); square boxes of side `BOX_FRACTION` times the longer image side,
  clipped; train and valid subsets split by session (fixed seed, about 85/15); images resized so the longer side is
  at most 1600 px (labels scaled, aspect kept, no letterbox or padding: the model squashes to 384x384, D16), written
  as JPEG; `_annotations.coco.json` per subset as `rfdetr` expects, `categories` with ids 0 to 4 in
  `model_spec.CLASS_NAMES` order (D16); a `manifest.json` with counts per subset, drop counts per reason, the
  `accept_drift` flag and the list of source session names (stays in `~/kosmo-runs`).
- Added `tools/camera/kosmo_camera/public_import.py`: reads a COCO export plus a class map file
  (`<set>/kosmo-class-map.json`: source class to `tip`, `cal1`..`cal4` or `drop-image`) and converts its points to
  our boxes; images whose calibration convention differs are dropped (map says `drop-image`).
- Added tests `tools/camera/tests/test_dataset.py`, `test_public_import.py`.

## Depends on
T3 (`model_spec.CLASS_NAMES`), T4b, T8b.

## Rules
CV4 (an eval session or block raises even if passed explicitly; no lock or a wrong hash raises), CV17 (`captures-eval/`
refused), CV2, CV5, CV16 (sources read-only), CV19, CV20, D16. Constants from `config.py`. Check in the `rfdetr`
1.12.1 source how COCO category ids reach the logit slots and record it in the notes; if `rfdetr` remaps them,
adapt so that slot k is `CLASS_NAMES[k]` (T3 contract).

## Tests
- Synthetic dev session: image count, box count per image (earlier tips present), box centres equal tips after
  resizing, boxes clipped at the border, COCO JSON readable by `json` with consistent ids.
- Eval units are skipped and refused when forced, in session and in block mode; missing or mismatched lock raises;
  a `captures-eval/` path raises (CV4, CV17).
- Missing calibration annotation: the session is skipped with a counted reason.
- T7 session with a 3 px drift from dart 4 (under threshold): calibration box centres of photos 4+ equal the
  drifted true points within 0.3 px after resizing; a second segment is honoured; a beyond photo refuses the build,
  and with `accept_drift` is dropped and counted; an unmeasurable photo is dropped and counted.
- COCO `categories` ids 0 to 4 named `tip`, `cal1`..`cal4`; every annotation's `category_id` in that range.
- Public import: class map applied, `drop-image` honoured, unknown class raises.
- Source folders unchanged (hashes before and after).

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: specified conversion, CV4 guard and drift guard.

## Implementation notes
