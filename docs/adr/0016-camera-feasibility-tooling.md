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
2. Stdlib-only core (`config`, `truth`, `split`, `annotations`, `board`, `strict_json`): the annotation tool runs with a bare
   `python3` (3.14 in Kosmo's WSL, also valid on 3.13) in WSL, outside the container and the venv. Everything using numpy, PIL, PyAV, torch or LiteRT lives
   in other modules.
3. Keypoints as objects: the detector (RF-DETR Nano) finds 5 classes (`tip`, `cal1` to `cal4`), each a fixed square
   box centred on the point. Alternative rejected: a dedicated keypoint model, which would leave the object-detection
   export route of `rfdetr`. Superseded by ADR 0020 (calibration by registration, tip by a heatmap model).
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

## Annotation format v3: control clicks

Context: Kosmo stopped K5 (D30): the drift measure is not reliable, so the squad needs ground truth per image, the 4
calibration points clicked again on chosen photos and video frames, independent of the segments. The assisted click
(CV31, D39, D43) also needs to record whether each stored point is the snapped crossing or the click itself.
Decisions (rule CV25, `docs/camera/annotation-format.md`):

- The annotation file moves to version 3: the root gains `controls`, a list of `{dart, source, positionMs, width,
  height, points, snap}`, `positionMs` present exactly when `source` is `video`; `points` holds 4 entries in CV2
  order, each a point or `null` (hidden). The list is sorted by dart, then photo before video, one control per
  (dart, source), at most `MAX_CONTROLS` = 1024 next to `MAX_SEGMENTS`. A control belongs to a judged dart; a video
  control needs a volley video and a non-null `entry.videoPositionMs`.
- `snap` field: every control and every segment carries 4 statuses aligned with `points`, `snapped` or `unsnapped`
  (CV31) for a point and `null` exactly for a `null` point; segments may also hold `manual` (clicked before snapping
  existed). The format change lands before the snapping is built, so no later version bump is needed for it.
- Controls never change the segments: `segment_for` and `segments_sha256` ignore them, and `segments_sha256` leaves
  `snap` out so drift flags measured on version 2 stay current. `controls_sha256` hashes the control list, `snap`
  included, for the diagnosis; `control_for` is the one lookup every tool uses.
- The version 2 rules are kept unchanged; `migrate_v2` adds `controls: []` and `snap: ["manual" x4]` on every
  segment, and a version 1 file goes through `migrate_v1` then `migrate_v2`. The writer writes version 3 only, on the
  first saved change (CV16). Alternative rejected: storing controls as extra segments, which would change
  `segment_for` and the drift hash, and mix the reference clicks with the ground truth that checks them.
- Proof: the version 2 and version 1 golden examples migrate to the version 3 golden example without its controls
  and its second segment, byte for byte; a version 2 file written by the batch 1 tool (`flow.apply` and
  `write_atomic` at ab85842) and 50 seeded random version 2 files encode as version 3 and give back their version 2
  text once the empty `controls` and the `manual` statuses are dropped; the worst case (64 segments, 1024 controls,
  1000 darts) stays under the 1 MiB cap.

## Assisted click

Status: Superseded by ADR 0020 on 2026-11-18: the snapping of clicks is stopped (rule CV31, 0 of 697 real photo points snapped); code removed by task C1.

Context: Kosmo's clicks designate the right wire crossing, they do not locate it (D39, CV31): a human does not hold 1 to
2 px for an hour. The tool therefore snaps every calibration-point click (first calibration, recalibration, control)
to the exact crossing with `snap.py` (numpy, on the luma of `luma.py`, numpy and Pillow). The standard library cannot
decode JPEG, so decision 2 and decision 5 of the plan ("stdlib only") are amended (D43, confirmed by Kosmo in Q14, D44).
Decisions:
- Where it runs: in the tool process, in WSL, from a small venv `~/kosmo-tool-venv` created once with Kosmo's `python3`
  (3.14) and holding only numpy and Pillow (`tools/camera/requirements-tool.txt`: exact versions, two hashes each, cp313
  and cp314 manylinux x86_64 wheels, installed with `--require-hashes --only-binary=:all:`). The stdlib core is
  unchanged: `snapping.py` imports `snap.py` and `luma.py` lazily, only a missing numpy or Pillow means "Recalage
  indisponible" (any other import error is a bug and propagates), and the tool still starts with a bare `python3`.
- Alternatives rejected: the tool in the container (published port, network other than `none`, server bound to 0.0.0.0,
  annotations mounted read-write, a Windows browser that the sandbox cannot test); a container pre-computation of
  candidate crossings read by the stdlib tool (a new file format with its reader and caps, windows unknown for video
  frames, stale after a recalibration, the most code); a pure-stdlib JPEG decoder (thousands of lines, seconds per 12 MP
  photo); snapping in JavaScript (untestable here, a second implementation next to the diagnosis).
- Flow: the previous item's points are snapped on the new image and proposed; a click goes to the point named by the
  click rule (`flow.click_target`) and is snapped at once; Entrée (`save`) writes only when 4 points are ready.
  A proposal is never stored as such. An image that cannot be decoded leaves the point at the click, `unsnapped`.
- Decoded images stay in memory in a cache of `SNAP_CACHE_IMAGES` (2) images, keyed by path and modification time
  (48 MB per 12 MP image).
- Repeatability is measured by the machine (CV28 `D`), accuracy only against synthetic truth (T15m).

## Tripod drift

Status: Superseded by ADR 0020 on 2026-11-18: the drift measure and its compensation are stopped (rules CV18, CV19); code removed by task C1. Was: not validated and suspended on 2026-11-15 (D30).

Context: the clicked calibration of a segment is only true for the photo it was clicked on; between two clicks the
tripod moves by a few pixels. Decisions (rules CV18 and CV19, `tools/camera/kosmo_camera/drift.py`):

- Method (CV18): each judged photo is compared with the reference photo of its segment, decoded to luma in stored pixel
  order. Phase correlation (`numpy.fft`) first on the board box, block-averaged, then on four patches centred on the
  clicked calibration points, with a 3-point parabolic sub-pixel fit: one shift per point. Drift in px is the largest
  shift; drift in mm is the CV9 error the stale calibration would cause. The whitened spectrum carries a Gaussian
  low-pass (0.08 cycles per pixel), a named constant of the module, because a bare whitened peak is a sinc and the
  parabola then pulls the shift towards the integer pixel by about 0.1 px. Alternatives rejected: OpenCV feature
  matching (a dependency for one translation per point), one global translation (misses roll and per-corner change),
  compensation from the model's own calibration (the reference must not depend on the model under test).
- Compensation labels (CV19): the reference calibration of a photo is the segment points plus its measured shifts,
  derived from the reference photo of the segment only, when the drift is at most the threshold. Above it, or when the
  photo cannot be measured, the photo has no reference calibration and is dropped from the dataset and from CV9 and
  CV12, counted by reason. `dataset` and `measure` refuse to run while a judged photo of their set is beyond.
- Threshold: one constant, `DRIFT_MAX_MM` = 1.0 mm (half of `CAL_MAX_ERROR_MM`, Kosmo's choice D18), in
  `config/defaults.json`, read only through `config.py` and pinned only in `test_config.py`. If the compensation were
  wrong on a photo just under the threshold, the reference calibration would be off by at most 1.0 mm, half the CV9
  budget. The report also lists, for information, how many sessions and darts each candidate value (0.5, 1.0, 2.0 mm)
  would flag.
- Untrusted photos: JPEG decoding is Pillow with `Image.MAX_IMAGE_PIXELS` untouched; a decompression bomb (error or
  warning), a truncated or garbage file, and a decoded size different from the segment give the status `unreadable` or
  `size_mismatch`, never an exception out of the measure.
- Flag file: `drift.json` (strict, stdlib-only reader with a 1 MiB cap, `drift_file.py`) is written by `drift` in the
  container into `~/kosmo-runs/drift/`, next to the numbers-only report, and read by the stdlib annotation tool on the
  same machine. It holds session names, so it is a local, regenerable working file like the split file: never pasted,
  never committed (CV15). The report labels sessions `S01`, `S02`... in session-name order and carries no name.
- Container: the image copies `config/` next to the package, since `config.py` reads it at import, and runs
  `python -m kosmo_camera.drift` with the data mounted read-only; the `drift` command needs no split lock (CV18).

## Public data and version selection

- Map file: `tools/camera/config/public-class-maps.json` (format `kosmo-camera-public-class-maps`, version 1) is the
  only way a public set enters the tools: slug (Kosmo's folder name) to licence and a source-category-to-target table.
  It is committed and reviewable. Alternative rejected: a map file inside each data folder (not reviewable, not
  versioned). `docs/camera/datasets.md` has one row per set and a test keeps the two consistent.
- Targets: `tip` (the centre of the source box is the tip, the box is rebuilt with `BOX_FRACTION`),
  `whole-dart-checked` (ignored as a label, but the image is dropped when no tip lies inside it, so an unlabelled tip
  never stays as background), `pseudo-tip-box` (raw box, never a label by itself), `ignore`, `drop-image`. There is no
  calibration target.
- Train-only rule: public images join the training subset only, in every fold and in the full training. Validation,
  dev measures and eval hold Kosmo's photos only (CV21).
- Untrusted input (CV21, S10): the downloaded JSON and images are validated before use. JSON over 64 MiB, invalid, with
  duplicated or dangling ids, or with category lists that disagree refuses the set. An image is dropped and counted
  per reason when its name is not a bare name of a regular file inside its subset folder (read without following
  links), when it is over 32 MiB, does not decode (Pillow's pixel limit is kept), differs in size from the JSON or
  carries a box that is not finite or not inside it. Source folders are never written.

## Consequences

The tools add no dependency to the apps. The pinned wheels cover Linux x86_64 and cp313 only. The eval data is guarded
by rule CV4 so that a measure on it cannot be tuned afterwards.
