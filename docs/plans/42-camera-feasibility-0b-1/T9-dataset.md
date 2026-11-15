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
  `public` argument (root CV22, D23): `"none"`, `"A"` (every `tip`/`whole-dart-checked` set of the map present on
  disk; missing folders listed) or `"B"` (A plus the `pseudo-tip-box` sets); `version` written in the manifest
  (`sans`, `A` or `B`).
  `A` and `B` need `cal_labels` (a callable `(slug, subset, file_name, image_sha256) -> list of (calN, x, y) | drop
  reason`, provided by T9c from `pseudo-cal.json`; tests pass a stub) and `B` also needs `tip_labels` (same shape
  for tips, T9d); missing input raises. `pseudo_files` (kind, SHA-256) passed by the caller go to the manifest.
  Public images go to train only, resized like Kosmo's (longer side 1600 px); the Kosmo part (images, labels,
  train/valid assignment) is byte-identical for every `public` value (CV22).
- Added `tools/camera/kosmo_camera/public_import.py`: strict reader of `tools/camera/config/public-class-maps.json`
  (format of root "Files and types", CV21: unknown key, target outside `tip`, `whole-dart-checked`,
  `pseudo-tip-box`, `ignore`, `drop-image`, slug not matching `^[a-z0-9_-]{1,64}$`, 64 KiB cap rejected); reads every
  subset (`train`/`valid`/`test` `_annotations.coco.json`) of `<public_root>/<slug>/` for mapped slugs only (other
  folders never opened); refuses a set with an annotated category absent from the map, a mapped name absent from
  the set, differing category lists between subsets or a slug absent from the map; categories without any
  annotation are ignored. Per image: `drop-image` drops it, `ignore` drops the annotation, `tip` becomes our square
  box at the source box centre, `whole-dart-checked` drops the annotation and drops the image when no `tip` centre
  lies inside its box (counted), `pseudo-tip-box` boxes are returned raw (source px) for T9d and never labelled.
  Also exposes `iter_images(slug)` (validated image path, size, SHA-256, raw boxes per target) for T9c and T9d.
  Downloads are untrusted (root CV21, S10): `MAX_JSON_BYTES` (64 MiB) and `MAX_IMAGE_BYTES` (32 MiB) named in the
  module and checked before reading; JSON invalid, oversized, with duplicated image or category ids or a dangling
  `image_id`/`category_id` refuses the set; per image, `file_name` must be a bare name (no `/`, `\`, `..`, NUL,
  absolute path) whose `lstat` is a regular file (no symlink) inside `<slug>/<subset>/`, opened with `O_NOFOLLOW`;
  oversized, undecodable (`Image.MAX_IMAGE_PIXELS` kept, `DecompressionBombError`, truncated), size differing from
  the JSON, or a box not finite or not inside the image: image dropped and counted per reason in the manifest.
- Added `tools/camera/config/public-class-maps.json` with `sets` empty (T9b fills it).
- Changed `docs/camera/datasets.md` (French): the search rows are removed; table header
  `| slug (dossier) | jeu | lien | licence | attribution | statut | motif |` with no row yet (T9b writes the six rows
  of root D23); statuses `accepted`, `conditional` (usable only by the method in its motif), `rejected`. Acceptance
  rule kept (CC BY 4.0 only, DeepDarts excluded); "How a set is added" points to the class map file.
- Added tests `tools/camera/tests/test_dataset.py`, `test_public_import.py`, `test_datasets_doc.py` (row structure:
  7 cells, status in `accepted|conditional|rejected`, non-empty motif, link `https://universe.roboflow.com/` and
  licence cell matching `CC BY 4.0 (page vue par Kosmo le YYYY-MM-DD)` for `accepted` and `conditional`; every map
  slug is a row, `conditional` exactly when its map has a `pseudo-tip-box` target, else `accepted`; no map entry
  for a `rejected` row; zero rows allowed; attribution text not checked).
- Changed `docs/adr/0016-camera-feasibility-tooling.md`: new section "Public data and version selection" (A17): map
  file and its targets, train-only rule, public datasets as untrusted input with the CV21 validation (T9c, T9d and
  T13 add their parts).

## Depends on
T3 (`model_spec.CLASS_NAMES`), T4b, T8b.

## Rules
CV4 (an eval session or block raises even if passed explicitly; no lock or a wrong hash raises), CV17 (`captures-eval/`
refused), CV2, CV5, CV16 (sources read-only), CV19, CV20, CV21, CV22, D16, D23, D24. Writes CV21 and CV22 into
`docs/rules/camera.md` (CV23 is T9c's). Real data are not needed here: everything is built and tested on
synthetic COCO exports; T9b commits the real map. Root "Approach, Public datasets" gives the size check (S10). Constants from `config.py`. Check in the `rfdetr`
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
- Public import on a synthetic Roboflow-style export (with the unannotated supercategory at id 0, unmapped and
  ignored): `tip` box centred on the source box centre with side `BOX_FRACTION`; `whole-dart-checked` with a tip
  centre inside (kept, annotation absent) and without (image dropped, counted); `ignore` and `drop-image` honoured;
  annotated unmapped category, mapped name absent, unknown slug, differing subset categories each raise; a
  `pseudo-tip-box` set gives no label and `public="B"` without `tip_labels` raises; `"A"` without `cal_labels`
  raises; a stub `cal_labels` adds its boxes and its drop reason drops the image; an unmapped folder next to the
  mapped ones is never opened (open calls recorded); map strict reader rejections and cap.
- Real size: a synthetic 2700x3600 JPEG passes `MAX_IMAGE_BYTES` and `Image.MAX_IMAGE_PIXELS` and is kept.
- Untrusted downloads (S10), synthetic files, deterministic: `file_name` `../x`, an absolute path and a symlink to a
  file outside the public folder: each image dropped and counted, the outside file never opened (open calls
  recorded); image of `MAX_IMAGE_BYTES` + 1: dropped; decompression bomb (`Image.MAX_IMAGE_PIXELS` lowered in the
  test only, restored by the fixture): dropped; box NaN, infinite, outside the image: dropped; JSON of
  `MAX_JSON_BYTES` + 1, invalid JSON, duplicated image id, duplicated category id, dangling `image_id`, dangling
  `category_id`: each refuses the set; public folder unchanged (hashes before and after).
- `public="none"`, `"A"` and `"B"` (stubs) on the same seed: same `kosmoPartSha256`, same valid subset, public
  images only in train, `version` in the manifest; one accepted folder missing: `"A"` built from the other, the
  missing slug listed; empty map or no accepted folder: `"A"` raises `NotBuilt` with the reason "aucun jeu
  accepté disponible" (written by T14 for `compare`); no public image kept: `NotBuilt` "aucune image publique
  conservée"; `"B"` with no `pseudo-tip-box` image kept: `NotBuilt` (root "Approach, Three trainings").
- Source folders unchanged (hashes before and after).

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: specified conversion, CV4, CV21 and drift guards, tested on synthetic COCO.

## Implementation notes
