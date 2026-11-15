# T9p: Public import, strict class map and untrusted COCO exports

## Goal
Read the accepted public sets safely and turn their annotations into our targets (root CV21), before any dataset is
built. Split out of T9 (design review A18).

## Files
- Added `tools/camera/kosmo_camera/public_import.py`: strict reader of `tools/camera/config/public-class-maps.json`
  (format of root "Files and types": unknown key, target outside `tip`, `whole-dart-checked`, `pseudo-tip-box`,
  `ignore`, `drop-image`, slug not matching `^[a-z0-9_-]{1,64}$`, 64 KiB cap rejected); reads every subset
  (`train`/`valid`/`test` `_annotations.coco.json`) of `<public_root>/<slug>/` for mapped slugs only (other folders
  never opened); refuses a set with an annotated category absent from the map, a mapped name absent from the set,
  differing category lists between subsets or a slug absent from the map; categories without any annotation are
  ignored. Per image: `drop-image` drops it, `ignore` drops the annotation, `tip` becomes our square box at the
  source box centre (`BOX_FRACTION`), `whole-dart-checked` drops the annotation and drops the image when no `tip`
  centre lies inside its box (counted under its own rule, D27), `pseudo-tip-box` boxes are returned raw (source px)
  for T9d and never labelled. Exposes `iter_images(slug)` (validated path, size, SHA-256, raw boxes per target) for
  T9, T9c, T9d. Untrusted input (CV21, S10): `MAX_JSON_BYTES` (64 MiB) and `MAX_IMAGE_BYTES` (32 MiB) named in the
  module and checked before reading; JSON invalid, oversized, with duplicated image or category ids or a dangling
  `image_id`/`category_id` refuses the set; `file_name` must be a bare name (no `/`, `\`, `..`, NUL, absolute path)
  whose `lstat` is a regular file inside `<slug>/<subset>/`, opened with `O_NOFOLLOW`; oversized, undecodable
  (`Image.MAX_IMAGE_PIXELS` kept, `DecompressionBombError`, truncated), size differing from the JSON, or a box not
  finite or not inside the image: image dropped and counted per reason.
- Added `tools/camera/config/public-class-maps.json` with `sets` empty (T9b fills it).
- Changed `docs/camera/datasets.md` (French): search rows removed; header
  `| slug (dossier) | jeu | lien | licence | attribution | statut | motif |`, no row yet (T9b); statuses `accepted`,
  `conditional`, `rejected`; acceptance rule kept (CC BY 4.0 only, DeepDarts excluded); "How a set is added" points
  to the class map file.
- Changed `docs/rules/camera.md`: writes CV21. Changed `docs/adr/0016-camera-feasibility-tooling.md`: new section
  "Public data and version selection" (A17): map file, targets, train-only rule, CV21 validation.
- Added tests `tools/camera/tests/test_public_import.py`, `test_datasets_doc.py` (7 cells, status in
  `accepted|conditional|rejected`, non-empty motif, link `https://universe.roboflow.com/` and licence cell matching
  `CC BY 4.0 (page vue par Kosmo le YYYY-MM-DD)` for `accepted` and `conditional`; every map slug is a row,
  `conditional` exactly when its map has a `pseudo-tip-box` target; no map entry for a `rejected` row; zero rows
  allowed; attribution cell of accepted and conditional rows starts with `<jeu> par ` and contains `, CC BY 4.0 ;
  modifié : ` (D28)).

## Depends on
T1 (config). Batch 1 done.

## Rules
CV15, CV16 (sources read-only), CV21, D22, D23, D24, D27 (strict rules final, no provisional branch). Root "Approach,
Public datasets" gives the size check (S10).

## Tests
Synthetic Roboflow-style exports (helpers in `tests/coco_fixtures.py`, reused by T9, T9c, T9d): the cases of root
"Test plan, T9p": unannotated supercategory at id 0 ignored; `tip` box centred with side `BOX_FRACTION`;
`whole-dart-checked` with and without a tip centre inside (dropped, counted under `whole-dart-checked`); `ignore`,
`drop-image`; each set refusal; unmapped folder never opened (open calls recorded); map reader rejections and cap;
2700x3600 JPEG kept; each untrusted-file case dropped or refused with nothing read outside; folder hashes unchanged.

## Done when
Python checks of root "Test plan" green (including `python -m pytest -q -m "not slow"` from `tools/camera`),
`kosmo-detekt` exit 0.

## Model
sonnet. Why: strict reader and untrusted-input rules fully specified in CV21, tested on synthetic files.

## Implementation notes
