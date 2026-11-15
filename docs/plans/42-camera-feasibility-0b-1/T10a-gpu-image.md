# T10a: GPU training image and GPU check (PR A)

## Goal
Deliver in PR A the training image and the GPU check, so that Kosmo builds the image and checks his GPU (step K3 of
root "How Kosmo verifies") while he annotates. Training itself stays in T10 (PR B).

## Files
- Added `tools/camera/kosmo_camera/gpu_check.py` (runs as `python -m kosmo_camera.gpu_check`; imports only the
  standard library and `torch`): prints `nvidia-smi` output (or its absence), torch version, CUDA version,
  `cuda available: <bool>`, device name; exit 1 if CUDA is unavailable.
- Added `tools/camera/requirements-gpu.txt`: same pins and `--hash` lines as `requirements-cpu.txt` (T1) except
  `torch`, `torchvision`, `torchao` `+cu130` from `https://download.pytorch.org/whl/cu130` (hashes from that index);
  installed with `--require-hashes`.
- Added `tools/camera/Dockerfile`: `python:3.13.18-slim`, non-cached `pip install -r requirements-gpu.txt`, copies
  `kosmo_camera/` only, downloads `https://repo.roboflow.com/rfdetr/nano_coco/checkpoint_best_regular.pth` to
  `/opt/weights/rf-detr-nano.pth`, checks MD5 `fb6504cce7fbdc783f7a46991f07639f` (from `rfdetr` 1.12.1) and the
  `NANO_SHA256` build argument, prints the SHA-256; `ENV HOME=/tmp TORCH_HOME=/tmp/torch HF_HOME=/tmp/hf
  MPLCONFIGDIR=/tmp/mpl HF_HUB_OFFLINE=1`. No entry point yet: K3 runs `python -m kosmo_camera.gpu_check` explicitly;
  T14 adds the `python -m kosmo_camera` entry point and the `gpu-check` subcommand. Header comment: build command and
  the run flags of decision 10.
- Added tests `tools/camera/tests/test_requirements.py`, `test_dockerfile.py`, `test_gpu_check.py`.

## Depends on
T1.

## Rules
Decisions 9 and 10, D6, D7 (no `USER`: uid 0 inside rootless Docker is Kosmo's own uid; reasons in root
"Decisions"), D10. `repo.roboflow.com` is not reachable from the sandbox: the SHA-256 is pinned from the MD5-checked
file at Kosmo's first build (step K3); until then the build fails with the printed hash and the message to report it
(an empty `NANO_SHA256` always fails). Notes state the residual risk: the weights are a pickle, trusted on first
download after the MD5 check, loaded in an offline, read-only, capability-free container.

## Tests
- Requirements: CPU and GPU files list the same packages with the same versions apart from the three torch packages
  and their local suffix and hashes; every package line is pinned with `==` and has at least one `--hash`.
- Dockerfile static checks: base image tag exact, MD5 present, `--require-hashes` used, no `pip install` without
  `-r`, the run flags of decision 10 in the header comment.
- Hash check: the weights check is a `sh` script in the Dockerfile `RUN` (extracted by the test, run with `sh` on a
  temp file): exits non-zero and prints the SHA-256 when `NANO_SHA256` is empty or does not match, exits 0 when it
  matches. Kosmo sees the real check at his first build (K3).
- `gpu_check` with `torch.cuda.is_available` patched to `False` and `nvidia-smi` absent: prints the absence and
  `cuda available: False`, exits 1; patched to `True` with a fake device name: exits 0.

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0. The image itself is built only by Kosmo (no Docker in
the sandbox).

## Model
sonnet. Why: container and requirements wiring with given flags, URL and hashes; no design choice left.

## Implementation notes
