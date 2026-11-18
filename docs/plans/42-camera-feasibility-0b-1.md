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

Kosmo's method correction of 2026-11-15 (D25, priority over Q2): no version comparison and no threshold tuning on
images seen in training. The two dev sessions of the locked split serve as cross-validation by session (CV24): per
version, one training on dev session 1 predicting dev session 2 and the reverse, so every dev dart gets an
out-of-training prediction; those predictions compare the versions (paired exact one-sided McNemar test, D26),
tune `tau_low`/`tau_sure` and the pseudo-label thresholds; the final model is the retained version trained on the
whole dev set with the same hyperparameters. Q2 and Q3 are closed (D26, D27), the `datasets.md` attribution format
confirmed (D28).

Kosmo's stop of K5 on 2026-11-15 (D30, absolute priority of PR B): the drift measure is not reliable. Median drift
per session S01 13 px / 1.8 mm, S02 311 px / 46.9 mm, S03 268 px / 41.9 mm, S04 122 px / 18.5 mm; 419 of 425
photos beyond the threshold (49 `border`, 31 `low_peak`), flags from dart 2 in every session, and the net move Kosmo
saw in the 18:24 eval session (darts 57 to 59) is not visible. His "few pixels" were screen pixels: the true drift is
unknown. Two hypotheses: H1, the measure is wrong (phase correlation on a periodic 18-degree wire pattern can lock
onto a neighbouring wire; darts accumulate during the volley); H2, tapping the value on the tripod-mounted phone
triggers the photo and tilts the phone. PR B therefore first gets ground truth per photo (control clicks, format v3,
CV25, CV26), a numeric diagnosis with a decision rule fixed in advance (CV28, CV29), a local visual check page, a
tool rebuilt around Kosmo's journey, and ADR 0017 (the game app calibrates on every image and recalibrates by
itself). The correction (robust measure, per-photo calibration, or both) is planned at plan update P2 from the
diagnosis and validated on the control clicks before any compensation (CV30). T9 to T14 wait, except T11 and T12.

## Scope and out of scope

In scope:
- `tools/camera/`: Python package `kosmo_camera` (truth reader, dev/eval split, annotation format v1 then v2, board
  geometry, homography, synthetic boards, tripod drift measure, COCO dataset builder, pseudo-labels of public images
  (CV23), training, export, LiteRT detector, per-dart decision, arrival
  and empty-board events, metrics and report, CLI), the stdlib-only annotation tool, the GPU training image
  (`Dockerfile`, pinned requirements), CPU tests on generated boards.
- Docs: `docs/rules/camera.md` (rules CV1 to CV30 below), `docs/camera/annotation-format.md` (version 2 added in
  PR B, version 3 with control clicks after D30),
  `docs/camera/datasets.md`, `docs/camera/training.md`, `docs/camera/feasibility-report.md` (template, then filled),
  `docs/adr/0016-camera-feasibility-tooling.md`, `docs/adr/0017-per-image-calibration.md` (product requirement for
  phases 1 to 3, D30 point 6).
- After D30: control clicks (CV25, CV26), video frames before the entry (CV27, PyAV already pinned), diagnosis
  (CV28, CV29), visual check page, journey-based tool screens; conditional corrections T16a to T16c (finalized at
  P2).
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
  Everything with numpy, PIL, av, torch or LiteRT lives in other modules. Amended by D43 and D44 (Q14, option 1):
  the tool imports
  `snap.py` (numpy) and the shared decoder `luma.py` (numpy, Pillow; D45) lazily from the small WSL tool venv of "Approach, Where the snapping runs"; the core
  modules stay stdlib and the tool still starts without that venv (snapping then unavailable).
- **Detector** (issue): RF-DETR Nano from COCO weights, "keypoints as objects": 5 classes, `tip` and `cal1` to `cal4`,
  each a fixed square box centred on the point (side `BOX_FRACTION` = 2.5 % of the longer image side, a training
  constant); the point is the box centre. Training on Kosmo's dev photos (`dart-NNN.jpg`) in three versions: without
  public data, A and B (D23), compared on out-of-training dev predictions of a cross-validation by session (CV24,
  D25) and retained by the paired test of CV22 (D21, D26). Model contract fixed by T3 (D16): input 384x384 reached by a squash bilinear resize (no
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
- **Status after D30**: the two bullets below ("Tripod drift", "Drift threshold") describe the batch 1 measure,
  delivered and kept as code, but not validated: its results are information only, no compensation and no flag
  guard apply (CV18, CV19 status), and the threshold choice D18 is suspended until P2 (Q13). Segments (CV20) stay.
- **Calibration control** (D30 point 1, CV25, CV26): Kosmo clicks the 4 calibration points of chosen photos in a
  "Contrôle de calibration" mode, independent of the segments, stored as `controls` in annotation format v3 (strict
  reader, lossless v2 migration). Sample, fixed by rule and computed, never chosen by hand: every judged eval photo
  (reference for CV9 per photo), dev judged-dart ranks 1, 11, 21, ... plus the last, every segment reference photo
  (agreement of the segment clicks, information), and the video frame about 1 s before the entry for the dev sample
  plus darts 55 to 60 of every eval session (this includes the 18:24 session without naming it; Kosmo confirmed
  both readings, D36). Alternative rejected: a random sample
  (not reproducible across tool restarts without storing a seed, and no guarantee on the reference photos).
- **Assisted click** (Kosmo, Q10, D39; CV31): Kosmo's clicks designate the right wire crossing, the machine locates
  it. Every calibration-point click (control, first calibration, recalibration) is snapped by `snap.py` (numpy)
  to the exact crossing of the radial wire and the outer edge of the double ring, sub-pixel, within
  `SNAP_RADIUS_PX` (10) of the start point. Algorithm (T15m): luma window of half side `SNAP_WINDOW_PX` (16)
  around the current estimate; scale-normalised Hessian ridge response and gradient magnitude at the scales
  `SNAP_SIGMAS_PX` (1, 2, 3), best scale per window (wires 1 to 5 px wide); line A = the dominant straight ridge
  (radial wire: orientation histogram then weighted total least squares on its ridge pixels); line B = ridge or
  step edge (ring wire or colour edge) whose orientation differs from A by at least `SNAP_MIN_ANGLE_DEG` (30), fitted
  the same way; estimate = A ∩ B; re-centre and repeat until it moves less than `SNAP_CONVERGE_PX` (0.05) or
  `SNAP_MAX_ITER` (5). "No clear crossing" (status `unsnapped`, the point stays at the click) when any of: a line
  with fewer than `SNAP_MIN_SUPPORT_PX` (8) pixels, a ridge or edge response below `SNAP_MIN_CONTRAST` (4) times
  the robust noise (MAD) of the window, an RMS residual above `SNAP_MAX_RESIDUAL_PX` (0.75), angle below the
  minimum, no convergence, a result farther than `SNAP_RADIUS_PX` from the start or outside the image, a shape that is
  not two sharp wires or edges crossing at the found point (each standing out from its sides by the contrast floor on
  a flank of the point, blur at most 2 px), or an ambiguity: two or more distinct crossings of the board lattice
  (crossings of the lines seen in the region, closer than 3 px counting as one) within `SNAP_RADIUS_PX` of the click
  (reason `ambiguous`; with one crossing in range only that one is accepted). Domain (Kosmo, D46, option a): photos
  of 12 Mpx and video of 1080p, so wires of at least 2 px (1600 x 1200 and larger), blur at most 0.5 px, noise at most
  3 grey levels; on it every snapped point is within 0.25 px of the true crossing and at least 90 % of the starts
  snap, the rest staying "non recalé" and counted; noise 5 is tested for criterion 2 only (never a wrong crossing);
  cases outside the domain (1200 x 900, blur above 0.5 px) are tested and reported with no bound; no time target (one
  snap takes 80 to 140 ms and the default suite asserts no wall-clock). Constants
  are initial values in `defaults.json`, tuned by T15m on synthetic boards only (never on Kosmo's photos) and
  pinned once in `test_config.py`. Flow on the control screen: the 4 final points of the previous item of the same
  session and source (else, for the first photo item, the segment points of that dart; the first video item of a
  session has no proposal) are snapped on the new image and proposed; each point is shown in a zoomed inset with
  its status; Entrée saves when every point is snapped, clicked or hidden; a click goes to the point named by the
  click rule of CV31 (pure function `flow.click_target`, T15n) and is snapped at once. Repeatability is measured by the machine, never by Kosmo's
  hand (CV28 `D`): the diagnosis restarts the snap from `SNAP_REPEAT_DIRECTIONS` (8) starts offset by
  `SNAP_REPEAT_OFFSET_PX` (3) around each snapped point; dispersion = largest board-plane distance (mm, through the
  homography of that control) between the stored point and a restart result, a failed restart counting as
  infinite; p95 over the snapped points, target at most `SNAP_REPEAT_MAX_MM` (0.25 mm). `D` measures
  repeatability, not accuracy: restarts that all converge on the same wrong crossing give `D` = 0, so accuracy is
  proven only by the T15m tests against the `board.py` truth, and the diagnosis report says so in its limits
  (A35, T2, D45).
- **One image decoder** (A33, S14, D45): `kosmo_camera/luma.py` (numpy and Pillow) holds the only JPEG-to-luma
  decoder, moved from `drift.py` `_decode_luma` (T15m): file-size cap `luma.MAX_IMAGE_BYTES` (32 MiB, same value as
  `public_import.MAX_IMAGE_BYTES`; checked before reading, never read past the cap), `Image.MAX_IMAGE_PIXELS` kept
  and the decompression-bomb warning raised as an error, no `LOAD_TRUNCATED_IMAGES`; truncated, random or oversized
  bytes, a bomb, or a size different from the expected one give `(None, reason)`, never an exception. `drift.py`,
  the tool's snapping (T15n) and the diagnosis (T15i) all call it.
- **Where the snapping runs** (amends plan decision 5, D5, CV16 "stdlib only" and ADR 0016 decision 2; D43,
  confirmed by Kosmo in Q14, D44): the
  snap needs the image pixels and the standard library cannot decode JPEG. Chosen (simplest that keeps the tool in
  WSL): the tool server, still started in WSL on 127.0.0.1 with Kosmo's `python3` (3.14), runs the snap in-process
  from a venv holding only numpy and Pillow, the versions already pinned (`tools/camera/requirements-tool.txt`, exact
  pins with `--hash` for the cp313 and cp314 manylinux x86_64 wheels, installed with `--require-hashes
  --only-binary=:all:`; the wheels `numpy-2.5.4-cp314-cp314-manylinux_2_27_x86_64.manylinux_2_28_x86_64.whl` and
  `pillow-13.0.0-cp314-cp314-manylinux_2_27_x86_64.manylinux_2_28_x86_64.whl` and their cp313 twins are listed on
  PyPI, checked on 2026-11-17, and the sandbox's system Python 3.14.4 created a venv and installed both
  binary-only), created once in K7.1b with Kosmo's WSL `python3` (3.14, D44). The stdlib core is unchanged: `snap.py` and `luma.py` are imported lazily, so
  `python3 -I -S -m kosmo_camera.annotate --help` still works without the venv; then calibration, recalibration and
  control are "Recalage indisponible" (tips annotation unchanged). The diagnosis (container) imports the same
  `snap.py` and `luma.py`. Alternatives: (1) the tool in the container (numpy and Pillow already there): no venv, but the tool
  needs a published port `-p 127.0.0.1:8765:8765`, a network other than `none` (outbound access for that
  container), a server bound to 0.0.0.0 inside the container, the annotations mounted read-write, a long `docker run`
  per start and rootless-port forwarding to the Windows browser that the sandbox cannot test; (2) a container
  precompute writing a local regenerable file of candidate crossings per photo, read by the stdlib tool that snaps a
  click to the nearest candidate: keeps decision 5 literally, but adds a new file format with its strict reader
  and caps, a search window per photo guessed from the segment points (unknown for video frames, whose crop and
  scale differ, D3), staleness after a recalibration, no snapping outside the precomputed windows, and the most code;
  (3) a pure-stdlib JPEG decoder: thousands of lines and seconds per 12 MP photo, rejected; (4) snapping in the
  page's JavaScript from the decoded image: untestable in the sandbox (JavaScript does not run) and a second
  implementation next to the diagnosis's numpy one, rejected.
- **Video frame before the entry** (CV27): the container extracts, for each sampled dart, the last frame at or
  before `entry.videoPositionMs` − `VIDEO_BEFORE_ENTRY_MS` (1000) from `volley-NNN.mp4` with PyAV (pinned, no new
  library) to JPEG plus a `frames.json` manifest; the stdlib tool only serves listed files. Alternative rejected:
  decoding in the tool (PyAV and the ffmpeg libraries bundled in its wheels would enlarge the WSL tool venv beyond
  the two packages, numpy and Pillow, that D44 allows; A34).
- **Diagnosis** (D30 point 2, CV28): per photo control, true drift `T` (clicks versus segment calibration), error
  `E` of the automatic measure (its shifted points versus the clicks), `E_seeded` (patches seeded with the clicked
  points: separates coarse-search failure from patch failure), peak ratios, agreement `R` of the segment clicks,
  snapping dispersion `D` and points not snapped; on the video side the photo to frame offset `O` through a per-session similarity `M` fitted on paired clicks, the durable move `V` and
  the motion during capture `W`. Numbers and roles only in the report (Kosmo pastes it); `diagnostic.json` local.
- **Diagnosis decision rule** (CV29, written before any number is seen): see CV29. "Suite proposée": H1 only:
  T16a then compensation per CV30; H2 or H3 or "photos déplacées" with a correct measure: T16b with the current
  method validated per CV30, else T16c; H1 and moved photos: T16a used as the per-photo calibration (T16b); non
  concluant for the snapping dispersion: the squad revises the snapping at P2 and the diagnosis is rerun on the
  same stored clicks (Kosmo does not click again); non concluant for too few controls: Kosmo completes the control
  clicks, then reruns the diagnosis. H2 also produces the
  Capture proposal of "PR material" (an issue draft, no Capture code in this PR).
- **Visual check** (D30 point 3): the annotation tool, on 127.0.0.1 with the stdlib, alternates reference photo and
  measured photo with the alignment of choice (automatic, from the control clicks, none; named "alignement" in
  French so that "recalé / non recalé" names the snap only, U30) applied by CSS `matrix3d` from a homography
  computed in Python; images never leave the PC.
- **Journey** (D30 point 5): per session the steps calibration, tips, control photo, control video with their
  state; exactly one active instruction computed by the server (`journey.py`), never a stale one; information (drift
  of the unvalidated measure) in a separate grey box; the recalibration screen explains what a recalibration is.
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
  photos (same 1600 px resize). Public images join the train subset only, in every fold and in the full training:
  the inner valid subset and the held-out session of a fold (CV24) hold Kosmo's photos only, identical for the
  three versions. Untrusted input as in CV21 (S10): the dart-detection3 images are about
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
  and the measured T3 chain, and every decision is a counted, reported number. M0 is the `sans` model trained on
  the whole dev set (fold `full`, D25: public images are never measured). Its thresholds are tuned without
  circularity on the out-of-training predictions of the `sans` cross-validation (CV24: fold models that never saw
  the photo they predict), then applied to M0's predictions on the public images (limit stated in the report: M0
  saw twice as many dev photos as the fold models, so its scores may sit slightly higher; the band can only drop
  more images, never add a label below `tauCalHigh`): `tauCalHigh` = the lowest score at which the best
  out-of-training prediction per calibration class is within `PSEUDO_MAX_MM` (2.0 mm, as `CAL_MAX_ERROR_MM`) of the reference point
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
- **Constrained tip pseudo-labels of v17** (D23, CV23, D25): with model A (MA, the `A` model trained on the whole
  dev set, fold `full`), on each `dart-tip-detection-v17i-coco`
  image, every source box must receive one predicted tip inside the box, in its lower-right quarter
  (`x + w/2 <= px <= x + w`, `y + h/2 <= py <= y + h`, source pixels), score at or above `tauTip`; `tauTip` = the
  lowest score at which the out-of-training tip predictions of the `A` cross-validation (CV24) are within
  `PSEUDO_MAX_MM` of a labelled tip for at least 99 % of at least 30 predictions (one-to-one, by score; duplicates within half a box side
  merged). Otherwise the annotation is rejected and its whole image dropped; the image is also dropped when one
  prediction serves two boxes, or when a tip prediction at or above `tauTip` is assigned to no box (an unlabelled
  dart would stay as background, D24), or when M0 gave the image no calibration labels (CV23). The retained tip becomes a `BOX_FRACTION` box. Figures reported: boxes
  accepted and images kept (rates), drops per reason, median and p95 distance in px between the retained tip and the
  box's lower-right corner `(x + w, y + h)`. No `tauTip`, no v17 folder or no image kept: B not built, stated.
- **Cross-validation by session** (D25, CV24): the locked split (session mode, 2 dev units, `split-lock.json`)
  has two judged dev sessions. Roles: `dev 1` and `dev 2` are the dev sessions in ascending order of their CV3 unit
  key (a SHA-256, so the lock fixes the order, never a name); printed output, reports and every file of the run
  folder name sessions by role only (CV15). Folds: `cv1` trains on dev 1 and predicts dev 2, `cv2` trains on dev 2
  and predicts dev 1, `full` trains on both and predicts no dev photo. `rfdetr` needs a valid subset in every
  dataset: it is an inner sample of the training session(s), blocks of `SPLIT_BLOCK_VOLLEYS` consecutive volleys,
  `VALID_RATIO` (15 %) of the blocks drawn with the fixed seed, used by `rfdetr` only for its checkpoint choice;
  the held-out session never enters the dataset of its fold (no image, label or inner valid photo). Each `cv` model,
  exported and run through the T3 chain, predicts every judged photo of its held-out session: the two folds give
  one out-of-training prediction per judged dev photo. These predictions are the only dev input of the version
  comparison (CV22), of the `tau_low`/`tau_sure` tuning (`tune.py`) and of the pseudo-label thresholds (CV23);
  the dev report takes its photo metrics from them and its video events from the fold model of the held-out
  session (never from a `full` model). Hyperparameters (`TRAIN_EPOCHS`, `TRAIN_BATCH`, `TRAIN_SEED` in
  `defaults.json`, resolution 384 from `model_spec`) are the same for every training of the chain, recorded in each
  `train-run.json` and checked equal by `compare`. Final model: the `full` model of the retained version;
  `frozen.json` holds the pair tuned on that version's out-of-training predictions and the SHA-256 of that `full`
  model's `.tflite`. Limit stated in the report: thresholds tuned on fold models are applied to a model trained on
  twice the dev photos; eval (K6) measures exactly that final model with those thresholds. Fail closed: the first
  step refuses before any training, exit non-zero, with a French message asking Kosmo's decision through main, when
  the lock is in block mode ("Validation croisée par session impossible en mode blocs"), when the dev set does not
  have exactly 2 judged sessions ("Découpage verrouillé inattendu : N sessions dev jugées, 2 attendues"), or when a
  dev session has fewer than `CV_MIN_SESSION_DARTS` (50) judged darts ("Session dev k : N fléchettes jugées,
  minimum 50"). No automatic fallback: a random split of darts would put photos of the same session, tripod and
  lighting on both sides. Why 50: a fold trained on fewer photos stands poorly for the final model; with 368 dev
  darts this guards against a lopsided split, it is not an expected case. Alternative rejected: the former inner
  15 % valid subset as comparison set (about 55 darts, taken from the training sessions themselves).
- **Three trainings, run chain** (D21, D23, D25, CV22 to CV24, A19): one run folder `--run /runs/chaine`, steps
  numbered as in K5.6 and `training.md`: (1) `dataset --public none` creates the folder and `chain.json` (split
  SHA-256, fold plan with roles and judged darts per role, hyperparameters, `defaults.json` and map SHA-256;
  refuses an existing chain folder) and builds `sans/cv1`, `sans/cv2`, `sans/full`; (2) `train --version sans`
  (trains and exports the three folds); (3) `predict --version sans` (out-of-training predictions); (4) `pseudo-cal`
  (thresholds from the `sans` predictions, labels from `sans/full` = M0); (5) `dataset --public A`; (6) `train
  --version A` (three folds; `A/full` = MA); (7) `predict --version A`; (8) `pseudo-tip` (`tauTip` from the `A`
  predictions, tips from MA); (9) `dataset --public B`; (10) `train --version B` (`cv1`, `cv2` only); (11) `predict
  --version B`; (12) `compare` (CV22) writes the comparison and `retained.json` and prints the retained version and
  whether step 13 is needed; (13) `train --version B --fold full`, only when B is retained (the `sans` and `A`
  `full` models already exist with the same hyperparameters and are their versions' final models). Trainings:
  3 (`sans`) + 3 (`A`) + 2 (`B`) = 8, 9 when B is retained; 6 when B is not built, 3 when A is not built. Every
  `train-run.json` records start, end and duration; `compare` and the report list each training (version, fold,
  held-out role, Kosmo and public images, duration) with the count and the total duration. Indicative durations on
  the RTX 4070 SUPER, replaced in the docs by the first measured ones: about 10 min per `sans` training, 35 min per
  `A`, 70 min per `B`; whole chain about 5 to 6 h, unattended. Run folder (A19): fixed paths derived from `--run`,
  `--version` and `--fold` (`chain.json`, `datasets/<version>/<fold>/`, `models/<version>/<fold>/` with
  `train-run.json`, checkpoint, `model.tflite`, `export.json`, `predictions/<version>.json`, `pseudo-cal.json`,
  `pseudo-tip.json`, `not-built/<version>.json`, `compare/`); no command takes an intermediate file path, so a file
  of another chain cannot be passed. Each file records the SHA-256 of the files it was made from; each command
  first checks `chain.json` against the lock and the current `defaults.json` and map, then the recorded hashes of
  every file it reads (T9 `chain.py`, the single checker): `train` skips a fold whose `train-run.json` and
  `export.json` match the current manifest (resume after an interruption) and retrains one whose dataset changed;
  `predict` checks each `cv` export against its training and manifest; `pseudo-cal` checks the `sans` predictions
  against the current `sans` exports; `dataset --public A` checks `pseudo-cal.json` against `sans/full`; `pseudo-tip`
  checks the `A` predictions, MA and `pseudo-cal.json`; `dataset --public B` checks both pseudo files; `compare`
  checks everything and the same `kosmoPartSha256` per fold across versions. A mismatch exits non-zero with
  "Étape n à refaire : <fichier> ne correspond plus à l'étape m" (step numbers above). A version that cannot be
  built (CV23 fallbacks, missing folders) writes `not-built/<version>.json`, prints "Version <A|B> non construite :
  <raison>" and exits 0; its later steps print the same line and exit 0, so the chain goes on; `compare` lists it.
  Fallbacks: a missing accepted folder builds A from those present (stated); no accepted set: "sans jeux publics
  seulement". Eval stays untouched until the single K6 run of the final model (`frozen.json` holds its hash, CV4).
- **Version selection** (D26, CV22): each built version is tuned on its own out-of-training predictions, which
  gives each judged dev dart a CV7 outcome per version. Sequential paired test in the order `sans` -> `A` -> `B`:
  the retained version starts as `sans`; a candidate is compared with the version retained so far on the same
  darts, `b` = darts correct with the candidate and not with the retained version, `c` = the reverse, exact one-sided
  McNemar p-value `p = sum_{k=b}^{b+c} C(b+c, k) / 2^(b+c)` (`p = 1` when `b + c = 0`); the candidate becomes the
  retained version only when `p < MCNEMAR_ALPHA` (0.05; `p` is a multiple of `2^-(b+c)` and never equals 0.05).
  Kosmo (Q5, D34): a candidate without a feasible pair (`NoFeasiblePair`, unflagged errors above 1 %) never
  replaces a retained version that has one, whatever `p` (the 1 % budget comes before McNemar). The output and the report give the figures of every
  version and, per step, `b`, `c`, `p` and the decision, with the glosses "A : photos de Kosmo + jeux publics de
  détection ; B : A + pointes v17 pseudo-étiquetées".
- **Per dart** (CV5 to CV7): each non-voided entry is judged on its own photo. Tips found on the photo are mapped to
  board millimetres with the photo's predicted calibration; a candidate within `MATCH_RADIUS_MM` (6 mm) of a tip
  announced earlier in the volley is an old dart. The best new candidate is announced if its score is at least
  `TAU_SURE`, flagged "to check" if between `TAU_LOW` and `TAU_SURE` or if the calibration of the photo failed; no new
  candidate announces a miss. Thresholds tuned on out-of-training dev predictions only (CV4, CV24).
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
- CV4 Eval units never enter training, tuning, threshold choice or dataset building. Dev tuning (D25) means:
  version comparison, `tau_low`/`tau_sure` and the pseudo-label thresholds are chosen only on out-of-training
  predictions of the dev cross-validation (CV24), never on a photo seen in training by the model that predicts it;
  `frozen.json` holds the pair so tuned and the SHA-256 of the retained version's `full` model. `measure --set eval` needs a
  valid `frozen.json` (strict: format, version, `tau_low`, `tau_sure`, 64-hex `modelSha256`, no other key) with a
  matching model SHA-256; the report records the model, `frozen.json` and split hashes. Eval control clicks (CV25)
  are reference data for CV9 and the diagnosis only: never a training label, never an input of a parameter choice
  (CV30 tunes on dev controls; eval controls confirm).
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
  calibration (CV19) is not evaluated and is counted. The CV19-based reference above is suspended with CV19 (D30,
  A29) and replaced at P2; the held tasks T9 and T13 say so in their first line. After D30 (ADR 0017, details fixed at P2): on eval the
  reference homography of a photo is that of its control clicks (CV25), and CV9 is also reported per photo on every
  judged eval photo (automatic calibration of each photo against its control clicks), not only per volley.
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
  sessions `S01`, `S02`... in session-name order. Everywhere else sessions are named by their role in the locked
  split ("session dev 1", "session dev 2", order of CV3 keys, CV24), never by name, including every file of the
  training run folder (D25). The drift flag file `drift.json` (CV18) holds session names and is,
  like the split file, a local working file in `~/kosmo-runs/`: never pasted, never committed. Public dataset slugs,
  licences and image counts (CV21) and the pseudo-label figures (CV23: thresholds, counts, px distances) are allowed
  in reports: they name public sets, not Kosmo's sessions; no public `file_name` is printed. After D30 the
  diagnostic report names sessions by role only (`dev 1`, `dev 2`, `eval 1`, `eval 2`, CV3 key order within each
  set, `control.roles`); the S01 labels stay only in the batch 1 `drift` report (Kosmo, Q8, D37: roles only). `frames.json`, the
  extracted frames folder and `diagnostic.json` hold session names and are local working files like `drift.json`.
- CV16 Annotation tool: stdlib only, `127.0.0.1` only, no external resource, captures read-only, writes only in the
  annotations folder, atomic; every route resolves the session name against the listed sessions; never overwrites
  an annotation file that the strict reader rejects (a version 1 file is accepted and written back as version 2 on
  the first change, CV20). It reads the optional drift flag file read-only through its strict stdlib reader; a missing,
  invalid or oversized file gives a warning in the session list, never an error. Details in T6a and T6c. After D30:
  a version 2 file is written back as version 3 on the first change (CV25); optional read-only inputs `--split`
  with `--lock` (control sample, roles), `--frames` (video frames, served only when listed in `frames.json`) and
  `--diagnostic` (visual check), each through a strict reader, each missing input giving one "indisponible" line,
  never an error. Drift flags are shown as information only (CV19 status). Details in T15d, T15e, T15k. After
  D39 and D43: the stdlib-only rule covers every module except `snap.py` and the shared decoder `luma.py`, both
  imported lazily; without numpy and Pillow the tool starts, shows "Recalage indisponible" and refuses calibration,
  recalibration and control clicks (CV31); snapping reads only the photo or listed frame already served by the
  tool, through `luma.decode_luma` (file-size cap `luma.MAX_IMAGE_BYTES`, `Image.MAX_IMAGE_PIXELS` kept, S8), at
  most `SNAP_CACHE_IMAGES` (2) decoded images in memory; an image that cannot be decoded leaves the point at the
  click, `unsnapped` with reason `unreadable`, and never stops the tool. Details in T15n.
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
  CV18 status (D30): not validated. Its results are information only until a measure passes the CV30 gate; the
  report starts with "Mesure de dérive non validée (diagnostic en cours) : ne pas recalibrer d'après ce rapport".
- CV19 Drift threshold and compensation: a photo is "beyond" when drift mm > `DRIFT_MAX_MM` (1.0 mm =
  `CAL_MAX_ERROR_MM` / 2, Kosmo, D18). Reference calibration of a photo = its compensated points `p_i + d_i`
  when measured and not beyond; none otherwise. Used for the `cal1`-`cal4` training labels (T9), CV9, CV12 and CV14.
  Photos without reference calibration are dropped from the dataset and excluded from CV9 and CV12, each counted by
  reason. `dataset` and `measure` refuse to run while a judged photo of their set is beyond (message: run `drift`,
  recalibrate, run `drift` again); `--accept-drift` overrides and the run records the count. CV7 (and CV8) never use
  the clicked calibration: no dart or session is excluded from the main criterion.
  CV19 status (D30): suspended. No compensation, no "beyond" guard, no K5 recalibration loop until plan update P2
  replaces this rule (T16b) on the diagnosis (CV29) and the gate (CV30); the value of D18 is reopened (Q13).
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
  `sans` < `A` < `B`. Comparison set: the out-of-training predictions of every judged dev dart (CV24), the same
  darts for every version (same fold plan in `chain.json`, same `kosmoPartSha256` per fold, same hyperparameters,
  else refused); eval and campaign results are refused. Each version is tuned on its own predictions (T13 tune;
  without a feasible pair, the pair with the fewest unflagged errors, ties to more correct darts), giving one CV7
  outcome per dart and version. Selection (Kosmo, D26): sequential paired test in the order `sans` -> `A` -> `B`;
  the retained version starts as `sans`; each built candidate is compared with the version retained so far on the
  same darts: `b` = darts correct (CV7) with the candidate and not with the retained version, `c` = the reverse,
  exact one-sided McNemar p-value `p = sum_{k=b}^{b+c} C(b+c, k) / 2^(b+c)`, `p = 1` when `b + c = 0`; the
  candidate is retained only when `p < MCNEMAR_ALPHA` (0.05, `defaults.json`), otherwise the simpler version stays.
  Kosmo (Q5, D34): a candidate without a feasible pair never replaces a retained version that has one.
  Output and report: per version the CV8 rates with Wilson intervals, the tuned pair or "aucune paire", CV9
  successes, CV12 median; per step the versions compared, `b`, `c`, `p` (4 decimals) and the decision; the letter
  glosses "A : photos de Kosmo + jeux publics de détection ; B : A + pointes v17 pseudo-étiquetées" at least once.
  Versions not built (CV23 fallbacks, missing folders) are listed with their reason and skipped in the sequence;
  only `sans` built: "sans jeux publics seulement". Limits stated in the report: tuning and comparison use the same
  out-of-training predictions (eval is the unbiased check); the pseudo-label thresholds (CV23) come from those
  predictions and only filter public labels, never the decision thresholds of CV6; the `full` models that
  pseudo-label public images saw both dev sessions (D25: public images are never measured).
- CV24 Cross-validation by session (D25): requires a session-mode lock with exactly 2 judged dev sessions of at
  least `CV_MIN_SESSION_DARTS` (50) judged darts each, else refused before any training with the French message
  of "Approach, Cross-validation by session". Roles `dev 1`, `dev 2` in ascending CV3 key order. Per version, folds
  `cv1` (train dev 1, predict dev 2) and `cv2` (train dev 2, predict dev 1), plus `full` (train both) for `sans`, for
  `A` and for the retained version; inner valid subset drawn from the training sessions only (volley blocks,
  `VALID_RATIO`, fixed seed); the held-out session is never in its fold's dataset. Out-of-training predictions (one
  per judged dev photo, by the fold model that did not train on it) are the only dev input of CV22, of `tune` and
  of the CV23 thresholds; the final model is the retained version's `full` model, same hyperparameters. The report
  gives the number of trainings, each training's version, fold, held-out role and duration, and the total.
- CV23 Pseudo-labels of public images (D23, D24): computed only from exported models of this run chain, through
  the T3 detector, on images read by the CV21 validated reader. Calibration (versions A and B): model M0 (`sans`,
  fold `full`) and thresholds `tauCalHigh` and `tauCalLow` from the out-of-training predictions of the `sans`
  cross-validation (CV24), rules and fallbacks of "Approach, Calibration points on public images"; every public
  image of A and B carries M0's calibration labels or is dropped (reason counted; v17 images too, Kosmo, Q4, D33). Tips of
  `pseudo-tip-box` sets (version B): model MA (`A`, fold `full`) and `tauTip` from the out-of-training predictions
  of the `A` cross-validation, rules of "Approach, Constrained tip pseudo-labels of v17" (strict rules confirmed by
  Kosmo, D27); one rejected box drops the whole image;
  report: acceptance rates, images dropped per rule (Kosmo, D27: mat24 `whole-dart-checked`, v17 per-box reasons,
  `tip-shared`, `tip-unassigned`, missing calibration labels, and the calibration reasons), median and p95 px
  distance to the box's lower-right corner. Pseudo
  files (`pseudo-cal.json`, `pseudo-tip.json`, in the run folder, strict reader, cap `pseudo_file.MAX_BYTES` 16 MiB)
  hold the `chain.json` SHA-256, the labelling model's SHA-256 and source version (`sans` or `A`, fold `full`), the
  SHA-256 of the predictions file the thresholds came from, the map SHA-256, the thresholds and per
  image (slug, subset, `file_name`, image SHA-256) its labels or drop reason; a dataset refuses a pseudo file from
  another chain or map, from the wrong version (`sans` for calibration, `A` for tips) or whose model is not the
  current `full` export of that version, and drops an image whose SHA-256 differs. Constants `PSEUDO_MAX_MM` 2.0, `PSEUDO_MIN_PRECISION` 0.99, `PSEUDO_MIN_SUPPORT` 30,
  `PSEUDO_LOW_QUANTILE` 0.01 in `defaults.json`.
- CV25 Control clicks (D30, annotation format version 3): `controls` is a list of `{dart, source, width, height,
  points}` plus `positionMs` when `source` is `video`; `source` is `photo` or `video`; `points` holds exactly 4
  entries in CV2 order, each `[x, y]` in stored pixels of that image or `null` (hidden); sorted by dart then photo
  before video, at most one control per (dart, source), at most `MAX_CONTROLS` (1024). A control belongs to a judged
  dart (CV5); a video control needs a volley video and a non-null `entry.videoPositionMs`. Controls are independent
  of segments (CV20) and never change them. Each control and each segment carries `snap`: 4 entries aligned with
  `points`, `"snapped"` or `"unsnapped"` (CV31) for a point, `null` exactly when the point is `null`; segments may
  also hold `"manual"` (clicked before snapping existed). A version 2 file is migrated without loss (`controls: []`,
  every segment `snap: ["manual", "manual", "manual", "manual"]`); writers write version 3 only. Use: CV9
  reference and diagnosis (CV28); on dev, label source only through T16b (P2).
- CV26 Control sample (computed, never chosen by hand; roles of CV15): photo controls: every judged eval photo;
  per dev session the judged darts of rank 1, 1 + `CONTROL_DEV_STRIDE` (10), ... in dart order, plus the last
  judged dart, plus the reference photo of every segment; video controls: the dev photo sample, plus the judged
  darts `CONTROL_EVAL_VIDEO_FIRST` (55) to `CONTROL_EVAL_VIDEO_LAST` (60) of every eval session (Kosmo, Q7, D36).
  Order of work: session list order, then dart order, photo before video.
- CV27 Video frame before the entry: target `entry.videoPositionMs` − `VIDEO_BEFORE_ENTRY_MS` (1000); the frame
  used is the last decoded frame whose time is at or before the target; reasons for no frame, counted:
  `no_position`, `before_start` (target before the first frame), `too_close` (the target precedes the previous
  entry's `photo` stamp: the phone may still be moving from the previous tap), `past_end`, `video_missing`, `video_unreadable`,
  `orientation_mismatch` (frame and photo orientations differ). Saved as JPEG (quality 95) with its size and
  SHA-256 in `frames.json`. Bounds (S13): a video larger than `VIDEO_MAX_BYTES` (512 MiB) or needing more than
  `VIDEO_MAX_DECODED_FRAMES` (20000) decoded frames is `video_unreadable`; any decode error (corrupt or truncated
  file) is `video_unreadable` for the darts of that video and the run continues (exit 0); the output prints
  `Images vidéo absentes : N` with the count per reason.
- CV28 Diagnosis measures (all in mm with the CV9 metric unless stated): per complete photo control of dart k with
  segment calibration `S`: `T` = CV9(`H(controls)`, `H(S)`), true drift; `E` = CV9(`H(controls)`, `H(auto)`), with
  `auto` the CV18 shifted points even when beyond (unmeasurable counts as a failure, reason kept); `E_seeded` the
  same with patches centred on the clicked points; coarse error px = |`t0` − mean(clicked − segment points)|; peak
  ratio = second peak / first peak (second peak outside `PEAK_EXCLUSION` cells); `R` = CV9(`H(controls)`, `H(S)`)
  on segment reference photos (agreement of the segment clicks with the snapped controls; information, it bounds
  the part of `T` due to the segment clicks and decides nothing); `D` = snapping dispersion per snapped point
  (CV31, "Approach, Assisted click"), photo and video controls, p95 with its CV13 interval; points `unsnapped`
  counted per role and source, and a control with an `unsnapped` point is excluded from `T`, `E`, `O` and from the
  `DIAG_MIN_CONTROLS` count, counted apart like a partial control. Video side: `M` = least-squares similarity from photo px to
  video px fitted per session on paired complete controls (residual median and p95 reported; refused below
  `MAP_MIN_PAIRS` or above `MAP_MAX_RESIDUAL_PX`); `O` = CV9(`H(photo controls)`, `H(M⁻¹(video controls))`): move
  between the untouched phone and the photo; `V` = move of the untouched frame against the first video control of
  its segment (durable move); `W` = largest move during the capture window (frames every `VIDEO_WINDOW_STEP_MS`
  from `entry` to the `photo` stamp), only with a video method validated per CV30 on the video controls. Report:
  counts, median and p95 with CV13 intervals, per role and overall. Limits stated (section « Limites » of the
  diagnosis report): a constant tilt present in every photo is absorbed by `M`; and, in one plain French sentence,
  « La dispersion D mesure la répétabilité du recalage (le même calcul relancé depuis des départs voisins retombe
  au même point), pas son exactitude : un recalage qui se tromperait toujours de la même façon aurait une
  dispersion nulle. » (A35, T2). A control image that the decoder cannot read (CV16) is counted per role and
  source with reason `unreadable` and excluded like a partial control.
- CV29 Diagnosis decision rule (fixed before any number is seen; constants in `defaults.json`): "non concluant" if
  the snapping dispersion `D` p95 > `SNAP_REPEAT_MAX_MM` (0.25; the dispersion, not Kosmo's hand, bounds the
  reference, Kosmo, Q10, D39) or fewer than `DIAG_MIN_CONTROLS` (30) complete snapped dev photo controls; each
  cause has its own line ("Non concluant : dispersion du recalage p95 X mm, au-delà de 0,25 mm", X = « infinie »
  when failed restarts reach the p95, or "Non concluant :
  N contrôles photo dev complets et recalés, il en faut 30") and its "Suite proposée" (Approach); no click
  threshold on Kosmo's hand exists (Q10 option 3); else
  H1 "mesure fausse" if the share of photos with `E` ≤ `VALIDATION_MAX_MM` (0.5) is below `VALIDATION_MIN_SHARE`
  (0.95); "photos déplacées" if at least `DIAG_MIN_SHARE` (0.10) of photos have `T` > `DIAG_MOVE_MM` (1.0); H2
  "le téléphone bouge à la frappe" if at least `DIAG_MIN_SHARE` of the darts with `O` have `O` > `DIAG_MOVE_MM`
  and at least `DIAG_MIN_VIDEO` (10) darts have `O`, else "H2 non mesurable"; H3 "la cible bouge entre les
  fléchettes" likewise with `V`. "Non concluant" short-circuits: when it holds, its line (both lines when both causes hold) is the only verdict
  output printed (T1).
  Otherwise the lines are independent (several may hold); "Suite proposée" as in "Approach,
  Diagnosis decision rule". `W` is reported as support, it decides nothing.
- CV30 Validation gate: an automatic calibration or drift measure is validated when its error against the control
  clicks is at most `VALIDATION_MAX_MM` on at least `VALIDATION_MIN_SHARE` of the complete dev photo controls, its
  parameters having been chosen on dev controls only, then confirmed on the eval photo controls (Kosmo, Q12, D41). No
  compensation and no automatic per-photo calibration is used for labels or measures before the gate passes; a
  failed gate is reported with its numbers.
- CV31 Assisted click (Kosmo, Q10, D39; design D43): every calibration-point click (first calibration,
  recalibration, control) is a designation; `snap.snap_point(luma, start)` returns the sub-pixel crossing of
  "Approach, Assisted click" within `SNAP_RADIUS_PX` of the start, status `snapped`, or the start itself with status
  `unsnapped` and one reason (`support`, `contrast`, `residual`, `angle`, `no_convergence`, `too_far`, `border`,
  `ambiguous`).
  The tool adds the reason `unreadable` when `luma.decode_luma` cannot read the image (the point stays at the click).
  The stored point is the snapped point or the click (D8 pixels); its status goes to `snap` (CV25). Proposals (the
  previous item's points snapped on the new image) are never stored as such: a proposal that does not snap must be
  clicked or hidden before Entrée saves. Click rule (U27, pure function `flow.click_target(slots, click,
  selected)`, T15n): a point selected with keys 1 to 4 takes the click; else the positioned point nearest to the
  click within `CLICK_REPLACE_RADIUS_PX` (100 source px) is replaced; else the first point without a position, in
  order 1 to 4, is filled; else (every point positioned, none within the radius) the click is refused with 409
  « Clic loin des 4 points : choisir le point avec les touches 1 à 4, puis cliquer ». Hide rule (U25, control
  only): `hide` applies to the selected point, then clears the selection; with no selection it hides the first
  point `à cliquer`; with neither it is refused with 409 « Choisir le point à cacher : touche 1 à 4, puis H »,
  so a stray `hide` never hides a point silently. Save rule (U29): `save` carries the open item (`dart` and
  `source`, or the recalibration dart) and is refused with 409 when fewer than 4 points are ready (« Points prêts :
  n / 4 ») or when that item is no longer the open one (« Photo déjà enregistrée »), so a repeated save never
  writes the next item. Repeatability `D` (CV28): restarts from `SNAP_REPEAT_DIRECTIONS` starts at
  `SNAP_REPEAT_OFFSET_PX` around the stored point, board-plane mm through the control's homography, failed restart
  = infinite; target `SNAP_REPEAT_MAX_MM`. Constants `SNAP_*` in `defaults.json`, each pinned once in
  `test_config.py`; the same function serves the tool and the diagnosis.

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
  entries of "Approach, Public datasets"). `PSEUDO_*` constants added to `defaults.json` by T9c (CV23);
  `CV_MIN_SESSION_DARTS` (50) by T9, `TRAIN_EPOCHS` (50), `TRAIN_BATCH` (8), `TRAIN_SEED` (42) by T10 and
  `MCNEMAR_ALPHA` (0.05) by T13 (CV24, CV22), each pinned once in `test_config.py`; `VALID_RATIO` (0.15, PR A) now
  sizes the inner valid subset (CV24). The `Dockerfile` copies `config/` next to the package (T8b), since `config.py` reads
  `config/defaults.json` at import.
- Added package `tools/camera/kosmo_camera/`: `config.py`, `board.py`, `truth.py`, `split.py`, `annotations.py` (v2 in
  T5b), `geometry.py`, `groups.py`, `synth.py`, `drift.py`, `drift_file.py`, `chain.py`, `dataset.py`, `public_import.py`, `predictions.py`, `pseudo_file.py`, `pseudo_label.py`, `model_spec.py`, `train.py`, `gpu_check.py`, `export.py`,
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
  written in the container and read by the stdlib tool, never pasted or committed, CV15; after D30, T15a opens this
  section with the status line of "ADR 0017 text", item "ADR 0016 status line", A28, and P2 closes it); "Public data and version
  selection" (A17): T9p writes the map file, the train-only rule and public datasets as untrusted input with the
  strict validation of CV21 (S10); T9 adds the cross-validation by session and the run chain (CV24, D25, with the
  alternatives of "Approach, Cross-validation by session"); T9c adds the calibration pseudo-labels with the options of "Approach, Calibration
  points on public images" (loss masking rejected: no class mask in `rfdetr` 1.12.1); T9d adds the constrained v17
  tips; T13 adds the sequential paired McNemar selection of CV22 (D26).
- After D30 (T15a to T15l; T16a to T16c conditional): `annotations.py` v3 (`Control`, `controls_sha256`,
  `control_for`, `MAX_CONTROLS` 1024; T15b), `control.py` and `frames_file.py` (stdlib; T15c), `video_frames.py`
  (PyAV; T15c), `snap.py` (numpy only, works on a luma array, imported lazily by the tool; T15m), `luma.py` (the
  one JPEG-to-luma decoder, moved from `drift.py` `_decode_luma`, `MAX_IMAGE_BYTES` 32 MiB, used by `drift.py`, the
  tool and the diagnosis; T15m, A33, S14), `requirements-tool.txt` (numpy
  and Pillow only, hashed, cp313 and cp314; T15n, D44), `requirements-tool-test.txt` (pytest and its four
  dependencies at the `requirements-cpu.txt` pins and hashes, for the Python 3.14 test runs only, never installed by
  Kosmo; T15n, D44), `synth.py` imports `av` inside `_write_video` only (T15n, D44), `annotate/snapping.py` (stdlib glue: lazy import, image cache,
  availability; T15n), `annotate/journey.py` (stdlib; T15e), `annotate/homography.py` (stdlib, with `next_above`; T15k), `tests/test_plan_docs.py` (stdlib doc test: ADR
  0016 status line, ADR 0017 sections, "HELD by D30:" line of each held task file; T15a), `diagnose.py` and
  `diagnostic_file.py` (T15i, T15j), `drift.py` gains `phase_shift_detail` and `measure_photo_detail` with
  `measure_photo` unchanged (T15i). `defaults.json` gains `CONTROL_DEV_STRIDE` 10, `CONTROL_EVAL_VIDEO_FIRST` 55,
  `CONTROL_EVAL_VIDEO_LAST` 60, `VIDEO_BEFORE_ENTRY_MS` 1000, `VIDEO_MAX_DECODED_FRAMES` 20000,
  `VIDEO_MAX_BYTES` 536870912 (512 MiB) (T15c), `VALIDATION_MAX_MM` 0.5,
  `VALIDATION_MIN_SHARE` 0.95, `PEAK_EXCLUSION` 3, `DIAG_MIN_CONTROLS` 30 (T15i), `SNAP_RADIUS_PX` 10,
  `SNAP_WINDOW_PX` 16, `SNAP_SIGMAS_PX` [1, 2, 3], `SNAP_MIN_ANGLE_DEG` 30, `SNAP_MIN_SUPPORT_PX` 8,
  `SNAP_MIN_CONTRAST` 4, `SNAP_MAX_RESIDUAL_PX` 0.75, `SNAP_CONVERGE_PX` 0.05, `SNAP_MAX_ITER` 5,
  `SNAP_REPEAT_DIRECTIONS` 8, `SNAP_REPEAT_OFFSET_PX` 3, `SNAP_REPEAT_MAX_MM` 0.25 (T15m; initial values, T15m may
  tune them on synthetic boards only), `SNAP_CACHE_IMAGES` 2 and `CLICK_REPLACE_RADIUS_PX` 100 (T15n, U27),
  `VIDEO_WINDOW_STEP_MS` 100, `DIAG_MOVE_MM` 1.0, `DIAG_MIN_SHARE` 0.10, `DIAG_MIN_VIDEO` 10, `MAP_MIN_PAIRS` 3,
  `MAP_MAX_RESIDUAL_PX` 2.0 (T15j), each pinned once in `test_config.py`. Format caps next to their readers:
  `frames_file.MAX_BYTES` 4 MiB, `diagnostic_file.MAX_BYTES` 4 MiB. ADR 0016 gains the sections "Annotation format
  v3: control clicks" (T15b, with the `snap` field) and "Assisted click" (T15n: ADR decision 2 and plan decision 5
  amended, the options of "Approach, Where the snapping runs"), and its decision 2 gains the line "Amended: see
  section Assisted click (D43, D44)" (T15n, A31); `docs/rules/camera.md` CV16 gains the "After D39 and D43" sentence
  of "Rules" (T15n, A31); `docs/adr/0017-per-image-calibration.md` is added by T15a from "ADR 0017 text".
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
  beyond, suggested and unmeasurable; read by the annotation tool on the same machine only (information only after
  D30).
- After D30, local working files holding session names, never pasted or committed (CV15): `~/kosmo-runs/frames/`
  (`frames.json` and `<session>/video-NNN.jpg`, frames of Kosmo's own videos, T15c) and
  `~/kosmo-runs/diagnostic/diagnostic.json` (per photo the automatic and clicked points and the CV28 values, T15i,
  T15j). Exposed: `diagnostic-report.md` only (numbers and roles, pasted by Kosmo). Control clicks live in the
  annotation files (`~/kosmo-data/annotations/`), like the tips, with their `snap` status (CV25, CV31); snapping
  writes no file of its own (no new route either: the tool snaps inside the existing flow route, from the photo or
  frame it already serves). The WSL tool venv (`~/kosmo-tool-venv`, K7.1b) holds only numpy and Pillow. The visual check page shows Kosmo's own photos on
  127.0.0.1 only.
- Run folder `~/kosmo-runs/chaine/` (local, CV24, A19): `chain.json` (split SHA-256, fold plan by role with judged
  darts per role, hyperparameters, `defaults.json` and map SHA-256), datasets, models, predictions, pseudo files,
  `not-built/` and `compare/`; no file of it carries a session name (roles and dart numbers only, CV15).
- Dataset `manifest.json` (local): `fold` (`cv1`, `cv2`, `full`), training and held-out roles, adds per public slug
  its licence, images kept and dropped per reason, and `kosmoPartSha256` (hash of the Kosmo images and labels,
  train and inner valid, of that fold) so `compare` can prove the versions share the same Kosmo part per fold
  (CV22); `version` (`sans`, `A`, `B`), the SHA-256 of the pseudo files used and their figures.
- Predictions file `predictions/<version>.json` (local, T9o): per fold the `.tflite` SHA-256 used, per held-out
  judged photo (role, dart number) the raw detections (class, score, x, y in source px); no image, no session name.
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

Version 3 (T15b, CV25): `version: 3`, root keys `format`, `version`, `session`, `truthSha256`, `calibrations`,
`controls`, `darts` (exactly these). `controls: [ { dart, source: "photo"|"video", positionMs (video only), width,
height, points: [ {x, y} | null x4 in CV2 order ], snap: [ "snapped"|"unsnapped"|null x4 ] } ]`, sorted by dart
then photo before video, unique per (dart, source), at most 1024; each segment of `calibrations` gains `snap`
(`"snapped"`, `"unsnapped"` or `"manual"`; v2 segments migrate to `"manual"` x4; CV25, CV31). Reader knows versions 1, 2 and 3 and returns the v3 model (v1 and v2 get `controls: []`);
writer writes version 3 only, on the first saved change. Controls never alter `calibrations`; `segments_sha256` is
unchanged by controls and `controls_sha256` covers them, so the T8b flag file stays valid when only controls change.
`truthSha256` copied unchanged.

## Screens and states (annotation tool, French)

Session list, calibration screen, darts screen, the per-dart flow (one click sets the tip and moves on, autosave on
every action), keys, markers, the error and empty states and their French messages: written once in
`42-camera-feasibility-0b-1/T6a-annotation-server.md` (flow and states) and `T6b-annotation-page.md` (page, keys,
markers, contrast). The tool does not show dev or eval: Kosmo annotates every session the same way.
PR B (T6c flow and server, T6d page): action "Recalibrer à partir de cette fléchette" (key R) on the darts screen,
recalibration screen "Recalibration à partir de la fléchette N : point k sur 4", and drift flags in the session list
and on flagged darts, read from `drift.json` (`--drift FILE`, optional).

After D30 (T15d to T15g, T15k). Exact French strings; each lives once as a constant (server for instructions,
`app.js` for labels):
- **Session list** (T15g): one card per session: name, role when `--split` is given (Kosmo, Q9, D38), badge `À faire`
  / `En cours` / `Terminée` / `Ignorée`, step rows from templates (U21) `Calibration : faite|à faire`, `Pointes :
  D / T`, `Contrôle photo : D / T`, `Contrôle vidéo : D / T` or `Contrôle vidéo : indisponible (images vidéo
  absentes)` / `Contrôle : indisponible (option --split absente)`; below, a grey box titled `Information` with the
  drift line `Mesure de dérive non validée : information seulement` (when `--drift` is given), the "indisponible"
  lines and `Aucune image vidéo utilisable pour cette session (N manquantes)`. The focus session's card (server
  field `focus`) shows `Prochaine étape :` with its instruction and a button `Continuer` (also Enter, but only
  when no other button or link has the keyboard focus, so Enter on a focused button keeps its own action; U23,
  D42; the assisted point screen has the opposite rule, U25: there Entrée always means « Enregistrer ») that applies
  the row's `action` (`calibrate`, `goto N`, `control`) through the existing flow route (U15).
- **Three page elements** (T15g, U19): `#journey`, the one active instruction, the only element styled as an
  instruction; `#help`, the static key help of the screen (renamed from `#instruction`); `#banner`, alerts only
  (save errors, refused actions), never an instruction and never a drift text (A30).
- **Instruction** (`#journey`, T15e and T15g): "Instructions" table, the first matching row of the session in focus
  wins (`journey.next_step`); the text is followed by the hint of the screen: on the list `(bouton Continuer)`, on
  a session screen the key shown in the table:

  | Condition | Instruction | Session hint | Action |
  | --- | --- | --- | --- |
  | no calibration | `Cliquer les 4 points de calibration de la photo de la fléchette 1` | none | `calibrate` |
  | tips left | `Pointer les fléchettes : prochaine fléchette N` | none | `goto N` |
  | photo controls left | `Contrôle de calibration : photo de la fléchette N` | `(touche K)` | `control` |
  | video controls left, frames present | `Contrôle de calibration : image vidéo de la fléchette N` | `(touche K)` | `control` |
  | the matching row has action `calibrate` or `control` and snapping is unavailable (D43): replaces that row | `Recalage indisponible : relancer l'outil avec ~/kosmo-tool-venv/bin/python (K7.1b)` | none | none |
  | controls left, no `--split` | `Contrôle indisponible : relancer l'outil avec --split (K7.3)` | none | none |
  | video controls left, no frames | `Images vidéo absentes : lancer l'extraction (K7.2)` | none | none |
  | session done, others left | `Session terminée : passer à la session suivante` | none | `goto` focus |
  | every row of every session done | `Contrôle terminé : fermer l'outil et lancer le diagnostic (K7.4)` | none | none |

  The final row is reached only when no row of any non-ignored session is left or unavailable (U17): an
  "indisponible" row is shown instead. On the control screen `#journey` shows the control title instead; on the
  recalibration screen, the recalibration title. No stale text: recomputed from the server state after every
  save.
- **Assisted point screen** (T15f; the control screen, and the calibration and recalibration screens, which keep
  their titles; CV31, D39): the image in full view (key 0 and the wheel zoom of T6b keep working) and, in the side
  panel, 4 insets, one per point, in a 2 x 2 grid (points 1 and 2 on the first row, 3 and 4 on the second, gap
  8 px), each a x3 zoom of `SNAP_INSET_PX` (60, page constant) source px around its point, so 180 x 180 CSS px; on
  this screen the side panel is 400 px wide (U26), and the four insets, the line `Points prêts` and the button
  `Enregistrer (Entrée)` are visible together without scrolling at 1080p. Insets are labelled `1 : 5|20`,
  `2 : 13|6`, `3 : 17|3`, `4 : 8|11`, with a thin cross on the point and its status in text (colour is never the
  only cue): `recalé`, `non recalé` (clicked, no clear crossing, or photo unreadable), `caché` (H), `à cliquer` (no
  proposal, or a proposal that did not snap); each inset has an `aria-label` with its number and status (`Point
  2, recalé`, U31). The 4 points are also drawn as numbered crosses on the full view. The CV2 board diagram that
  highlights the next point (T6b) stays on the screens where points are filled in order: the first calibration,
  and any item opened without proposals (the first video item of a session); it highlights the point the next
  click fills (U27). Line `Points prêts : n / 4 (Entrée pour enregistrer)`; Entrée saves only when n = 4 (U16
  kept: nothing is written before; leaving by Échap, next, previous or goto drops the changes; the server refuses
  `save` below 4 with 409, CV31). Click: the click rule of CV31 (selected point, else nearest positioned point within
  `CLICK_REPLACE_RADIUS_PX`, else first point without a position, else refused with its banner text), snapped at
  once. Keys 1 to 4 select a point: the next click and H apply to it; H follows the hide rule of CV31 (selected
  point, else first point `à cliquer`, else refused with « Choisir le point à cacher : touche 1 à 4, puis H »).
  U: undoes the last change of the item; with no change, back to the previous item, whose saved control is removed
  from the file at once and shown again as proposals (Entrée saves it again), and `#banner` says `Photo précédente
  rouverte : son enregistrement est retiré jusqu'à Entrée` (U28). Buttons `Enregistrer (Entrée)`, `Point caché
  (H)`, `Annuler (U)`, `Quitter (Échap)`. **Entrée rule (U25, D45; next to the list rule U23)**: on the assisted
  point screen Entrée always means « Enregistrer », whichever button has the focus: one `keydown` handler on the
  document calls `event.preventDefault()` for Entrée before acting, so a focused button never runs its own action
  on Entrée, and every button of the screen calls `blur()` after its click (after a mouse click on `Point caché
  (H)`, the next Entrée saves). **Repeat guard (U29)**: Entrée with `event.repeat` is ignored, and Entrée is
  ignored for `SAVE_GUARD_MS` (300 ms, page constant next to `CLICK_GUARD_MS`) after each save, so a held key or
  a quick double press never saves the next item unseen; the server's save rule (CV31) refuses a repeated `save`
  of the same item. Hint `Cliquer près du bon croisement : l'outil le recale. H seulement si une fléchette
  cache le point` (U18: an unusable photo is skipped with H four times then Entrée when its proposals are `à
  cliquer`, otherwise 1 H 2 H 3 H 4 H then Entrée; counted as hidden). On the calibration and recalibration
  screens H, its button and the second sentence of the hint are absent: a segment needs 4 points (CV20); the
  server refuses `hide` there (T15n). An image the decoder cannot read: `#banner` says `Photo illisible : le
  recalage est impossible pour cette photo`, every point is `à cliquer` or, once clicked, `non recalé` at the
  click; the tool never freezes (S14). Saved line `Enregistré à HH:MM:SS`. Without numpy and Pillow: `Recalage
  indisponible : relancer l'outil avec ~/kosmo-tool-venv/bin/python (K7.1b)`, no click accepted on this screen.
- **Control screen** (T15f): title `Contrôle de calibration : photo de la fléchette N` (or `image vidéo de la
  fléchette N`), progress from templates `Photos contrôlées : D / T · Images vidéo : D / T`; entry button `Contrôle
  de calibration (K)` on every session screen, K accepted from every cursor including `Terminé` (with nothing
  left: `Contrôle terminé`). Proposals: the previous item of the same session and source, else the segment of that
  dart for the first photo item, none for the first video item of a session (D31's "no marker" rule is replaced by
  Kosmo's proposals, D39). Errors: `Nombre maximal de contrôles atteint (1024)`; without `--split`: `Contrôle :
  indisponible (option --split absente)`.
- **Recalibration screen** (T15g, explanation in the side panel so the photo stays fully visible at 1080p without
  scrolling; assisted point screen with `Points prêts : n / 4`, proposals = the segment in force at that dart, U16
  and U19): `Recalibrer veut dire : la cible ou
  le téléphone a bougé à partir de cette fléchette. Les 4 points enregistrés ici remplacent la calibration pour cette
  fléchette et les suivantes, jusqu'à la prochaine recalibration. Les fléchettes déjà pointées ne changent pas.
  Ne recalibrer que si le déplacement se voit à l'œil.` The texts `Dérive au-delà du seuil` and `À recalibrer à
  partir de` are removed.
- **Visual check** (T15k): title `Contrôle visuel : fléchette N (référence : fléchette M)`, the two images
  alternating every 700 ms, line `Décalage automatique : 3,2 px (0,4 mm) · d'après les clics : 0,6 mm · écart :
  0,2 mm`, choices `1 Alignement automatique`, `2 Alignement d'après les clics`, `3 Aucun alignement` (U30: the
  visual check says « alignement », never « recalage », which names the snap only), `Espace : pause`, `← → : photo
  précédente / suivante`, `N : photo suivante au-delà de 0,5 mm` (`Aucune autre photo au-delà de 0,5 mm` when
  none), position `Photo i / n`; opens on the photo with the largest gap (U20); the arrows follow dart order and
  `i` is the rank of the shown photo in that same dart order (U24, D42), so N and the opening jump keep `i`
  consistent; `Non mesurable` when an
  alignment is missing; route states shown in the page, never a bare error (S12): `Diagnostic périmé : relancer
  le diagnostic (K7.4)`, `Pas de diagnostic pour cette session : relancer le diagnostic (K7.4)`; without the
  option: `Contrôle visuel : indisponible (option --diagnostic absente)`.

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
- T9p: on synthetic COCO exports: class map strict reader, unannotated category ignored, annotated unmapped
  category, mapped name absent, unknown slug each refused, `tip` at the source box centre with a `BOX_FRACTION` box,
  `whole-dart-checked` with and without a tip inside (dropped and counted per rule), `pseudo-tip-box` boxes returned
  raw, `drop-image` and `ignore`, rejected folder never opened (CV21); untrusted downloads (S10): `file_name`
  `../x`, absolute or symlink, oversized image, decompression bomb, non-finite or outside boxes each dropped and
  counted with nothing read outside the public folder; a 2700x3600 JPEG kept; oversized or invalid JSON, dangling or
  duplicated ids each refuse the set; source folders unchanged (hashes); `datasets.md` 7-cell rows, dated licence on
  accepted and conditional rows (S11).
- T9: fold plan (CV24): roles in CV3 key order, refusals for block mode, 1 or 3 judged dev sessions and a session of
  49 judged darts (50 accepted), with their French messages and nothing written; datasets `cv1`, `cv2`, `full`:
  the held-out session absent from its fold (images, labels, inner valid), inner valid drawn from training-session
  volley blocks only, `full` holds both sessions; COCO output with 0-based category ids, box size, compensated
  calibration boxes, drift guard and `--accept-drift`, eval unit refused (CV4, CV19); public images in train only,
  A or B without calibration labels refused, versions `none`, `A`, `B` give the same `kosmoPartSha256` and inner
  valid subset per fold (CV22); `chain.json` strict reader; every hash check of "Approach, Three trainings, run
  chain" refuses a stale input with the step numbers (A19); no session name in any written file (CV15).
- T9b: the committed map loads strictly and equals the three entries of "Approach, Public datasets"; mapped slugs
  match `datasets.md` statuses (`pseudo-tip-box` only in the `conditional` row); rejected rows unmapped; attribution
  cells as D28 (CV21).
- T9o (fake detector): each judged dev photo predicted exactly once, by the fold model that did not train on it;
  a `full` model or a model of the wrong fold refused; predictions file strict reader (cap, unknown key, role and
  dart only); stale export refused (A19); not-built version passes through (CV24).
- T9c (fake detector, synthetic dataset and COCO exports): `tauCalHigh` and `tauCalLow` on scripted out-of-training
  scores and distances (precision exactly 99 % and just below, support 29 and 30, cap at `tauCalHigh`), a
  predictions file of version `A`, or one whose model hashes are not the current `sans` exports, refused; label above
  `tauCalHigh` (with the `sans/full` model), image dropped in the band, same-class duplicate dropped, clockwise rule
  (3 and 4 points, a swapped pair dropped), no `tauCalHigh` fallback, no `tauCalLow` refusal; pseudo file strict
  reader (cap, unknown key, wrong version, chain or map, model not the current `full` export, image hash changed
  drops the image); dataset A carries M0's labels, no kept public image has a calibration prediction above
  `tauCalLow` without its label (CV23).
- T9d: `tauTip` choice on out-of-training `A` predictions; per box: tip in the lower-right quarter accepted (quarter
  edges inclusive), elsewhere in the box, outside, below `tauTip` each rejected and the whole image dropped; one
  prediction for two boxes, including two overlapping boxes sharing one confident point inside both (`tip-shared`,
  the whole image dropped, not one box), an unassigned confident tip and missing calibration labels drop the image;
  duplicates merged; figures (rates, per-rule counts, median and p95 px to the corner) against a hand computation;
  no `tauTip`, no v17 folder, no image kept: B not built (CV23, D27).
- T10a: CPU and GPU requirements in sync and hashed, Dockerfile static checks, `gpu_check` exit codes.
- T10 (slow, fixed seed): 2-iteration training on CPU from random weights on synthetic data produces a checkpoint;
  `train-run.json` lists the version, fold, held-out role, hyperparameters, manifest SHA-256, start, end and
  duration, the public sets and the pseudo files of the dataset manifest (empty without); `train_folds` trains and
  exports each requested fold, skips a complete consistent fold and retrains one whose manifest changed (A19).
- T11: CV6 and CV7 on fake detector outputs, boundaries (6.0 mm, `tau_low`, ties, same-class duplicates).
- T12: CV10 and CV11 on generated videos: arrival in window, false arrival, person occlusion, empty board.
- T13: intervals, breakdowns, tuning (including no feasible pair), CV8 boundaries, 0 judged darts, strict
  `frozen.json`, CV4 guard with split lock, CV9 and CV12 with compensated reference and photos without reference
  counted, CV7 unchanged by drift; tuning reads out-of-training predictions only (a `full` model's results refused);
  CV22 exact one-sided McNemar p-value against hand values (`b`, `c`) = (0, 0) gives 1, (5, 0) gives 0.03125
  (retained), (4, 0) gives 0.0625 (not retained), (10, 3) gives 0.0461 (retained), (9, 3) gives 0.0730 (not
  retained); sequence `sans` -> `A` -> `B`: A not significant then B tested against `sans`, A retained then B tested
  against A, B significant against `sans` but A not retained, B not built, `sans` alone; the Q5 gate (infeasible
  candidate never replaces a feasible retained version); different fold plan, `kosmoPartSha256` per fold or
  hyperparameters, or an eval result, refused.
- T13b: limits section (with the drift summary, the CV22 limits and the CV24 threshold-transfer limit), D12 verdict
  at the interval boundary, path list or campaign size, public sets section (slugs, licences, images, pseudo-label
  figures, drops per rule) and comparison section (one row per version, one row per McNemar step with `b`, `c`,
  `p`, decision, letter glosses), trainings section (count, list, durations, total), CV15 privacy scan (no session
  name, roles only).
- T14: CLI end to end on a synthetic chain (fake detector, fake trainer), `drift` then `dataset` refused while
  beyond, the 13 steps of the run chain in order, a not-built version exits 0 with its line and the chain goes on,
  a stale step exits non-zero with its step numbers, `compare` prints the retained version, the McNemar steps and
  the next step; `measure --set dev` uses the predictions and fold models, never a `full` model.
- T14b: every subcommand of `--help` appears in `training.md`, every command in `training.md` parses with the CLI,
  the "Trois entraînements" block holds the 13 steps in K5.6 order, each with a duration and a paste instruction.
- After D30 (each task file lists its cases):
  - T15a: ADR 0017 file present with its sections; training.md K5 marked suspended (doc test).
  - T15b: v3 golden round trip byte for byte; v2 golden and the v2 file written by the batch 1 tool (ab85842)
    migrate without loss; 50 seeded v2 files round-tripped; strict rejections (5 points, unknown source,
    `positionMs` on a photo control or missing on a video one, duplicate (dart, source), unsorted, non-judged dart,
    video control without video or entry position, 1025 controls); worst case under `MAX_BYTES` (CV25); `snap`:
    v2 segments migrate to `"manual"` x4, a `snap` entry misaligned with `null`, a fifth entry, an unknown value,
    `"manual"` on a control each rejected (CV25, CV31).
  - T15m: on T7 boards with ring wires and on bare colour edges, wire widths 1 to 5 px, noise, blur and perspective:
    snap from the starts within `SNAP_RADIUS_PX` lands within 0.25 px of the true crossing (rendered from
    `board.py` geometry) on the domain of D46 (wires at least 2 px, blur at most 0.5 px, noise at most 3), at least
    90 % of the starts snapping per case, median and max reported in px and mm (4080 x 3072 photos, 1080p video);
    noise 5 never ends on a wrong crossing; cases outside the domain only reported; a window with no crossing (plain sector, a single wire, a flat dark area) gives
    `unsnapped` with its reason; a start with two crossings in range (inner and outer double edge) is `unsnapped`
    with reason `ambiguous` and stays at the click, one with a single crossing in range snaps to it, the start that
    once ended 17 px away (noise 5) is refused, every snapped point is checked against the `board.py` truth (a
    converged wrong crossing is caught by the truth, not by the dispersion, T2), and the restart dispersion of a
    point between two crossings exceeds `SNAP_REPEAT_MAX_MM` (a failed restart counts as infinite); restarts on a clean crossing give a dispersion under 0.05
    mm; border and too-far cases; one board rendered with a seed and a style not used for tuning (other wire
    width and colours, other perspective) meets the same 0.25 px bound (T1); constants pinned once (CV31).
    `luma.py` (A33, S14): a JPEG decoded to the same luma as before the move (the T8b drift tests stay green
    unchanged); decompression bomb at both Pillow levels (error and warning, `Image.MAX_IMAGE_PIXELS` lowered by
    `monkeypatch` in the test only and checked restored), truncated JPEG, random bytes, size different from the
    expected size, file of exactly `MAX_IMAGE_BYTES` accepted and of `MAX_IMAGE_BYTES` + 1 refused (cap lowered by
    `monkeypatch`, never read past it): each gives `(None, reason)`, no exception; the T8b static check (no
    assignment of `MAX_IMAGE_PIXELS`, no `LOAD_TRUNCATED_IMAGES`) parametrized over `drift.py`, `luma.py` and
    `snap.py` (T15n adds `annotate/snapping.py`, T15i adds `diagnose.py`).
  - T15n: lazy import (`python3 -I -S -m kosmo_camera.annotate --help` without numpy; tool starts, "Recalage
    indisponible", calibration and recalibration clicks refused with 409, tips unchanged); assisted flow on the
    calibration and recalibration screens with a fake snapper (proposals, nearest-point replacement, Entrée saves
    only at 4 ready points, U, leave drops changes); `requirements-tool.txt` holds only numpy and Pillow at the
    `requirements-cpu.txt` versions with exactly two hashes each, one of them the cp313 hash of
    `requirements-cpu.txt`; `requirements-tool-test.txt` holds exactly pytest, iniconfig, packaging, pluggy and
    pygments with the `requirements-cpu.txt` pins and hashes; `kosmo_camera.synth` imports and renders a board with
    `av` unimportable; image cache bounded by `SNAP_CACHE_IMAGES` (CV16, CV31). `flow.click_target` tested as a
    pure function without a browser (U27): selected point wins; nearest positioned point within
    `CLICK_REPLACE_RADIUS_PX` replaced (at the radius: replaced; radius + 1: not); two points within the radius:
    the nearer; none within: first point without a position, order 1 to 4; every point positioned and none within:
    refused with its message. `save` below 4 ready points 409, file unchanged; repeated save (two `save` actions
    for the same recalibration item): the second 409 « Photo déjà enregistrée », one segment written (U29).
    Availability (A32): numpy or PIL missing (`ModuleNotFoundError` for those names only) gives "Recalage
    indisponible"; an error raised inside `snap.py` at import (a test module replacing it that raises
    `RuntimeError`, or `ModuleNotFoundError` for another name) is not hidden: it propagates. Decode failure (S14,
    real `luma.py` on a bomb, a truncated JPEG, random bytes and a size different from the listed size): the click
    stays where it was, `unsnapped` with reason `unreadable`, the response is 200 with the banner text « Photo
    illisible : le recalage est impossible pour cette photo », never an exception out of the server thread, and the
    next request is served. Doc test (A31) in `test_plan_docs.py`: `docs/rules/camera.md` CV16 holds the "After
    D39 and D43" sentence, ADR 0016 decision 2 holds "Amended: see section Assisted click (D43, D44)", plan rows 5
    and D5 hold "Amended by D43, D44"; each check fails when its line is removed (checked once by hand, noted, as
    T15a). The "tool and snapping tests"
    (`TOOL_TESTS`: `tests/test_snap.py`, `tests/test_luma.py`, `tests/test_annotate_snapping.py`, `tests/test_annotate_flow.py`,
    `tests/test_annotate_server.py`, `tests/test_annotate_static.py`, `tests/test_requirements.py`) also pass under
    Python 3.14 in a throwaway venv holding only the two tool files (D44; command in T15n "Done when").
  - T15c: sample on scripted sessions (ranks, last dart, reference photos, eval all, darts 55 to 60, voided and
    photo-less never sampled); `frame_at` on a generated video (exact frame, each missing reason); `frames.json`
    strict reader (cap, cap + 1); privacy of printed lines (CV26, CV27, CV15).
  - T15d: control flow (K, proposals from the previous item of the same source or the segment, none on the first
    video item, Entrée, H, U, leave, next item, cap 409, `snap` stored; the CV31 hide rule with no selection and
    no point `à cliquer` refused 409, a `save` naming an item that is no longer open refused 409, U27 click rule
    through `flow.click_target`, unreadable frame `unsnapped` `unreadable`), frame route serves only listed files and
    resists the T6a attacks, missing `--split` or `--frames` (CV25, CV16, CV31).
  - T15e: journey states and the "Instructions" table row by row, no instruction when the state is stale, info
    lines kept apart (D30 point 5), "Recalage indisponible" replacing calibration and control rows (tips row
    unchanged) when snapping is unavailable (D43), role per session with `--split` only (Q9, D38).
  - T15f, T15g, T15k (static): strings once, keys bound, unique banner, removed strings absent, T6b scan; T15f
    also: Entrée handler calls `preventDefault`, ignores `event.repeat` and `SAVE_GUARD_MS`, buttons `blur()`
    after click, inset `aria-label`, `SNAP_INSET_PX` 60 (U25, U26, U29, U31); T15k: « recalage » absent from the
    visual check strings (U30).
  - T15h, T15l: commands of K7.0 to K7.6 (K7.1b included) parse with their parsers; K7.1b is one command of three
    parts joined by `&&` (venv with `python3`, pip install of `requirements-tool.txt` with `--require-hashes
    --only-binary=:all:`, version print), the named file exists and is fully hashed, the printed line matches the
    file's versions; container flags kept (S10); T15l's « Recalage des
    clics » paragraph names the report section `diagnose.py` writes.
  - T15i: `T`, `E`, `E_seeded`, `R` on synthetic sessions, the periodic trap, peak ratio by hand, partial
    controls, `D` on rendered controls (clean, between two crossings, a failed restart counted infinite),
    `unsnapped` controls excluded and counted, unreadable control images counted with reason `unreadable`, the
    « Limites » sentence on `D` present in the report (A35, T2), `measure_photo` unchanged, report privacy (CV28,
    CV31, CV15).
  - T15j: mapping recovered and refused, `O`, `V`, `W`, each CV29 line at its boundary, both "non concluant" causes
    (`D` p95 just above and at `SNAP_REPEAT_MAX_MM`, 29 and 30 snapped dev controls), `R` never changes a verdict
    (CV29).
  - T15k: homography against known transforms, server route and stale diagnostic, photos served in dart order with
    `start`, `nextAbove` and "Photo i / n" in that order (U24, D42).

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
  - K5 (suspended, D30), paragraph written in `training.md` by T15a, then replaced by the K7 section (T15h):
    « Étape K5 suspendue le 15/11/2026 : la mesure de dérive n'est pas fiable (presque toutes les photos sortent
    au-delà du seuil, et le déplacement vu à l'œil à partir de la fléchette 57 de la session d'éval de 18h24
    n'apparaît pas). Ne recalibrez plus d'après le rapport de dérive. Les recalibrations déjà faites sont gardées.
    La suite est l'étape K7 (contrôle de calibration). »
- K7 (PR B batch 3, after D30; in plain French in `training.md`, section "Contrôle de calibration", T15h and T15l).
  Batch 3a (T15a to T15h, with T15m and T15n) is pushed first so Kosmo clicks while T15i to T15l are built:
  - K7.0 Dans votre clone : `git fetch origin`, puis `git switch feat/camera-feasibility-0b-1-detection` et
    `git pull`. (1 min)
  - K7.1 Reconstruire l'image depuis la racine du dépôt : `docker build -f tools/camera/Dockerfile -t
    kosmo-camera:gpu tools/camera`. (2 à 5 min)
  - K7.1b Une seule fois : préparer l'environnement du recalage dans WSL (il ne contient que numpy et Pillow, aux
    versions déjà utilisées dans l'image, vérifiées par empreinte), en collant cette seule commande depuis la
    racine du dépôt :
    `python3 -m venv ~/kosmo-tool-venv && ~/kosmo-tool-venv/bin/pip install --require-hashes --only-binary=:all: -r tools/camera/requirements-tool.txt && ~/kosmo-tool-venv/bin/python -c "import numpy, PIL; print('numpy', numpy.__version__, '- Pillow', PIL.__version__)"`.
    Elle se termine par la ligne `numpy 2.5.4 - Pillow 13.0.0`. Si WSL répond que `venv` manque :
    `sudo apt install python3-venv`, puis recoller la commande. L'outil se lance ensuite toujours avec
    `~/kosmo-tool-venv/bin/python` (K7.3). (2 à 5 min)
  - K7.2 Extraire les images vidéo prises environ 1 s avant chaque saisie (pas de carte graphique nécessaire) :
    `docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,size=2g -v ~/kosmo-data:/data:ro -v ~/kosmo-runs:/runs:rw kosmo-camera:gpu python -m kosmo_camera.video_frames --captures /data/captures --annotations /data/annotations --split /runs/split.json --out /runs/frames`.
    Elle affiche le nombre d'images extraites, la ligne « Images vidéo absentes : N » et les manquantes par
    raison ; une vidéo illisible ne l'arrête pas. Rien à coller. (2 à 5 min)
  - K7.3 Relancer l'outil d'annotation dans WSL, depuis `tools/camera/`, avec l'environnement de K7.1b :
    `~/kosmo-tool-venv/bin/python -m kosmo_camera.annotate --captures ~/kosmo-data/captures --annotations ~/kosmo-data/annotations --split ~/kosmo-runs/split.json --frames ~/kosmo-runs/frames`,
    puis ouvrir `http://127.0.0.1:8765`. La ligne en haut donne la seule consigne en cours. Sur la liste, la carte
    « Prochaine étape » a un bouton « Continuer » (ou Entrée) ; dans une session, appuyer sur K (« Contrôle de
    calibration »), depuis n'importe quel écran de la session, même « Terminé ». Vos clics servent à désigner le
    bon croisement de fils, pas à le viser au pixel : l'outil recale chaque point sur le croisement exact. Pour
    chaque photo, les 4 points de la photo précédente sont déjà proposés et recalés ; les 4 vignettes agrandies à
    droite montrent chaque point avec son état (« recalé », « non recalé », « caché », « à cliquer »). Si les 4
    sont sur le bon croisement : Entrée. Si un point est sur le mauvais croisement ou « à cliquer » : cliquer près
    du bon croisement (le point le plus proche est remplacé et recalé ; pour un point loin de votre clic,
    choisir d'abord le point avec les touches 1 à 4), puis Entrée. H seulement si une fléchette cache le point
    (touche 1 à 4 puis H ; une photo inutilisable : 1 H 2 H 3 H 4 H puis Entrée, elle compte comme cachée) ; U
    annule le dernier changement, ou revient à la photo précédente (son enregistrement est retiré jusqu'à
    Entrée) ; Échap pour quitter. Entrée enregistre toujours, même après un clic sur un bouton. Rien n'est enregistré avant Entrée
    (« Points prêts : n / 4 », puis « Enregistré à HH:MM:SS »). La première photo de chaque session part de la
    calibration de la session ; la première image vidéo de chaque session n'a pas de proposition : 4 clics. Après
    un déplacement de la cible, les points proposés partent sur un mauvais croisement ou restent « à cliquer » :
    les recliquer. Même travail ensuite sur les images vidéo. Environ 190 photos et 45 images vidéo (les nombres
    exacts s'affichent sur la carte de chaque session, « Contrôle photo : D / T ») : le plus souvent une seule
    touche par photo (environ 5 s), 4 clics sur la première image vidéo de chaque session (4 images), quelques
    clics après un déplacement : environ 20 à 30 min au total (au lieu de 1 h 15), en plusieurs fois si vous voulez (l'outil
    reprend où vous étiez). Un point « non recalé » est gardé à votre clic et compté dans le diagnostic. Sans
    `--split` ou sans `--frames`, l'outil affiche « Contrôle indisponible » ou « Images vidéo absentes » ; lancé
    avec `python3` au lieu de `~/kosmo-tool-venv/bin/python`, il affiche « Recalage indisponible » : relancer
    comme indiqué. Rien à coller.
  - K7.4 Lancer le diagnostic (pas de carte graphique nécessaire) :
    `docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,size=2g -v ~/kosmo-data:/data:ro -v ~/kosmo-runs:/runs:rw kosmo-camera:gpu python -m kosmo_camera.diagnose --captures /data/captures --annotations /data/annotations --split /runs/split.json --runs /runs --frames /runs/frames`.
    Elle affiche le chemin du rapport ; le rapport contient la section « Recalage des clics » (points recalés et
    non recalés, dispersion mesurée par la machine, cible 0,25 mm). (5 à 15 min)
  - K7.5 Coller dans le chat le contenu de `~/kosmo-runs/diagnostic/diagnostic-report.md` (des chiffres et des
    rôles « dev 1 », « eval 2 », aucun nom de session). Ne jamais coller `diagnostic.json` ni `frames.json`.
  - K7.6 Contrôle visuel : relancer l'outil comme en K7.3 en ajoutant
    `--diagnostic ~/kosmo-runs/diagnostic/diagnostic.json`, appuyer sur V. Les deux photos alternent ; touche 1
    alignement automatique, 2 alignement d'après vos clics, 3 aucun alignement ; Espace pour figer ; flèches pour changer
    de photo ; N pour la photo suivante au-delà de 0,5 mm ; l'outil s'ouvre sur la photo au plus grand écart et
    affiche « Photo i / n ». Dire dans le chat ce que vous voyez (par exemple « avec 1 l'image saute, avec 2 elle est immobile »).
    (10 min)
  - K7.C Checklist (ce que seul Kosmo peut vérifier, le JavaScript ne tourne pas dans le bac à sable) : une seule
    consigne affichée à la fois et jamais une consigne déjà faite ; chaque carte de session dit ce qui est fait et
    ce qui reste ; aucun « Dérive au-delà du seuil » ni « À recalibrer » ; l'écran de recalibration explique ce que
    fait une recalibration, sur le côté, et la photo reste entière à l'écran en 1080p sans faire défiler ; sur la
    liste, la carte « Prochaine étape » est celle de la session annoncée, la consigne ne cite aucune touche et le
    bouton « Continuer » ouvre l'étape annoncée ; Entrée lance « Continuer » seulement quand aucun autre bouton
    n'a le focus (après Tab sur un autre bouton, Entrée active ce bouton-là) ; en contrôle, les 4 points proposés
    apparaissent déjà recalés, chacun dans sa vignette agrandie avec son état en toutes lettres ; un clic à
    quelques pixels du bon croisement déplace le point le plus proche et le recale exactement sur le croisement
    (vignette à l'appui) ; un clic loin de tout croisement net donne « non recalé » ; Entrée enregistre seulement
    quand les 4 points sont prêts, même quand un bouton a le focus (après un clic sur « Point caché (H) », Entrée
    enregistre) ; Entrée gardée enfoncée ou pressée deux fois vite n'enregistre pas la photo suivante sans la
    voir ; les 4 vignettes, « Points prêts » et « Enregistrer (Entrée) » se voient ensemble sans faire défiler en
    1080p ; H sans point choisi ni point « à cliquer » affiche « Choisir le point à cacher : touche 1 à 4, puis
    H » et ne cache rien ; touches 1 à 4, H, U, Échap, la touche 0, la molette et K marchent, K aussi
    depuis « Terminé » ; « Points prêts : n / 4 » suit les changements et « Enregistré à HH:MM:SS » apparaît
    après Entrée ; après un clic puis Échap, puis K, la même photo revient avec les points proposés ; U sans
    changement revient à la photo précédente avec ses points ; la progression « Photos contrôlées » augmente ; la
    recalibration (R) propose les points en vigueur, recalés, et s'enregistre par Entrée ; lancé avec `python3`
    seul, l'outil s'ouvre et dit « Recalage indisponible » ; relancé sans `--split` ou sans `--frames`, l'outil
    dit « Contrôle indisponible » ou « Images vidéo absentes » et jamais « Contrôle terminé » ; en contrôle
    visuel, l'alternance, les touches 1, 2, 3, N, Espace et les flèches marchent, les flèches suivent l'ordre des
    fléchettes et « Photo i / n » suit le même ordre (après N ou à l'ouverture, i est le rang de la photo
    affichée), et les chiffres s'affichent.
- K5b (complete PR B branch, after T14b; commands in `training.md` "Run", "Trois entraînements" and "Dev loop"):
  - K5.5 `git pull`, rebuild the image (entry point `python -m kosmo_camera` now), run `gpu-check`.
  - K5.6 Three trainings with cross-validation (D21, D23, D25, D26, CV22 to CV24): the 13 numbered steps of
    "Approach, Three trainings, run chain", one copy-paste block in `training.md`, all in the run folder
    `~/kosmo-runs/chaine`, about 5 to 6 h unattended (8 trainings, 9 when B is retained). Step 1 prints the roles
    and judged darts per role, or the CV24 refusal (then stop and paste it). Step 4 prints the calibration
    thresholds and images kept or dropped; step 8 prints `tauTip`, the v17 acceptance rate, drops per rule and the
    median and p95 distance in px. "Version <A|B> non construite : <raison>" is not an error: go on with the next
    step. Step 12 prints the report path, each McNemar step (`b`, `c`, `p`, decision), "Version retenue : sans jeux
    publics|A|B" and "Étape 13 : à lancer" or "Étape 13 : inutile"; paste the comparison section (pseudo-label
    figures, trainings and their durations included). Any other non-zero exit stops the chain: paste its message;
    a rerun of the same step resumes (completed folds are kept when their inputs are unchanged).
  - K5.7 Dev report of the retained version: `tune` (on its out-of-training predictions) then `measure --set dev`
    (photo metrics from those predictions, events from the fold models); paste the dev report. The squad iterates
    on dev reports only (any change of training means a new chain), then commits `frozen.json` (the tuned pair and
    the SHA-256 of the retained version's `full` model).
- K6 (PR B) Run `measure --set eval` once, on the final model only (the `frozen.json` hash guard refuses any
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
T6d, T9p, T15a to T15n, T11, T12, then after P2 the retained T16 tasks and T9, T9b, T9o, T9c, T9d, T10, T13, T13b,
T14, T14b as revised; `Closes #42`). Batch 1 (T5b to T6d) is pushed on this branch before the
PR, without opening it (D19); no extra PR. The batch 1 hand-back states the full pytest count (as in PR A, T4).
- How to verify: K5.0 to K5.4 (from batch 1: image rebuilt, drift loop before any training), K5.5 to K5.7 (public
  data comparison, then dev loop), then K6.
  The tool steps state what Kosmo sees: an old v1 file opens unchanged, the first change saves it as v2 without
  losing a tip; the session list flags "À recalibrer à partir de la fléchette 59" for the 18h24 eval session; after
  recalibration and a new `drift`, the flag is gone; "Effacer" during a recalibration removes the last point and,
  with no point left, leaves without change.
- Versions table only if a pin changes in part B (none planned: drift uses numpy and Pillow, already pinned). The
  new `requirements-tool.txt` (T15n, D43) repeats the numpy and Pillow pins of `requirements-cpu.txt` for the WSL
  tool venv (cp313 and cp314 wheels, hashed): listed in the table with their PyPI pages, and the deviation "tool
  venv on Kosmo's WSL Python 3.14" stated next to the "Python 3.13" deviation; no new library. The new
  `requirements-tool-test.txt` (T15n, D44) repeats the pytest, iniconfig, packaging, pluggy and pygments pins of
  `requirements-cpu.txt` for the Python 3.14 test runs (sandbox and proposed CI step), listed in the same table.
- Justifications the issue asks for: input size and FP32 versus FP16 for the Pixel 7, then the confidence threshold
  from the dev report; plus the drift threshold (CV19, 1.0 mm, D18), the drift method (CV18), and the version
  retained among the three trainings with the figures of all three and the McNemar steps (CV22, D21, D23, D26);
  the cross-validation by session in plain French (two trainings per version, each dev dart predicted by a model
  that never saw it, final model on the whole dev set, number of trainings and their durations; CV24, D25).
- States (design review T1): the first real values of `tauCalHigh`, `tauCalLow` and `tauTip` come only from Kosmo's
  dev run (K5.6), none is chosen by the squad; "version non construite" for A or B is a valid outcome, shown with
  its reason, not a failure.
- Public datasets: the description lists the sets used (slug, licence, status) with their attribution lines as in
  `docs/camera/datasets.md` (CC BY 4.0, D23), and lists every mapping of the class map (slug, source category,
  target) so main and Kosmo confirm them once (T2).
- Calibration points on public images (Kosmo asked for the justification in the PR): the options of "Approach,
  Calibration points on public images", why (a) was chosen, the `rfdetr` 1.12.1 finding (no class mask, crowd boxes
  become background), the thresholds obtained and images kept or dropped, and the test that proves it (T9c).
- v17 pseudo-labels: `tauTip`, acceptance rates, median and p95 distance to the lower-right corner (T9d figures from
  Kosmo's pasted comparison section); the strict rules of D24, confirmed by Kosmo (D27), with images dropped per
  rule; the attribution lines with their modification mentions (D28).
- Kosmo's pasted numbers recorded in the PR: the last drift report (all under the threshold), the comparison
  section, then the dev report. After D30: the K5 figures that stopped the loop (Goal), the diagnostic report (K7.5)
  with its CV29 verdict, the CV30 gate result, and the per-photo CV9 measure of the eval set (ADR 0017). "How to
  verify" gains K7.0 to K7.6 (K7.1b included) and K7.C; K5.0 to K5.4 are listed as suspended. Justification of
  the assisted click (D39, D43): why the snapping dispersion, not Kosmo's hand, bounds the reference, and where the
  snapping runs with the alternatives of "Approach, Where the snapping runs". The description states that Kosmo's
  K7 steps (K7.2 and K7.4 in the container, K7.3 and K7.6 in the tool) are the check of the real container run and
  of the page, which the sandbox cannot run (no GPU, no browser, no real data; design review T4).
- Justifications after D30: why control clicks rather than trusting the measure; the decision rule fixed before the
  numbers (CV29); the correction chosen at P2 and its validation (CV30); D18 replaced (Q13).
- Capture proposal (only if the verdict says H2; an issue draft in French for Kosmo, no Capture code in this PR):
  options (a) photo taken a fixed delay after the tap, delay sized from the settling time of `W` (recommended: no
  change of the capture flow, one constant); (b) photo taken from the video frame just before the tap (no touch
  effect, but video resolution and compression); (c) zero-shutter-lag capture (`CAPTURE_MODE_ZERO_SHUTTER_LAG`,
  frame from the buffer before the tap; depends on device support); (d) entering darts without touching the camera
  phone (second phone or remote entry: protocol change). Kosmo chooses (Q13).
- Proposed owner changes (ADR 0017 is a `docs/adr/` file, not protected): add to the job `camera-tools` of
  `.github/workflows/android.yml`, after the existing test step, a Python 3.14 run of the tool and snapping tests
  (Kosmo, Q14, D44; he applies it with the other CI changes of PR B; the squad never edits `.github`). Why: Kosmo
  runs the tool and its snapping with his WSL Python 3.14 and only the cp314 wheels of `requirements-tool.txt`; the
  existing step runs on 3.13 with the full CPU set. `update-environment: false` keeps `python` on 3.13 for the
  earlier steps; the venv holds only the two tool files, so a tool or snapping import outside numpy and Pillow
  fails here. The `actions/setup-python` pin is the one the job already uses. Snippet for the description:

  ```yaml
      # Python 3.14: Kosmo's WSL interpreter for the annotation tool and its snapping (issue #42, Q14).
      - uses: actions/setup-python@6d4fd22b20465ccc0f7354476588e8df7c9ca0e0 # v7.1.0
        id: py314
        if: ${{ hashFiles('tools/camera/requirements-tool.txt') != '' }}
        with:
          python-version: "3.14"
          update-environment: false

      - name: Tool and snapping tests on Python 3.14 (tool requirements only, hashes required, wheels only)
        if: ${{ hashFiles('tools/camera/requirements-tool.txt') != '' }}
        working-directory: tools/camera
        env:
          PY314: ${{ steps.py314.outputs.python-path }}
        run: |
          "$PY314" -m venv "$RUNNER_TEMP/tool-venv"
          "$RUNNER_TEMP/tool-venv/bin/pip" install --require-hashes --only-binary=:all: \
            -r requirements-tool.txt -r requirements-tool-test.txt
          "$RUNNER_TEMP/tool-venv/bin/python" -m pytest -q -m "not slow" \
            tests/test_snap.py tests/test_luma.py tests/test_annotate_snapping.py tests/test_annotate_flow.py \
            tests/test_annotate_server.py tests/test_annotate_static.py tests/test_requirements.py
  ```

  The file list is `TOOL_TESTS` of root "Test plan" (T15n entry); developer-fix copies it from there and checks
  that the snippet matches the command T15n runs in the sandbox.

## ADR 0017 text

T15a copies this text into `docs/adr/0017-per-image-calibration.md` (English, like ADR 0016):

- Title: "ADR 0017: Per-image calibration and automatic recalibration in the game app (camera phases 1 to 3)".
- Status: Proposed (Kosmo's requirement of 2026-11-15, D30 point 6). Date: 2026-11-15.
- Context: in phase 0b-1 the phone on its tripod moved during Kosmo's captures (D15). A calibration clicked once per
  session and reused was wrong on part of the photos, and a drift measure against one reference photo per segment
  was not reliable (D30). In real games the tripod is bumped, games are interrupted and resumed, and the player
  must not have to manage the camera.
- Decision: in phases 1 to 3 the game app (1) calibrates the board on every image it scores, never reusing a
  calibration from another image as truth; (2) detects a displacement of the board in the image (tripod bumped,
  phone moved, game interrupted then resumed) and recalibrates automatically, with no action from Kosmo; the game
  state (configuration and ordered darts) is kept across a recalibration; (3) when an image cannot be calibrated,
  its dart is not scored automatically: it is flagged "à vérifier" for manual entry, never guessed.
- Verification: each phase that ships camera scoring is tested with deliberate tripod displacements: a small
  nudge, a rotation of the phone on the tripod, a full repositioning of the tripod, and a game interrupted then
  resumed after moving the tripod; for each, darts after the move are scored with the same criteria as before it.
- Phase 0b-1: its report measures the automatic calibration of every eval photo against Kosmo's control clicks
  (CV9 per photo, CV25), not only one photo per volley.
- Alternatives rejected: one calibration at the start of a game (breaks at the first bump, the 0b-1 finding);
  asking the player to recalibrate (Kosmo stands at the board, phone in one hand: no camera handling during a
  game); a drift threshold with compensation from a reference image (not reliable in 0b-1, D30).
- Consequences: the detector must find the calibration points on every image (already a model class, CV2); the
  cost per image includes calibration (measured in 0b-2 on the Pixel 7); test protocols of phases 1 to 3 include
  the displacement cases above.
- ADR 0016 status line (A28; T15a writes it as the first line of the ADR 0016 section "Tripod drift", and
  `test_plan_docs.py` asserts it): "Status: Not validated and suspended on 2026-11-15 (D30): compensation,
  threshold and guard are replaced at plan update P2; see ADR 0017 and rules CV18 to CV19, CV30."

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
then the branch is pushed and Kosmo runs K5.0 to K5.4 while batch 2 (T9p to T14b) is built:

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
| T9p | Public import: strict class map reader, CV21 validation of untrusted COCO exports, `datasets.md` table and doc test | `42-camera-feasibility-0b-1/T9p-public-import.md` | T1 | sonnet | Strict reader and untrusted-input rules fully specified in CV21; split out of T9 (A18) |
| T9 | Fold plan and run chain (`chain.py`), COCO dataset builder per fold, drift guard, versions none/A/B manifest | `42-camera-feasibility-0b-1/T9-dataset.md` | T3, T4b, T8b, T9p | sonnet | Specified conversion, CV4, CV22, CV24 and drift guards, tested on synthetic data; local regenerable files, no migration |
| T9b | Public class map and `datasets.md` rows from Kosmo's messages (D23, D28) | `42-camera-feasibility-0b-1/T9b-public-maps.md` | T9p | haiku | Values copied from the plan (three map entries, six rows with Kosmo's texts), no design choice left |
| T9o | Out-of-training predictions of the cross-validation (`predictions.py`, `predict`) | `42-camera-feasibility-0b-1/T9o-oof-predictions.md` | T3, T9 | sonnet | Fold-to-model rule of CV24 and a strict local file, fake detector tests |
| T9c | Calibration-point pseudo-labels of public images with M0, pseudo file, dataset version A | `42-camera-feasibility-0b-1/T9c-pseudo-cal.md` | T3, T8, T8b, T9, T9o | sonnet | Thresholds, band, clockwise and drop rules fully specified in CV23 and Approach; fake detector tests |
| T9d | Constrained tip pseudo-labels of v17 with MA, figures, dataset version B | `42-camera-feasibility-0b-1/T9d-pseudo-tip.md` | T9c | sonnet | Box constraint, drop rules and figures specified by Kosmo (D23, D27) and CV23; same patterns as T9c |
| T10 | Training per fold, export, resume and durations | `42-camera-feasibility-0b-1/T10-training.md` | T3, T9, T10a | sonnet | Wrapper around a library training call with given parameters and tests |
| T11 | Per-dart decision | `42-camera-feasibility-0b-1/T11-decide.md` | T3, T8 | sonnet | Rules CV5 to CV7 specified, fake detector tests |
| T12 | Arrival and empty-board events on video | `42-camera-feasibility-0b-1/T12-events.md` | T11 | sonnet | Algorithm and rules CV10, CV11 specified |
| T13 | Metrics, tuning on out-of-training predictions, frozen thresholds, eval guard, McNemar selection | `42-camera-feasibility-0b-1/T13-metrics-report.md` | T8b, T9o, T12 | sonnet | Statistics, strict reader and the exact test of CV22 fully specified with hand values |
| T13b | Preliminary report (public sets, pseudo-label figures, McNemar comparison, trainings and durations) | `42-camera-feasibility-0b-1/T13b-report.md` | T13, T9d | sonnet | Report content and verdict rule D12 fully specified |
| T14 | CLI: run chain of 13 steps (`dataset`, `train`, `predict`, `pseudo-cal`, `pseudo-tip`, `compare`), `drift`, `tune`, `measure`, `export`, entry point | `42-camera-feasibility-0b-1/T14-cli-docs.md` | T6d, T9d, T10, T13b | sonnet | Wiring of existing pieces with end-to-end tests |
| T14b | `training.md` ("Trois entraînements" numbered block, dev loop, eval run) and report template | `42-camera-feasibility-0b-1/T14b-training-doc.md` | T14 | haiku | Documentation copied from this plan and the T14 `--help`, no design choice; split out of T14 (size) |

Part B, batch 3 (D30, absolute priority; T9, T9b, T9o, T9c, T9d, T10, T13, T13b, T14, T14b on hold until plan update
P2; T9p done at 474ebdc):

| Id | Title | Task file | Depends on | Model | Why this model |
|---|---|---|---|---|---|
| T15a | ADR 0017, ADR 0016 "Tripod drift" status line, K5 suspended, plan doc test | `42-camera-feasibility-0b-1/T15a-adr-per-image-calibration.md` | - | haiku | Text copied from "ADR 0017 text" and "How Kosmo verifies", no design choice |
| T15b | Annotation format v3 (control clicks, `snap` status on controls and segments), lossless v2 migration, doc | `42-camera-feasibility-0b-1/T15b-annotation-format-v3.md` | T5b | opus | Changed stored format with its migration (case 1) on Kosmo's real files |
| T15m | Snapping algorithm `snap.py` (sub-pixel wire crossing, "non recalé" reasons, restart dispersion), shared decoder `luma.py` (D45), ring wires in T7 boards | `42-camera-feasibility-0b-1/T15m-snap-algorithm.md` | T7 | opus | Rule still open after design review (case 3): detector, failure tests and thresholds of the snap are outlined, not specified, and they bound the reference truth |
| T15c | Control sample, roles, video frame extraction and `frames.json` | `42-camera-feasibility-0b-1/T15c-control-sample-frames.md` | T15b, T4b | sonnet | Sample and frame rules fully specified in CV26 and CV27, generated-video tests |
| T15n | Tool snapping: tool venv requirements, lazy import, image cache, assisted calibration and recalibration flow with the click, hide and save rules (D45), ADR 0016 "Assisted click" | `42-camera-feasibility-0b-1/T15n-tool-snapping.md` | T15b, T15m, T6c | sonnet | Wiring and flow rules specified in CV31 and "Screens and states", fake snapper tests, same patterns as T6c |
| T15d | Tool: control flow and server (options, proposals, frame route, cap) | `42-camera-feasibility-0b-1/T15d-control-flow.md` | T15b, T15c, T15n | sonnet | Flow and security rules specified, reuses the T15n assisted flow |
| T15e | Tool: journey state, role per session, the single instruction ("Recalage indisponible" row included) | `42-camera-feasibility-0b-1/T15e-journey-state.md` | T15d | sonnet | State holder from the "Instructions" table, Python tests |
| T15f | Tool page: assisted point screen (insets, statuses, keys) for control, calibration and recalibration | `42-camera-feasibility-0b-1/T15f-control-page.md` | T15d | sonnet | Page from a specified screen and strings, same patterns as T6b |
| T15g | Tool page: session cards with roles, focus card "Continuer" (Enter rule U23), instruction line, recalibration explained | `42-camera-feasibility-0b-1/T15g-journey-page.md` | T15e, T15f | sonnet | Page from specified screens and strings |
| T15h | `training.md`: K7.0 to K7.3 with K7.1b, checklist K7.C, K5 retired | `42-camera-feasibility-0b-1/T15h-control-steps-doc.md` | T15c, T15g, T15n | haiku | Documentation copied from this plan; doc test moved |
| - | Push batch 3a (no PR), main tells Kosmo: K7.0 to K7.3 | - | T15h | - | - |
| T15i | Diagnosis, photo side: `T`, `E`, `E_seeded`, `R`, `D`, points not snapped, error location, `diagnostic.json` | `42-camera-feasibility-0b-1/T15i-diagnose-photos.md` | T15c, T8b, T15m | sonnet | Every quantity defined in CV28 and CV31 on top of T8, T8b and T15m, synthetic tests |
| T15j | Diagnosis, video side (`M`, `O`, `V`, `W`) and CV29 verdict | `42-camera-feasibility-0b-1/T15j-diagnose-video-verdict.md` | T15i, T15c | opus | Algorithm with an open design (the photo to video mapping, its validity test, video registration) |
| T15k | Tool page: visual check in dart order (U24), homography in Python | `42-camera-feasibility-0b-1/T15k-visual-check-page.md` | T15i, T15g | sonnet | Screen and route from a specified layout, textbook 4-point homography |
| T15l | `training.md`: K7.4 to K7.6 | `42-camera-feasibility-0b-1/T15l-diagnosis-steps-doc.md` | T15h, T15j, T15k | haiku | Documentation copied from this plan; doc test extended |
| - | Push batch 3b (no PR), main tells Kosmo: K7.4 to K7.6 | - | T15l | - | - |
| T11 | Per-dart decision (unchanged row above) | `42-camera-feasibility-0b-1/T11-decide.md` | T3, T8 | sonnet | Not blocked by D30 |
| T12 | Arrival and empty-board events (unchanged row above) | `42-camera-feasibility-0b-1/T12-events.md` | T11 | sonnet | Not blocked by D30 |
| - | Plan update P2 (developer-plan) from Kosmo's pasted diagnosis: retained T16 tasks, held tasks revised, Q13 | - | K7.5 | - | - |
| T16a | CONDITIONAL (H1): robust registration, validated per CV30 | `42-camera-feasibility-0b-1/T16a-robust-registration.md` | T15j, P2 | opus | Algorithm with an open design, chosen from the diagnosis |
| T16b | CONDITIONAL (photos moved): per-photo calibration in the label build, CV19 replaced | `42-camera-feasibility-0b-1/T16b-per-photo-calibration.md` | T15j, T16a if H1, P2 | sonnet | Source order fixed at P2 wired into the label build |
| T16c | CONDITIONAL (no validated automatic calibration): assisted manual calibration of every dev photo | `42-camera-feasibility-0b-1/T16c-assisted-calibration-all-dev.md` | T16b, T15g, P2 | sonnet | Extra sample option and progress string on the T15 patterns |

28 tasks (8 in part A, 20 in part B), two `opus` tasks (T5, T5b). Execution order: part A in table order, PR A, then
part B in table order, one task at a time (D11, D19): batch 1 T5b, T7, T8, T8b, T6c, T6d, push (done, 7a6f5b5),
then batch 2 T9p, T9, T9b, T9o, T9c, T9d, T10, T11, T12, T13, T13b, T14, T14b. T9b no longer waits for Kosmo (his
class names and attributions are in D23 and D28). Q4 and Q5 are decided (D33, D34): the current T9d input and
T13 gate stay.
Each task ends green on the
CI command `python -m pytest -q -m "not slow"` in `tools/camera`, so a pushed partial branch keeps the CI green. Push
of batch 1 only after main confirms the code-phase approval of its head; batch 2 developers fetch and realign on the
remote branch first and never rewrite pushed commits. PR B opens after T14b. K1 to K3 ran on PR A, K4 during part B, K5 from batch 1, K5b and K6 from the complete branch.

After D30 and D43 (supersedes the batch 2 order above): 28 + 14 batch 3 tasks + 3 conditional = 45 task files;
`opus` tasks: T5, T5b (done), T15b, T15m, T15j, plus T16a if built (6 at most, 3 still to build for sure). Execution order, one
task at a time: T9p (done, 474ebdc, to review first), then T15a, T15b, T15m, T15c, T15n, T15d, T15e, T15f, T15g,
T15h, push batch 3a after main confirms the code-phase approval of its head (Kosmo does K7.0 to K7.3 with K7.1b
once, about 20 to 30 min of clicks instead of 1 h 15, D39; Q14 is decided, D44, so nothing in batch 3a waits for
Kosmo),
then T15i, T15j, T15k, T15l, push batch 3b (Kosmo does K7.4 to K7.6 and pastes the report), then T11 and T12 while
Kosmo works, then plan update P2 on the diagnosis (Kosmo reviews it before any code), then the retained T16 tasks
and the held tasks as revised at P2. T9b does not depend on labels but stays on hold by Kosmo's priority (D30).
Labels or calibration feed T9, T9o, T9c, T9d, T10, T13, T13b; T14 and T14b chain and document them.

## Decisions

| Id | Decision | Rules |
|---|---|---|
| Py | Python 3.13 everywhere (sandbox, image, CI); deviation from "latest stable" because `rfdetr` and `litert-torch` do not install together on 3.14. `download-r2.pytorch.org` allowlisted. | - |
| B1 | (Issue) RF-DETR Nano first; keypoint model only on Kosmo's decision after a report. | - |
| B2 | (Issue) LiteRT only, measures on the `.tflite` with `ai-edge-litert`; ONNX Runtime only as fallback on Kosmo's decision. | CV4 |
| B4 | (Issue) Kosmo clicks tips and the 4 calibration points once per session; amended by D15: once per calibration segment, a new segment where the drift measure asks for it. Amended by D30: segments only where a move is visible, plus control clicks on sampled photos (CV25, CV26). | CV2, CV12, CV20, CV25 |
| B5 | Superseded by K0. (Issue) Existing captures are dev; new dedicated sessions (>= 500 darts) are eval. | - |
| K0 | Kosmo, 2026-11-15: no capture campaign before a first result; existing captures split about 70/30 by session (block mode, "more optimistic", if too coarse); preliminary report with limits; path proposal or targeted campaign into `captures-eval/`; annotation tool first. Supersedes B5, D1, P2. | CV3, CV11, CV17 |
| B6 | (Issue) >= 95 % correct without flag and <= 1 % unflagged errors. | CV8 |
| 1 | Each dart was entered right after its throw: arrival windows apply to every capture. | CV10 |
| 2 | Removal criterion "empty board recognised before Next volley"; protocol: press within 3 s after leaving the field. Applies to the `captures-eval/` campaign only (K0). | CV11 |
| 3 | Nothing typed: tripod group and lighting derived from calibration and luminance, zoom from `truth.json`. | CV14 |
| 4 | An off-board miss is correct if nothing is announced on the board for that dart. | CV7 |
| 5 | Annotation tool: stdlib only, run in WSL outside the container, `127.0.0.1` only, no external resource; writes in `~/kosmo-data/annotations/`, captures read-only. Amended by D43, D44: `snap.py` and `luma.py` (numpy, Pillow) are imported lazily from the WSL tool venv `~/kosmo-tool-venv`; without it the tool starts and snapping is unavailable. | CV16 |
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
| D5 | Annotation tool UI in French only (it is for Kosmo); started with `python3`. Amended by D43, D44: started with `~/kosmo-tool-venv/bin/python` for the assisted click (K7.1b); plain `python3` still starts it without snapping. | CV16 |
| D6 | CUDA build `cu130` for the GPU image. | - |
| D7 | Design review S5: the container keeps uid 0 inside, no `USER`. With rootless Docker, container root maps to Kosmo's own unprivileged uid; a non-root `USER` maps to a subordinate uid that cannot write `~/kosmo-runs` without changing its permissions. Root inside has no extra power (`--cap-drop ALL`, `no-new-privileges`, `--read-only`, `--network none`). Accepted by the security reviewer at the design review of 1377af2. | - |
| D8 | Pixel coordinates in stored JPEG pixel order, EXIF orientation ignored everywhere; the server checks clicks against the real JPEG size (U3). | CV16 |
| D9 | Constants in `defaults.json` only, read by `config.py` (A1). | - |
| D10 | Requirements pinned with hashes, installed with `--require-hashes` (S6). | - |
| D11 | Kosmo, 2026-11-15 (P3): two PRs. PR A: T1, T4, T4b, T5, T6a, T6b, T10a, T2 (T10a taken out of T10: image and `gpu_check`, depends on T1 only), so Kosmo builds the image and checks the GPU while he annotates; `docs/camera/datasets.md` ships in PR A so licences are checked during part B. PR B: the rest, from a new branch, T10 reduced to training. | - |
| D13 | Kosmo, 2026-11-15: sessions with no judged dart (CV5) are ignored by the split: in no set, not counted for "at least 2 sessions per set". Tested in T4b; the annotation tool lists such a session as "Ignorée" (design review U9, T6a). | CV3, CV16 |
| D14 | Design review A5: two-step image. PR A delivers the image with `gpu_check` only (no entry point); PR B adds training and the `python -m kosmo_camera` entry point, and Kosmo rebuilds it (K5). Recorded in the ADR (T1). | - |
| D12 | Verdict per threshold: "clearly below" when the whole 95 % interval misses the target; else "close or above" (preliminary report, K0). | CV8 to CV11 |
| D15 | Kosmo, 2026-11-15 (tripod drift found after K2): in PR B, before training: drift measure per photo against the calibration photo of its segment, in px and mm through the homography, report per session (max drift, darts beyond a threshold tied to `CAL_MAX_ERROR_MM`); sub-threshold drift compensated in the `cal1`-`cal4` labels and the mm measures; annotation format v2 with calibration segments (v1 reader kept, lossless migration); tool action "Recalibrer à partir de cette fléchette" and session list flags from the drift measure; order measure, recalibrate, measure again (all under the threshold), then training; no session excluded since CV7 does not use the clicked calibration. Squad's design: phase correlation with numpy FFT (JPEG decoding by Pillow, already pinned), flag file `drift.json` local like the split file, ordering enforced by a guard in `dataset` and `measure` with an explicit `--accept-drift` override recorded in the run. Threshold value: D18. Amended by D30: the "few pixels" were screen pixels, the true drift is unknown; measure not validated, compensation and guard suspended (CV18, CV19 status). | CV9, CV12, CV14 to CV16, CV18 to CV20 |
| D16 | T3 result (a9576aa): export works with `litert-torch` 0.10.0; logits `[1,300,6]` with a trailing no-object slot; category ids 0-based in `CLASS_NAMES` order; squash resize to 384x384 (no letterbox). T9, T10 and T11 rely on this contract. | CV4 |
| D17 | Design review of 5c4d06f: PR B records format v2 and the drift decisions in two sections of ADR 0016 (T5b, T8b; A13); caps `MAX_SEGMENTS` = 64 and `drift_file.MAX_BYTES` = 1 MiB, surfaced in the tool as "Mesure de dérive illisible : <cause>" (S9, A14); one `segment_for` (A15); `DRIFT_MAX_MM` read from config in every test, pinned once in `test_config.py` (A16, T1); Pillow decompression bomb, truncated or mismatched photos become a status, `Image.MAX_IMAGE_PIXELS` kept (S8); stale flags stay shown with "(mesure périmée)" (U7); picker marker, old points as hollow labelled crosses, recalibration button on its own row (U8 to U10); per-candidate threshold table in the drift report (U11). | CV16, CV18 to CV20 |
| D18 | Kosmo, 2026-11-15 (Q1): « Seuil de dérive : B, 1,0 mm. » `DRIFT_MAX_MM` = 1.0 in `defaults.json`, still one named constant pinned only in `test_config.py`; the "Seuils candidats" table stays in the drift report for information. Suspended by D30: compensation under the threshold is not applied; its replacement is decided at P2 (Q13). | CV19 |
| D19 | Kosmo, 2026-11-15: « Plan de la PR B validé, avec un ajustement d'ordre : réalise d'abord T5b, T8, T8b, T6c et T6d, pousse la branche et préviens-moi dès qu'ils sont faits. Je lance alors la mesure de dérive et je recalibre depuis la branche, pendant que tu continues T9 à T14. Pas de PR supplémentaire. » Batch 1 = T5b, T7 (needed by T8), T8, T8b, T6c, T6d; then push of the PR B branch without a PR and main tells Kosmo; batch 2 = T9 to T14 (and T9b). Batch 1 runs `drift` as `python -m kosmo_camera.drift` in the container (no entry point before T14) and T8b adds `config/` to the image; T6d documents the batch 1 steps in `training.md`. | CV18 to CV20 |
| D20 | Kosmo, 2026-11-15: « Jeux publics : acceptés les jeux affichant la licence CC BY 4.0 sur Roboflow Universe : dartsdetectfulldart (SchoolPXL) : https://universe.roboflow.com/schoolpxl/dartsdetectfulldart ; dart tip detection (PJATK) : https://universe.roboflow.com/pjatk-ni0m7/dart-tip-detection ; mat24_dartsv2 (school) : https://universe.roboflow.com/school-asc0d/mat24_dartsv2 ; dart_detection3 (DartsKEY01) : https://universe.roboflow.com/dartskey01/dart_detection3 ; dart-tip-detection-6d3mw (DartsDetector) : https://universe.roboflow.com/dartsdetector/dart-tip-detection-6d3mw, seulement si sa page affiche CC BY 4.0 au téléchargement. Exclu : bullseyebot (Hugging Face), aucune licence. Toute décision antérieure contraire est remplacée par celle-ci. docs/camera/datasets.md : une ligne par jeu avec lien direct, licence, attribution exacte et statut. Je télécharge ces jeux au format COCO JSON dans ~/kosmo-data/public/<slug>/ et je te transmets la liste des catégories de chaque jeu pour la table de correspondance (T9). » Supersedes decision 8 and the search rows of `datasets.md` (P1). Replaced by D23 where they differ (set list and statuses, slug = folder name, class map, use of the sets); still in force: CC BY 4.0 only, bullseyebot excluded, one `datasets.md` row per set, committed map. | CV15, CV21 |
| D21 | Kosmo, 2026-11-15: « L'apport des jeux publics est mesuré, pas supposé : entraînement avec et sans jeux publics, comparaison sur dev uniquement (jamais eval). La meilleure version est retenue, et le choix et ses chiffres figurent dans le rapport. » Squad's design: same seed and Kosmo part, public images in train only, comparison on the dev photos of the shared valid subset (the dev photos not used for training by either version), selection rule CV22, `compare` subcommand (T13, T13b, T14); eval run once on the retained version (K6). Comparison set replaced by D25 (out-of-training predictions of the cross-validation), selection rule by D26. | CV4, CV22 |
| D22 | Design review of cf648af: downloaded public files are untrusted input, validated as in CV21 (bare `file_name` inside its subset folder, no symlink, byte caps `MAX_JSON_BYTES` 64 MiB and `MAX_IMAGE_BYTES` 32 MiB, `Image.MAX_IMAGE_PIXELS` kept, finite in-image boxes, unique and valid ids; bad image dropped and counted, bad JSON refuses the set; S10); accepted rows carry the licence page date, attribution copied from the page, never invented (S11); ADR section "Public data and version selection" (A17); CV22 overlap fails closed with a visible French message until Q2 (T1; superseded by D26, no overlap branch any more); PR lists every `tip`/`calN` mapping (T2); step-order test (T3); batch 1 hand-back with the full pytest count (T4). | CV21, CV22 |
| D23 | Kosmo, 2026-11-15 (complement for PR B, replaces point 3 of his previous message and D20 where they differ; verbatim, line breaks flattened): « Complément de Kosmo pour la PR B : jeux publics (remplace le point 3 de mon message précédent ; concerne T9 et T10). Kosmo a téléchargé les jeux au format COCO JSON dans ~/kosmo-data/public/<dossier>/ (lecture seule). Licences CC BY 4.0 vérifiées sur les pages sources. Taille des boîtes analysée (médiane rapportée à la largeur d'image) : seule une boîte centrée sur la pointe est exploitable telle quelle. 1. Retenus (datasets.md : accepted) - dart-detection3-v1i-coco : DartsKEY01, https://universe.roboflow.com/dartskey01/dart_detection3, CC BY 4.0. Récupéré via un fork Roboflow de Kosmo (aucune version publiée), version brute sans prétraitement ni augmentation ; attribution à DartsKEY01. Classe dart_tip -> tip. 1847 annotations, boîte médiane 1,0 % de la largeur (24 px, images d'environ 2700 px). - mat24-dartsv2-v2i-coco : school, https://universe.roboflow.com/school-asc0d/mat24_dartsv2, CC BY 4.0. Classe Dart-Tip -> tip (127 annotations, boîte médiane 2,1 %). Classe Dart (fléchette entière) ignorée. 2. Conditionnel (datasets.md : conditional, motif et méthode indiqués) - dart-tip-detection-v17i-coco : DartsDetector, https://universe.roboflow.com/dartsdetector/dart-tip-detection-6d3mw, CC BY 4.0, 1108 images, 2100 boîtes dart_tip à 14,9 % de la largeur. Constat de Kosmo : chaque boîte entoure la fléchette entière, toujours du même côté et sous un angle proche ; la pointe est presque toujours dans le coin inférieur droit. Le coin brut n'est PAS accepté comme pointe (erreur de plusieurs px, environ 1 mm par px à cette résolution). Utilisable uniquement par pseudo-étiquetage contraint (point 4). 3. Rejetés (datasets.md : rejected, motif) - dart-tip-detection-v1i-coco (PJATK, CC BY 4.0) : boîtes à 11,2 %, ne localisent pas la pointe. - dartsdetectfulldart-v1i-coco (SchoolPXL, CC BY 4.0) : dart = fléchette entière (24 %), db/sb hors de notre convention, 41 images. - bullseyebot (Hugging Face) : aucune licence. 4. Règles pour T9 et T10 - Jeux retenus : la pointe est le centre de la boîte d'origine ; la boîte est reconstruite avec BOX_FRACTION, comme pour les photos de Kosmo. - Points de calibration : les images publiques n'ont aucune étiquette cal1 à cal4. Elles ne doivent pas apprendre au modèle que ces points sont du fond. Traite-le explicitement et justifie le choix dans la PR (par exemple pseudo-étiquetage des points de calibration par le modèle entraîné sans jeux publics, avec seuil de confiance, ou toute autre solution argumentée). - Entraînements à comparer : - Sans jeux publics : photos dev de Kosmo seules. - A : photos dev de Kosmo + dart-detection3 + mat24 (Dart-Tip). - B : A + pointes v17 obtenues par pseudo-étiquetage contraint avec le modèle A : pour chaque boîte d'origine, la pointe prédite doit être dans la boîte, dans son quart inférieur droit, avec une confiance au-dessus d'un seuil justifié (choisi sur dev). Sinon l'annotation est écartée ; une image dont une boîte est écartée est écartée entière (aucune pointe non étiquetée laissée en fond). Rapport chiffré : taux d'acceptation, distance médiane et p95 (en px) entre la pointe retenue et le coin inférieur droit de la boîte. - Comparaison sur dev uniquement, jamais eval. La meilleure version est retenue, avec le choix et les chiffres des trois entraînements dans le rapport. » Squad's design: CV21 targets, CV22 for three versions, CV23 (calibration option (a) chosen and justified in "Approach"), tasks T9b (reshaped), T9c, T9d. | CV15, CV21 to CV23 |
| D24 | Squad's design for D23 (plan change of 2026-11-15, for design review; stricter than the literal text, never looser, so main may ask Kosmo to confirm): mat24 `Dart` is ignored as a label but an image whose `Dart` box contains no `tip` centre is dropped (`whole-dart-checked`); in v17 an image is also dropped when one prediction serves two boxes or a confident tip is assigned to no box; a category with no annotation (Roboflow supercategory) is ignored without a map entry, a mapped name absent from the set refuses it; pseudo-label thresholds use `PSEUDO_MAX_MM` 2.0 mm, 99 % precision on at least 30 dev predictions; calibration labels on every public image of A and B come from M0, including the v17 images of B. The three image-drop rules are confirmed by Kosmo (D27); M0 and MA are now the `full` models and the thresholds come from out-of-training predictions (D25); the source of the v17 calibration labels is Q4. | CV21, CV23 |
| D25 | Kosmo, 2026-11-15 (answer 0, priority over Q2; verbatim, line breaks flattened): « 0. Correction de méthode, prioritaire sur Q2 : aucune comparaison de versions ni aucun réglage de seuil (tau_low, tau_sure, seuils de pseudo-étiquetage) ne se fait sur des images vues à l'entraînement. Les 2 sessions dev servent de validation croisée par session : - pour chaque version comparée : entraînement sur la session dev 1 (+ jeux publics de la version), prédiction sur la session dev 2, puis l'inverse ; chaque fléchette dev obtient une prédiction hors entraînement ; - ces prédictions hors entraînement servent à comparer les versions, à régler tau_low/tau_sure (tune.py) et les seuils de pseudo-étiquetage ; - le modèle final est réentraîné sur tout le jeu dev, pour la seule version retenue, avec les mêmes hyperparamètres ; frozen.json garde les seuils réglés hors entraînement et le SHA-256 de ce modèle final ; - le pseudo-étiquetage v17 et celui des points de calibration utilisent le modèle A entraîné sur tout le jeu dev (ces images ne sont jamais mesurées) ; - les sessions sont désignées par leur rôle dans le découpage verrouillé, jamais par leur nom ; - le rapport indique le nombre d'entraînements et leur durée. Mets à jour CV4, le plan et les tâches T10, T13, T13b, T14. » Squad's design: CV24 and "Approach, Cross-validation by session" and "Three trainings, run chain" (roles by CV3 key order; inner valid subset from the training sessions only; `full` models of `sans` and `A` serve pseudo-labelling and are reused as final models when retained, same hyperparameters, so only B's `full` model is conditional; 8 or 9 trainings; fail closed for block mode, a dev session count other than 2 or a session under 50 judged darts); CV4, CV15, CV22, CV23 amended. Reading of « le modèle A » for calibration points: A cannot label its own training images, so calibration labels come from the `sans` `full` model (M0) and v17 tips from the `A` `full` model (MA); the v17 calibration labels are Q4. | CV4, CV15, CV22 to CV24 |
| D26 | Kosmo, 2026-11-15 (Q2, verbatim): « 1. Q2 : comparaison appariée sur les mêmes fléchettes dev hors entraînement (test de McNemar exact, unilatéral, alpha 0,05), dans l'ordre sans -> A -> B : une version plus complexe n'est retenue que si elle fait significativement mieux que la version retenue jusque-là ; sinon la plus simple reste. Les chiffres des trois versions et les p-values figurent dans le rapport. » Replaces the CV22 ranking and its fail-closed overlap branch (rule and test replaced together in T13, design review T3). Squad's design: "mieux" tested on CV7 correct darts with each version's tuned pair; `MCNEMAR_ALPHA` 0.05; `b`, `c`, `p` per step in the output and report; infeasible-candidate gate pending Q5. | CV22 |
| D27 | Kosmo, 2026-11-15 (Q3, verbatim): « 2. Q3 : option 1, règles strictes confirmées (mat24 : image écartée si une boîte Dart ne contient aucune Dart-Tip ; v17 : image écartée si une même pointe prédite sert deux boîtes ou si une pointe prédite avec confiance n'est dans aucune boîte). Le rapport compte les images écartées par règle. » D24's three rules are final (no provisional branch left in T9p or T9d); images dropped per rule in the pseudo-label figures and the report. | CV21, CV23 |
| D28 | Kosmo, 2026-11-15 (verbatim): « 3. Attribution dans datasets.md : format confirmé « <jeu> par <auteur>, <lien>, CC BY 4.0 », complété par « modifié : boîtes reconstruites autour de la pointe » (et « pointes obtenues par pseudo-étiquetage » pour v17), comme l'exige CC BY. » Cells written by T9b: accepted rows `<jeu> par <auteur>, <lien>, CC BY 4.0 ; modifié : boîtes reconstruites autour de la pointe`; the v17 row `<jeu> par <auteur>, <lien>, CC BY 4.0 ; modifié : pointes obtenues par pseudo-étiquetage, boîtes reconstruites autour de la pointe`; rejected rows `non utilisé`. The mention of the added calibration labels is Q6. | CV21 |
| D29 | Design review of e03b9dc (minors): T9 split into T9p (public import, CV21) and T9 (builder, fold plan, run chain), T9b after T9p (A18); one run folder with fixed paths and per-step hash checks naming the step to redo (A19); letter glosses for A and B in the compare output and report, "Trois entraînements" as one numbered block in K5.6 order with durations, paste points and the "version non construite" line (U); PR states that `tauCalHigh`, `tauCalLow`, `tauTip` come only from Kosmo's run and that "not built" is a valid outcome (T1); T9d test with two overlapping boxes sharing one point (T2); Q2 and Q3 answers change rule and test together in the task files (T3). T14 split into T14 (CLI) and T14b (docs) for size. | CV22 to CV24 |
| D30 | Kosmo, 2026-11-15, stop of K5 (figures in "Goal"; absolute priority of PR B; T9 to T14 on hold except T11 and T12; verbatim, line breaks flattened): « 1. Vérité de calibration photo par photo : mode « calibration de contrôle » dans l'outil d'annotation, où Kosmo clique les 4 points de calibration d'une photo donnée (indépendamment des segments). Échantillon : toutes les photos du jeu eval (pour mesurer la calibration automatique photo par photo, CV9), plus environ 1 photo sur 10 dans chaque session dev, plus les fléchettes 55 à 60 de la session d'éval de 18h24. Format d'annotation étendu proprement (lecteur strict, migration sans perte), aucun nom de session dans les sorties partagées. 2. Diagnostic chiffré qui tranche H1/H2 : (a) dérive vraie par photo, d'après les clics de contrôle, comparée à la mesure automatique (erreur par photo, médiane et p95) ; (b) pour les photos de contrôle, décalage entre la photo et l'image de la vidéo de la volée environ 1 s avant entry.videoPositionMs (téléphone non touché), mesuré par la même méthode validée, et contrôlé sur quelques photos par des clics sur l'image vidéo si nécessaire ; (c) pour H1, l'emplacement de l'erreur (recherche grossière ou vignettes) et le rapport entre le premier et le deuxième pic de corrélation. Rapport en chiffres seulement, colle-le par Kosmo dans le chat. 3. Page de contrôle visuel locale pour Kosmo (127.0.0.1, bibliothèque standard ; images jamais sorties du PC) : alternance photo de référence / photo mesurée, recalage appliqué, décalage affiché. 4. Correction selon le résultat : si H1, mesure robuste (rejet des pics ambigus, masquage des fléchettes connues grâce aux pointes cliquées, zones statiques, borne de décalage plausible), validée sur les clics de contrôle (erreur au plus 0,5 mm sur au moins 95 % de l'échantillon) avant toute compensation ; si H2, calibration photo par photo (automatique validée sur les clics de contrôle, ou manuelle assistée) et proposition de correction de Kosmo Capture (par exemple photo prise depuis la vidéo juste avant la frappe, ou avec un délai après la frappe). Aucune compensation automatique tant que la validation n'est pas atteinte. 5. Ergonomie de l'outil refaite autour du parcours : état de chaque session (ce qui est fait, ce qui reste), une seule consigne active à la fois, jamais de consigne périmée affichée comme active, recalibration expliquée à l'écran. 6. Exigence produit, à inscrire dès maintenant dans docs/camera (ADR) pour les phases 1 à 3 : l'appli de jeu calibre sur chaque image, détecte un déplacement de la cible (trépied heurté, partie interrompue puis reprise) et se recalibre automatiquement, sans action de Kosmo ; elle sera testée par des déplacements volontaires du trépied. Le rapport de la 0b-1 mesure la calibration automatique photo par photo contre les clics de contrôle du jeu eval. Mets à jour le plan (nouvelles tâches avec modèles), soumets-le à la revue, puis arrête-toi pour que Kosmo le relise avant de coder. » EXIF orientation is ruled out. | CV4, CV9, CV15, CV16, CV18, CV19, CV25 to CV30 |
| D31 | Squad's design for D30 (plan change of 2026-11-15, for design review): control clicks as `controls` in annotation format v3, independent of segments, `controls_sha256` apart from `segments_sha256` (CV25); sample computed by rule, "about 1 in 10" read as ranks 1, 11, 21 ... plus the last dart plus every segment reference photo (agreement of the segment clicks, information since D39), "darts 55 to 60 of the 18:24 session" covered by darts 55 to 60 of every eval session so no session name is needed (CV26); video frames extracted by the container with PyAV into a local `frames.json` folder, the stdlib tool only serves listed files (CV27); measures `T`, `E`, `E_seeded`, `R`, `O`, `V`, `W` and the decision rule fixed before the numbers (CV28, CV29); "validée" read as error at most 0.5 mm on at least 95 % of dev controls with parameters tuned on dev only, confirmed on eval (CV30); the `drift` command and flags kept as information, never an instruction; the tool zooms on the expected area but draws no marker and shows no measure while Kosmo clicks (no anchoring; replaced by the assisted click of D39 and D43); exactly one instruction computed by the server; homography of the visual check in Python, applied by CSS `matrix3d`; Kosmo's recalibrations already made are kept (Q11); roles `dev 1`, `dev 2`, `eval 1`, `eval 2` in reports (Q8); ADR 0017 numbered after ADR 0016 in `docs/adr/` (where the project's ADRs live; `docs/camera/` links to it from `training.md`); conditional tasks T16a to T16c finalized at P2. | CV15, CV25 to CV30 |
| D32 | Design review of 2fefedd (test and security approve; architecture and ux changes requested), squad's fixes: A28 ADR 0016 "Tripod drift" gets a status line "Not validated and suspended ... (D30)" ("ADR 0017 text"), asserted by the new doc test `test_plan_docs.py` (T15a); A29 every held task file (T9, T9b, T9o, T9c, T9d, T10, T13, T13b, T14, T14b) opens with "HELD by D30:" naming the parts replaced at P2, asserted by the same doc test; A30 drift never instructs: a T15e test crosses every journey state with every drift input and asserts identical instructions, drift text only in `info`, never on the list or dart-screen banner; S12 the visual-check route resolves the session through the listing (T6a attacks give 404), a listed session gets 200 with a French state ("Diagnostic périmé", "Pas de diagnostic", "Non mesurable"), Host and Origin checks (T15k); S13 `VIDEO_MAX_DECODED_FRAMES` 20000 and `VIDEO_MAX_BYTES` 512 MiB, decode errors give `video_unreadable` and the run continues, "Images vidéo absentes : N", a session with every frame missing handled (CV27, T15c, T15d, T15e); T1 "non concluant" short-circuits, combination tests (CV29, T15j); T2 K5 regression test `test_cv28_k5_wrong_shift_flags_measure` (T15i); T3 CV29/CV30 constants pinned once in `test_config.py` (T15i, T15j); T4 PR states the K7 steps are the real container check ("PR material"); T5 each answer to Q7 to Q13 lands with its rule, test and K7 numbers ("Open questions"); U15 the focus card "Prochaine étape" with a "Continuer" button on the list, hints per screen, K accepted from every session screen including "Terminé", a T15e test that each hint names what is bound (T15d, T15e, T15g); U16 only the 4th point writes, "Points posés : n / 4 (enregistrés au 4e point)", pending points dropped on Échap, next, previous or goto, flow test "2 points then leave, file unchanged" (T15d, T15f, T15g); U17 explicit "Contrôle indisponible" and "Images vidéo absentes" rows, final row "Contrôle terminé : fermer l'outil et lancer le diagnostic (K7.4)" only when nothing is left or unavailable (T15e); U18 hint "Point hors de la vue : touche 0 ...", key 0 and wheel kept, H four times skips an unusable photo (T15f); U19 three page elements `#journey`, `#help`, `#banner`, recalibration explanation in the side panel (T15g); U20 visual check opens on the largest gap, "Photo i / n", key N via the tested server function `next_above` (T15k); U21 counts as templates "D / T", K7.3 sizes marked "environ"; U22 K7.C gains the list button, K from "Terminé", "Points posés", the indisponible messages, full view and wheel, the 1080p check. K7.3, K7.C and T15h ("K7.4 à K7.6 : le diagnostic arrive dans la prochaine livraison" until T15l) updated. | CV16, CV27, CV29, CV30 |
| D33 | Kosmo, 2026-11-17, review of 280189d (verbatim): « Q4 : 1) modèle « sans jeux publics ». » Calibration points of every public image of A and B, v17 images of B included, come from M0 (the `sans` `full` model); T9d input unchanged. | CV23 |
| D34 | Kosmo, 2026-11-17 (verbatim): « Q5 : 1) la plus simple reste : le plafond de 1 % d'erreurs non signalées passe avant McNemar. » The fail-closed T13 gate stays as planned. | CV22 |
| D35 | Kosmo, 2026-11-17 (verbatim): « Q6 : 1) ajouter « points de calibration ajoutés par pseudo-étiquetage ». » T9b cells: accepted rows `<jeu> par <auteur>, <lien>, CC BY 4.0 ; modifié : boîtes reconstruites autour de la pointe, points de calibration ajoutés par pseudo-étiquetage`; the v17 row `<jeu> par <auteur>, <lien>, CC BY 4.0 ; modifié : pointes obtenues par pseudo-étiquetage, boîtes reconstruites autour de la pointe, points de calibration ajoutés par pseudo-étiquetage`; rejected rows `non utilisé`. | CV21 |
| D36 | Kosmo, 2026-11-17 (verbatim): « Q7 : 1) échantillon dev + fléchettes 55 à 60 de chaque session d'éval (environ 45 images). » Confirmed readings (verbatim): « Interprétations confirmées : « fléchettes 55 à 60 de chaque session d'éval » ; échantillon dev aux rangs 1, 11, 21…, plus la dernière fléchette et chaque photo de référence de segment. » | CV26 |
| D37 | Kosmo, 2026-11-17 (verbatim): « Q8 : 1) rôles seulement. » | CV15 |
| D38 | Kosmo, 2026-11-17 (verbatim): « Q9 : 1) oui. » The role (`dev 1`, `eval 2`) shows on the session cards (T15e, T15g). | CV16 |
| D39 | Kosmo, 2026-11-17 (verbatim, line breaks flattened): « Q10 : 3) aucun seuil de précision sur les clics de Kosmo. Un humain ne tient pas 1 à 2 px pendant une heure : ses clics servent à désigner le bon croisement, pas à le localiser au pixel. À la place, clic assisté dans la calibration de contrôle (et dans la recalibration) : - Kosmo clique approximativement sur le bon croisement de fils ; l'outil recale le point sur le croisement exact par un ajustement local sub-pixel (fils de part et d'autre du point, rayon limité de l'ordre d'une dizaine de px) et affiche le résultat zoomé ; - par défaut, les 4 points de la photo précédente sont proposés et recalés automatiquement ; Kosmo valide d'une touche, ou reclique un point parti sur le mauvais croisement ; - si le recalage échoue (pas de croisement net dans le rayon), le point reste au clic, marqué « non recalé », et le rapport le compte ; - la répétabilité du recalage est mesurée par la machine : recalage relancé depuis des départs décalés de quelques px autour de chaque point, dispersion p95 rapportée (cible : au plus 0,25 mm) ; c'est cette dispersion, pas la main de Kosmo, qui borne la vérité de référence ; - le recalage est du code numpy testé sur cibles synthétiques ; il tourne côté serveur (bibliothèque standard + lecture JPEG) ou dans le conteneur si Pillow est nécessaire : propose la solution la plus simple qui garde l'outil utilisable dans WSL ; - l'estimation du temps de saisie de K7 est revue à la baisse en conséquence et indiquée dans le plan. » `CLICK_REPEAT_MAX_MM` is dropped. | CV28, CV29, CV31 |
| D40 | Kosmo, 2026-11-17 (verbatim): « Q11 : 1) les garder. » Recalibrations made during K5 stay; their `snap` is `manual` after migration (CV25). | CV20, CV25 |
| D41 | Kosmo, 2026-11-17 (verbatim): « Q12 : 1) réglée sur dev, validée sur dev, puis confirmée sur les clics eval. » | CV30 |
| D42 | Kosmo, 2026-11-17 (verbatim): « Remarques U23 et U24 : à appliquer. Choix de l'équipe (50 époques, lot de 8, graine 42, 8 à 9 entraînements, 5 à 6 h) : notés. » U23: Enter runs "Continuer" only when no other button has focus (T15g, K7.C). U24: the arrows of the visual check follow dart order and "Photo i / n" counts in that order (T15k, K7.C). | CV16 |
| D43 | Squad's design for D39 (plan change of 2026-11-17; amends decision 5, D5, CV16 and ADR 0016 "stdlib only", confirmed by Kosmo in Q14, D44): snapping in `kosmo_camera/snap.py` (numpy, Pillow luma loader; outline in "Approach, Assisted click", constants `SNAP_*` in `defaults.json` pinned once in `test_config.py`); it runs in the tool process from a WSL venv `~/kosmo-tool-venv` with only numpy and Pillow (`tools/camera/requirements-tool.txt`, hashes for cp313 and cp314), imported lazily: without the venv the tool starts as today and shows "Recalage indisponible" and refuses `calibrate` and `control` saves (409); format v3 stores a `snap` status per point (`snapped`, `unsnapped`, and `manual` on segments migrated from v2), so the format change lands in T15b before it is built; no new data file, no new route; the previous item's 4 points are proposed and snapped, Enter saves; D31's "no marker" rule replaced; `R` (segment click agreement) becomes information; "non concluant" comes from the snap dispersion `D` p95 above `SNAP_REPEAT_MAX_MM` 0.25 or fewer than `DIAG_MIN_CONTROLS` complete snapped dev controls; when `D` is too high the squad revises the snap at P2 and reruns on the same stored clicks (no new clicks); controls with an unsnapped point are excluded from `T`, `E` and counted. New tasks T15m (opus, case 3) and T15n (sonnet). | CV16, CV25, CV28, CV29, CV31 |
| D44 | Kosmo, 2026-11-17 (Q14, verbatim, line breaks flattened): « Q14 : option 1, l'environnement ~/kosmo-tool-venv dans le WSL de Kosmo, avec trois conditions : - fichier de dépendances dédié à l'outil (par exemple tools/camera/requirements-tool.txt) : numpy et Pillow seulement, versions exactes avec empreintes, installation avec --require-hashes et --only-binary=:all: ; il vise le Python du WSL de Kosmo, qui est 3.14 (pas de python3.13 hors du bac à sable et de l'image) ; vérifie que les versions choisies publient bien des roues cp314 manylinux x86_64 ; - K7.1b : une seule commande à copier-coller qui crée l'environnement avec python3, installe ce fichier et affiche les versions installées ; l'outil se lance ensuite avec ~/kosmo-tool-venv/bin/python ; démarrage sans l'environnement : « Recalage indisponible », comme proposé ; - le recalage est aussi testé sous Python 3.14 : propose dans « Proposed owner changes » l'ajout au job camera-tools d'une étape Python 3.14 qui installe requirements-tool.txt et lance les seuls tests de l'outil et du recalage. Kosmo l'appliquera avec les autres changements de CI de la PR B. » Squad's application: D43 confirmed. (1) `requirements-tool.txt` = numpy 2.5.4 and Pillow 13.0.0 (the `requirements-cpu.txt` pins), one file with two hashes per package, cp313 and cp314 manylinux_2_27/2_28 x86_64 (pip checks the one wheel matching the interpreter: Kosmo's 3.14 gets cp314, the sandbox and image 3.13 get cp313, same versions; a second file would duplicate the pins and could drift; free-threaded cp314t excluded, Kosmo's `python3` is the standard build); cp314 wheels confirmed on PyPI and installed binary-only by the sandbox's Python 3.14.4 on 2026-11-17; hashes generated and cross-checked as T15n says. (2) K7.1b is one command (`python3 -m venv`, pip `--require-hashes --only-binary=:all:`, version print); the separate `--help` check is dropped from K7.1b (the tool still prints its « Recalage » line). (3) Python 3.14 test run of `TOOL_TESTS` in the sandbox (T15n "Done when") and proposed for CI (PR B "Proposed owner changes"); pytest comes from a second small file `requirements-tool-test.txt` (pytest and its 4 dependencies, `requirements-cpu.txt` pins and hashes) so Kosmo's file stays numpy and Pillow only; `synth.py` imports `av` lazily so the snap tests' ring-wire boards render without PyAV. | CV16, CV31, D10 |
| D45 | Design review of 67968fa (test and security approve; architecture and ux changes requested), squad's fixes: A31 the amendment of plan decision 5, D5, CV16 and ADR 0016 decision 2 is written where it applies (rows 5 and D5 "Amended by D43, D44", the CV16 "After D39 and D43" sentence, ADR 0016 decision 2 line "Amended: see section Assisted click (D43, D44)"), asserted by a doc test in `test_plan_docs.py` (T15n); A32 the tool treats only `ModuleNotFoundError` for numpy or PIL as "Recalage indisponible", any other import error of `snap.py` propagates (T15n); A33 and S14 one JPEG-to-luma decoder `kosmo_camera/luma.py` moved from `drift.py` `_decode_luma`, with `MAX_IMAGE_BYTES` 32 MiB checked before reading, `Image.MAX_IMAGE_PIXELS` kept, bomb warning raised as an error, `(None, reason)` and never an exception, used by `drift.py`, the tool and the diagnosis; `snap.py` becomes numpy only on a luma array (amends D43's "Pillow luma loader"); an unreadable image leaves the point at the click, `unsnapped` with reason `unreadable`, banner « Photo illisible : le recalage est impossible pour cette photo », the tool never freezes (T15m, T15n, T15d, T15i); A34 the rejected alternative "decoding video in the tool" is justified by the D44 venv limit (numpy and Pillow only), not by decision 5; A35 and T2 `D` measures repeatability, not accuracy: accuracy is proven only by the T15m tests against `board.py` truth (a converged wrong crossing checked against the truth), and the diagnosis report states it in « Limites » (CV28, T15i); T1 one T15m test board in a seed and style not used for tuning meets the 0.1 px bound; U25 Entrée on the assisted point screen always means « Enregistrer » (`preventDefault`, buttons `blur()` after click), the opposite of the list rule U23; H follows a hide rule (selected point, else first `à cliquer`, else refused 409 « Choisir le point à cacher : touche 1 à 4, puis H ») (T15d, T15f); U26 insets 2 x 2 of `SNAP_INSET_PX` 60 source px at x3, side panel 400 px, insets, ready line and save button visible together at 1080p (T15f); U27 click rule `flow.click_target` (selected point, else nearest positioned point within `CLICK_REPLACE_RADIUS_PX` 100, else first point without a position, else refused 409), the CV2 diagram kept where points are filled in order (T15n, T15d, T15f); U28 banner « Photo précédente rouverte : son enregistrement est retiré jusqu'à Entrée » when U reopens the previous item (T15f); U29 `save` carries the open item and is refused 409 below 4 ready points or for an item no longer open, Entrée with `event.repeat` ignored and `SAVE_GUARD_MS` 300 after a save (T15n, T15d, T15f); U30 the visual check says « alignement » (choices « Alignement automatique », « Alignement d'après les clics », « Aucun alignement »), « recalé » names the snap only (T15k); U31 each inset has an `aria-label` with its number and status (T15f). | CV16, CV28, CV31 |
| D46 | Kosmo, 2026-11-18 (T15m, option a; summary of his decision): real photos are 4080 x 3072 and video is 1080p; the 1200 x 900 case is not in the domain. Realistic domain: wires of about 2 px at least (1600 x 1200 and larger), blur at most 0.5 px, noise at most 3. Criterion 1: at most 0.25 px for every snapped point (median and max reported per case, with the mm equivalent for the board size of the photos and of the video); criterion 3: at least 90 % of the points snapped per realistic case, the rest "non recalé", clicked by hand and counted; cases outside the domain (1200 x 900, blur above 0.5 px, noise 5 for rate and accuracy) tested and reported without a blocking bound. Speed: the 50 ms target is removed (80 to 140 ms per snap accepted), no wall-clock assertion in the default suite. Criterion 2, never a wrong crossing, on the whole domain, noise 5 included: explicit shape check, ambiguity test (two or more distinct crossings within `SNAP_RADIUS_PX` of the click: refused, reason `ambiguous`), regression test of the noise-5 start that ended 17 px away. Squad's application: reason `ambiguous` added to CV31, to `snap.REASONS` and to T15m; the 0.1 px bound of T15m and T15n becomes 0.25 px. | CV31 |

## Open questions

Each answer lands in one commit with the rule it changes, its test and the K7 numbers and steps it affects (design
review T5). Q4 to Q12 landed together in the commit of D33 to D43; Q14 in the commit of D44; the design fixes of D45 change no
answer.

- P1: resolved by D23 (Kosmo checked the pages and gave the attribution texts and statuses).
- P3: decided, two PRs (D11).
- Q1: decided, 1.0 mm (D18).
- Q2: decided, paired exact one-sided McNemar test, order sans -> A -> B (D26).
- Q3: decided, strict rules confirmed (D27).
- Q4: decided, calibration points from the model "sans jeux publics" (D33).
- Q5: decided, the 1 % unflagged-error cap comes before McNemar (D34).
- Q6: decided, « points de calibration ajoutés par pseudo-étiquetage » added (D35).
- P4 (information for Kosmo, not blocking): "stdlib + numpy" is read as "no new library": the drift computation is
  numpy only, but JPEG decoding needs Pillow, already pinned and in the image. So `drift` runs in the container, not
  with the bare `python3` of WSL; the annotation tool stays stdlib and only reads `drift.json` (amended by D43:
  the assisted click needs numpy and Pillow from the WSL venv, D44).
- Q7: decided, dev sample plus darts 55 to 60 of every eval session, about 45 frames (D36).
- Q8: decided, roles only (D37).
- Q9: decided, role shown on the session cards (D38).
- Q10: decided, no threshold on Kosmo's clicks, assisted click with machine-measured snap dispersion (D39, D43).
- Q11: decided, K5 recalibrations kept (D40).
- Q12: decided, tuned and validated on dev, confirmed on eval clicks (D41).
- Q14: decided, option 1, WSL tool venv `~/kosmo-tool-venv` with Kosmo's three conditions (D44).
- Q13 (Kosmo, after the diagnosis, at P2): replacement of D18 (sub-threshold compensation) and, if H2, the Kosmo
  Capture change among options (a) to (d) of "PR material".
