# T10: Training (PR B)

## Goal
Train RF-DETR Nano on the T9 dataset inside Kosmo's offline GPU container. The image, the GPU requirements and the
GPU check come from T10a (PR A).

## Files
- Added `tools/camera/kosmo_camera/train.py`: `train(dataset_dir, out_dir, weights, epochs, batch, resolution,
  device)` wrapping `rfdetr.RFDETRNano(pretrain_weights=weights, num_classes=5, resolution=...)` then `.train(...)`;
  writes `train-run.json` (versions, config, dataset manifest hash, best checkpoint path, metrics per epoch).
  `weights=None` only for tests. Fails before training if any network access would be needed (explicit weights
  path, `HF_HUB_OFFLINE=1`). Checkpoints loaded with `torch.load(..., weights_only=True)` wherever `rfdetr` lets the
  caller choose; notes list the places where it does not. Default `weights` is `/opt/weights/rf-detr-nano.pth`,
  the file of the T10a image.
- Added tests `tools/camera/tests/test_train.py` (slow).

## Depends on
T3, T9, T10a.

## Rules
CV4 (training reads the T9 dataset only), D16 (`num_classes=5`, category ids 0 to 4 in `CLASS_NAMES` order as
written by T9; the trained checkpoint exports with T3's `export_litert` unchanged). Decision 10 (offline container of T10a). No change to the T10a
`Dockerfile` or requirements unless training needs it; any such change is listed in the notes and Kosmo rebuilds
the image (K3 again).

## Tests
- Slow: `torch.manual_seed(42)` and synthetic seed 7; 2 iterations on CPU from random weights on a 4-image synthetic
  dataset produce a checkpoint and
  `train-run.json`.
- No network: a weights path that does not exist fails before training with a clear message.

## Done when
Python checks of root "Test plan" with `-m slow` green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: wrapper around a library training call with given parameters and tests.

## Implementation notes
