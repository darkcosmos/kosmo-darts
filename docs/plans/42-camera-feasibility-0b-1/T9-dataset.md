# T9: COCO dataset builder and public import

## Goal
Build the RF-DETR training dataset (COCO layout) from annotated dev photos and accepted public sets, never from eval.

## Files
- Added `tools/camera/kosmo_camera/dataset.py`: `build_dataset(captures, annotations, public_dirs, out_dir, seed)`:
  dev sessions only; one image per judged dart photo with a VISIBLE tip or an OFF_BOARD/HIDDEN status (negatives keep
  earlier tips of the volley), boxes for every tip known in that photo (earlier VISIBLE tips of the volley included)
  and the 4 calibration points of the session; square boxes of side `BOX_FRACTION` times the longer image side,
  clipped; train and valid subsets split by session (fixed seed, about 85/15); images resized so the longer side is
  at most 1600 px (labels scaled), written as JPEG; `_annotations.coco.json` per subset as `rfdetr` expects; a
  `manifest.json` with counts per subset and the list of source session names (stays in `~/kosmo-runs`).
- Added `tools/camera/kosmo_camera/public_import.py`: reads a COCO export plus a class map file
  (`<set>/kosmo-class-map.json`: source class to `tip`, `cal1`..`cal4` or `drop-image`) and converts its points to
  our boxes; images whose calibration convention differs are dropped (map says `drop-image`).
- Added `tools/camera/config/defaults.json` with `BOX_FRACTION` 0.025 and the split ratio.
- Added tests `tools/camera/tests/test_dataset.py`, `test_public_import.py`.

## Depends on
T8.

## Rules
CV4 (eval refused: a session after the cutoff raises, even if passed explicitly), CV2, CV5, CV16 (sources read-only).

## Tests
- Synthetic dev session: image count, box count per image (earlier tips present), box centres equal tips after
  resizing, boxes clipped at the border, COCO JSON readable by `json` with consistent ids.
- An eval session in the captures folder is skipped by listing and refused when forced (CV4).
- Missing calibration annotation: the session is skipped with a counted reason.
- Public import: class map applied, `drop-image` honoured, unknown class raises.
- Source folders unchanged (hashes before and after).

## Done when
`.venv/bin/python -m pytest tools/camera -q` green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: specified conversion and CV4 guard.

## Implementation notes
