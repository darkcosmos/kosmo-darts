# T10: Training (PR B)

CANCELLED (2026-11-18): the RF-DETR chain is not built; this work is rewritten in phases 2 to 4 after their prototypes (root plan N12, N18).

HELD by D30: revised at P2; trains on per-photo labels (root plan "Tasks").

## Goal
Train RF-DETR Nano on each fold dataset of a version inside Kosmo's offline GPU container, export each model, and
record what and how long (root "Approach, Cross-validation by session" and "Three trainings, run chain", D25). The
image, the GPU requirements and the GPU check come from T10a (PR A).

## Files
- Added `tools/camera/kosmo_camera/train.py`: `train(dataset_dir, out_dir, weights, device)` wrapping
  `rfdetr.RFDETRNano(pretrain_weights=weights, num_classes=5, resolution=model_spec.RESOLUTION)` then `.train(...)`
  with `TRAIN_EPOCHS`, `TRAIN_BATCH` and `TRAIN_SEED` from `defaults.json` (no override argument: every training
  of the chain uses the same hyperparameters); the best checkpoint is chosen by `rfdetr` on the inner valid subset
  of the fold dataset (T9), never on held-out photos. Writes `train-run.json`: versions, `version`, `fold`,
  `heldOutRole` (null for `full`), hyperparameters, `datasetManifestSha256`, copied from the T9 manifest
  `kosmoPartSha256`, `publicSets` (slug, licence, images) and `pseudoFiles` (kind, SHA-256), Kosmo and public image
  counts, `startedAt`, `finishedAt` (UTC ISO 8601), `durationSeconds`, best checkpoint path, metrics per epoch.
- `train_folds(run, version, folds, train_fn=train, export_fn=export.export_litert)`: for each requested fold
  (default `cv1`, `cv2`, `full`; `B` defaults to `cv1`, `cv2`), checks the dataset with T9 `chain.check_inputs`,
  skips a fold whose `train-run.json` and `export.json` match the current manifest, otherwise trains, exports with
  T3's chain to `models/<version>/<fold>/model.tflite` and writes `export.json` (tflite SHA-256, checkpoint and
  `train-run.json` SHA-256). `not-built/<version>.json` present: prints "Version <A|B> non construite : <raison>"
  and returns. No public-data option here (D23): the versions differ only by their datasets (T9 `public`). The calibration classes of public
  images are handled in the dataset (CV23), never in the loss: no change to the `rfdetr` criterion.
  `weights=None` only for tests. Fails before training if any network access would be needed (explicit weights
  path, `HF_HUB_OFFLINE=1`). Checkpoints loaded with `torch.load(..., weights_only=True)` wherever `rfdetr` lets the
  caller choose; notes list the places where it does not. Default `weights` is `/opt/weights/rf-detr-nano.pth`,
  the file of the T10a image.
- Changed `tools/camera/config/defaults.json` and `config.py`: `TRAIN_EPOCHS` 50, `TRAIN_BATCH` 8, `TRAIN_SEED` 42,
  pinned once in `test_config.py` (squad choice, open to review: epochs and batch sized for the RTX 4070 SUPER and
  the indicative durations of the root plan).
- Added tests `tools/camera/tests/test_train.py` (slow and not slow).

## Depends on
T3 (`export.export_litert`, `model_spec`), T9 (fold datasets, `chain.py`), T10a.

## Rules
CV4 (training reads the fold dataset only; the held-out session never enters it), CV22, CV23, CV24, D23, D25,
A19 (fixed run-folder paths, resume), D16 (`num_classes=5`, category ids 0 to 4 in `CLASS_NAMES` order as
written by T9; the trained checkpoint exports with T3's `export_litert` unchanged). Decision 10 (offline container of T10a). No change to the T10a
`Dockerfile` or requirements unless training needs it; any such change is listed in the notes and Kosmo rebuilds
the image (K3 again).

## Tests
- Slow: `torch.manual_seed(42)` and synthetic seed 7; 2 iterations on CPU from random weights on a 4-image synthetic
  dataset produce a checkpoint and
  `train-run.json`.
- Not slow: `train-run.json` built from a synthetic manifest of version `B` fold `cv2` lists its version, fold,
  held-out role `dev 1`, hyperparameters equal to `defaults.json`, manifest SHA-256, start, end and duration (fake
  clock), public slug in `publicSets` and both pseudo files in `pseudoFiles`; a `sans` manifest gives empty lists;
  a manifest without `version` or `fold` is refused.
- `train_folds` with a fake trainer and fake exporter on a synthetic chain: default folds per version (three, two
  for `B`), `--fold full` alone; a complete consistent fold skipped (trainer not called); a fold whose manifest
  changed retrained; a stale dataset raises `StaleStep`; `not-built/B.json` gives the line and no training.
- No network: a weights path that does not exist fails before training with a clear message.

## Done when
Python checks of root "Test plan" with `-m slow` green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: wrapper around a library training call and a fold loop with given parameters and tests; local
regenerable files (no migration).

## Implementation notes
