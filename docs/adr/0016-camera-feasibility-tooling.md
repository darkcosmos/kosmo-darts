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
   `python3` (3.14 in Kosmo's WSL, also valid on 3.13) in WSL, outside the container and the venv. Everything using numpy, PIL, PyAV, torch or LiteRT lives
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

## Annotation format v2 and migration

Context: after part A, Kosmo found that the tripod can drift during a session (D15), so one clicked calibration per
session is not enough. Decisions (rule CV20, `docs/camera/annotation-format.md`):

- The annotation file moves to version 2: the single `calibration` becomes `calibrations`, an ordered list of
  segments `{fromDart, photo, width, height, points}`. The first starts at dart 1, `fromDart` is strictly ascending,
  and each segment applies from its `fromDart` until the next one; a later segment starts at a judged dart and is
  clicked on that dart's photo. One function, `annotations.segment_for`, picks the segment of a dart for every tool.
- The reader knows both versions and always returns the version 2 model. The version 1 rules are kept unchanged;
  `migrate_v1` turns a version 1 `calibration` into the single segment from dart 1 (`null` into an empty list) and
  copies every other field, `truthSha256` included, so the stale-truth warning keeps working. Alternative rejected:
  a separate one-shot migration command, one more step for Kosmo and a window where his files are unreadable.
- The writer writes version 2 only. A version 1 file is never rewritten by opening, listing or browsing it; the
  annotation tool writes version 2 on the first saved change (CV16). Alternative rejected: rewriting on open, which
  would modify files Kosmo only looked at.
- At most `MAX_SEGMENTS` = 64 segments per file, a named constant next to `MAX_BYTES`, so a file stays far below the
  1 MiB cap; any other version, lower or higher, is still rejected and any later change bumps the version.
- Proof of the policy, in `tools/camera/tests/test_annotations.py`: the version 1 golden example migrates to the
  version 2 golden example byte for byte; a version 1 file written by the part A tool itself (`flow.apply` and
  `write_atomic` at 8d69f64) and 50 seeded random version 1 files round-trip through migrate, encode and decode to
  their original version 1 content.

## Consequences

The tools add no dependency to the apps. The pinned wheels cover Linux x86_64 and cp313 only. The eval data is guarded
by rule CV4 so that a measure on it cannot be tuned afterwards.
