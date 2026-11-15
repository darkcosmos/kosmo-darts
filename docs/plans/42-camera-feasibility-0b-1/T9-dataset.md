# T9: Fold plan, run chain and COCO dataset builder per fold

Status: on hold (D30): needs per-photo labels; revised at plan update P2 (root plan "Tasks").

## Goal
Build the RF-DETR training datasets (COCO layout) of the cross-validation by session (root CV24, D25): folds `cv1`,
`cv2`, `full` per version, from annotated dev photos and the public import of T9p, never from eval; and the run
folder with its hash checks shared by every later step (root "Approach, Three trainings, run chain", A19).

## Files
- Added `tools/camera/kosmo_camera/chain.py`: `fold_plan(split, lock, annotations) -> FoldPlan` (roles `dev 1`,
  `dev 2` in ascending CV3 key order; refuses block mode, a judged dev session count other than 2, a session under
  `CV_MIN_SESSION_DARTS` judged darts, each with the French message of root "Approach, Cross-validation by session",
  nothing written); run folder layout (fixed paths from run, version, fold); strict reader and atomic writer of
  `chain.json` (`format` `"kosmo-camera-chain"`, `version` 1, split SHA-256, fold plan by role with judged darts,
  hyperparameters, `defaults.json` and map SHA-256; unknown key, cap 64 KiB rejected); `record(path, inputs)` and
  `check_inputs(path, step)` (recorded SHA-256 of every input versus the files on disk; mismatch raises `StaleStep`
  with "Étape n à refaire : <fichier> ne correspond plus à l'étape m", step numbers of the root chain); `NotBuilt`
  records in `not-built/<version>.json`. The single checker: later tasks call it, never re-implement it.
- Added `CV_MIN_SESSION_DARTS` (50) to `defaults.json` and `config.py`, pinned once in `test_config.py`.
- Added `tools/camera/kosmo_camera/dataset.py`: `build_datasets(run, captures, annotations, public, cal_labels=None,
  tip_labels=None)` writes `datasets/<version>/cv1`, `cv2`, `full` of the run folder: the training session(s) of the
  fold only (T4b `check_lock`; `cv1` dev 1, `cv2` dev 2, `full` both); the held-out session never appears (no image,
  label or valid photo); one image per judged dart photo with a VISIBLE tip or an OFF_BOARD/HIDDEN status (negatives keep
  earlier tips of the volley), boxes for every tip known in that photo (earlier VISIBLE tips of the volley included)
  and the 4 calibration points of that photo's reference calibration (T8b `measure_session`, CV19: segment points
  plus the measured drift); photos without reference calibration are dropped, counted per reason; before building,
  `drift.require_clean(dev drifts, accept_drift)` (beyond photos refuse the build, or are dropped and counted with
  `accept_drift=True`); square boxes of side `BOX_FRACTION` times the longer image side,
  clipped; inner valid subset (CV24): blocks of `SPLIT_BLOCK_VOLLEYS` consecutive volleys of the training
  session(s), `VALID_RATIO` of the blocks drawn with the fixed seed, the rest train; images resized so the longer side is
  at most 1600 px (labels scaled, aspect kept, no letterbox or padding: the model squashes to 384x384, D16), written
  as JPEG; `_annotations.coco.json` per subset as `rfdetr` expects, `categories` with ids 0 to 4 in
  `model_spec.CLASS_NAMES` order (D16); a `manifest.json` per fold with `fold`, training and held-out roles, counts
  per subset, drop counts per reason, the `accept_drift` flag (no session name: roles only, CV15); plus `public`
  (per slug: licence, images kept, dropped per rule) and `kosmoPartSha256` of that fold (root "Data read, stored or
  exposed"). `public="none"` first creates the run folder and `chain.json` (refuses an existing chain folder);
  every call checks `chain.json` and its inputs with `chain.check_inputs`.
  `public` argument (root CV22, D23): `"none"`, `"A"` (every `tip`/`whole-dart-checked` set of the map present on
  disk; missing folders listed) or `"B"` (A plus the `pseudo-tip-box` sets); `version` written in the manifest
  (`sans`, `A` or `B`).
  `A` and `B` need `cal_labels` (a callable `(slug, subset, file_name, image_sha256) -> list of (calN, x, y) | drop
  reason`, provided by T9c from `pseudo-cal.json`; tests pass a stub) and `B` also needs `tip_labels` (same shape
  for tips, T9d); missing input raises. `pseudo_files` (kind, SHA-256) passed by the caller go to the manifest.
  Public images go to train only, resized like Kosmo's (longer side 1600 px); the Kosmo part (images, labels,
  inner valid assignment) of a fold is byte-identical for every `public` value (CV22). Public images come from the
  T9p import only (no other reader), the same kept images in the three folds of a version.
- Added tests `tools/camera/tests/test_chain.py`, `test_dataset.py`.
- Changed `docs/rules/camera.md`: writes CV22 (as amended by D26) and CV24; amends CV4 and CV15 (root text).
  Changed ADR 0016 "Public data and version selection": cross-validation by session and run chain (CV24, D25), with
  the alternatives of root "Approach, Cross-validation by session".

## Depends on
T3 (`model_spec.CLASS_NAMES`), T4b, T8b, T9p.

## Rules
CV4 (an eval session or block raises even if passed explicitly; no lock or a wrong hash raises), CV17 (`captures-eval/`
refused), CV2, CV5, CV15 (roles only), CV16 (sources read-only), CV19, CV20, CV21, CV22, CV24, D16, D23, D25, A19.
Root sections: "Approach, Cross-validation by session" and "Approach, Three trainings, run chain" (layout, step
numbers, hash checks, not-built records). Writes CV22 and CV24 into `docs/rules/camera.md` (CV21 is T9p's, CV23
T9c's). Real data are not needed: everything is built and tested on synthetic sessions and COCO exports. Constants
from `config.py`. Check in the `rfdetr`
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
- Fold plan (CV24): roles follow the CV3 key order, not the name order (two names whose orders differ); block-mode
  lock, 1 and 3 judged dev sessions, a session of 49 judged darts each refused with its French message and no file
  written; 50 accepted.
- Folds: `cv1` holds dev 1 only, `cv2` dev 2 only, `full` both; no photo of the held-out session in any subset of
  its fold; inner valid made of whole volley blocks of the training session(s), about `VALID_RATIO`, same draw on
  rerun; no session name in `manifest.json` or `chain.json` (scan for the synthetic names).
- Run chain (A19): `public="none"` on an existing chain folder raises; `chain.json` strict reader (unknown key, cap,
  wrong version); `defaults.json` or map changed after `chain.json`: `StaleStep`; a recorded input changed on disk:
  `StaleStep` naming both step numbers; `NotBuilt` writes `not-built/<version>.json` with its reason.
- `public="B"` without `tip_labels` raises; `"A"` without `cal_labels` raises; a stub `cal_labels` adds its boxes and
  its drop reason drops the image; a `pseudo-tip-box` set gives no label without `tip_labels`.
- `public="none"`, `"A"` and `"B"` (stubs) on the same seed: same `kosmoPartSha256` and inner valid subset per fold,
  public images only in train and the same kept images in the three folds, `version` and `fold` in the manifest;
  one accepted folder missing: `"A"` built from the other, the missing slug listed; empty map or no accepted folder: `"A"` raises `NotBuilt` with the reason "aucun jeu
  accepté disponible"; no public image kept: `NotBuilt` "aucune image publique conservée"; `"B"` with no
  `pseudo-tip-box` image kept: `NotBuilt` (root "Approach, Three trainings, run chain").
- Source folders unchanged (hashes before and after).

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: fold plan, run chain and conversion fully specified (CV4, CV15, CV22, CV24, D25), tested on synthetic
sessions and COCO; `chain.json` and the manifests are local and regenerable (no migration), so not an `opus` case.

## Implementation notes
