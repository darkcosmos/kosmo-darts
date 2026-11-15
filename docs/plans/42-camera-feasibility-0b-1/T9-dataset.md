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
  `accept_drift` flag and the list of source session names (stays in `~/kosmo-runs`); plus `public` (per slug:
  licence, images kept, dropped per reason) and `kosmoPartSha256` (root "Data read, stored or exposed").
  `public` argument: `"none"`, `"all"` (every slug of the map) or a list of slugs; public images go to train only,
  the Kosmo part (images, labels, train/valid assignment) is byte-identical for every `public` value (CV22).
- Added `tools/camera/kosmo_camera/public_import.py`: strict reader of `tools/camera/config/public-class-maps.json`
  (format of root "Files and types", CV21: unknown key, target outside `tip`, `cal1`..`cal4`, `ignore`,
  `drop-image`, slug not matching `^[a-z0-9_-]{1,64}$`, 64 KiB cap rejected); reads every subset
  (`train`/`valid`/`test` `_annotations.coco.json`) of `<public_root>/<slug>/`, refuses a set with an unknown
  category name, differing category lists between subsets or a slug absent from the map; per image, `drop-image`
  drops it, `ignore` drops the annotation, `tip`/`calN` become our square box at the source box centre.
  Downloads are untrusted (root CV21, S10): `MAX_JSON_BYTES` (64 MiB) and `MAX_IMAGE_BYTES` (32 MiB) named in the
  module and checked before reading; JSON invalid, oversized, with duplicated image or category ids or a dangling
  `image_id`/`category_id` refuses the set; per image, `file_name` must be a bare name (no `/`, `\`, `..`, NUL,
  absolute path) whose `lstat` is a regular file (no symlink) inside `<slug>/<subset>/`, opened with `O_NOFOLLOW`;
  oversized, undecodable (`Image.MAX_IMAGE_PIXELS` kept, `DecompressionBombError`, truncated), size differing from
  the JSON, or a box not finite or not inside the image: image dropped and counted per reason in the manifest.
- Added `tools/camera/config/public-class-maps.json` with `sets` empty (T9b fills it).
- Changed `docs/camera/datasets.md` (French): the search rows are replaced by one row per set of root D20, table
  `| slug | jeu | lien | licence | attribution | statut |`, e.g. `| mat24_dartsv2 | mat24_dartsv2 (school) |
  https://universe.roboflow.com/school-asc0d/mat24_dartsv2 | CC BY 4.0 (page vue par Kosmo le 2026-11-15) | à
  compléter par Kosmo au téléchargement | accepted |` (S11: the date is the day Kosmo read the licence on the page;
  attribution copied from the page by Kosmo, never invented); four `accepted`, `dart-tip-detection-6d3mw` `conditional` (accepted only if its page shows CC BY 4.0
  at download), `bullseyebot` (Hugging Face, link "non fourni", licence "aucune") `rejected`. Acceptance rule kept
  (CC BY 4.0 only, DeepDarts excluded); "How a set is added" points to the class map file and T9b.
- Added tests `tools/camera/tests/test_dataset.py`, `test_public_import.py`, `test_datasets_doc.py` (row structure:
  6 cells, status in `accepted|conditional|rejected`, link `https://universe.roboflow.com/` and licence starting
  `CC BY 4.0` for `accepted` and `conditional`, every `accepted` licence cell matches
  `CC BY 4.0 (page vue par Kosmo le YYYY-MM-DD)`, every slug of the map is an `accepted` row; attribution text not checked).
- Changed `docs/adr/0016-camera-feasibility-tooling.md`: new section "Public data and version selection" (A17): map
  file, train-only rule, public datasets as untrusted input with the CV21 validation (T13 adds the selection order).

## Depends on
T3 (`model_spec.CLASS_NAMES`), T4b, T8b.

## Rules
CV4 (an eval session or block raises even if passed explicitly; no lock or a wrong hash raises), CV17 (`captures-eval/`
refused), CV2, CV5, CV16 (sources read-only), CV19, CV20, CV21, CV22, D16, D20. Writes CV21 and CV22 into
`docs/rules/camera.md`. Kosmo's real category lists are not needed here: everything is built and tested on
synthetic COCO exports; T9b adds the real maps when the lists arrive. Constants from `config.py`. Check in the `rfdetr`
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
- COCO `categories` ids 0 to 4 named `tip`, `cal1`..`cal4`, equal to `model_spec.CLASS_NAMES` order; every
  annotation's `category_id` in that range (T4 of the design review).
- Written images keep the source aspect ratio (no padding or letterbox; the model squashes to
  `model_spec.RESOLUTION`, 384).
- Source check (A15): `flow.py`, `drift.py` and `dataset.py` import `segment_for` from `annotations` and define no
  function of their own walking `fromDart` (AST scan of the three files).
- Public import on a synthetic Roboflow-style export (with the unused supercategory at id 0): map applied, `ignore`
  and `drop-image` honoured, unknown category, unknown slug, differing subset categories each raise; map strict
  reader rejections and cap.
- Untrusted downloads (S10), synthetic files, deterministic: `file_name` `../x`, an absolute path and a symlink to a
  file outside the public folder: each image dropped and counted, the outside file never opened (open calls
  recorded); image of `MAX_IMAGE_BYTES` + 1: dropped; decompression bomb (`Image.MAX_IMAGE_PIXELS` lowered in the
  test only, restored by the fixture): dropped; box NaN, infinite, outside the image: dropped; JSON of
  `MAX_JSON_BYTES` + 1, invalid JSON, duplicated image id, duplicated category id, dangling `image_id`, dangling
  `category_id`: each refuses the set; public folder unchanged (hashes before and after).
- `public="none"` and `"all"` on the same seed: same `kosmoPartSha256`, same valid subset, public images only in
  train; empty map: `"all"` equals `"none"` plus an empty `public` list.
- Source folders unchanged (hashes before and after).

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: specified conversion, CV4, CV21 and drift guards, tested on synthetic COCO.

## Implementation notes
