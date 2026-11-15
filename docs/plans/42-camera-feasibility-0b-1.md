# Plan: camera phase 0b-1, detection feasibility (issue #42)

## Goal

Issue [#42](https://github.com/darkcosmos/kosmo-darts/issues/42): go/no-go for camera scoring. The squad writes Python
tooling that Kosmo runs on his own captures on his GPU station: annotation, training of RF-DETR Nano, LiteRT export,
measurement of the exported `.tflite` against the thresholds of the issue. Only numbers come back (reports pasted in PR
comments); the squad fills `docs/camera/feasibility-report.md` from them. Phase 0b-2 (#83, Kotlin vision module and
diagnostic screen) is out of scope and only starts after a "go".

## Scope and out of scope

In scope:
- `tools/camera/`: Python package `kosmo_camera` (truth reader, dev/eval split, annotation format, board geometry,
  homography, synthetic boards, COCO dataset builder, training, export, LiteRT detector, per-dart decision, arrival
  and empty-board events, metrics and report, CLI), the stdlib-only annotation tool, the GPU training image
  (`Dockerfile`, pinned requirements), CPU tests on generated boards.
- Docs: `docs/rules/camera.md` (rules CV1 to CV16 below), `docs/camera/annotation-format.md`,
  `docs/camera/datasets.md`, `docs/camera/training.md`, `docs/camera/feasibility-report.md` (template, then filled),
  `docs/adr/0016-camera-feasibility-tooling.md`.
- `.gitignore`: `.venv/`, `__pycache__/`, `.pytest_cache/`, `*.tflite`, `*.pth` (no model or weights committed).

Out of scope: any Kotlin, any change to `:app`, `:capture`, `:game-engine`; inference time on the Pixel 7 (0b-2); the
keypoint fallback model and ONNX Runtime (only on Kosmo's decision after a report); pseudo-annotation (decision 7);
any model file in the repository (decision 6); `.github/**` (CI job proposed to the owner, see "PR material").

## Approach

- **Two environments, one Python 3.13** (Kosmo's Python decision). Sandbox and CI: `.venv` at the repository root,
  `tools/camera/requirements-cpu.txt` (full transitive set, exact versions, torch from the PyTorch CPU index). GPU
  image: `tools/camera/requirements-gpu.txt`, same set except `torch`, `torchvision`, `torchao` from the `cu130`
  index (CUDA 13.0 runtime in the wheels; any 13.x driver works, widest choice for a "recent driver"). Key pins
  checked with the resolver on 2026-11-15 in the sandbox: `rfdetr` 1.12.1, `litert-torch` 0.10.0, `ai-edge-litert`
  2.3.0, `ai-edge-quantizer` 0.10.1, `torch` 2.14.1, `torchvision` 0.29.2, `numpy` 2.5.4, `pillow` 13.0.0, `av` 19.0.2
  (all pulled by `rfdetr` or `litert-torch` except `pytest` 9.2.0: no extra library).
- **Stdlib-only core**: `truth.py`, `split.py`, `annotations.py`, `board.py` import only the standard library, so the
  annotation tool runs in WSL with a bare `python3.13`, outside the container and without the venv (decision 5).
  Everything with numpy, PIL, av, torch or LiteRT lives in other modules.
- **Detector** (issue): RF-DETR Nano from COCO weights, "keypoints as objects": 5 classes, `tip` and `cal1` to `cal4`,
  each a fixed square box centred on the point (side `BOX_FRACTION` = 2.5 % of the longer image side, a training
  constant); the point is the box centre. Training on Kosmo's dev photos (`dart-NNN.jpg`) plus public sets once Kosmo
  confirmed their licence.
- **Calibration points** (CV2): the 4 corners where the outer edge of the double ring (170 mm) meets the wires 5|20,
  13|6, 17|3 and 8|11, 90 degrees apart. Why: sharp wire crossings are easy to click and to detect, the largest radius
  and 90-degree spacing give the best-conditioned homography, outer double corners are rarely hidden by darts, and the
  class identity fixes the orientation (20 on top). Four points give an exact homography; a missing point fails the
  calibration of that photo. Alternative: adding the bull centre as a fifth point (redundancy, but the bull is often
  covered by darts): rejected for now, kept as a proposal if calibration fails in the report.
- **Export** (issue, verified in the official LiteRT export page of `rfdetr`): `model.export(format="litert")` writes a
  float32 `.tflite` (this route is FP32 only; FP16 only through `ai-edge-quantizer`, measured on dev as an option).
  The route runs on the CPU (XNNPACK) delegate per that page; GPU delegate work is 0b-2. The page says the route was
  validated on `litert-torch` 0.9.4; T3 proves 0.10.0 on CPU early. If export fails: stop and report (issue).
- **Measure on the exported file only**: `detector.py` runs `ai_edge_litert` directly (NCHW float32 ImageNet
  normalisation, outputs `boxes [1,300,4]` cxcywh normalised and class logits), with our own pre- and post-processing,
  the exact chain 0b-2 will port to Kotlin. `RFDETRInference` is not used for measures.
- **Per dart** (CV5 to CV7): each non-voided entry is judged on its own photo. Tips found on the photo are mapped to
  board millimetres with the photo's predicted calibration; a candidate within `MATCH_RADIUS_MM` (6 mm) of a tip
  announced earlier in the volley is an old dart. The best new candidate is announced if its score is at least
  `TAU_SURE`, flagged "to check" if between `TAU_LOW` and `TAU_SURE` or if the calibration of the photo failed; no new
  candidate announces a miss. Thresholds tuned on dev only (CV4).
- **Calibration and scoring geometry** (numpy): homography by normalised DLT, image to board mm, zone by the same
  geometry as `BoardGeometry.kt` (CV1, a test parses the Kotlin constants to stay in sync).
- **Events on video** (CV10, CV11): PyAV decodes each volley video at 10 frames per second, scaled to 960 px wide. A
  state is stable when consecutive frames differ by less than `STABLE_DIFF` in the board region for 300 ms. An arrival
  is a new stable state whose difference from the previous one is one blob of dart size in the board region; a large
  change (a person) waits for the next stable state without counting. Empty board: a stable state where the model sees
  no tip at or above `TAU_LOW`. The board region comes from the model's calibration on the first stable frame of the
  volley. Video and photo frames differ (crop, scale), so calibration and tip metrics use photos, events use video.
- **Evaluation guard** (CV4): `measure --set eval` refuses to run unless `tools/camera/config/frozen.json` exists
  (thresholds and the model SHA-256), and fails if the model file hash differs. Training, tuning and dataset building
  refuse any eval session.
- **Annotation tool** (decision 5): `http.server` on `127.0.0.1`, French UI in one static page (HTML, CSS, JS from the
  package, no CDN, no web font), Host and Origin checks, CSP `default-src 'self'`, captures served read-only by
  validated name only, writes only `~/kosmo-data/annotations/<session>.json` atomically. Zoom and magnifier for tips.
- **Groups for breakdowns** (decision 3, CV14): tripod position from the clicked calibration (viewing angle from the
  board ellipse ratio, side from the foreshortening direction, distance from the apparent board diameter divided by
  the zoom ratio, each binned); lighting from the median luma of the board region of the photo; zoom from
  `truth.json`. Bins are fixed in `docs/camera/training.md` (T2) before any measure.
- **Training image** (decision 10): `python:3.13.18-slim` (same patch as the sandbox), requirements installed and RF-DETR
  Nano COCO weights downloaded at build time (URL and MD5 from `rfdetr` 1.12.1 `assets/model_weights.py`, SHA-256
  pinned, decision 9). Run with `--network none --cap-drop ALL --security-opt no-new-privileges --read-only
  --device nvidia.com/gpu=all`, `~/kosmo-data` read-only, `~/kosmo-runs` read-write, tmpfs `/tmp` for every cache
  (`HOME`, `TORCH_HOME`, `HF_HOME`, `MPLCONFIGDIR`, `HF_HUB_OFFLINE=1`). `gpu-check` (`nvidia-smi` plus
  `torch.cuda.is_available()`) runs first.
- Alternatives rejected: OpenCV (not needed: numpy DLT and PyAV cover it, one dependency less); a Tk annotation tool
  (decision 5); one shared requirements file with CUDA torch (multi-GB in the sandbox); `RFDETRInference` for
  measures (hides the pre- and post-processing that 0b-2 must reproduce).

## Rules (new `docs/rules/camera.md`, prefix CV; T1 writes them, later tasks cite them)

- CV1 Board geometry in mm, origin bull centre, y towards 20, x towards 6, radii and border ownership of BC1 and BC5.
- CV2 Calibration points `cal1` to `cal4`: 170 mm radius on wires 5|20, 13|6, 17|3, 8|11, in this order.
- CV3 Split by session: folder name (UTC start, CP1) before `EVAL_CUTOFF` = `2026-11-16T00:00:00Z` is dev, at or
  after is eval; a name that does not parse is rejected.
- CV4 Eval sessions never enter training, tuning, threshold choice or dataset building; `measure --set eval` needs
  `frozen.json` and a matching model SHA-256; the report records both hashes.
- CV5 Darts judged: non-voided entries with a photo; entries with `photo: null` are counted apart, not judged.
- CV6 Announcement per dart as in "Approach, Per dart" (`TAU_LOW`, `TAU_SURE`, `MATCH_RADIUS_MM`).
- CV7 Outcome per dart: correct (announced value equals truth, not flagged), flagged, or unflagged error. A truth miss
  is correct when nothing is announced on the board (decision 4). Hidden tips stay in the total.
- CV8 Detection thresholds: correct >= 95 % and unflagged errors <= 1 % of judged darts.
- CV9 Calibration success per volley (first photo of the volley): project 41 board points (bull centre, the 20 sector
  centres at 103 mm and at 166 mm) to the image with the clicked homography, back to the board with the predicted
  one; success if the largest error is at most `CAL_MAX_ERROR_MM` = 2.0 mm and all 4 points were found. Target 99 %.
  Why 2 mm: a quarter of the 8 mm treble and double rings, so a calibration error alone cannot move a dart centred in
  a ring out of it; to be confirmed by the report.
- CV10 Arrival of dart k (non-voided, segment not 0, both entry positions in the video) is detected if an arrival
  event falls between the previous non-voided entry (volley start for the first) and entry k. Every other arrival
  event is false. Targets: >= 98 % detected, false events <= 1 % of evaluated darts.
- CV11 Empty board (eval sessions, volleys with at least one dart on the board): success if declared between the last
  entry and the volley `end`; a false empty is one declared after the first on-board dart and before the last entry.
  Targets: >= 99 % success, 0 false empty.
- CV12 Tip error: clicked tip versus matched predicted tip, both mapped with the clicked homography; median and 95th
  percentile in mm, VISIBLE annotations only.
- CV13 Confidence intervals: Wilson 95 % for rates, distribution-free order-statistic interval for quantiles.
- CV14 Breakdowns: near and far half (split line through the bull, perpendicular to the camera direction), single,
  double, treble, 25, bull (from truth), lighting class, tripod group, zoom bin.
- CV15 Reports contain numbers and group labels only: no image, path, file or session name.
- CV16 Annotation tool: stdlib only, `127.0.0.1` only, no external resource, captures read-only, writes only in the
  annotations folder, atomic.

## Files and types added or changed

- Added `tools/camera/`: `requirements-cpu.txt`, `requirements-gpu.txt`, `pytest.ini`, `Dockerfile`, `README.md`
  (pointer to `docs/camera/training.md`), `config/defaults.json` (training and decision constants), later
  `config/frozen.json` (committed by developer-fix from Kosmo's dev report, before the eval run).
- Added package `tools/camera/kosmo_camera/`: `board.py`, `truth.py`, `split.py`, `annotations.py`, `geometry.py`,
  `groups.py`, `synth.py`, `dataset.py`, `public_import.py`, `model_spec.py`, `train.py`, `gpu_check.py`, `export.py`,
  `detector.py`, `decide.py`, `video.py`, `events.py`, `metrics.py`, `tune.py`, `report.py`, `cli.py`, `__main__.py`,
  `annotate/` (`server.py`, `static/`). The ADR is written by T1.
- Added tests `tools/camera/tests/` (pytest; marker `slow` for export and training smoke tests).
- Added docs listed in "Scope". Changed `.gitignore`. Nothing else changes (`git diff origin/main -- app capture
  game-engine .github` stays empty).

## Data read, stored or exposed

- Read (Kosmo's machine): `~/kosmo-data/captures/<session>/` (truth, photos, videos), `~/kosmo-data/public/`,
  `~/kosmo-data/annotations/`. Never modified by any tool except the annotation tool, which writes only annotations.
- Written: `~/kosmo-runs/` (datasets, checkpoints, `.tflite`, per-run JSON results, reports). Nothing written in the
  repository by the tools.
- Exposed: the Markdown report only (CV15), pasted by Kosmo. The model stays in `~/kosmo-runs/`; the repository keeps
  its SHA-256, size and rebuild command (decision 6). No network at run time (container `--network none`; annotation
  tool loopback only).

## Annotation format outline (finalized by T5 in `docs/camera/annotation-format.md`)

`{ format: "kosmo-camera-annotations", version: 1, session, truthSha256, calibration: { photo: "dart-NNN.jpg",
width, height, points: [ {x, y} x4 in CV2 order ] } | null, darts: [ { number, status: "VISIBLE"|"HIDDEN"|"OFF_BOARD",
tip: {x, y} | null } ] }`. Pixel coordinates of the photo (origin top left, floats). `tip` set exactly when VISIBLE.
Strict reader (unknown fields, duplicates, version, numbers that do not exist in the truth file are rejected).

## Screens and states (annotation tool, French)

- Session list: name, dev or eval, progress (darts annotated / to annotate, calibration done or not).
- Calibration: first photo of the session, a board diagram showing which point to click next (1 to 4), zoom, undo
  last point, save.
- Darts: photo of entry n with the truth value shown ("T20"), earlier tips of the volley drawn, click the tip;
  buttons and keys "Pointe cachée" (H), "Hors cible" (O), "Annuler" (U), "Suivante" (N), "Précédente" (P);
  magnifier on hover. Voided entries and null photos are skipped and shown as such.
- States: loading, saved (time), save error (message, nothing lost on screen), session complete.

## Test plan

Every test name or comment cites its CV id. All tests run on CPU in the sandbox on generated data; task files list
the cases. Summary:
- T1: board zones on borders (CV1), Kotlin constants in sync, calibration point coordinates (CV2).
- T3 (slow): untrained Nano with 5 classes exports to `.tflite`, LiteRT runs it, decoded output shapes and value
  ranges; parity of LiteRT and PyTorch boxes on one synthetic image within the bound of the `rfdetr` page.
- T4: truth reader on golden v2 text from `docs/camera/capture-format.md`, rejections; split boundary (CV3).
- T5: annotation round trip, strict rejections, consistency with the truth file.
- T6: server binds loopback, path traversal, Host and Origin checks, atomic write, stdlib-only import under
  `python3.13 -I -S`, no external URL in static files (CV16).
- T7: synthetic board render reproducible from a seed, known tips land in known zones.
- T8: DLT exact on 4 points, CV9 metric (0 for identical, known value for a shifted point), groups (CV14).
- T9: COCO output, box size, eval session refused (CV4), public class map.
- T10 (slow): 2-iteration training on CPU from random weights on synthetic data produces a checkpoint.
- T11: CV6 and CV7 on fake detector outputs: old dart matching, flag band, miss, failed calibration.
- T12: CV10 and CV11 on generated videos: arrival in window, false arrival, person occlusion, empty board.
- T13: Wilson and quantile intervals against known values, breakdowns, threshold tuning, CV15 privacy scan, CV4 guard.
- T14: CLI end to end on a synthetic session (fake detector), commands documented match `--help`.

## How Kosmo verifies

No change to the apps: on the Pixel 7, install the CI debug APK; Kosmo Darts and Kosmo Capture behave as before.
Then on the PC (steps K1 to K6, detailed in `docs/camera/training.md`):
- K1 Build the image once with network, run `gpu-check`: GPU name and `cuda available: True`. Report the SHA-256 the
  build prints for the COCO weights if the squad asks for it (T10 notes).
- K2 Check the licences of the public set candidates (`docs/camera/datasets.md`), download the accepted ones.
- K3 Capture the eval sessions with the protocol (at least 500 darts, other lightings and tripod positions).
- K4 Annotate dev and eval sessions with the tool (about 1 h 30).
- K5 Run on dev: dataset, train, export, measure, tune; paste the dev report in the PR. The squad iterates on dev
  reports only, then commits `frozen.json`.
- K6 Run `measure --set eval` once; paste the eval report. The squad fills `feasibility-report.md` with the go/no-go.

## PR material (for developer-fix)

- Proposed owner changes: new job `camera-tools` in `.github/workflows/android.yml`: `ubuntu-26.04`,
  `actions/setup-python` with `3.13`, `pip install -r tools/camera/requirements-cpu.txt`, `python -m pytest
  tools/camera -m "not slow"`; no artifact (no data). Optional second step for the slow tests.
- Versions table: every top-level pin (Python 3.13.18, `rfdetr`, `litert-torch`, `ai-edge-litert`,
  `ai-edge-quantizer`, `torch`, `torchvision`, `av`, `numpy`, `pillow`, `pytest`, base image) with its PyPI or
  official page, and the documented deviation "Python 3.13, not 3.14" (Kosmo's decision).
- Justifications the issue asks for: calibration points (CV2), calibration limit (CV9), input size and FP32 versus
  FP16 for the Pixel 7, then the confidence threshold from the dev report.

## Tasks

| Id | Title | Task file | Depends on | Model | Why this model |
|---|---|---|---|---|---|
| T1 | Tooling skeleton, pins, board geometry, rules doc | `42-camera-feasibility-0b-1/T1-skeleton-board.md` | - | sonnet | Configuration plus geometry mirrored from `BoardGeometry.kt` with listed tests |
| T2 | Kosmo docs: capture protocol, group bins, dataset candidates | `42-camera-feasibility-0b-1/T2-kosmo-docs.md` | T1 | haiku | Documentation copied from this plan, no design choice |
| T3 | LiteRT export feasibility and detector runtime | `42-camera-feasibility-0b-1/T3-export-detector.md` | T1 | sonnet | Library wiring with a specified output contract, early go/no-go on export |
| T4 | Truth v2 reader and dev/eval split | `42-camera-feasibility-0b-1/T4-truth-split.md` | T1 | sonnet | Existing documented format, rule CV3 specified |
| T5 | Annotation format, reader and writer, doc | `42-camera-feasibility-0b-1/T5-annotation-format.md` | T4 | opus | New stored format (case 1): schema, strict reader, version policy |
| T6 | Local annotation tool | `42-camera-feasibility-0b-1/T6-annotation-tool.md` | T5 | sonnet | Small stdlib server and page, behavior and security rules specified |
| T7 | Synthetic boards and sessions for tests | `42-camera-feasibility-0b-1/T7-synthetic.md` | T4, T5 | sonnet | Test fixture generator from fixed geometry |
| T8 | Homography, calibration metric, groups | `42-camera-feasibility-0b-1/T8-geometry-groups.md` | T7 | sonnet | Formulas specified in CV9 and CV14 |
| T9 | COCO dataset builder and public import | `42-camera-feasibility-0b-1/T9-dataset.md` | T8 | sonnet | Specified conversion and CV4 guard |
| T10 | Training, training image, GPU check | `42-camera-feasibility-0b-1/T10-training-image.md` | T3, T9 | sonnet | Wrapper and container wiring with given flags |
| T11 | Per-dart decision | `42-camera-feasibility-0b-1/T11-decide.md` | T3, T8 | sonnet | Rules CV5 to CV7 specified, fake detector tests |
| T12 | Arrival and empty-board events on video | `42-camera-feasibility-0b-1/T12-events.md` | T11 | sonnet | Algorithm and rules CV10, CV11 specified |
| T13 | Metrics, tuning, eval guard, report | `42-camera-feasibility-0b-1/T13-metrics-report.md` | T12 | sonnet | Statistics specified in CV8 to CV15 |
| T14 | CLI, training doc commands, report template | `42-camera-feasibility-0b-1/T14-cli-docs.md` | T10, T13 | sonnet | Wiring of existing pieces and their documentation |

One `opus` task (T5). Tasks needing Kosmo on his machine after the build: none blocks a task; K1 to K6 run once the PR
is open (K2 and K3 can start right after T2 is merged into the branch).

## Decisions

| Id | Decision | Rules |
|---|---|---|
| Py | Python 3.13 everywhere (sandbox, image, CI); deviation from "latest stable" because `rfdetr` and `litert-torch` do not install together on 3.14. `download-r2.pytorch.org` allowlisted. | - |
| B1 | (Issue) RF-DETR Nano first; keypoint model only on Kosmo's decision after a report. | - |
| B2 | (Issue) LiteRT only, measures on the `.tflite` with `ai-edge-litert`; ONNX Runtime only as fallback on Kosmo's decision. | CV4 |
| B4 | (Issue) Kosmo clicks tips and the 4 calibration points once per session. | CV2, CV12 |
| B5 | (Issue) Existing captures are dev; new dedicated sessions (>= 500 darts) are eval. | CV3 |
| B6 | (Issue) >= 95 % correct without flag and <= 1 % unflagged errors. | CV8 |
| 1 | Each dart was entered right after its throw: arrival windows apply to every capture. | CV10 |
| 2 | Removal criterion "empty board recognised before Next volley"; protocol: press within 3 s after leaving the field. | CV11 |
| 3 | Nothing typed: tripod group and lighting derived from calibration and luminance, zoom from `truth.json`. | CV14 |
| 4 | An off-board miss is correct if nothing is announced on the board for that dart. | CV7 |
| 5 | Annotation tool: stdlib only, run in WSL outside the container, `127.0.0.1` only, no external resource; writes in `~/kosmo-data/annotations/`, captures read-only. | CV16 |
| 6 | No model file in the repository in 0b-1: SHA-256, size and rebuild command only. | - |
| 7 | No pseudo-annotation: manual clicks. | - |
| 8 | Public set candidates listed with links; Kosmo checks licences before download. | - |
| 9 | RF-DETR Nano COCO weights downloaded at image build, pinned by SHA-256. | - |
| 10 | GPU station: RTX 4070 12 GB, WSL2 Ubuntu 26.04, rootless Docker 29.9, GPU by CDI only, container flags as in "Approach". | - |
| 12 | CI job proposed under "Proposed owner changes". | - |
| D1 | Dev/eval cutoff `2026-11-16T00:00:00Z` (every session started from that date is eval). To confirm by Kosmo at the plan gate. | CV3 |
| D2 | 4 calibration points on the outer double corners 5|20, 13|6, 17|3, 8|11; calibration limit 2.0 mm. | CV2, CV9 |
| D3 | Detection and calibration metrics on photos, events on video (different crop and scale). | CV9 to CV12 |
| D4 | No OpenCV; numpy DLT and PyAV (already pulled by `rfdetr`). Only `pytest` added as a direct dependency. | - |
| D5 | Annotation tool UI in French only (it is for Kosmo); started with `python3.13`. | CV16 |
| D6 | CUDA build `cu130` for the GPU image. | - |

## Open questions

- P1 (information for main, not blocking): Roboflow Universe, Hugging Face, Kaggle and Zenodo are not reachable
  from the sandbox, so no public dataset page or licence could be opened. `docs/camera/datasets.md` (T2) gives
  search links and acceptance criteria (CC BY 4.0 only, DeepDarts excluded); the tooling works with zero public sets.
- P2 (Kosmo, at the plan gate): confirm the cutoff of D1 (no dev capture from 2026-11-16 on).
