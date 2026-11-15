# T10: Training (PR B)

## Goal
Train RF-DETR Nano on the T9 dataset inside Kosmo's offline GPU container. The image, the GPU requirements and the
GPU check come from T10a (PR A).

## Files
- Added `tools/camera/kosmo_camera/train.py`: `train(dataset_dir, out_dir, weights, epochs, batch, resolution,
  device)` wrapping `rfdetr.RFDETRNano(pretrain_weights=weights, num_classes=5, resolution=...)` then `.train(...)`;
  writes `train-run.json` (versions, config, dataset manifest hash, and copied from the T9 manifest: `version`
  (`sans`, `A`, `B`), `kosmoPartSha256`, `publicSets` (slug, licence, images) and `pseudoFiles` (kind, SHA-256),
  best checkpoint path, metrics per epoch). No public-data option here (D23): the three trainings use the same
  `train` call with the same parameters and seed on three datasets (T9 `public`, root "Approach, Three
  trainings"); the version is read from the dataset, compared by T13 (root CV22). The calibration classes of public
  images are handled in the dataset (CV23), never in the loss: no change to the `rfdetr` criterion.
  `weights=None` only for tests. Fails before training if any network access would be needed (explicit weights
  path, `HF_HUB_OFFLINE=1`). Checkpoints loaded with `torch.load(..., weights_only=True)` wherever `rfdetr` lets the
  caller choose; notes list the places where it does not. Default `weights` is `/opt/weights/rf-detr-nano.pth`,
  the file of the T10a image.
- Added tests `tools/camera/tests/test_train.py` (slow).

## Depends on
T3, T9, T10a.

## Rules
CV4 (training reads the T9 dataset only), CV22, CV23, D23, D16 (`num_classes=5`, category ids 0 to 4 in `CLASS_NAMES` order as
written by T9; the trained checkpoint exports with T3's `export_litert` unchanged). Decision 10 (offline container of T10a). No change to the T10a
`Dockerfile` or requirements unless training needs it; any such change is listed in the notes and Kosmo rebuilds
the image (K3 again).

## Tests
- Slow: `torch.manual_seed(42)` and synthetic seed 7; 2 iterations on CPU from random weights on a 4-image synthetic
  dataset produce a checkpoint and
  `train-run.json`.
- Not slow: `train-run.json` built from a synthetic manifest of version `B` lists its version, public slug in
  `publicSets` and both pseudo files in `pseudoFiles`; a `sans` manifest gives empty lists (D21, D23); a manifest
  without `version` is refused.
- No network: a weights path that does not exist fails before training with a clear message.

## Done when
Python checks of root "Test plan" with `-m slow` green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: wrapper around a library training call with given parameters and tests.

## Implementation notes
