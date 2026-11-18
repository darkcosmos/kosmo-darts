# Plan: camera phase 0b-1, detection feasibility (issue #42), registration strategy

## Goal

Issue [#42](https://github.com/darkcosmos/kosmo-darts/issues/42): measure offline, on Kosmo's captures, whether a phone
camera can score darts at the thresholds of rule CV8 (at least 95 % correct without flag, at most 1 % unflagged
errors). This plan follows Kosmo's strategy approved on issue #42 on 2026-11-18 ("Caméra phase 0b-1 : nouvelle
stratégie"). It replaces the previous version of this file from decision D30 included, the lot 3b plan and its update
P3 (closed PR #90). Decisions D1 to D29 that still hold are kept, one line each (section "Decisions").

## Scope and out of scope

- In scope, phase 0 (now): this plan, ADR 0020, the status of the stopped rules, ADRs and docs, the cancelled task
  files, the doc tests, the closing of PR #90, and one cleanup task C1 scheduled by Kosmo's answer to Q1.
- In scope, phases 1 to 4: registration, new-dart events, tip model, final eval (section "Phases and gates"). Their
  tasks are written only after each phase prototype (N18).
- Out of scope: any phase 1 work until Kosmo confirms on #42 that the real image folder is mounted (N19); the game
  app and Kosmo Capture (no Android change in 0b-1); any new click by Kosmo (N6); camera work beyond 0b-1.

## Approach

### Findings (measured on the real data, 2026-11-18)

Eval session of 14:08 and photo `dart-001.jpg`, analysed outside the plan from Kosmo's clicks only:

- Kosmo's clicks are right: outer double corners at 5|20, 13|6, 17|3 and 8|11, rounded to 3.45 px steps (0.46 mm,
  reduced display); two clicks on one photo differ by 0.2 to 0.9 mm. Calibration truth already exists, at about 0.5 mm.
- The camera really moves, by a few mm: the board slides in the image by 0.3 to 3.5 mm between dart 1 and dart 33, in
  steps, with a motion common to the 4 points. The K5 drift measure (20 to 85 mm) was wrong.
- One calibration per session is not enough: per session instead of per photo, 4 darts out of 29 change score (14 %).
  Calibration is needed on every image, otherwise the 95 % threshold is out of reach.
- Local snapping (`snap.py`) does not work on real images: 0 of 697 photo points snapped. It was designed and tuned on
  unrepresentative synthetic boards (radial wires stopping at the double, flat colours, flat grey wire, a +-2 mm window
  for a 1.1 mm wire) and aimed at 0.03 mm, unrelated to the need.
- Root cause of method: the squad never saw a real image and stacked instruments to validate instruments (snapping,
  re-snapping, drift, H1/H2 diagnosis, compensation), each tested on synthetic data, one round trip a day with Kosmo.
- Proof of direction: a throwaway prototype of about 200 lines (numpy, scipy) registers the whole board on
  `dart-001.jpg` with no click and no board-specific setting; it finds the board, its rotation and the 20 in about 4 s
  (unoptimised Python), 0.13 to 0.78 mm from Kosmo's clicks (the spread of two of his clicks), and converges to the same
  solution from a start 10 mm and 3 degrees off. One image only: phase 1 measures it on the 4 sessions. The prototype
  (`reg.py`, `auto2.py`) comes in the mounted real image folder: a starting point to rewrite, not code to ship.

### Principles (binding on every task)

1. Calibration on every image, always; no fixed-camera assumption (bumped tripod, resumed game, zoom change are normal).
2. Board-independent: only the normalised steel-tip geometry (rings, 20 sectors); never a logo, digit font or brand;
   brand colours and wire widths are noise for a robust loss.
3. Resolution-independent: same code on 12 Mpx photos, 1080p frames and public images (pyramid, tolerances in mm).
4. Real first: every brick is measured on real images before it is called ready; synthetic data only for unit tests
   (geometry, edge cases), never for tuning or acceptance.
5. Precision sized to the need: about 0.5 mm on calibration; no sub-pixel pursuit.
6. No instrument to validate an instrument: the truth is the clicks and tips Kosmo already entered; no new click.
7. Kosmo's budget: at most 10 min per campaign; one command, one numbers report to paste; time estimates measured.
8. Stop rule: a failed gate gets one analysis on the real images and one fix; after a second failure, back to Kosmo
   with options. Never a new layer of instruments.

### Target architecture (Python for 0b-1, ported as is to the app in Kotlin and LiteRT)

Registration R, on every image, no learning (ADR 0020):

- R1 find the board: saturated ring pixels, RANSAC ellipse fit on their edges, rough homography.
- R2 rotation: the ring and wire pattern repeats every 18 degrees; 12 angles tried, best alignment kept.
- R3 dense alignment: 8-DOF homography fitted on about 3 000 model edge points (6 circles, 20 radii); edge searched
  along the normal on a coarse-to-fine pyramid, Tukey-reweighted least squares; darts, digits, logo and glare rejected
  by the robust loss. Known fix to the prototype: align on the wire centre, not its edge. Optional radial distortion
  term only if the residuals ask for it.
- R4 identify the 20 (Kosmo): the board always hangs 20 up, so the 20 is the red-double sector nearest the top of the
  image once uprighted by the phone orientation; no set-up confirmation, no digit reading. The phase 1 report counts
  the images where the rule hesitates (a sector within 9 degrees either side of the top).
- R5 tracking: the next image starts from the previous homography; R1 restarts when quality drops.
- R6 quality: share of kept points, residual in mm, consistency with the previous image; an image out of bounds is
  flagged, never scored with a doubtful calibration.
- R is deterministic: fixed RANSAC seed, no time-based stop; geometry unit tests (ellipse fit, 18 degree symmetry, R4
  hesitation exactly at 9 degrees) on synthetic boards; real-image figures only from the numbers report.

Dart detection D:

- D1 new dart: two successive images registered by R are compared in the board frame; the difference gives the new
  dart's area, with no learning; it also detects removal (empty board).
- D2 tip: a learned model on a rectified crop (front view, fixed px/mm) around that area, one dart per crop. Public
  sets become usable through R (no calibration labels needed); public images where R fails are dropped and counted.
- D3 score and flag: the tip position in mm gives the score; the dart is flagged when its distance to the nearest wire
  is below k sigma (tip uncertainty combined with registration uncertainty).

Tip model (N12): (a) heatmap regression, a small CNN with sub-pixel output, chosen: precise for one point, tiny, simple
LiteRT export, about 200 lines of PyTorch to write. (b) RF-DETR Nano, rejected: oversized for one point per crop and
less precise. Several dart types with no type-specific training (N13): D1 does not depend on the dart's look; the crop
holds the tip and the shaft end only (not flights or full barrel); rectified public sets are mixed with Kosmo's data
with colour, blur and small-rotation augmentation; the phase 3 report gives results per session and per dart type
when known, and a gap above 3 points between two types fails the gate (it is not a tuning knob).

Why it lasts: nothing assumes a fixed camera, a brand or a resolution; the same registration serves the study, public
sets and the app, photo and video; the only learned model sees rectified, normalised crops.

### Ground truth (automatic, no new click)

- Calibration: Kosmo's existing clicks, about 700 photo points and 120 video points, used for validation only. The few
  settings of R are chosen on dev; acceptance is on eval.
- Tips: the 505 existing annotations, converted to mm by the registration R of their own image.
- Split unchanged: dev = 17:44 and 18:03, eval = 14:08 and 18:24 (roles `dev 1`, `dev 2`, `eval 1`, `eval 2` in reports,
  CV15). Cross-validation by session on dev; eval is seen once, at the end.

### Phases and gates

| Phase | Content | Gate, measured on real data | Kosmo's time |
|---|---|---|---|
| 0 | Cleanup: PR #90 closed, this short plan, ADR 0020 replacing the local registration and drift ADRs, C1 per Q1 | Plan approved by Kosmo | 5 min reading |
| 1 | R1 to R6, command `register_report` (container `--network none`, data read-only) | Eval photo controls: median error <= 0.5 mm and p95 <= 1.0 mm vs clicks; >= 99 % of images registered, the rest flagged; 0 unflagged image with error > 2 mm; same figures on the listed video frames; <= 1 % of darts whose score differs between R and the click calibration; share of public images registered (information) | 3 min: 1 command, paste the report |
| 2 | D1, events | Annotated tip inside the detected area in >= 99.5 % of cases (dev, then eval); empty board detected | 2 min |
| 3 | D2 and D3, cross-validation by session on dev; McNemar "Kosmo only" vs "Kosmo + rectified public sets" | Dev estimate: >= 95 % correct without flag and <= 1 % unflagged errors; gap <= 3 points between dart types | 0 min, or 2 min to start the GPU training |
| 4 | Single evaluation on eval | Go if >= 95 % and <= 1 %; the 1 % cap prevails | 3 min |

Total for Kosmo: about 10 min, plan reading excluded. Kosmo's thresholds above are not changed by the squad.

Reporting of every gate (thresholds unchanged): each figure with its n and 95 % interval (CV13; 0 of n bounds a rate
at about 3/n), verdict per D12. Phase 1 also measures and prints the click noise floor (median and p95 of the gap
between two click sets of the same photo: segment reference photos that also carry a control, CV26) next to the
error figures; how the gate reads it is Q2. Phase 3: the phase 3 plan update names who supplies the dart type and the
minimum darts per type; below it the gap is printed with its interval and the case goes to Kosmo.

### Kept and stopped

Kept: the data, the split, all of Kosmo's annotations (tips, calibrations, controls); the annotation tool, as a viewer;
annotation format v3, read only; the GPU image and the CI job `camera-tools` (Python 3.13 and 3.14); thresholds 95 %
and 1 %, cross-validation by session, McNemar; LiteRT export; the retained public sets (dart_detection3, mat24);
ADR 0018 (automatic truth, 10-min budget) and ADR 0019 (capture framing).

Stopped: `snap.py`, `snap_report`, re-snapping and `snapped-points.json` (the last two only on the closed PR #90
branch); the assisted click in the tool, the K5 drift measure, `drift.py` and compensation; the H1/H2 diagnosis
(T15i, T15j, T15o, T15k, T15l) and tracking in the video; T16a to T16c, T17a to T17e, Q15; calibration pseudo-labels
of public sets (replaced by R); RF-DETR Nano (replaced by the heatmap model). H2 (the finger tilting the phone) is only
an ergonomics note for Kosmo Capture, later: trigger without touching the phone.

### Squad working rules (N18)

- Prototype first: before the tasks of a phase are written, one `opus` task builds a throwaway prototype on the real
  images of N19 and returns a number. The tasks are written afterwards, in a plan update reviewed as usual.
- Short plan: one line per decision; no conditional task deeper than one level; about 300 lines at most.
- A phase ends with a numbers report, never with a new instrument.
- Questions to Kosmo: once per phase, consolidated, with options and a recommendation.

## Files and types added or changed

Phase 0 (this stage, documentation and doc tests only, no production code):

- `docs/plans/42-camera-feasibility-0b-1.md`: this file, replacing the previous version.
- `docs/plans/42-camera-feasibility-0b-1/`: done task files kept as history; every task file that was not built
  starts with a `CANCELLED (2026-11-18):` line; new `C1-remove-stopped-code.md`.
- `docs/adr/0020-global-per-image-registration.md`: new. `docs/adr/0018-automatic-ground-truth.md` and
  `docs/adr/0019-capture-framing.md`: brought unchanged from the closed PR #90 branch (Kosmo keeps them).
- `docs/adr/0016-camera-feasibility-tooling.md` ("Assisted click", "Tripod drift") and
  `docs/adr/0017-per-image-calibration.md`: status lines "Superseded by ADR 0020".
- `docs/rules/camera.md`: status note; CV18, CV19, CV31 stopped; retired and reserved rule numbers.
- `docs/camera/training.md`: K7 stopped on 2026-11-18, K7.4 to K7.6 cancelled.
- `tools/camera/tests/test_plan_docs.py`, `tools/camera/tests/test_training_doc.py`: adapted to the above.

Phase 0, task C1 (after Q1): see its task file. Phases 1 to 4: written after each prototype.

## Data read, stored or exposed

- Read: `~/kosmo-data/captures/`, `annotations/`, public sets, all read-only; in the sandbox, from phase 1, the real
  development image folder mounted read-only (N19): never committed, never copied into the repository, never pasted.
- Stored: nothing new in phase 0. Later phases write only regenerable local files under `~/kosmo-runs/`.
- Exposed: numbers-only reports with session roles (CV15); no image, path or session name.

## Screens and states

None in phase 0. After C1 (Q1 option A) the annotation tool is a read-only viewer; its existing screens lose the
assisted-click states (« recalé », « non recalé », « Recalage indisponible ») and the drift flags. Kosmo Capture and the
game app are unchanged in 0b-1.

## Test plan

- Phase 0 doc tests (`tools/camera/tests/test_plan_docs.py`): ADR 0020 title, status, "Supersedes" line and sections;
  ADR 0017 and ADR 0016 "Assisted click" and "Tripod drift" open with their "Superseded by ADR 0020" status; ADR 0018
  and ADR 0019 titles, statuses and sections (as on PR #90); every not-built task file opens with
  `CANCELLED (2026-11-18):`; the root plan has the sections of this file, at most 300 lines, and no phase 1 task file
  exists (the phase 1 prototype row has no file); `camera.md` marks CV18, CV19, CV31 stopped and the reserved numbers.
- `tools/camera/tests/test_training_doc.py`: the "Contrôle de calibration" section carries the 2026-11-18 stop line
  and K7.4 to K7.6 cancelled; the K7.0 to K7.3 checks stay while the code exists (removed by C1).
- Checks of the stage: `python -m pytest -q -m "not slow"` in `tools/camera`, `kosmo-detekt` (exit 0),
  `git diff --check`.
- C1 and later phases: in their task files.

## How Kosmo verifies

- Phase 0: nothing to install or run, the apps do not change (the CI debug APK is unchanged in behavior). Kosmo reads
  the phase 0 PR description (French summary of this plan, phase table, Q1 to Q3), checks that PR #90 is closed with its
  branch kept, then approves the plan on #42 and answers Q1 to Q3.
- Phase 1 onwards: one command and one pasted report per phase, as in the "Phases and gates" table, within 10 min.

## Tasks

| Id | Title | Task file | Depends on | Model | Why this model |
|---|---|---|---|---|---|
| P0 | This plan, ADR 0020, superseded statuses, rules and training doc status, cancelled task files, doc tests, PR #90 closed | - (this stage) | - | opus | Done by `developer-plan`, main's model for planning |
| C1 | Remove the stopped code: snapping, assisted click, drift and its flags; the tool becomes a read-only viewer | `42-camera-feasibility-0b-1/C1-remove-stopped-code.md` | P0 approved, Q1 answered A | opus | Refactoring across modules (case 2): tool flow, server, journey and page, shared readers in `drift_file`, config, requirement files, rules and docs |
| P1 | Phase 1 prototype: registration R on the real images, a number vs Kosmo's clicks | not written (N19) | Kosmo confirms on #42 that the image folder is mounted | opus | Kosmo's prototype-first rule (N18) |

Two `opus` tasks to come (C1, P1). Phase 1 tasks are written after P1, in a plan update reviewed as usual.
Done task files of the previous plan stay as history (T1 to T8b, T9p, T10a, T15a to T15h, T15m, T15n).
Cancelled (not built): T9, T9b, T9c, T9d, T9o, T10, T11, T12, T13, T13b, T14, T14b (RF-DETR chain, rewritten in
phases 2 to 4 after their prototypes), T15i, T15j, T15k, T15l, T16a, T16b, T16c (stopped work). T15o and T17a to T17e
existed only on the closed PR #90 branch.

## Decisions

Kept from the previous plan (one line each; ids unchanged so that rules and ADRs still resolve):

| Id | Decision | Rules |
|---|---|---|
| Py | Python 3.13 in the sandbox, the image and CI (ADR 0016); Kosmo's WSL tool runs on 3.14. | - |
| B2 | LiteRT only; measures run on the exported `.tflite` with `ai-edge-litert`. | CV4 |
| K0 | No capture campaign before a first result; existing captures split about 70/30 by session; campaign later into `captures-eval/`. | CV3, CV11, CV17 |
| B6 | At least 95 % correct without flag and at most 1 % unflagged errors. | CV8 |
| 1 | Each dart was entered right after its throw: arrival windows apply to every capture. | CV10 |
| 2 | Empty board recognised before "Next volley"; measured on the `captures-eval/` campaign only. | CV11 |
| 3 | Nothing typed: groups derived from data, zoom from `truth.json`. | CV14 |
| 4 | An off-board miss is correct if nothing is announced on the board. | CV7 |
| 5 | Annotation tool in WSL on `127.0.0.1` only, captures read-only, no external resource; a viewer from now on (N17). | CV16 |
| 6 | No model file in the repository: SHA-256, size and rebuild command only. | - |
| 7 | No pseudo-annotation of Kosmo's photos: his clicks are the truth. | - |
| 10 | GPU station: RTX 4070 12 GB, WSL2, rootless Docker, GPU by CDI only. | - |
| D2 | 4 control points on the outer double corners 5\|20, 13\|6, 17\|3, 8\|11 (truth for R). | CV2 |
| D3 | Detection and calibration metrics on photos, events on video. | CV9 to CV12 |
| D4 | No OpenCV; numpy and PyAV. scipy (used by the prototype) is decided in phase 1 with its version table. | - |
| D5 | Annotation tool UI in French only. | CV16 |
| D6 | CUDA build `cu130` for the GPU image. | - |
| D7 | Container keeps uid 0 inside under rootless Docker, `--cap-drop ALL`, `no-new-privileges`, `--read-only`, `--network none`. | - |
| D8 | Pixel coordinates in stored JPEG pixel order, EXIF orientation ignored. | CV16 |
| D9 | Constants in `config/defaults.json` only, read by `config.py`. | - |
| D10 | Requirements pinned with hashes, installed with `--require-hashes`. | - |
| D12 | Verdict per threshold: "clearly below" when the whole 95 % interval misses the target. | CV8 to CV11 |
| D13 | Sessions with no judged dart are ignored by the split. | CV3, CV16 |
| D20 | Public sets: CC BY 4.0 only, bullseyebot excluded, one `datasets.md` row per set, committed class map. | CV21 |
| D21 | The value of public sets is measured, never assumed: with and without, on dev only. | CV4 |
| D22 | Downloaded public files are untrusted input, validated as in CV21. | CV21 |
| D23 | Retained public sets: dart_detection3 and mat24 (tip = box centre); v17 (conditional) is reconsidered in phase 3, its pseudo-tip route used RF-DETR. | CV21 |
| D25 | No comparison or threshold tuning on images seen in training: cross-validation by session on dev, final model on all dev. | CV4 |
| D26 | Exact one-sided paired McNemar (alpha 0.05): a more complex version is kept only if significantly better. | - |
| D27 | mat24: an image whose `Dart` box holds no `Dart-Tip` is dropped; drops counted per rule. | CV21 |
| D28 | Attribution format « <jeu> par <auteur>, <lien>, CC BY 4.0 » plus what was modified. | CV21 |
| D48 | Kosmo (ADR 0018): every future ground truth is automatic; his manual budget is at most 10 min per campaign. | - |
| D50 | Video frames are 16:9, cropped top and bottom against the 4:3 photo (fact for R on video). | - |
| D51 | Kosmo (ADR 0019): the preview shows exactly the analysed or recorded field; no implementation in 0b-1. | - |

Retired: B1, B4, B5, 8, 9, 12, D1, D11, D14 to D19, D24, D29, D30 to D47, D49, D52 to D56 (history in git; rules and
ADRs that cite them keep the citation as history).

New (Kosmo's strategy of 2026-11-18 and the squad's phase 0 design):

| Id | Decision | Rules |
|---|---|---|
| N1 | Calibration on every image, no fixed-camera assumption (principle 1, ADR 0020). | - |
| N2 | Board- and resolution-independent: steel-tip geometry only, tolerances in mm (principles 2, 3). | - |
| N3 | Real first: synthetic data for unit tests only, never tuning or acceptance (principle 4). | - |
| N4 | Calibration precision target about 0.5 mm, no sub-pixel pursuit (principle 5). | - |
| N5 | Truth = Kosmo's existing clicks and tips; no new click; no instrument to validate an instrument (principle 6). | - |
| N6 | Kosmo's time: at most 10 min per campaign, one command, one pasted numbers report (principle 7). | - |
| N7 | Stop rule: one analysis and one fix per failed gate, then back to Kosmo with options (principle 8). | - |
| N8 | Registration R1 to R6 as in "Target architecture" (ADR 0020). | - |
| N9 | Kosmo: the 20 is the red-double sector nearest the top once uprighted (R4); hesitations counted. | - |
| N10 | New dart by difference of registered images (D1); tip on a rectified crop (D2); flag by k sigma to the wire (D3). | - |
| N11 | Ground truth and split as in "Ground truth"; settings on dev, acceptance on eval, eval seen once. | CV3, CV4 |
| N12 | Tip model: heatmap regression (a); RF-DETR Nano no longer used (decided by the assistant on Kosmo's delegation). | - |
| N13 | Several dart types, no type-specific training; a gap above 3 points between types fails the phase 3 gate. | - |
| N14 | Phases 0 to 4 and their gates as in "Phases and gates". | - |
| N15 | Kept and stopped as in "Kept and stopped"; ADR 0020 supersedes ADR 0017, ADR 0016 "Assisted click" and "Tripod drift", ADR 0018 "Phase 0b-1" and "Re-snap file". | CV18, CV19, CV31 |
| N16 | PR #90 closed, its branch kept; ADR 0018 and 0019 brought unchanged to this branch. | - |
| N17 | The annotation tool is a viewer; the stopped code is removed by C1, scheduled by Q1. | CV16 |
| N18 | Prototype first: one `opus` prototype task per phase on the real images, with a number, before its tasks are written. | - |
| N19 | Kosmo provides about 20 photos and 10 video frames of the 4 sessions (board only, no EXIF) with their annotations, mounted read-only in the sandbox, never committed; a development set: these exact images never enter an acceptance figure, each report lists them by number, excludes them and states any eval-session image as a leak; sessions they come from: Q3. Phase 1 starts only after his confirmation on #42. | CV4, CV15 |
| N20 | Not-built task files of the previous plan open with `CANCELLED (2026-11-18):`; new camera rules start at CV33 (CV22 to CV24 and CV28 to CV30 lived only in the replaced plan, CV32 only on PR #90; these numbers are not reused). | - |
| N21 | H2 (finger tilting the phone) becomes an ergonomics note for Kosmo Capture (trigger without touching the phone), later. | - |

## Open questions

Consolidated for Kosmo (in French, as he reads them):

- Q1 (avant C1) : quand supprimer le code arrêté (recalage des clics, dérive) ? (A, recommandé) Dans la PR de la
  phase 0 (tâche C1) : l'outil devient une visionneuse ; vous voyez toujours photos, pointes et contrôles, mais vous
  ne pouvez plus cliquer ni rien enregistrer ; vous perdez la saisie de pointes et de calibration dans l'outil (une
  future campagne serait annotée automatiquement, ADR 0018). (B) Même suppression, mais vous pouvez encore placer des
  pointes (plus de clics de calibration ni de contrôle). (C) Rien ne change dans l'outil pendant la 0b-1 : code figé,
  documentation marquée « arrêté ». Pourquoi A : la stratégie fait de l'outil une visionneuse et exclut tout nouveau
  clic ; le code mort garde des tests et une étape de CI inutiles (avec A ou B, la PR propose de faire tourner les
  tests de l'outil sous Python 3.14 sans `requirements-tool.txt`).
- Q2 (porte de la phase 1, seuils inchangés) : vos clics varient de 0,2 à 0,9 mm entre deux saisies. (A, recommandé)
  Les seuils 0,5 / 1,0 mm s'appliquent à l'écart entre R et vos clics, bruit des clics compris ; le rapport affiche à
  côté le plancher mesuré et les intervalles. (B) Les seuils s'appliquent à l'erreur de R une fois le bruit des clics
  retiré (estimation). (C) Si le plancher mesuré dépasse 0,5 mm, retour vers vous avant tout verdict. Pourquoi A :
  le plus simple et le plus strict ; C reste possible par la règle d'arrêt.
- Q3 (images montées dans le bac à sable) : (A, recommandé) les tirer des seules sessions dev (17h44 et 18h03), pour
  que l'éval reste vue une seule fois. (B) Les 4 sessions comme prévu : ces images sont exclues de tous les chiffres
  d'acceptation, listées dans le rapport, et la fuite y est signalée.
- Points for the phase 1 prototype (not questions to Kosmo): the source of the phone orientation for R4 (D8 ignores
  EXIF and the image folder has none); scipy as a new dependency (version table in the phase 1 PR).
