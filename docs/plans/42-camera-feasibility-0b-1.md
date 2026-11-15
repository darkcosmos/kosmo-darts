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

Kosmo's information of 2026-11-15 after annotation (decision D15): the tripod moved during the captures (a net move
from dart 59 in one eval session, drifts of a few pixels in every session), so the calibration clicked once per
session is wrong on part of the photos; `truth.json` values and the tips clicked per photo stay right. PR B therefore
adds, before training: a drift measure per photo (CV18), calibration segments in annotation format version 2 with a
"Recalibrer à partir de cette fléchette" action in the tool (CV20), compensation of sub-threshold drift in the
`cal1`-`cal4` training labels and in the mm measures (CV19), and the order measure, recalibrate, measure again, then
train. The main criterion (correct dart, CV7) does not use the clicked calibration: no session is excluded.

Kosmo's answers of 2026-11-15 on the PR B plan: drift threshold 1.0 mm (D18); batch 1 (T5b, T7, T8, T8b, T6c,
T6d) built first and pushed on the PR B branch so Kosmo measures drift and recalibrates from it while the rest is
built, still one PR B (D19); five public datasets accepted under CC BY 4.0 (D20); the benefit of public data is
measured on dev by training with and without them, the best version kept and reported (D21). Kosmo's complement
of 2026-11-15 (D23, replaces D20 where they differ): two sets accepted with a tip box (`dart-detection3-v1i-coco`,
`mat24-dartsv2-v2i-coco`), one conditional set whose boxes surround whole darts (`dart-tip-detection-v17i-coco`,
usable only through constrained pseudo-labelling), three rejected; public images must not teach that calibration
points are background (CV23); three trainings compared on dev: without public data, A, B (CV22).

## Scope and out of scope

In scope:
- `tools/camera/`: Python package `kosmo_camera` (truth reader, dev/eval split, annotation format v1 then v2, board
  geometry, homography, synthetic boards, tripod drift measure, COCO dataset builder, pseudo-labels of public images
  (CV23), training, export, LiteRT detector, per-dart decision, arrival
  and empty-board events, metrics and report, CLI), the stdlib-only annotation tool, the GPU training image
  (`Dockerfile`, pinned requirements), CPU tests on generated boards.
- Docs: `docs/rules/camera.md` (rules CV1 to CV23 below), `docs/camera/annotation-format.md` (version 2 added in
  PR B),
  `docs/camera/datasets.md`, `docs/camera/training.md`, `docs/camera/feasibility-report.md` (template, then filled),
  `docs/adr/0016-camera-feasibility-tooling.md`.
- `.gitignore`: `.venv/`, `__pycache__/`, `.pytest_cache/`, `*.tflite`, `*.pth` (no model or weights committed).

Out of scope: any Kotlin, any change to `:app`, `:capture`, `:game-engine`; inference time on the Pixel 7 (0b-2); the
keypoint fallback model and ONNX Runtime (only on Kosmo's decision after a report); pseudo-annotation of Kosmo's photos (decision 7; public
images only get pseudo-labels, CV23, D23);
any model file in the repository (decision 6); `.github/**` (CI job proposed to the owner, see "PR material").

## Approach

- **Two environments, one Python 3.13** (Kosmo's Python decision). Sandbox and CI: `.venv` at the repository root,
  `tools/camera/requirements-cpu.txt` (full transitive set, exact versions with `--hash`, installed with `--require-hashes`, torch from the PyTorch CPU index; Linux x86_64 cp313 only). GPU
  image: `tools/camera/requirements-gpu.txt`, same set except `torch`, `torchvision`, `torchao` from the `cu130`
  index (CUDA 13.0 runtime in the wheels; any 13.x driver works, widest choice for a "recent driver"). Key pins
  checked with the resolver on 2026-11-15 in the sandbox: `rfdetr` 1.12.1, `litert-torch` 0.10.0, `ai-edge-litert`
  2.3.0, `ai-edge-quantizer` 0.10.1, `torch` 2.14.1, `torchvision` 0.29.2, `numpy` 2.5.4, `pillow` 13.0.0, `av` 19.0.2
  (all pulled by `rfdetr` or `litert-torch` except `pytest` 9.2.0: no extra library).
- **Stdlib-only core**: `config.py`, `truth.py`, `split.py`, `annotations.py`, `board.py`, `drift_file.py` import only the standard library, so the
  annotation tool runs in WSL with a bare `python3` (3.14 in Kosmo's WSL, which has no python3.13; the stdlib core must work on 3.13 and 3.14), outside the container and without the venv (decision 5).
  Everything with numpy, PIL, av, torch or LiteRT lives in other modules.
- **Detector** (issue): RF-DETR Nano from COCO weights, "keypoints as objects": 5 classes, `tip` and `cal1` to `cal4`,
  each a fixed square box centred on the point (side `BOX_FRACTION` = 2.5 % of the longer image side, a training
  constant); the point is the box centre. Training on Kosmo's dev photos (`dart-NNN.jpg`) in three versions: without
  public data, A and B (D23), the one that measures best on dev kept (D21, CV22). Model contract fixed by T3 (D16): input 384x384 reached by a squash bilinear resize (no
  letterbox, x and y scale independently), category ids 0-based in the order `tip`, `cal1`, `cal2`, `cal3`, `cal4`
  (`model_spec.CLASS_NAMES`), outputs `boxes [1,300,4]` and `logits [1,300,6]` whose trailing slot is "no object".
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
  normalisation, squash resize, outputs `boxes [1,300,4]` cxcywh normalised and `logits [1,300,6]`, slot 5 ignored),
  with our own pre- and post-processing, the exact chain 0b-2 will port to Kotlin. `LiteRtDetector.detect` returns one
  point per query (best of the 5 classes) in image pixels, sorted by score, unthresholded. `RFDETRInference` is not
  used for measures.
- **Tripod drift** (D15, CV18 to CV20): the clicked calibration becomes a list of segments (format v2): a segment
  starts at a dart number and applies until the next segment. `drift.py` (numpy; JPEG decoding by Pillow, already
  pinned, no new dependency) measures, for each judged photo, how far the board moved relative to the reference photo
  of its segment: phase correlation (numpy FFT) first on the board box downscaled, then on 4 patches centred on the
  clicked calibration points, giving one sub-pixel shift per point. Drift in px is the largest point shift; drift in
  mm is the CV9 error of the segment homography against the homography of the shifted points. At or below
  `DRIFT_MAX_MM` the shifted points are the reference calibration of that photo (compensation); above it Kosmo
  recalibrates from that dart in the tool. The same function serves the `drift` command (report plus a local flag file
  read by the tool), the dataset builder (labels) and the measures (CV9, CV12, CV14), so nothing stale is reused.
  Alternatives rejected: OpenCV feature matching (D4, a dependency for one translation per point); a global
  translation only (misses roll and per-corner perspective change); compensation from the model's own calibration
  (the reference must not depend on the model under test).
- **Drift threshold** (CV19, D18): `DRIFT_MAX_MM` = 1.0 mm = `CAL_MAX_ERROR_MM` / 2 (Kosmo chose option B). Why: compensation
  models each corner's motion as a local translation; for jitter of a few pixels this is accurate to the sub-pixel
  precision of the fit (well under 0.2 mm on a board several hundred pixels wide), but a real move (pan, roll, focus
  or zoom change) can break that model. If compensation were entirely wrong on a photo just under the threshold, its
  reference calibration would be off by at most 1.0 mm, half the CV9 budget: a CV9 error measured against it is off
  by at most 1.0 mm (triangle inequality over the 41 points), and a tip label mapped with it stays inside an 8 mm
  ring when centred. 0.5 mm would make the reference error a quarter (the CV9 ratio of 2 mm to 8 mm rings) but asks
  Kosmo to recalibrate on most "few pixels" drifts; 2.0 mm would let a stale calibration that alone fails CV9 pass.
  The value is a trade-off between Kosmo's clicking time and label safety; Kosmo chose 1.0 mm (D18).
- **Public datasets** (D23, CV21): Kosmo downloaded the sets in COCO JSON (Roboflow layout `train/`, `valid/`,
  `test/`, each with `_annotations.coco.json`) into `~/kosmo-data/public/<folder>/`, read-only; the slug is the
  folder name. `docs/camera/datasets.md` has one row per set (slug, set and author, direct link, dated licence,
  exact attribution, status `accepted`, `conditional` or `rejected`, reason; cells in T9b). The committed
  `tools/camera/config/public-class-maps.json` holds only the accepted and conditional sets:
  `dart-detection3-v1i-coco` `dart_tip` -> `tip`; `mat24-dartsv2-v2i-coco` `Dart-Tip` -> `tip`, `Dart` ->
  `whole-dart-checked` (ignored as Kosmo asked, but the image is dropped when no tip centre lies inside that box, so
  an unlabelled tip never stays as background, D24); `dart-tip-detection-v17i-coco` `dart_tip` -> `pseudo-tip-box`
  (never a label by itself: its tip comes only from CV23). A category carrying annotations and absent from the map
  refuses the set; a mapped name absent from the set refuses it (typo guard); a category with no annotation in any
  subset (the Roboflow supercategory at id 0) is ignored without a map entry, since it cannot change a label.
  Accepted sets: the tip is the centre of the source box, the box is rebuilt with `BOX_FRACTION` like Kosmo's
  photos (same 1600 px resize). Public images join the train subset only: the valid subset stays Kosmo's dev
  sessions, shared by the three versions. Untrusted input as in CV21 (S10): the dart-detection3 images are about
  2700 px wide (about 7 to 10 Mpx, a few MB of JPEG), well under `MAX_IMAGE_BYTES` (32 MiB) and Pillow's
  `Image.MAX_IMAGE_PIXELS` (89.5 Mpx, kept), so the caps keep the real files and still stop bombs (T9 tests a
  2700x3600 JPEG). Alternative rejected: a map file inside each data folder (not reviewable, not versioned).
- **Calibration points on public images** (D23, CV23): public images carry no `cal1`..`cal4` label; left as is, the
  visible corners of their boards would be trained as background. Checked in `rfdetr` 1.12.1: the classification
  loss (`models/criterion.py`, `loss_labels`, sigmoid per class with the `ia_bce_loss` default) has no per-image or
  per-class mask, and `iscrowd=1` annotations are removed from the training targets by `ConvertCoco` (they become
  background), so the library offers no supported way to exclude classes on some images. Options: (a) pseudo-label
  the calibration points with the model trained without public data (M0) under a confidence threshold chosen on
  dev, dropping the whole image when a point is uncertain; (b) mask the calibration classes in the loss for public
  images by patching the criterion (private API of a pinned library, a per-image flag through the data loader,
  untestable without training, breaks on any upgrade); (c) keep only public images where no corner is visible
  (needs a detector anyway: it is (a) without the labels); (d) accept the background effect (refused by Kosmo).
  Chosen: (a). M0 already exists (it is the "without public data" version), runs through the exported `.tflite`
  and the measured T3 chain, and every decision is a counted, reported number. Thresholds from M0 on the valid
  subset of its dataset (Kosmo's dev photos, held out): `tauCalHigh` = the lowest score at which M0's best
  prediction per calibration class is within `PSEUDO_MAX_MM` (2.0 mm, as `CAL_MAX_ERROR_MM`) of the reference point
  for at least `PSEUDO_MIN_PRECISION` (99 %) of at least `PSEUDO_MIN_SUPPORT` (30) predictions; `tauCalLow` = the
  `PSEUDO_LOW_QUANTILE` (1 %) quantile of the scores of correct predictions, capped at `tauCalHigh`. On each public
  image: best prediction per class at or above `tauCalHigh` becomes a label; any calibration prediction in
  `[tauCalLow, tauCalHigh)` drops the image (uncertain point); two same-class predictions at or above `tauCalHigh`
  more than half a box side apart drop it (second board or ghost); three or more labels must run clockwise in the order
  `cal1`..`cal4` (CV2), else the image is dropped. No `tauCalHigh` (support or precision not reached): no label, and
  every image with a calibration prediction at or above `tauCalLow` is dropped; no `tauCalLow` either: public data
  refused, A and B not built, stated. Proof: tests with a fake detector on synthetic COCO exports (thresholds,
  band, clockwise rule, whole-image drop) and the dataset test that a kept public image has no calibration
  prediction above `tauCalLow` without its label. Domain shift (other boards, other distances) can lower M0's scores
  on public images: the band drops them rather than guessing, and the A-versus-without comparison measures any harm.
- **Constrained tip pseudo-labels of v17** (D23, CV23): with model A (MA), on each `dart-tip-detection-v17i-coco`
  image, every source box must receive one predicted tip inside the box, in its lower-right quarter
  (`x + w/2 <= px <= x + w`, `y + h/2 <= py <= y + h`, source pixels), score at or above `tauTip`; `tauTip` = the
  lowest score at which MA's tip predictions on the valid subset of its dataset are within `PSEUDO_MAX_MM` of a
  labelled tip for at least 99 % of at least 30 predictions (one-to-one, by score; duplicates within half a box side
  merged). Otherwise the annotation is rejected and its whole image dropped; the image is also dropped when one
  prediction serves two boxes, or when a tip prediction at or above `tauTip` is assigned to no box (an unlabelled
  dart would stay as background, D24), or when M0 gave the image no calibration labels (CV23). The retained tip becomes a `BOX_FRACTION` box. Figures reported: boxes
  accepted and images kept (rates), drops per reason, median and p95 distance in px between the retained tip and the
  box's lower-right corner `(x + w, y + h)`. No `tauTip`, no v17 folder or no image kept: B not built, stated.
- **Three trainings** (D21, D23, CV22): same seed and Kosmo part (`kosmoPartSha256`), public images in train only.
  Chain: `dataset --public none` (D0), train, export E0; `pseudo-cal` (E0 on D0 valid, then on every image of the
  mapped sets); `dataset --public A --pseudo-cal R` (D0 + dart-detection3 + mat24), train, export EA;
  `pseudo-tip` (EA on its valid, then on v17); `dataset --public B --pseudo-cal R --pseudo-tip R` (A + v17 tips),
  train, export EB; `compare` measures the three exports on the dev photos of the shared valid subset, tunes each,
  keeps one by the CV22 order. Eval stays untouched until the single K6 run of the retained version (`frozen.json`
  holds its hash, CV4). Fallbacks: a missing accepted folder builds A from those present (stated); no accepted set:
  "sans jeux publics seulement"; B missing: two versions compared, the reason stated.
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
- **Evaluation guard** (CV4): every tool except `split`, `drift` and the annotation tool needs the split file whose SHA-256
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
- **Dev/eval split** (CV3, decision K0): computed once on the real session list by `python3 -m kosmo_camera.split`
  (stdlib, runs in WSL before annotation), which prints counts only. Session mode first; if the balance criteria of
  CV3 fail (real session count unknown to the squad), block mode, flagged "more optimistic" everywhere it appears.
  The split file stays in `~/kosmo-runs/`; the repository keeps its SHA-256 in `tools/camera/config/split-lock.json`
  (no session name committed). Block mode is locked only after Kosmo's OK in the PR.
- **Constants** (A1): one source of truth, `tools/camera/config/defaults.json`, loaded by the stdlib module
  `kosmo_camera/config.py` (strict reader); modules never repeat the values.
- **Groups for breakdowns** (decision 3, CV14): tripod position from the reference calibration of the photo (CV19; viewing angle from the
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
  locked in `split-lock.json`. Every tool except `split`, `drift` (CV18) and the annotation tool checks it.
- CV4 Eval units never enter training, tuning, threshold choice or dataset building; `measure --set eval` needs a
  valid `frozen.json` (strict: format, version, `tau_low`, `tau_sure`, 64-hex `modelSha256`, no other key) with a
  matching model SHA-256; the report records the model, `frozen.json` and split hashes.
- CV5 Darts judged: non-voided entries with a photo; entries with `photo: null` are counted apart, not judged.
- CV6 Announcement per dart as in "Approach, Per dart" (`TAU_LOW`, `TAU_SURE`, `MATCH_RADIUS_MM`).
- CV7 Outcome per dart: correct (announced value equals truth, not flagged), flagged, or unflagged error. A truth miss
  is correct when nothing is announced on the board (decision 4). Hidden tips stay in the total.
- CV8 Detection thresholds: correct >= 95 % and unflagged errors <= 1 % of judged darts.
- CV9 Calibration success per volley (first photo of the volley): project 41 board points (bull centre, the 20 sector
  centres at 103 mm and at 166 mm) to the image with the reference homography of that photo (CV19), back to the board
  with the predicted one; success if the largest error is at most `CAL_MAX_ERROR_MM` = 2.0 mm and all 4 points were
  found. Target 99 %. Why 2 mm: a quarter of the 8 mm treble and double rings, so a calibration error alone cannot
  move a dart centred in a ring out of it; to be confirmed by the report. A volley whose first photo has no reference
  calibration (CV19) is not evaluated and is counted.
- CV10 Arrival of dart k (non-voided, segment not 0, both entry positions in the video) is detected if an arrival
  event falls between the previous non-voided entry (volley start for the first) and entry k. Every other arrival
  event is false. Targets: >= 98 % detected, false events <= 1 % of evaluated darts.
- CV11 Empty board (only on `captures-eval/` sessions captured with the removal protocol; not measurable on
  `captures/`, where the protocol was mixed, and reported as such; volleys with at least one dart on the board): success if declared between the last
  entry and the volley `end`; a false empty is one declared after the first on-board dart and before the last entry.
  Targets: >= 99 % success, 0 false empty.
- CV12 Tip error: clicked tip versus matched predicted tip, both mapped with the reference homography of the photo
  (CV19); median and 95th percentile in mm, VISIBLE annotations only; photos without reference calibration counted
  apart.
- CV13 Confidence intervals: Wilson 95 % for rates, distribution-free order-statistic interval for quantiles.
- CV14 Breakdowns: near and far half (split line through the bull, perpendicular to the camera direction), single,
  double, treble, 25, bull (from truth), lighting class, tripod group, zoom bin.
- CV15 Reports contain numbers and group labels only: no image, path, file or session name. The drift report labels
  sessions `S01`, `S02`... in session-name order. The drift flag file `drift.json` (CV18) holds session names and is,
  like the split file, a local working file in `~/kosmo-runs/`: never pasted, never committed. Public dataset slugs,
  licences and image counts (CV21) and the pseudo-label figures (CV23: thresholds, counts, px distances) are allowed
  in reports: they name public sets, not Kosmo's sessions; no public `file_name` is printed.
- CV16 Annotation tool: stdlib only, `127.0.0.1` only, no external resource, captures read-only, writes only in the
  annotations folder, atomic; every route resolves the session name against the listed sessions; never overwrites
  an annotation file that the strict reader rejects (a version 1 file is accepted and written back as version 2 on
  the first change, CV20). It reads the optional drift flag file read-only through its strict stdlib reader; a missing,
  invalid or oversized file gives a warning in the session list, never an error. Details in T6a and T6c.
- CV17 Sessions in `~/kosmo-data/captures-eval/` (confirmation campaign, later) are always eval: never in the split,
  never in training or tuning; `measure --set campaign` reads them under the same `frozen.json` guard.
- CV18 Drift of a judged photo (D15): compared with the reference photo of its segment (CV20), both decoded to luma
  in stored pixel order (D8). (1) Coarse: the box of the 4 segment points enlarged by 15 % is cropped from both,
  block-averaged so its longer side is at most `DRIFT_COARSE_PX` (512), and phase-correlated (zero mean, Hann
  window, normalised cross-power spectrum, `numpy.fft`): shift `t0`. (2) Fine, per point `i`: square patch of side
  `DRIFT_PATCH_FRACTION` (0.06) times the longer image side, centred on `p_i` in the reference and on `p_i + t0` in
  the photo, phase-correlated with a 3-point parabolic sub-pixel fit per axis: `d_i = t0 + r_i`. (3) Drift px =
  max `|d_i|`; drift mm = CV9 error with `H_true` = homography of the points `p_i + d_i` and `H_pred` = segment
  homography (the error the stale calibration would cause). The reference photo itself has drift 0 without
  computation. No usable reference gives status "unmeasurable" with one reason: `uncalibrated` (session without
  calibration), `size_mismatch` (photo size differs from the segment), `border` (a patch not inside both images),
  `low_peak` (a correlation peak below `DRIFT_MIN_PEAK`: occlusion or change), `unreadable`. Drift is annotation
  quality control, independent of any model, so it reads every session, dev and eval, without the split lock (CV4
  unaffected). The `drift` command writes a numbers-only report (CV15) and the local flag file `drift.json`.
- CV19 Drift threshold and compensation: a photo is "beyond" when drift mm > `DRIFT_MAX_MM` (1.0 mm =
  `CAL_MAX_ERROR_MM` / 2, Kosmo, D18). Reference calibration of a photo = its compensated points `p_i + d_i`
  when measured and not beyond; none otherwise. Used for the `cal1`-`cal4` training labels (T9), CV9, CV12 and CV14.
  Photos without reference calibration are dropped from the dataset and excluded from CV9 and CV12, each counted by
  reason. `dataset` and `measure` refuse to run while a judged photo of their set is beyond (message: run `drift`,
  recalibrate, run `drift` again); `--accept-drift` overrides and the run records the count. CV7 (and CV8) never use
  the clicked calibration: no dart or session is excluded from the main criterion.
- CV20 Calibration segments (annotation format version 2): `calibrations` is an ordered list of segments
  `{fromDart, photo, width, height, points}`, at most `MAX_SEGMENTS` (64); the first starts at dart 1, `fromDart` strictly ascending, each applies
  from its `fromDart` until the next one. A version 1 file is read by the kept v1 reader and migrated without loss
  (a v1 `calibration` becomes the single segment from dart 1; `null` becomes an empty list). Writers write version 2
  only. Details in T5b.
- CV21 Public datasets (D20, D23): only sets with an entry in `tools/camera/config/public-class-maps.json` are read,
  from `~/kosmo-data/public/<slug>/` (slug = Kosmo's folder name); the map holds only sets with status `accepted`
  or `conditional` in `docs/camera/datasets.md`, a set with a `pseudo-tip-box` target being `conditional` and any
  other `accepted` (checked by a test, not at run time); rejected sets have no entry and their folders are never
  opened. Targets: `tip` (the box centre is the tip; box rebuilt with `BOX_FRACTION`), `whole-dart-checked`
  (annotation ignored; image dropped when no `tip` centre lies inside the box), `pseudo-tip-box` (whole-dart box,
  tip only from CV23, set read only with a pseudo-tip file), `ignore`, `drop-image`; no calibration target (public
  calibration labels come only from CV23). Every category carrying an annotation in any subset must be in the map;
  a mapped name absent from the set, an unknown slug or subsets with different category lists refuse the set with a
  message naming them; a category with no annotation in any subset is ignored. Public images go to the
  train subset only, never to valid, dev measures or eval. Downloaded files are untrusted input (S10): each
  `_annotations.coco.json` is capped at `public_import.MAX_JSON_BYTES` (64 MiB) and must parse with unique image
  and category ids and no dangling `image_id` or `category_id`, else the set is refused; an image is dropped and
  counted per reason when its `file_name` is not a bare name resolving inside `<slug>/<subset>/` (no `/`, `\`,
  `..`, absolute path or symlink, opened without following links), when it exceeds `public_import.MAX_IMAGE_BYTES`
  (32 MiB), fails to decode (`Image.MAX_IMAGE_PIXELS` kept, decompression bomb, truncated, size different from the
  JSON, as S8), or has a box that is not finite or not inside the image. Sources are never written.
- CV22 Benefit of public data (D21, D23): up to three versions, `sans` (`--public none`), `A` and `B` (CV23), same
  seed and same Kosmo part (`kosmoPartSha256` equal in every dataset manifest, else refused). Simplicity order:
  `sans` < `A` < `B`. Comparison set: the dev photos of the shared valid subset; eval and campaign results are
  refused. Each version is tuned on it (T13 tune), then the versions are ranked by the first difference: (1) a
  version with a feasible pair (unflagged errors <= 1 %) beats one without, and between two without, the lower best
  reachable unflagged rate wins; (2) more correct darts (CV7, same denominator); (3) fewer unflagged errors; (4) more
  CV9 successes; (5) lower CV12 median tip error; (6) still equal: the simpler version. The leader is the first of
  that ranking; the deciding step is reported against each other version. When the 95 % interval of the leader's
  correct rate overlaps that of a simpler version the report says "écart dans le bruit"; until Kosmo answers Q2 this
  case fails closed: the simplest version whose interval overlaps the leader's is kept, and the compare output and
  report show "Écart dans le bruit : règle en attente de la décision de Kosmo (Q2), version <sans jeux publics|A>
  retenue par prudence". Versions not built (CV23 fallbacks, missing folders) are listed with their reason; only
  `sans` built: "sans jeux publics seulement". Limit stated in the report: the pseudo-label thresholds (CV23) are
  chosen on the same dev valid photos; they only filter public labels, never the decision thresholds of CV6.
- CV23 Pseudo-labels of public images (D23, D24): computed only from exported models of this run chain, through
  the T3 detector, on images read by the CV21 validated reader. Calibration (versions A and B): model M0 (dataset
  `sans`) and thresholds `tauCalHigh` and `tauCalLow` from its valid subset, rules and fallbacks of "Approach,
  Calibration points on public images"; every public image of A and B carries M0's calibration labels or is
  dropped (reason counted). Tips of `pseudo-tip-box` sets (version B): model MA (dataset `A`) and `tauTip` from its
  valid subset, rules of "Approach, Constrained tip pseudo-labels of v17"; one rejected box drops the whole image;
  report: acceptance rates, drops per reason, median and p95 px distance to the box's lower-right corner. Pseudo
  files (`pseudo-cal.json`, `pseudo-tip.json`, in the run folder, strict reader, cap `pseudo_file.MAX_BYTES` 16 MiB)
  hold the model SHA-256, the source dataset's `kosmoPartSha256` and version, the map SHA-256, the thresholds and per
  image (slug, subset, `file_name`, image SHA-256) its labels or drop reason; a dataset refuses a pseudo file from
  another Kosmo part or map, from the wrong version (`sans` for calibration, `A` for tips), and drops an image whose
  SHA-256 differs. Constants `PSEUDO_MAX_MM` 2.0, `PSEUDO_MIN_PRECISION` 0.99, `PSEUDO_MIN_SUPPORT` 30,
  `PSEUDO_LOW_QUANTILE` 0.01 in `defaults.json`.

## Files and types added or changed

- Added `tools/camera/`: `requirements-cpu.txt`, `requirements-gpu.txt`, `pytest.ini`, `Dockerfile`, `README.md`
  (pointer to `docs/camera/training.md`), `config/defaults.json` (every constant: `BOX_FRACTION`, split ratio and
  criteria, `SPLIT_BLOCK_VOLLEYS`, `MATCH_RADIUS_MM`, `CAL_MAX_ERROR_MM`, initial `TAU_LOW`/`TAU_SURE`, group bins,
  event parameters, drift parameters `DRIFT_MAX_MM` (1.0, D18; the single place of the value, pinned only in
  `test_config.py`), `DRIFT_CANDIDATES_MM` (0.5, 1.0, 2.0: the report's per-candidate table, kept for information), `DRIFT_COARSE_PX`,
  `DRIFT_PATCH_FRACTION`, `DRIFT_MIN_PEAK` added by T8b; read only through `config.py`). Format caps stay next to
  their readers, like `annotations.MAX_BYTES`: `annotations.MAX_SEGMENTS` = 64 (T5b) and `drift_file.MAX_BYTES` =
  1 MiB (T8b), each tested at the cap and just above. Later, committed by developer-fix from Kosmo's pasted numbers:
  `config/split-lock.json` (mode, split file SHA-256, unit and judged-dart counts; no session name) and
  `config/frozen.json` (thresholds and model hash only, A3), before the eval run. `config/public-class-maps.json`
  (CV21: `format` `"kosmo-camera-public-class-maps"`, `version` 1, `sets`: slug to `{licence: "CC BY 4.0",
  categories: {source name: target}}`; no session name; T9 commits it with `sets` empty, T9b fills it with the three
  entries of "Approach, Public datasets"). `PSEUDO_*` constants added to `defaults.json` by T9c (CV23). The `Dockerfile` copies `config/` next to the package (T8b), since `config.py` reads
  `config/defaults.json` at import.
- Added package `tools/camera/kosmo_camera/`: `config.py`, `board.py`, `truth.py`, `split.py`, `annotations.py` (v2 in
  T5b), `geometry.py`, `groups.py`, `synth.py`, `drift.py`, `drift_file.py`, `dataset.py`, `public_import.py`, `pseudo_file.py`, `pseudo_label.py`, `model_spec.py`, `train.py`, `gpu_check.py`, `export.py`,
  `detector.py`, `decide.py`, `video.py`, `events.py`, `metrics.py`, `tune.py`, `compare.py`, `frozen.py`, `report.py`, `cli.py`, `__main__.py`,
  `annotate/` (`flow.py`, `server.py`, `jpeg_size.py`, `static/`). The segment of a dart is found by one function,
  `annotations.segment_for` (T5b), imported by `flow.py`, `drift.py` and `dataset.py`, never re-implemented (checked
  by a source test in T9).
- ADR: `docs/adr/0016-camera-feasibility-tooling.md`, written by T1 (PR A). PR B adds three sections to it (A13, A17):
  T5b "Annotation format v2 and migration" (v1 to v2 change, CV20 segments, reader of both versions, v2-only
  writer, rewrite only on the first saved change, segment cap, and the proof of the policy: v1 golden to v2 golden
  byte for byte, a v1 file written by the PR A tool, 50 seeded v1 files round-tripped); T8b "Tripod drift" (method
  CV18 with the alternatives rejected in "Approach, Tripod drift", compensation labels derived from the reference
  photo of the segment, threshold as one constant, 1.0 mm (D18), `drift.json` as a local, regenerable, name-bearing file
  written in the container and read by the stdlib tool, never pasted or committed, CV15); "Public data and version
  selection" (A17): T9 writes the map file, the train-only rule and public datasets as untrusted input with the
  strict validation of CV21 (S10); T9c adds the calibration pseudo-labels with the options of "Approach, Calibration
  points on public images" (loss masking rejected: no class mask in `rfdetr` 1.12.1); T9d adds the constrained v17
  tips; T13 adds the three-version CV22 order and the fail-closed overlap case.
- Added tests `tools/camera/tests/` (pytest; marker `slow` for export and training smoke tests).
- Added docs listed in "Scope". Changed `.gitignore`. Nothing else changes (`git diff origin/main -- app capture
  game-engine .github` stays empty).

## Data read, stored or exposed

- Read (Kosmo's machine): `~/kosmo-data/captures/<session>/` (truth, photos, videos), later
  `~/kosmo-data/captures-eval/<session>/` (CV17), `~/kosmo-data/public/<folder>/` (COCO JSON of the mapped sets,
  read-only, CV21), `~/kosmo-data/annotations/`. Never modified
  by any tool except the annotation tool, which writes only annotations. The squad never sees the session count.
- Written: `~/kosmo-runs/` (split file, `drift/drift.json` and `drift/drift-report.md`, datasets, checkpoints,
  `.tflite`, per-run JSON results, reports). Nothing written in the repository by the tools. The `split` command
  prints counts and the split SHA-256 only; `drift` prints the report path and "Tout sous le seuil : oui|non".
- `drift.json` (local, CV15): per session its name, label `Sxx`, `segmentsSha256`, max drift and the dart numbers
  beyond, suggested and unmeasurable; read by the annotation tool on the same machine only.
- Dataset `manifest.json` (local): adds per public slug its licence, images kept and dropped per reason, and
  `kosmoPartSha256` (hash of the Kosmo images and labels, train and valid) so `compare` can prove the versions share
  the same Kosmo part (CV22); `version` (`sans`, `A`, `B`), the SHA-256 of the pseudo files used and their figures.
- Pseudo files `pseudo-cal.json` and `pseudo-tip.json` (local, CV23): public file names, image hashes, labels,
  thresholds and model hashes; no session name, no Kosmo image.
- Exposed: the Markdown report only (CV15), pasted by Kosmo; it may name public set slugs and licences (CV21). The model stays in `~/kosmo-runs/`; the repository keeps
  its SHA-256, size and rebuild command (decision 6). No network at run time (container `--network none`; annotation
  tool loopback only).

## Annotation format outline (finalized by T5 in `docs/camera/annotation-format.md`)

`{ format: "kosmo-camera-annotations", version: 1, session, truthSha256, calibration: { photo: "dart-NNN.jpg",
width, height, points: [ {x, y} x4 in CV2 order ] } | null, darts: [ { number, status: "VISIBLE"|"HIDDEN"|"OFF_BOARD",
tip: {x, y} | null } ] }`. Pixel coordinates in the stored pixel order of the JPEG (origin top left, floats, EXIF
orientation ignored everywhere: page, server and PIL loaders, decision D8). `tip` set exactly when VISIBLE. Strict
reader (unknown fields, duplicates, version, numbers that do not exist in the truth file are rejected; 1 MiB cap).

Version 2 (T5b, CV20): same document with `version: 2` and `calibrations: [ { fromDart, photo, width, height,
points } ]` instead of `calibration`. Reader knows versions 1 and 2 and always returns the v2 model; writer writes
version 2 only; a v1 file on disk is rewritten as v2 only when the tool saves a change. `truthSha256` is copied
unchanged by the migration.

## Screens and states (annotation tool, French)

Session list, calibration screen, darts screen, the per-dart flow (one click sets the tip and moves on, autosave on
every action), keys, markers, the error and empty states and their French messages: written once in
`42-camera-feasibility-0b-1/T6a-annotation-server.md` (flow and states) and `T6b-annotation-page.md` (page, keys,
markers, contrast). The tool does not show dev or eval: Kosmo annotates every session the same way.
PR B (T6c flow and server, T6d page): action "Recalibrer à partir de cette fléchette" (key R) on the darts screen,
recalibration screen "Recalibration à partir de la fléchette N : point k sur 4", and drift flags in the session list
and on flagged darts, read from `drift.json` (`--drift FILE`, optional).

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
- T5b: v1 golden migrates to the v2 golden byte for byte, v1 to v2 to v1 identity on seeded files, v2 strict
  rejections (first `fromDart` not 1, order, duplicates, unknown key, v3), `segment_for` boundaries, tool flow
  unchanged with one segment, v1 file rewritten as v2 only on a change (CV20, CV16).
- T7: synthetic board render reproducible from a seed, known tips land in known zones, sessions with planned
  per-dart camera drift and v2 annotations.
- T8: DLT exact on 4 points, CV9 metric at exactly 2.0 mm and with fewer than 4 points, groups (CV14).
- T8b: drift recovered on synthetic shifts and roll, mm equal to the CV9 error of the true views, every
  unmeasurable reason, threshold boundary, compensation, report and flag file privacy, guard (CV18, CV19, CV15);
  `DRIFT_MAX_MM` pinned to 1.0 in `test_config.py` only (D18); the `Dockerfile` copies `config/` (batch 1, D19).
- T6c: recalibration flow (start, points, autosave, undo, leave), segment replaced or removed, drift flags and stale
  flag file, invalid flag file is a warning (CV20, CV16).
- T6d: R button and key, French strings, flags shown, static scan as T6b; the batch 1 commands of `training.md`
  parse with `python -m kosmo_camera.drift` and `annotate`.
- T9: COCO output with 0-based category ids, box size, compensated calibration boxes, drift guard and
  `--accept-drift`, eval unit refused (CV4, CV19); on synthetic COCO exports: class map strict reader, unannotated
  category ignored, annotated unmapped category, mapped name absent, unknown slug each refused, `tip` at the source
  box centre with a `BOX_FRACTION` box, `whole-dart-checked` with and without a tip inside, `pseudo-tip-box` set
  refused without pseudo-tip input, `drop-image` and `ignore`, rejected folder never opened, public images in train
  only, A or B without calibration labels refused, versions `none`, `A`, `B` give the same `kosmoPartSha256` and
  valid subset (CV21, CV22); untrusted downloads (S10): `file_name` `../x`, absolute or symlink, oversized image,
  decompression bomb, non-finite or outside boxes each dropped and counted with nothing read outside the public
  folder; a 2700x3600 JPEG kept; oversized or invalid JSON, dangling or duplicated ids each refuse the set; source
  folders unchanged (hashes); `datasets.md` 7-cell rows, dated licence on accepted and conditional rows (S11).
- T9b: the committed map loads strictly and equals the three entries of "Approach, Public datasets"; mapped slugs
  match `datasets.md` statuses (`pseudo-tip-box` only in the `conditional` row); rejected rows unmapped (CV21).
- T9c (fake detector, synthetic dataset and COCO exports): `tauCalHigh` and `tauCalLow` on scripted scores and
  distances (precision exactly 99 % and just below, support 29 and 30, cap at `tauCalHigh`), label above
  `tauCalHigh`, image dropped in the band, same-class duplicate dropped, clockwise rule (3 and 4 points, a swapped pair dropped), no
  `tauCalHigh` fallback, no `tauCalLow` refusal; pseudo file strict reader (cap, unknown key, wrong version or Kosmo
  part or map, image hash changed drops the image); dataset A carries M0's labels, no kept public image has a
  calibration prediction above `tauCalLow` without its label (CV23).
- T9d: `tauTip` choice; per box: tip in the lower-right quarter accepted (quarter edges inclusive), elsewhere in the
  box, outside, below `tauTip` each rejected and the whole image dropped; one prediction for two boxes and an
  unassigned confident tip and missing calibration labels drop the image; duplicates merged; figures (rates, per-reason counts, median and p95 px
  to the corner) against a hand computation; no `tauTip`, no v17 folder, no image kept: B not built (CV23).
- T10a: CPU and GPU requirements in sync and hashed, Dockerfile static checks, `gpu_check` exit codes.
- T10 (slow, fixed seed): 2-iteration training on CPU from random weights on synthetic data produces a checkpoint;
  `train-run.json` lists the version, the public sets and the pseudo files of the dataset manifest (empty without).
- T11: CV6 and CV7 on fake detector outputs, boundaries (6.0 mm, `tau_low`, ties, same-class duplicates).
- T12: CV10 and CV11 on generated videos: arrival in window, false arrival, person occlusion, empty board.
- T13: intervals, breakdowns, tuning (including no feasible pair), CV8 boundaries, 0 judged darts, strict
  `frozen.json`, CV4 guard with split lock, CV9 and CV12 with compensated reference and photos without reference
  counted, CV7 unchanged by drift; CV22 selection on scripted results for two and three versions: each decision
  step, exact tie keeps the simpler version, an earlier step winning over a later one that disagrees, overlap fails
  closed to the simplest overlapping version with its French message until Q2, different `kosmoPartSha256` or an
  eval result refused, `sans` alone, B not built listed with its reason.
- T13b: limits section (with the drift summary and the CV22 threshold limit), D12 verdict at the interval
  boundary, path list or campaign size, public sets section (slugs, licences, images, pseudo-label figures) and
  comparison section (one row per version, deciding step), CV15 privacy scan.
- T14: CLI end to end on a synthetic session (fake detector), `drift` then `dataset` refused while beyond, the
  three-version chain `dataset --public none|A|B`, `pseudo-cal`, `pseudo-tip`, `compare` on fake-detector exports
  writes the comparison and the retained model, commands documented match `--help`.

Python checks for every task (T5 of the design review): `.venv/bin/python -m pytest tools/camera -q` (plus `-m slow
-q` where the task says so) and `.venv/bin/python -m compileall -q tools/camera`. No Python linter is added (D4).
`kosmo-detekt` is still run before each review request because the policy requires it; it only proves the Kotlin
side unchanged, and the PR's "Local checks" says so.

## How Kosmo verifies

For each of the two PRs (D11): no change to the apps: on the Pixel 7, install the CI debug APK; Kosmo Darts and
Kosmo Capture behave as before. Then on the PC (steps detailed in `docs/camera/training.md`). No capture campaign before the preliminary report (K0).
- K1 (PR A) Split (WSL, no container): `python3 -m kosmo_camera.split --captures ~/kosmo-data/captures --lock ~/kosmo-runs/split-lock.json`. It prints
  the mode (session or block, "plus optimiste"), units and judged darts per set, and the split SHA-256; Kosmo pastes
  that output plus the content of `~/kosmo-runs/split-lock.json` (counts and hash only). The squad commits that
  content byte for byte as `tools/camera/config/split-lock.json` (block mode only after Kosmo's OK).
- K2 (PR A) Annotate all existing sessions with the tool (about 1 h). What he should see: relaunch with
  `python3 -m kosmo_camera.annotate --captures ~/kosmo-data/captures --annotations ~/kosmo-data/annotations`, open
  `http://127.0.0.1:8765` in Windows; the session list shows progress and calibration status; calibration asks the 4
  points in order 1 to 4 on a diagram that highlights the next point; each dart photo shows its true value ("T20"); voided entries are skipped; one
  click sets the tip and shows the next dart; "Enregistré à HH:MM:SS" after each action; closing and reopening a
  session resumes at the first dart not annotated, with earlier tips drawn on the same spots; files appear in
  `~/kosmo-data/annotations/<session>.json`, the captures folder is unchanged. Done (Kosmo, 2026-11-15) with
  annotation format v1; PR B reads those files and migrates them on the first change (CV20).
- K3 (PR A, while annotating) Build the image once with network, run `python -m kosmo_camera.gpu_check` in it: GPU
  name and `cuda available: True`; the COCO weights SHA-256 is pinned in the `Dockerfile` (done after Kosmo's first
  build), so the build now passes directly.
- K4 (during part B, D20, D23) Done by Kosmo on 2026-11-15: sets downloaded in COCO JSON into
  `~/kosmo-data/public/<folder>/` (read-only), licences checked on the source pages, box sizes analysed, statuses
  and class names sent (D23). T9b writes the map and the `datasets.md` rows from that message.
- K5 (PR B batch 1, from the pushed branch, D19) Drift loop, documented in `training.md` by T6d and enforced later by
  the drift guard of `dataset` and `measure` (CV19, D15). Batch 1 has no `python -m kosmo_camera` entry point (T14):
  the container runs the module `kosmo_camera.drift` directly, as `gpu_check` in K3.
  - K5.0 In his clone: `git fetch origin` then `git switch feat/camera-feasibility-0b-1-detection` (later
    `git pull`).
  - K5.1 Rebuild the image from the repository root (`docker build -f tools/camera/Dockerfile -t kosmo-camera:gpu
    tools/camera`, it now carries the drift code and `config/`), then the K3 GPU check line unchanged.
  - K5.2 Run the drift measure on all sessions (no GPU needed):
    `docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,size=2g -v ~/kosmo-data:/data:ro -v ~/kosmo-runs:/runs:rw kosmo-camera:gpu python -m kosmo_camera.drift --captures /data/captures --annotations /data/annotations --runs /runs`.
    It writes `~/kosmo-runs/drift/drift-report.md` and `drift.json` and prints "Tout sous le seuil : oui|non".
  - K5.3 Relaunch the annotation tool (WSL, from `tools/camera/` of the branch) with `--drift ~/kosmo-runs/drift/drift.json`: the session list shows the
    sessions to recalibrate and from which darts (the 18h24 eval session should ask from dart 59); on each suggested
    dart press "Recalibrer à partir de cette fléchette" (R) and click the 4 points; "Enregistré à HH:MM:SS". After a
    recalibration the session's flags stay shown with "(mesure périmée)" until `drift` runs again, so the other
    suggested darts of that session are not lost. "Effacer" (U) on a saved recalibration removes its last point and
    the segment from the file at once.
  - K5.4 Run K5.2 again; repeat K5.3 and K5.4 until "Tout sous le seuil : oui"; paste the last drift report,
    `~/kosmo-runs/drift/drift-report.md`.
  - The K5.2 `docker run` line is shown in `training.md` as one copy-paste block with the same wording, keeping
    `--network none`, `--cap-drop ALL`, `no-new-privileges`, `--read-only` and `~/kosmo-data` mounted `:ro` (S10).
- K5b (complete PR B branch, after T14; commands in `training.md` "Run", "Avec ou sans jeux publics" and "Dev loop"):
  - K5.5 `git pull`, rebuild the image (entry point `python -m kosmo_camera` now), run `gpu-check`.
  - K5.6 Three trainings (D21, D23, CV22, CV23), in this order: `dataset --public none`, `train`, `export`;
    `pseudo-cal` (prints the thresholds and images kept or dropped); `dataset --public A`, `train`, `export`;
    `pseudo-tip` (prints the v17 acceptance rate and the median and p95 distance in px); `dataset --public B`,
    `train`, `export`; then `compare` on the three exports: it prints the report path and "Version retenue :
    sans jeux publics|A|B" (plus the fail-closed message of CV22 when it applies); paste the comparison section,
    which holds the pseudo-label figures. A version that cannot be built prints why and `compare` runs on the
    others.
  - K5.7 Dev loop on the retained version: `measure --set dev`, `tune`; paste the dev report. The squad iterates on
    dev reports only, then commits `frozen.json` (thresholds and the retained model's SHA-256).
- K6 (PR B) Run `measure --set eval` once, on the retained version only (the `frozen.json` hash guard refuses any
  other model); paste the preliminary report. The squad fills `feasibility-report.md`: path
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
  weights are a pickle checked by MD5 and pinned by SHA-256 (`NANO_SHA256`, from Kosmo's first build, S5).
- Committed by developer-fix from Kosmo's pasted output: `split-lock.json` (K1: session mode, dev 2 units / 368 darts,
  eval 2 units / 137 darts) and `NANO_SHA256` (K3). K3 result: GPU check OK on NVIDIA GeForce RTX 4070 SUPER 12 GB,
  driver 610.57, torch 2.14.1+cu130, cuda available True.

PR B (branch `feat/camera-feasibility-0b-1-detection` from `main` after PR A, tasks T3, T5b, T7, T8, T8b, T6c,
T6d, T9, T9b, T9c, T9d, T10, T11, T12, T13, T13b, T14; `Closes #42`). Batch 1 (T5b to T6d) is pushed on this branch before the
PR, without opening it (D19); no extra PR. The batch 1 hand-back states the full pytest count (as in PR A, T4).
- How to verify: K5.0 to K5.4 (from batch 1: image rebuilt, drift loop before any training), K5.5 to K5.7 (public
  data comparison, then dev loop), then K6.
  The tool steps state what Kosmo sees: an old v1 file opens unchanged, the first change saves it as v2 without
  losing a tip; the session list flags "À recalibrer à partir de la fléchette 59" for the 18h24 eval session; after
  recalibration and a new `drift`, the flag is gone; "Effacer" during a recalibration removes the last point and,
  with no point left, leaves without change.
- Versions table only if a pin changes in part B (none planned: drift uses numpy and Pillow, already pinned).
- Justifications the issue asks for: input size and FP32 versus FP16 for the Pixel 7, then the confidence threshold
  from the dev report; plus the drift threshold (CV19, 1.0 mm, D18), the drift method (CV18), and the version
  retained among the three trainings with the figures of all three (CV22, D21, D23).
- Public datasets: the description lists the sets used (slug, licence, status) with their attribution lines as in
  `docs/camera/datasets.md` (CC BY 4.0, D23), and lists every mapping of the class map (slug, source category,
  target) so main and Kosmo confirm them once (T2).
- Calibration points on public images (Kosmo asked for the justification in the PR): the options of "Approach,
  Calibration points on public images", why (a) was chosen, the `rfdetr` 1.12.1 finding (no class mask, crowd boxes
  become background), the thresholds obtained and images kept or dropped, and the test that proves it (T9c).
- v17 pseudo-labels: `tauTip`, acceptance rates, median and p95 distance to the lower-right corner (T9d figures from
  Kosmo's pasted comparison section); the squad additions of D24 stated.
- Kosmo's pasted numbers recorded in the PR: the last drift report (all under the threshold), the comparison
  section, then the dev report.

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

Part B / PR B, drift and calibration segments (D15), then training and measurement. Batch 1 (D19) is T5b to T6d;
then the branch is pushed and Kosmo runs K5.0 to K5.4 while batch 2 (T9 to T14) is built:

| Id | Title | Task file | Depends on | Model | Why this model |
|---|---|---|---|---|---|
| T3 | LiteRT export feasibility and detector runtime (done, a9576aa) | `42-camera-feasibility-0b-1/T3-export-detector.md` | T1 | sonnet | Library wiring with a specified output contract, early go/no-go on export |
| T5b | Annotation format v2 (calibration segments), v1 reader and migration, tool kept working | `42-camera-feasibility-0b-1/T5b-annotation-format-v2.md` | T5, T6a | opus | Changed stored format with its migration (case 1): v2 schema, strict reader, lossless v1 migration of Kosmo's real files |
| T7 | Synthetic boards and sessions for tests | `42-camera-feasibility-0b-1/T7-synthetic.md` | T4, T5b | sonnet | Test fixture generator from fixed geometry |
| T8 | Homography, calibration metric, groups | `42-camera-feasibility-0b-1/T8-geometry-groups.md` | T7 | sonnet | Formulas specified in CV9 and CV14 |
| T8b | Drift measure, compensation, report, flag file and guard; `config/` in the image | `42-camera-feasibility-0b-1/T8b-drift.md` | T5b, T8 | sonnet | Algorithm, statuses and threshold (1.0 mm, D18) fully specified in CV18 and CV19; flag file derived and regenerable (like the split file) |
| T6c | Tool: recalibration action and drift flags (flow and server) | `42-camera-feasibility-0b-1/T6c-recalibration-flow.md` | T5b, T8b | sonnet | Flow, undo and flag rules specified, Python tests, same patterns as T6a |
| T6d | Tool page: recalibration button, screen and flags; batch 1 run steps in `training.md` | `42-camera-feasibility-0b-1/T6d-recalibration-page.md` | T6c | sonnet | Page changes from a specified flow and French strings, same patterns as T6b |
| - | Push batch 1 (no PR), main tells Kosmo (D19) | - | T6d | - | - |
| T9 | COCO dataset builder, public import with strict class map (targets of CV21), versions none/A/B with a calibration-label input, `datasets.md` rows | `42-camera-feasibility-0b-1/T9-dataset.md` | T3, T4b, T8b | sonnet | Specified conversion, CV4, CV21 and drift guards, tested on synthetic COCO |
| T9b | Public class map and `datasets.md` rows from Kosmo's message (D23) | `42-camera-feasibility-0b-1/T9b-public-maps.md` | T9 | haiku | Values copied from the plan (three map entries, six rows with Kosmo's texts), no design choice left |
| T9c | Calibration-point pseudo-labels of public images with M0, pseudo file, dataset version A | `42-camera-feasibility-0b-1/T9c-pseudo-cal.md` | T3, T8, T8b, T9 | sonnet | Thresholds, band, clockwise and drop rules fully specified in CV23 and Approach; fake detector tests |
| T9d | Constrained tip pseudo-labels of v17 with MA, figures, dataset version B | `42-camera-feasibility-0b-1/T9d-pseudo-tip.md` | T9c | sonnet | Box constraint, drop rules and figures specified by Kosmo (D23) and CV23; same patterns as T9c |
| T10 | Training | `42-camera-feasibility-0b-1/T10-training.md` | T3, T9, T10a | sonnet | Wrapper around a library training call with given parameters and tests |
| T11 | Per-dart decision | `42-camera-feasibility-0b-1/T11-decide.md` | T3, T8 | sonnet | Rules CV5 to CV7 specified, fake detector tests |
| T12 | Arrival and empty-board events on video | `42-camera-feasibility-0b-1/T12-events.md` | T11 | sonnet | Algorithm and rules CV10, CV11 specified |
| T13 | Metrics, tuning, frozen thresholds, eval guard, comparison of up to three versions | `42-camera-feasibility-0b-1/T13-metrics-report.md` | T8b, T12 | sonnet | Statistics, strict reader and selection rule specified in CV4, CV8 to CV14, CV19, CV22 |
| T13b | Preliminary report (with public sets, pseudo-label figures and three-version comparison) | `42-camera-feasibility-0b-1/T13b-report.md` | T13, T9d | sonnet | Report content and verdict rule D12 fully specified |
| T14 | CLI (with `drift`, `dataset --public none\|A\|B`, `pseudo-cal`, `pseudo-tip`, `compare`), training doc commands, report template | `42-camera-feasibility-0b-1/T14-cli-docs.md` | T6d, T9d, T10, T13b | sonnet | Wiring of existing pieces and their documentation |

25 tasks (8 in part A, 17 in part B), two `opus` tasks (T5, T5b). Execution order: part A in table order, PR A, then
part B in table order, one task at a time (D11, D19): batch 1 T5b, T7, T8, T8b, T6c, T6d, push, then T9, T9b, T9c,
T9d, T10, T11, T12, T13, T13b, T14. T9b no longer waits for Kosmo (his class names and attributions are in D23).
Each task ends green on the
CI command `python -m pytest -q -m "not slow"` in `tools/camera`, so a pushed partial branch keeps the CI green. Push
of batch 1 only after main confirms the code-phase approval of its head; batch 2 developers fetch and realign on the
remote branch first and never rewrite pushed commits. PR B opens after T14. K1 to K3 ran on PR A, K4 during part B, K5 from batch 1, K5b and K6 from the complete branch.

## Decisions

| Id | Decision | Rules |
|---|---|---|
| Py | Python 3.13 everywhere (sandbox, image, CI); deviation from "latest stable" because `rfdetr` and `litert-torch` do not install together on 3.14. `download-r2.pytorch.org` allowlisted. | - |
| B1 | (Issue) RF-DETR Nano first; keypoint model only on Kosmo's decision after a report. | - |
| B2 | (Issue) LiteRT only, measures on the `.tflite` with `ai-edge-litert`; ONNX Runtime only as fallback on Kosmo's decision. | CV4 |
| B4 | (Issue) Kosmo clicks tips and the 4 calibration points once per session; amended by D15: once per calibration segment, a new segment where the drift measure asks for it. | CV2, CV12, CV20 |
| B5 | Superseded by K0. (Issue) Existing captures are dev; new dedicated sessions (>= 500 darts) are eval. | - |
| K0 | Kosmo, 2026-11-15: no capture campaign before a first result; existing captures split about 70/30 by session (block mode, "more optimistic", if too coarse); preliminary report with limits; path proposal or targeted campaign into `captures-eval/`; annotation tool first. Supersedes B5, D1, P2. | CV3, CV11, CV17 |
| B6 | (Issue) >= 95 % correct without flag and <= 1 % unflagged errors. | CV8 |
| 1 | Each dart was entered right after its throw: arrival windows apply to every capture. | CV10 |
| 2 | Removal criterion "empty board recognised before Next volley"; protocol: press within 3 s after leaving the field. Applies to the `captures-eval/` campaign only (K0). | CV11 |
| 3 | Nothing typed: tripod group and lighting derived from calibration and luminance, zoom from `truth.json`. | CV14 |
| 4 | An off-board miss is correct if nothing is announced on the board for that dart. | CV7 |
| 5 | Annotation tool: stdlib only, run in WSL outside the container, `127.0.0.1` only, no external resource; writes in `~/kosmo-data/annotations/`, captures read-only. | CV16 |
| 6 | No model file in the repository in 0b-1: SHA-256, size and rebuild command only. | - |
| 7 | No pseudo-annotation of Kosmo's photos: manual clicks. Amended by D23: public images get pseudo-labels (CV23). | CV23 |
| 8 | Superseded by D20 (was: public set candidates listed as search links; Kosmo checks licences before download). | - |
| 9 | RF-DETR Nano COCO weights downloaded at image build, pinned by SHA-256. | - |
| 10 | GPU station: RTX 4070 12 GB, WSL2 Ubuntu 26.04, rootless Docker 29.9, GPU by CDI only, container flags as in "Approach". | - |
| 12 | CI job proposed under "Proposed owner changes". | - |
| D1 | Superseded by K0 (was: dev/eval cutoff date). | - |
| D2 | 4 calibration points on the outer double corners 5|20, 13|6, 17|3, 8|11; calibration limit 2.0 mm. | CV2, CV9 |
| D3 | Detection and calibration metrics on photos, events on video (different crop and scale). | CV9 to CV12 |
| D4 | No OpenCV; numpy DLT and PyAV (already pulled by `rfdetr`). Only `pytest` added as a direct dependency. | - |
| D5 | Annotation tool UI in French only (it is for Kosmo); started with `python3`. | CV16 |
| D6 | CUDA build `cu130` for the GPU image. | - |
| D7 | Design review S5: the container keeps uid 0 inside, no `USER`. With rootless Docker, container root maps to Kosmo's own unprivileged uid; a non-root `USER` maps to a subordinate uid that cannot write `~/kosmo-runs` without changing its permissions. Root inside has no extra power (`--cap-drop ALL`, `no-new-privileges`, `--read-only`, `--network none`). Accepted by the security reviewer at the design review of 1377af2. | - |
| D8 | Pixel coordinates in stored JPEG pixel order, EXIF orientation ignored everywhere; the server checks clicks against the real JPEG size (U3). | CV16 |
| D9 | Constants in `defaults.json` only, read by `config.py` (A1). | - |
| D10 | Requirements pinned with hashes, installed with `--require-hashes` (S6). | - |
| D11 | Kosmo, 2026-11-15 (P3): two PRs. PR A: T1, T4, T4b, T5, T6a, T6b, T10a, T2 (T10a taken out of T10: image and `gpu_check`, depends on T1 only), so Kosmo builds the image and checks the GPU while he annotates; `docs/camera/datasets.md` ships in PR A so licences are checked during part B. PR B: the rest, from a new branch, T10 reduced to training. | - |
| D13 | Kosmo, 2026-11-15: sessions with no judged dart (CV5) are ignored by the split: in no set, not counted for "at least 2 sessions per set". Tested in T4b; the annotation tool lists such a session as "Ignorée" (design review U9, T6a). | CV3, CV16 |
| D14 | Design review A5: two-step image. PR A delivers the image with `gpu_check` only (no entry point); PR B adds training and the `python -m kosmo_camera` entry point, and Kosmo rebuilds it (K5). Recorded in the ADR (T1). | - |
| D12 | Verdict per threshold: "clearly below" when the whole 95 % interval misses the target; else "close or above" (preliminary report, K0). | CV8 to CV11 |
| D15 | Kosmo, 2026-11-15 (tripod drift found after K2): in PR B, before training: drift measure per photo against the calibration photo of its segment, in px and mm through the homography, report per session (max drift, darts beyond a threshold tied to `CAL_MAX_ERROR_MM`); sub-threshold drift compensated in the `cal1`-`cal4` labels and the mm measures; annotation format v2 with calibration segments (v1 reader kept, lossless migration); tool action "Recalibrer à partir de cette fléchette" and session list flags from the drift measure; order measure, recalibrate, measure again (all under the threshold), then training; no session excluded since CV7 does not use the clicked calibration. Squad's design: phase correlation with numpy FFT (JPEG decoding by Pillow, already pinned), flag file `drift.json` local like the split file, ordering enforced by a guard in `dataset` and `measure` with an explicit `--accept-drift` override recorded in the run. Threshold value: D18. | CV9, CV12, CV14 to CV16, CV18 to CV20 |
| D16 | T3 result (a9576aa): export works with `litert-torch` 0.10.0; logits `[1,300,6]` with a trailing no-object slot; category ids 0-based in `CLASS_NAMES` order; squash resize to 384x384 (no letterbox). T9, T10 and T11 rely on this contract. | CV4 |
| D17 | Design review of 5c4d06f: PR B records format v2 and the drift decisions in two sections of ADR 0016 (T5b, T8b; A13); caps `MAX_SEGMENTS` = 64 and `drift_file.MAX_BYTES` = 1 MiB, surfaced in the tool as "Mesure de dérive illisible : <cause>" (S9, A14); one `segment_for` (A15); `DRIFT_MAX_MM` read from config in every test, pinned once in `test_config.py` (A16, T1); Pillow decompression bomb, truncated or mismatched photos become a status, `Image.MAX_IMAGE_PIXELS` kept (S8); stale flags stay shown with "(mesure périmée)" (U7); picker marker, old points as hollow labelled crosses, recalibration button on its own row (U8 to U10); per-candidate threshold table in the drift report (U11). | CV16, CV18 to CV20 |
| D18 | Kosmo, 2026-11-15 (Q1): « Seuil de dérive : B, 1,0 mm. » `DRIFT_MAX_MM` = 1.0 in `defaults.json`, still one named constant pinned only in `test_config.py`; the "Seuils candidats" table stays in the drift report for information. | CV19 |
| D19 | Kosmo, 2026-11-15: « Plan de la PR B validé, avec un ajustement d'ordre : réalise d'abord T5b, T8, T8b, T6c et T6d, pousse la branche et préviens-moi dès qu'ils sont faits. Je lance alors la mesure de dérive et je recalibre depuis la branche, pendant que tu continues T9 à T14. Pas de PR supplémentaire. » Batch 1 = T5b, T7 (needed by T8), T8, T8b, T6c, T6d; then push of the PR B branch without a PR and main tells Kosmo; batch 2 = T9 to T14 (and T9b). Batch 1 runs `drift` as `python -m kosmo_camera.drift` in the container (no entry point before T14) and T8b adds `config/` to the image; T6d documents the batch 1 steps in `training.md`. | CV18 to CV20 |
| D20 | Kosmo, 2026-11-15: « Jeux publics : acceptés les jeux affichant la licence CC BY 4.0 sur Roboflow Universe : dartsdetectfulldart (SchoolPXL) : https://universe.roboflow.com/schoolpxl/dartsdetectfulldart ; dart tip detection (PJATK) : https://universe.roboflow.com/pjatk-ni0m7/dart-tip-detection ; mat24_dartsv2 (school) : https://universe.roboflow.com/school-asc0d/mat24_dartsv2 ; dart_detection3 (DartsKEY01) : https://universe.roboflow.com/dartskey01/dart_detection3 ; dart-tip-detection-6d3mw (DartsDetector) : https://universe.roboflow.com/dartsdetector/dart-tip-detection-6d3mw, seulement si sa page affiche CC BY 4.0 au téléchargement. Exclu : bullseyebot (Hugging Face), aucune licence. Toute décision antérieure contraire est remplacée par celle-ci. docs/camera/datasets.md : une ligne par jeu avec lien direct, licence, attribution exacte et statut. Je télécharge ces jeux au format COCO JSON dans ~/kosmo-data/public/<slug>/ et je te transmets la liste des catégories de chaque jeu pour la table de correspondance (T9). » Supersedes decision 8 and the search rows of `datasets.md` (P1). Replaced by D23 where they differ (set list and statuses, slug = folder name, class map, use of the sets); still in force: CC BY 4.0 only, bullseyebot excluded, one `datasets.md` row per set, committed map. | CV15, CV21 |
| D21 | Kosmo, 2026-11-15: « L'apport des jeux publics est mesuré, pas supposé : entraînement avec et sans jeux publics, comparaison sur dev uniquement (jamais eval). La meilleure version est retenue, et le choix et ses chiffres figurent dans le rapport. » Squad's design: same seed and Kosmo part, public images in train only, comparison on the dev photos of the shared valid subset (the dev photos not used for training by either version), selection rule CV22, `compare` subcommand (T13, T13b, T14); eval run once on the retained version (K6). | CV4, CV22 |
| D22 | Design review of cf648af: downloaded public files are untrusted input, validated as in CV21 (bare `file_name` inside its subset folder, no symlink, byte caps `MAX_JSON_BYTES` 64 MiB and `MAX_IMAGE_BYTES` 32 MiB, `Image.MAX_IMAGE_PIXELS` kept, finite in-image boxes, unique and valid ids; bad image dropped and counted, bad JSON refuses the set; S10); accepted rows carry the licence page date, attribution copied from the page, never invented (S11); ADR section "Public data and version selection" (A17); CV22 overlap fails closed with a visible French message until Q2 (T1); PR lists every `tip`/`calN` mapping (T2); step-order test (T3); batch 1 hand-back with the full pytest count (T4). | CV21, CV22 |
| D23 | Kosmo, 2026-11-15 (complement for PR B, replaces point 3 of his previous message and D20 where they differ; verbatim, line breaks flattened): « Complément de Kosmo pour la PR B : jeux publics (remplace le point 3 de mon message précédent ; concerne T9 et T10). Kosmo a téléchargé les jeux au format COCO JSON dans ~/kosmo-data/public/<dossier>/ (lecture seule). Licences CC BY 4.0 vérifiées sur les pages sources. Taille des boîtes analysée (médiane rapportée à la largeur d'image) : seule une boîte centrée sur la pointe est exploitable telle quelle. 1. Retenus (datasets.md : accepted) - dart-detection3-v1i-coco : DartsKEY01, https://universe.roboflow.com/dartskey01/dart_detection3, CC BY 4.0. Récupéré via un fork Roboflow de Kosmo (aucune version publiée), version brute sans prétraitement ni augmentation ; attribution à DartsKEY01. Classe dart_tip -> tip. 1847 annotations, boîte médiane 1,0 % de la largeur (24 px, images d'environ 2700 px). - mat24-dartsv2-v2i-coco : school, https://universe.roboflow.com/school-asc0d/mat24_dartsv2, CC BY 4.0. Classe Dart-Tip -> tip (127 annotations, boîte médiane 2,1 %). Classe Dart (fléchette entière) ignorée. 2. Conditionnel (datasets.md : conditional, motif et méthode indiqués) - dart-tip-detection-v17i-coco : DartsDetector, https://universe.roboflow.com/dartsdetector/dart-tip-detection-6d3mw, CC BY 4.0, 1108 images, 2100 boîtes dart_tip à 14,9 % de la largeur. Constat de Kosmo : chaque boîte entoure la fléchette entière, toujours du même côté et sous un angle proche ; la pointe est presque toujours dans le coin inférieur droit. Le coin brut n'est PAS accepté comme pointe (erreur de plusieurs px, environ 1 mm par px à cette résolution). Utilisable uniquement par pseudo-étiquetage contraint (point 4). 3. Rejetés (datasets.md : rejected, motif) - dart-tip-detection-v1i-coco (PJATK, CC BY 4.0) : boîtes à 11,2 %, ne localisent pas la pointe. - dartsdetectfulldart-v1i-coco (SchoolPXL, CC BY 4.0) : dart = fléchette entière (24 %), db/sb hors de notre convention, 41 images. - bullseyebot (Hugging Face) : aucune licence. 4. Règles pour T9 et T10 - Jeux retenus : la pointe est le centre de la boîte d'origine ; la boîte est reconstruite avec BOX_FRACTION, comme pour les photos de Kosmo. - Points de calibration : les images publiques n'ont aucune étiquette cal1 à cal4. Elles ne doivent pas apprendre au modèle que ces points sont du fond. Traite-le explicitement et justifie le choix dans la PR (par exemple pseudo-étiquetage des points de calibration par le modèle entraîné sans jeux publics, avec seuil de confiance, ou toute autre solution argumentée). - Entraînements à comparer : - Sans jeux publics : photos dev de Kosmo seules. - A : photos dev de Kosmo + dart-detection3 + mat24 (Dart-Tip). - B : A + pointes v17 obtenues par pseudo-étiquetage contraint avec le modèle A : pour chaque boîte d'origine, la pointe prédite doit être dans la boîte, dans son quart inférieur droit, avec une confiance au-dessus d'un seuil justifié (choisi sur dev). Sinon l'annotation est écartée ; une image dont une boîte est écartée est écartée entière (aucune pointe non étiquetée laissée en fond). Rapport chiffré : taux d'acceptation, distance médiane et p95 (en px) entre la pointe retenue et le coin inférieur droit de la boîte. - Comparaison sur dev uniquement, jamais eval. La meilleure version est retenue, avec le choix et les chiffres des trois entraînements dans le rapport. » Squad's design: CV21 targets, CV22 for three versions, CV23 (calibration option (a) chosen and justified in "Approach"), tasks T9b (reshaped), T9c, T9d. | CV15, CV21 to CV23 |
| D24 | Squad's design for D23 (plan change of 2026-11-15, for design review; stricter than the literal text, never looser, so main may ask Kosmo to confirm): mat24 `Dart` is ignored as a label but an image whose `Dart` box contains no `tip` centre is dropped (`whole-dart-checked`); in v17 an image is also dropped when one prediction serves two boxes or a confident tip is assigned to no box; a category with no annotation (Roboflow supercategory) is ignored without a map entry, a mapped name absent from the set refuses it; pseudo-label thresholds use `PSEUDO_MAX_MM` 2.0 mm, 99 % precision on at least 30 dev predictions; calibration labels on every public image of A and B come from M0, including the v17 images of B. | CV21, CV23 |

## Open questions

- P1: resolved by D23 (Kosmo checked the pages and gave the attribution texts and statuses).
- P3: decided, two PRs (D11).
- Q1: decided, 1.0 mm (D18).
- Q2 (Kosmo, not blocking; restated for three versions after D23: until answered the overlap branch of CV22 fails
  closed, see CV22; the answer replaces that branch and its test in one commit): with a small comparison set, the
  versions (sans, A, B) may differ by a few darts while their 95 % intervals overlap ("écart dans le bruit").
  Options: 1) keep the leader of the CV22 order anyway, the overlap only stated in the report (follows "la
  meilleure version est retenue" literally; recommended); 2) keep the simplest version (sans, then A, then B) whose
  interval overlaps the leader's: public data, then the v17 pseudo-labels, must each prove a clear gain (with few
  dev darts this will likely mean "sans" almost always; this is the fail-closed behaviour until the answer).
  Either way the figures of the three trainings appear in the report.
- Q3 (Kosmo, not blocking: until answered the stricter D24 rules apply, they can only drop public images, never add
  a label; the answer, if "literal", removes the named rules and their tests in one commit): the squad added three
  safety rules to D23 so that no unlabelled tip stays as background. Options: 1) confirm D24 as written (mat24: an
  image whose `Dart` box contains no `Dart-Tip` is dropped; v17: an image is dropped when one predicted tip serves
  two boxes or a confident predicted tip lies in no box; recommended, it follows "aucune pointe non étiquetée
  laissée en fond"); 2) literal text only (mat24 `Dart` simply ignored, v17 only the per-box rule), more public
  images kept at the risk of unlabelled tips. Either way the report counts the images dropped by each rule.
- P4 (information for Kosmo, not blocking): "stdlib + numpy" is read as "no new library": the drift computation is
  numpy only, but JPEG decoding needs Pillow, already pinned and in the image. So `drift` runs in the container, not
  with the bare `python3` of WSL; the annotation tool stays stdlib and only reads `drift.json`.
