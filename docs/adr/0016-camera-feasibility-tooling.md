# ADR 0016: Camera feasibility tooling (phase 0b-1)

- Status: Proposed (design review of issue #42); accepted when its PR is merged. No change to the apps, to the saved
  game format or to the capture format.

## Context

Phase 0b-1 measures, offline and on Kosmo's captures, whether dart detection from a phone photo can reach the
thresholds of rules CV8 to CV11. It needs Python tools (annotation, split, training, export, measures, reports) that
must stay reproducible, offline and cheap to review, and a trained model that never enters the repository.

## Decisions

1. Two environments, one Python 3.13 (Kosmo's decision, documented deviation from the sandbox default 3.14). The CPU
   environment (`tools/camera/requirements-cpu.txt`, sandbox and CI) and the GPU environment
   (`requirements-gpu.txt`, training image) have the same set except `torch`, `torchvision` and `torchao`. Both are
   pinned with exact versions and SHA-256 hashes and installed with `--require-hashes`. Alternative rejected: one file
   with CUDA torch (multi-GB in the sandbox).
2. Stdlib-only core (`config`, `truth`, `split`, `annotations`, `board`): the annotation tool runs with a bare
   `python3.13` in WSL, outside the container and the venv. Everything using numpy, PIL, PyAV, torch or LiteRT lives
   in other modules.
3. Keypoints as objects: the detector (RF-DETR Nano) finds 5 classes (`tip`, `cal1` to `cal4`), each a fixed square
   box centred on the point. Alternative rejected: a dedicated keypoint model, which would leave the object-detection
   export route of `rfdetr`.
4. Measures run on the exported `.tflite` only, through `ai_edge_litert` with our own pre- and post-processing, the
   exact chain the next phase ports to Kotlin. Alternative rejected: `RFDETRInference`, which hides that chain.
5. No OpenCV: a numpy homography (normalised DLT) and PyAV cover the needs, one dependency less.
6. No model in the repository (`*.tflite` and `*.pth` are git-ignored); models and runs stay in `~/kosmo-runs/`.
7. Training image: delivered in the first PR so that Kosmo can build it and check the GPU (`gpu-check`) while he
   annotates; rebuilt in the second PR with the training code and its entry point. It runs without network and with a
   read-only root, and the data is mounted read-only.
8. Constants live in `tools/camera/config/defaults.json`, read by the strict `config.py`; modules never repeat them.
9. Annotation tool: a local `http.server` on `127.0.0.1` with a French page served from the package, no CDN. Alternative
   rejected: a Tk tool.

## Consequences

The tools add no dependency to the apps. The pinned wheels cover Linux x86_64 and cp313 only. The eval data is guarded
by rule CV4 so that a measure on it cannot be tuned afterwards.
