# T10: Training, training image, GPU check

## Goal
Train RF-DETR Nano on the T9 dataset inside Kosmo's offline GPU container, and provide the image and the GPU check.

## Files
- Added `tools/camera/kosmo_camera/train.py`: `train(dataset_dir, out_dir, weights, epochs, batch, resolution,
  device)` wrapping `rfdetr.RFDETRNano(pretrain_weights=weights, num_classes=5, resolution=...)` then `.train(...)`;
  writes `train-run.json` (versions, config, dataset manifest hash, best checkpoint path, metrics per epoch).
  `weights=None` only for tests. Fails before training if any network access would be needed (explicit weights
  path, `HF_HUB_OFFLINE=1`). Checkpoints loaded with `torch.load(..., weights_only=True)` wherever `rfdetr` lets the
  caller choose; notes list the places where it does not.
- Added `tools/camera/kosmo_camera/gpu_check.py`: prints `nvidia-smi` output (or its absence), torch version, CUDA
  version, `torch.cuda.is_available()`, device name; exit 1 if CUDA is unavailable.
- Added `tools/camera/requirements-gpu.txt`: same pins and `--hash` lines as `requirements-cpu.txt` except `torch`,
  `torchvision`, `torchao` `+cu130` from `https://download.pytorch.org/whl/cu130` (hashes from that index);
  installed with `--require-hashes`.
- Added `tools/camera/Dockerfile`: `python:3.13.18-slim`, non-cached `pip install -r requirements-gpu.txt`, copies
  `kosmo_camera/` only, downloads `https://repo.roboflow.com/rfdetr/nano_coco/checkpoint_best_regular.pth` to
  `/opt/weights/rf-detr-nano.pth`, checks MD5 `fb6504cce7fbdc783f7a46991f07639f` (from `rfdetr` 1.12.1) and the
  `NANO_SHA256` build argument, prints the SHA-256; `ENV HOME=/tmp TORCH_HOME=/tmp/torch HF_HOME=/tmp/hf
  MPLCONFIGDIR=/tmp/mpl HF_HUB_OFFLINE=1`; entry point `python -m kosmo_camera`.
- Added tests `tools/camera/tests/test_train.py` (slow), `test_requirements.py`.

## Depends on
T3, T9.

## Rules
CV4 (training reads the T9 dataset only). Decisions 9 and 10, D6, D7 (no `USER`: uid 0 inside rootless Docker is
Kosmo's own uid; reasons in root "Decisions"). `repo.roboflow.com` is not reachable from the
sandbox: the SHA-256 is pinned from the MD5-checked file at Kosmo's first build (step K1); until then the build
fails with the printed hash and the message to report it (an empty `NANO_SHA256` always fails). Notes state the
residual risk: the weights are a pickle, trusted on first download after the MD5 check, loaded in an offline,
read-only, capability-free container.

## Tests
- Slow: `torch.manual_seed(42)` and synthetic seed 7; 2 iterations on CPU from random weights on a 4-image synthetic
  dataset produce a checkpoint and
  `train-run.json`.
- Requirements: CPU and GPU files list the same packages with the same versions apart from the three torch packages
  and their local suffix and hashes; every package line is pinned with `==` and has at least one `--hash`.
- Dockerfile static checks: base image tag exact, MD5 present, `NANO_SHA256` empty makes the check fail,
  `--require-hashes` used, no `pip install` without `-r`, the run flags of
  decision 10 documented in the file header comment.

## Done when
Python checks of root "Test plan" with `-m slow` green, `kosmo-detekt` exit 0. The image itself is built
only by Kosmo (no Docker in the sandbox).

## Model
sonnet. Why: wrapper and container wiring with given flags.

## Implementation notes
