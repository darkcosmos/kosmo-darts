# Plan: camera phase 0b-1, detection feasibility (issue #42)

## Goal

Issue [#42](https://github.com/darkcosmos/kosmo-darts/issues/42): go/no-go for camera scoring. The squad writes Python
tooling that Kosmo runs on his own captures on his GPU station: annotation, training of RF-DETR Nano, LiteRT export,
measurement of the exported `.tflite` against the thresholds of the issue. Only numbers come back (reports pasted in PR
comments); the squad fills `docs/camera/feasibility-report.md` from them. Phase 0b-2 (#83, Kotlin vision module and
diagnostic screen) is out of scope and only starts after a "go".

Kosmo's change of 2026-11-15 (decision K0): no new capture campaign before a first result. 0b-1 runs on the existing
captures (`~/kosmo-data/captures/`, about 500 darts), split about 70/30 dev/eval (CV3), and ends with a preliminary
report with an explicit limits section. Depending on the result, that report proposes a path (clearly below the
thresholds) or specifies a targeted confirmation campaign, whose sessions go to `~/kosmo-data/captures-eval/` (CV17).
The annotation tool has priority: Kosmo annotates the existing captures as soon as it is delivered.

## Scope and out of scope

In scope:
- `tools/camera/`: Python package `kosmo_camera` (truth reader, dev/eval split, annotation format, board geometry,
  homography, synthetic boards, COCO dataset builder, training, export, LiteRT detector, per-dart decision, arrival
  and empty-board events, metrics and report, CLI), the stdlib-only annotation tool, the GPU training image
  (`Dockerfile`, pinned requirements), CPU tests on generated boards.
- Docs: `docs/rules/camera.md` (rules CV1 to CV17 below), `docs/camera/annotation-format.md`,
  `docs/camera/datasets.md`, `docs/camera/training.md`, `docs/camera/feasibility-report.md` (template, then filled),
  `docs/adr/0016-camera-feasibility-tooling.md`.
- `.gitignore`: `.venv/`, `__pycache__/`, `.pytest_cache/`, `*.tflite`, `*.pth` (no model or weights committed).

Out of scope: any Kotlin, any change to `:app`, `:capture`, `:game-engine`; inference time on the Pixel 7 (0b-2); the
keypoint fallback model and ONNX Runtime (only on Kosmo's decision after a report); pseudo-annotation (decision 7);
any model file in the repository (decision 6); `.github/**` (CI job proposed to the owner, see "PR material").

## Approach

- **Two environments, one Python 3.13** (Kosmo's Python decision). Sandbox and CI: `.venv` at the repository root,
  `tools/camera/requirements-cpu.txt` (full transitive set, exact versions with `--hash`, installed with `--require-hashes`, torch from the PyTorch CPU index; Linux x86_64 cp313 only). GPU
  image: `tools/camera/requirements-gpu.txt`, same set except `torch`, `torchvision`, `torchao` from the `cu130`
  index (CUDA 13.0 runtime in the wheels; any 13.x driver works, widest choice for a "recent driver"). Key pins
  checked with the resolver on 2026-11-15 in the sandbox: `rfdetr` 1.12.1, `litert-torch` 0.10.0, `ai-edge-litert`
  2.3.0, `ai-edge-quantizer` 0.10.1, `torch` 2.14.1, `torchvision` 0.29.2, `numpy` 2.5.4, `pillow` 13.0.0, `av` 19.0.2
  (all pulled by `rfdetr` or `litert-torch` except `pytest` 9.2.0: no extra library).
- **Stdlib-only core**: `config.py`, `truth.py`, `split.py`, `annotations.py`, `board.py` import only the standard library, so the
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
- **Evaluation guard** (CV4): every tool except `split` and the annotation tool needs the split file whose SHA-256
  matches `split-lock.json`. `measure --set eval` also needs `tools/camera/config/frozen.json` (only `tau_low`,
  `tau_sure` and the model SHA-256, strict reader `frozen.py` in T13, the single reader of that file) and fails if the model hash
  differs. Training, tuning and dataset building refuse any eval session or block, and anything in `captures-eval/`.
- **Preliminary report** (decision K0, T13b): same measures and thresholds with 95 % intervals, plus a "Limits"
  section (sample size and interval widths, overlap of tripod groups and lightings between dev and eval, split mode,
  removals not measurable in `captures/`). Verdict per threshold: "clearly below" when the whole interval misses the
  target, otherwise "close or above". Any "clearly below" gives a proposed path; otherwise the report specifies the
  confirmation campaign (darts needed for a 2-point half-width at the observed rates, tripod groups and lightings
  missing or thin in eval, weakest zones, the removal protocol of `training.md`).
- **Annotation tool** (decision 5): `http.server` on `127.0.0.1`, French UI in one static page (HTML, CSS, JS from the
  package, no CDN, no web font). The per-dart flow, the states and every security rule are written once, in
  `42-camera-feasibility-0b-1/T6a-annotation-server.md` (server and flow) and `T6b-annotation-page.md` (page); the
  flow logic is Python (`annotate/flow.py`), so it is tested without a browser.
- **Dev/eval split** (CV3, decision K0): computed once on the real session list by `python3.13 -m kosmo_camera.split`
  (stdlib, runs in WSL before annotation), which prints counts only. Session mode first; if the balance criteria of
  CV3 fail (real session count unknown to the squad), block mode, flagged "more optimistic" everywhere it appears.
  The split file stays in `~/kosmo-runs/`; the repository keeps its SHA-256 in `tools/camera/config/split-lock.json`
  (no session name committed). Block mode is locked only after Kosmo's OK in the PR.
- **Constants** (A1): one source of truth, `tools/camera/config/defaults.json`, loaded by the stdlib module
  `kosmo_camera/config.py` (strict reader); modules never repeat the values.
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
- CV3 Split of `captures/`, about 70/30 dev/eval, computed once. Key of a unit: SHA-256 of `"kosmo-0b1-split-v1:"`
  plus the session name (block mode: plus `":"` and the block index). Session mode: walk the sessions in key order and
  put a session in eval while the eval share of judged darts (CV5) is below 30 % and adding it keeps the share at or
  below 35 % (otherwise skip it). Sessions with no judged dart (CV5) are ignored: they are in no set, are not walked
  in either mode and do not count for "at least 2 sessions per set"; the split file and the `split` output give their number only
  (Kosmo, 2026-11-15). Accepted if the eval share is between 25 % and 35 %, each set has at least 2 judged
  sessions and eval has at least 100 judged darts. Otherwise block mode: each session cut into blocks of
  `SPLIT_BLOCK_VOLLEYS` (5) consecutive volleys, same walk over blocks; flagged "more optimistic" (same tripod,
  lighting and calibration clicks on both sides). The split file is canonical JSON (sorted keys); its SHA-256 is
  locked in `split-lock.json`. Every tool except `split` and the annotation tool checks it.
- CV4 Eval units never enter training, tuning, threshold choice or dataset building; `measure --set eval` needs a
  valid `frozen.json` (strict: format, version, `tau_low`, `tau_sure`, 64-hex `modelSha256`, no other key) with a
  matching model SHA-256; the report records the model, `frozen.json` and split hashes.
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
- CV11 Empty board (only on `captures-eval/` sessions captured with the removal protocol; not measurable on
  `captures/`, where the protocol was mixed, and reported as such; volleys with at least one dart on the board): success if declared between the last
  entry and the volley `end`; a false empty is one declared after the first on-board dart and before the last entry.
  Targets: >= 99 % success, 0 false empty.
- CV12 Tip error: clicked tip versus matched predicted tip, both mapped with the clicked homography; median and 95th
  percentile in mm, VISIBLE annotations only.
- CV13 Confidence intervals: Wilson 95 % for rates, distribution-free order-statistic interval for quantiles.
- CV14 Breakdowns: near and far half (split line through the bull, perpendicular to the camera direction), single,
  double, treble, 25, bull (from truth), lighting class, tripod group, zoom bin.
- CV15 Reports contain numbers and group labels only: no image, path, file or session name.
- CV16 Annotation tool: stdlib only, `127.0.0.1` only, no external resource, captures read-only, writes only in the
  annotations folder, atomic; every route resolves the session name against the listed sessions; never overwrites
  an annotation file that the strict reader rejects. Details in T6a.
- CV17 Sessions in `~/kosmo-data/captures-eval/` (confirmation campaign, later) are always eval: never in the split,
  never in training or tuning; `measure --set campaign` reads them under the same `frozen.json` guard.

## Files and types added or changed

- Added `tools/camera/`: `requirements-cpu.txt`, `requirements-gpu.txt`, `pytest.ini`, `Dockerfile`, `README.md`
  (pointer to `docs/camera/training.md`), `config/defaults.json` (every constant: `BOX_FRACTION`, split ratio and
  criteria, `SPLIT_BLOCK_VOLLEYS`, `MATCH_RADIUS_MM`, `CAL_MAX_ERROR_MM`, initial `TAU_LOW`/`TAU_SURE`, group bins,
  event parameters; read only through `config.py`). Later, committed by developer-fix from Kosmo's pasted numbers:
  `config/split-lock.json` (mode, split file SHA-256, unit and judged-dart counts; no session name) and
  `config/frozen.json` (thresholds and model hash only, A3), before the eval run.
- Added package `tools/camera/kosmo_camera/`: `config.py`, `board.py`, `truth.py`, `split.py`, `annotations.py`, `geometry.py`,
  `groups.py`, `synth.py`, `dataset.py`, `public_import.py`, `model_spec.py`, `train.py`, `gpu_check.py`, `export.py`,
  `detector.py`, `decide.py`, `video.py`, `events.py`, `metrics.py`, `tune.py`, `frozen.py`, `report.py`, `cli.py`, `__main__.py`,
  `annotate/` (`flow.py`, `server.py`, `jpeg_size.py`, `static/`). The ADR is written by T1.
- Added tests `tools/camera/tests/` (pytest; marker `slow` for export and training smoke tests).
- Added docs listed in "Scope". Changed `.gitignore`. Nothing else changes (`git diff origin/main -- app capture
  game-engine .github` stays empty).

## Data read, stored or exposed

- Read (Kosmo's machine): `~/kosmo-data/captures/<session>/` (truth, photos, videos), later
  `~/kosmo-data/captures-eval/<session>/` (CV17), `~/kosmo-data/public/`, `~/kosmo-data/annotations/`. Never modified
  by any tool except the annotation tool, which writes only annotations. The squad never sees the session count.
- Written: `~/kosmo-runs/` (split file, datasets, checkpoints, `.tflite`, per-run JSON results, reports). Nothing
  written in the repository by the tools. The `split` command prints counts and the split SHA-256 only.
- Exposed: the Markdown report only (CV15), pasted by Kosmo. The model stays in `~/kosmo-runs/`; the repository keeps
  its SHA-256, size and rebuild command (decision 6). No network at run time (container `--network none`; annotation
  tool loopback only).

## Annotation format outline (finalized by T5 in `docs/camera/annotation-format.md`)

`{ format: "kosmo-camera-annotations", version: 1, session, truthSha256, calibration: { photo: "dart-NNN.jpg",
width, height, points: [ {x, y} x4 in CV2 order ] } | null, darts: [ { number, status: "VISIBLE"|"HIDDEN"|"OFF_BOARD",
tip: {x, y} | null } ] }`. Pixel coordinates in the stored pixel order of the JPEG (origin top left, floats, EXIF
orientation ignored everywhere: page, server and PIL loaders, decision D8). `tip` set exactly when VISIBLE. Strict
reader (unknown fields, duplicates, version, numbers that do not exist in the truth file are rejected; 1 MiB cap).

## Screens and states (annotation tool, French)

Session list, calibration screen, darts screen, the per-dart flow (one click sets the tip and moves on, autosave on
every action), keys, markers, the error and empty states and their French messages: written once in
`42-camera-feasibility-0b-1/T6a-annotation-server.md` (flow and states) and `T6b-annotation-page.md` (page, keys,
markers, contrast). The tool does not show dev or eval: Kosmo annotates every session the same way.

## Test plan

Every test name or comment cites its CV id. All tests run on CPU in the sandbox on generated data; task files list
the cases. Summary:
- T1: board zones on borders (CV1), Kotlin constants in sync, calibration point coordinates (CV2), `defaults.json`
  loads strictly (A1).
- T3 (slow, fixed seeds): untrained Nano with 5 classes exports to `.tflite`, LiteRT runs it, decoded output shapes;
  LiteRT and PyTorch top-10 boxes within 1e-3 and logits within 0.1.
- T4: truth reader on golden v2 text from `docs/camera/capture-format.md`, rejections.
- T4b: CV3 session walk, balance criteria and fallback to block mode, sessions with no judged dart ignored (no set,
  not counted for the 2-session criterion), determinism, lock check, CV17 exclusion.
- T5: annotation round trip, strict rejections, consistency with the truth file, size cap.
- T6a: flow transitions, autosave round trip, session resolution on every route, write-route attacks (encoded `..`,
  `/`, backslash, NUL, unlisted name), body and header checks, error states (CV16).
- T6b: static files scan (no external URL, no `innerHTML`, `eval`, `document.write`), French strings.
- T7: synthetic board render reproducible from a seed, known tips land in known zones.
- T8: DLT exact on 4 points, CV9 metric at exactly 2.0 mm and with fewer than 4 points, groups (CV14).
- T9: COCO output, box size, eval unit refused (CV4), public class map.
- T10a: CPU and GPU requirements in sync and hashed, Dockerfile static checks, `gpu_check` exit codes.
- T10 (slow, fixed seed): 2-iteration training on CPU from random weights on synthetic data produces a checkpoint.
- T11: CV6 and CV7 on fake detector outputs, boundaries (6.0 mm, `tau_low`, ties, same-class duplicates).
- T12: CV10 and CV11 on generated videos: arrival in window, false arrival, person occlusion, empty board.
- T13: intervals, breakdowns, tuning (including no feasible pair), CV8 boundaries, 0 judged darts, strict
  `frozen.json`, CV4 guard with split lock.
- T13b: limits section, D12 verdict at the interval boundary, path list or campaign size, CV15 privacy scan.
- T14: CLI end to end on a synthetic session (fake detector), commands documented match `--help`.

Python checks for every task (T5 of the design review): `.venv/bin/python -m pytest tools/camera -q` (plus `-m slow
-q` where the task says so) and `.venv/bin/python -m compileall -q tools/camera`. No Python linter is added (D4).
`kosmo-detekt` is still run before each review request because the policy requires it; it only proves the Kotlin
side unchanged, and the PR's "Local checks" says so.

## How Kosmo verifies

For each of the two PRs (D11): no change to the apps: on the Pixel 7, install the CI debug APK; Kosmo Darts and
Kosmo Capture behave as before. Then on the PC (steps detailed in `docs/camera/training.md`). No capture campaign before the preliminary report (K0).
- K1 (PR A) Split (WSL, no container): `python3.13 -m kosmo_camera.split --captures ~/kosmo-data/captures --lock ~/kosmo-runs/split-lock.json`. It prints
  the mode (session or block, "plus optimiste"), units and judged darts per set, and the split SHA-256; Kosmo pastes
  that output plus the content of `~/kosmo-runs/split-lock.json` (counts and hash only). The squad commits that
  content byte for byte as `tools/camera/config/split-lock.json` (block mode only after Kosmo's OK).
- K2 (PR A) Annotate all existing sessions with the tool (about 1 h). What he should see: relaunch with
  `python3.13 -m kosmo_camera.annotate --captures ~/kosmo-data/captures --annotations ~/kosmo-data/annotations`, open
  `http://127.0.0.1:8765` in Windows; the session list shows progress and calibration status; calibration asks the 4
  points in order 1 to 4 on a diagram that highlights the next point; each dart photo shows its true value ("T20"); voided entries are skipped; one
  click sets the tip and shows the next dart; "Enregistré à HH:MM:SS" after each action; closing and reopening a
  session resumes at the first dart not annotated, with earlier tips drawn on the same spots; files appear in
  `~/kosmo-data/annotations/<session>.json`, the captures folder is unchanged.
- K3 (PR A, while annotating) Build the image once with network, run `python -m kosmo_camera.gpu_check` in it: GPU
  name and `cuda available: True`; paste the COCO weights SHA-256 the first build prints (T10a); developer-fix pins
  it in the `Dockerfile` (in PR A if pasted before merge, else in PR B).
- K4 (PR A, during part B) Check the licences of the public set candidates (`docs/camera/datasets.md`), download the
  accepted ones.
- K5 (PR B) Rebuild the image (it carries the PR B code), run `gpu-check`, then run on dev: dataset, train, export, measure, tune; paste the dev report. The squad iterates on dev reports only,
  then commits `frozen.json`.
- K6 (PR B) Run `measure --set eval` once; paste the preliminary report. The squad fills `feasibility-report.md`: path
  proposal, or the confirmation campaign to capture into `~/kosmo-data/captures-eval/` (CV17).

## PR material (for developer-fix)

Two PRs (decision D11). Common to both: review order (A2), one commit or more per task, never mixing tasks; the PR
description lists its tasks in the order of the table below with their commits; "How to verify" starts with the
unchanged apps on the Pixel 7, then the K-steps of that PR.

PR A (branch `feat/camera-feasibility-0b-1`, tasks T1, T4, T4b, T5, T6a, T6b, T10a, T2; refers to #42 without
closing it):
- How to verify: K1, K2, K3 (and K4 to start during part B).
- Proposed owner changes: new job `camera-tools` in `.github/workflows/android.yml`: `ubuntu-26.04`,
  `actions/setup-python` with `3.13`, `pip install -r tools/camera/requirements-cpu.txt`, `python -m pytest
  tools/camera -m "not slow"`; no artifact (no data). Optional second step for the slow tests.
- Versions table: every top-level pin (Python 3.13.18, `rfdetr`, `litert-torch`, `ai-edge-litert`,
  `ai-edge-quantizer`, `torch`, `torchvision`, `av`, `numpy`, `pillow`, `pytest`, base image) with its PyPI or
  official page, and the documented deviation "Python 3.13, not 3.14" (Kosmo's decision).
- Justifications the issue asks for: calibration points (CV2), calibration limit (CV9).
- Residual risks stated: `requirements-*.txt` pinned with `--hash` (S6, wheels for Linux x86_64 cp313 only); COCO
  weights are a pickle checked by MD5 then trusted on first download until `NANO_SHA256` is pinned (S5).
- Committed by developer-fix from Kosmo's pasted output before merge: `split-lock.json` (K1) and, if pasted,
  `NANO_SHA256` (K3).

PR B (new branch from an up-to-date `main` once PR A is merged, tasks T3, T7, T8, T9, T10, T11, T12, T13, T13b, T14;
`Closes #42`):
- How to verify: K5, K6 (image rebuilt first).
- Versions table only if a pin changes in part B; `NANO_SHA256` pinned here if PR A merged without it.
- Justifications the issue asks for: input size and FP32 versus FP16 for the Pixel 7, then the confidence threshold
  from the dev report.

## Tasks

Part A / PR A, annotation, image and GPU check first (decisions K0 and D11; Kosmo starts K1, K2 and K3 as soon as it
is delivered):

| Id | Title | Task file | Depends on | Model | Why this model |
|---|---|---|---|---|---|
| T1 | Tooling skeleton, pins, config, board geometry, rules doc | `42-camera-feasibility-0b-1/T1-skeleton-board.md` | - | sonnet | Configuration plus geometry mirrored from `BoardGeometry.kt` with listed tests |
| T4 | Truth v2 reader | `42-camera-feasibility-0b-1/T4-truth.md` | T1 | sonnet | Reader of an existing documented format |
| T4b | Dev/eval split, split file and lock | `42-camera-feasibility-0b-1/T4b-split.md` | T4 | sonnet | Algorithm and criteria fully specified in CV3; derived, regenerable file |
| T5 | Annotation format, reader and writer, doc | `42-camera-feasibility-0b-1/T5-annotation-format.md` | T4 | opus | New stored format (case 1): schema, strict reader, version policy |
| T6a | Annotation server and per-dart flow | `42-camera-feasibility-0b-1/T6a-annotation-server.md` | T5 | sonnet | Flow, states and security rules specified, Python tests |
| T6b | Annotation page | `42-camera-feasibility-0b-1/T6b-annotation-page.md` | T6a | sonnet | Page from a specified flow and layout |
| T10a | GPU training image and GPU check | `42-camera-feasibility-0b-1/T10a-gpu-image.md` | T1 | sonnet | Container and hashed requirements wiring with given flags, URL and hashes; not haiku because the GPU hashes are generated from the `cu130` index and checked against the CPU file |
| T2 | Kosmo docs: split, annotation, image and GPU check, campaign protocol, bins, datasets | `42-camera-feasibility-0b-1/T2-kosmo-docs.md` | T4b, T6b, T10a | haiku | Documentation copied from this plan and the T10a `Dockerfile`, no design choice |

Part B / PR B, training and measurement:

| Id | Title | Task file | Depends on | Model | Why this model |
|---|---|---|---|---|---|
| T3 | LiteRT export feasibility and detector runtime | `42-camera-feasibility-0b-1/T3-export-detector.md` | T1 | sonnet | Library wiring with a specified output contract, early go/no-go on export |
| T7 | Synthetic boards and sessions for tests | `42-camera-feasibility-0b-1/T7-synthetic.md` | T4, T5 | sonnet | Test fixture generator from fixed geometry |
| T8 | Homography, calibration metric, groups | `42-camera-feasibility-0b-1/T8-geometry-groups.md` | T7 | sonnet | Formulas specified in CV9 and CV14 |
| T9 | COCO dataset builder and public import | `42-camera-feasibility-0b-1/T9-dataset.md` | T4b, T8 | sonnet | Specified conversion and CV4 guard |
| T10 | Training | `42-camera-feasibility-0b-1/T10-training.md` | T3, T9, T10a | sonnet | Wrapper around a library training call with given parameters and tests |
| T11 | Per-dart decision | `42-camera-feasibility-0b-1/T11-decide.md` | T3, T8 | sonnet | Rules CV5 to CV7 specified, fake detector tests |
| T12 | Arrival and empty-board events on video | `42-camera-feasibility-0b-1/T12-events.md` | T11 | sonnet | Algorithm and rules CV10, CV11 specified |
| T13 | Metrics, tuning, frozen thresholds and eval guard | `42-camera-feasibility-0b-1/T13-metrics-report.md` | T12 | sonnet | Statistics and strict reader specified in CV4, CV8 to CV14 |
| T13b | Preliminary report | `42-camera-feasibility-0b-1/T13b-report.md` | T13 | sonnet | Report content and verdict rule D12 fully specified |
| T14 | CLI, training doc commands, report template | `42-camera-feasibility-0b-1/T14-cli-docs.md` | T10, T13b | sonnet | Wiring of existing pieces and their documentation |

18 tasks (8 in part A, 10 in part B), one `opus` task (T5). Execution order: part A in table order, PR A, then part B
in table order, PR B (D11). No task needs Kosmo's machine to be built; K1 to K3 run once PR A is delivered (K4 during
part B), K5 and K6 once PR B is.

## Decisions

| Id | Decision | Rules |
|---|---|---|
| Py | Python 3.13 everywhere (sandbox, image, CI); deviation from "latest stable" because `rfdetr` and `litert-torch` do not install together on 3.14. `download-r2.pytorch.org` allowlisted. | - |
| B1 | (Issue) RF-DETR Nano first; keypoint model only on Kosmo's decision after a report. | - |
| B2 | (Issue) LiteRT only, measures on the `.tflite` with `ai-edge-litert`; ONNX Runtime only as fallback on Kosmo's decision. | CV4 |
| B4 | (Issue) Kosmo clicks tips and the 4 calibration points once per session. | CV2, CV12 |
| B5 | Superseded by K0. (Issue) Existing captures are dev; new dedicated sessions (>= 500 darts) are eval. | - |
| K0 | Kosmo, 2026-11-15: no capture campaign before a first result; existing captures split about 70/30 by session (block mode, "more optimistic", if too coarse); preliminary report with limits; path proposal or targeted campaign into `captures-eval/`; annotation tool first. Supersedes B5, D1, P2. | CV3, CV11, CV17 |
| B6 | (Issue) >= 95 % correct without flag and <= 1 % unflagged errors. | CV8 |
| 1 | Each dart was entered right after its throw: arrival windows apply to every capture. | CV10 |
| 2 | Removal criterion "empty board recognised before Next volley"; protocol: press within 3 s after leaving the field. Applies to the `captures-eval/` campaign only (K0). | CV11 |
| 3 | Nothing typed: tripod group and lighting derived from calibration and luminance, zoom from `truth.json`. | CV14 |
| 4 | An off-board miss is correct if nothing is announced on the board for that dart. | CV7 |
| 5 | Annotation tool: stdlib only, run in WSL outside the container, `127.0.0.1` only, no external resource; writes in `~/kosmo-data/annotations/`, captures read-only. | CV16 |
| 6 | No model file in the repository in 0b-1: SHA-256, size and rebuild command only. | - |
| 7 | No pseudo-annotation: manual clicks. | - |
| 8 | Public set candidates listed with links; Kosmo checks licences before download. | - |
| 9 | RF-DETR Nano COCO weights downloaded at image build, pinned by SHA-256. | - |
| 10 | GPU station: RTX 4070 12 GB, WSL2 Ubuntu 26.04, rootless Docker 29.9, GPU by CDI only, container flags as in "Approach". | - |
| 12 | CI job proposed under "Proposed owner changes". | - |
| D1 | Superseded by K0 (was: dev/eval cutoff date). | - |
| D2 | 4 calibration points on the outer double corners 5|20, 13|6, 17|3, 8|11; calibration limit 2.0 mm. | CV2, CV9 |
| D3 | Detection and calibration metrics on photos, events on video (different crop and scale). | CV9 to CV12 |
| D4 | No OpenCV; numpy DLT and PyAV (already pulled by `rfdetr`). Only `pytest` added as a direct dependency. | - |
| D5 | Annotation tool UI in French only (it is for Kosmo); started with `python3.13`. | CV16 |
| D6 | CUDA build `cu130` for the GPU image. | - |
| D7 | Design review S5: the container keeps uid 0 inside, no `USER`. With rootless Docker, container root maps to Kosmo's own unprivileged uid; a non-root `USER` maps to a subordinate uid that cannot write `~/kosmo-runs` without changing its permissions. Root inside has no extra power (`--cap-drop ALL`, `no-new-privileges`, `--read-only`, `--network none`). Accepted by the security reviewer at the design review of 1377af2. | - |
| D8 | Pixel coordinates in stored JPEG pixel order, EXIF orientation ignored everywhere; the server checks clicks against the real JPEG size (U3). | CV16 |
| D9 | Constants in `defaults.json` only, read by `config.py` (A1). | - |
| D10 | Requirements pinned with hashes, installed with `--require-hashes` (S6). | - |
| D11 | Kosmo, 2026-11-15 (P3): two PRs. PR A: T1, T4, T4b, T5, T6a, T6b, T10a, T2 (T10a taken out of T10: image and `gpu_check`, depends on T1 only), so Kosmo builds the image and checks the GPU while he annotates; `docs/camera/datasets.md` ships in PR A so licences are checked during part B. PR B: the rest, from a new branch, T10 reduced to training. | - |
| D13 | Kosmo, 2026-11-15: sessions with no judged dart (CV5) are ignored by the split: in no set, not counted for "at least 2 sessions per set". Tested in T4b; the annotation tool lists such a session as "Ignorée" (design review U9, T6a). | CV3, CV16 |
| D14 | Design review A5: two-step image. PR A delivers the image with `gpu_check` only (no entry point); PR B adds training and the `python -m kosmo_camera` entry point, and Kosmo rebuilds it (K5). Recorded in the ADR (T1). | - |
| D12 | Verdict per threshold: "clearly below" when the whole 95 % interval misses the target; else "close or above" (preliminary report, K0). | CV8 to CV11 |

## Open questions

- P1 (information for main, not blocking): Roboflow Universe, Hugging Face, Kaggle and Zenodo are not reachable
  from the sandbox, so no public dataset page or licence could be opened. `docs/camera/datasets.md` (T2) gives
  search links and acceptance criteria (CC BY 4.0 only, DeepDarts excluded); the tooling works with zero public sets.
- P3: decided, two PRs (D11).
