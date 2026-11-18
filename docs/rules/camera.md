# Camera feasibility rules

Status: v1 (issue #42, phase 0b-1, detection feasibility measured offline on Kosmo's captures; plan
`docs/plans/42-camera-feasibility-0b-1.md`). This document is the single source of truth for the Python tests in
`tools/camera/`: tests cite these rules, never the issue text. Rule ids (CV1, CV2, ...) use the prefix CV so that they
never collide with the other rule documents in this folder. The truth file format is `docs/camera/capture-format.md`;
the board rules BC1 and BC5 are in `docs/rules/x01.md` and the match rules.

- CV1 Board geometry in mm, origin bull centre, y towards 20, x towards 6, radii and border ownership of BC1 and BC5.
- CV2 Calibration points `cal1` to `cal4`: 170 mm radius on wires 5|20, 13|6, 17|3, 8|11, in this order.
- CV3 Split of `captures/`, about 70/30 dev/eval, computed once. Key of a unit: SHA-256 of `"kosmo-0b1-split-v1:"`
  plus the session name (block mode: plus `":"` and the block index). Session mode: walk the sessions in key order and
  put a session in eval while the eval share of judged darts (CV5) is below 30 % and adding it keeps the share at or
  below 35 % (otherwise skip it). Sessions with no judged dart (CV5) are ignored: they are in no set, are not walked
  in either mode and do not count for "at least 2 sessions per set"; the split file and the `split` output give their
  number only. Accepted if the eval share is between 25 % and 35 %, each set has at least 2 judged sessions and eval
  has at least 100 judged darts. Otherwise block mode: each session cut into blocks of `SPLIT_BLOCK_VOLLEYS` (5)
  consecutive volleys, same walk over blocks; flagged "more optimistic" (same tripod, lighting and calibration clicks
  on both sides). The split file is canonical JSON (sorted keys); its SHA-256 is locked in `split-lock.json`. Every
  tool except `split`, `drift` (CV18) and the annotation tool checks it.
- CV4 Eval units never enter training, tuning, threshold choice or dataset building; `measure --set eval` needs a
  valid `frozen.json` (strict: format, version, `tau_low`, `tau_sure`, 64-hex `modelSha256`, no other key) with a
  matching model SHA-256; the report records the model, `frozen.json` and split hashes.
- CV5 Darts judged: non-voided entries with a photo; entries with `photo: null` are counted apart, not judged.
- CV6 Announcement per dart: each non-voided entry is judged on its own photo. Tips found on the photo are mapped to
  board millimetres with the photo's predicted calibration; a candidate within `MATCH_RADIUS_MM` (6 mm) of a tip
  announced earlier in the volley is an old dart. The best new candidate is announced if its score is at least
  `TAU_SURE`, flagged "to check" if between `TAU_LOW` and `TAU_SURE` or if the calibration of the photo failed; no new
  candidate announces a miss.
- CV7 Outcome per dart: correct (announced value equals truth, not flagged), flagged, or unflagged error. A truth miss
  is correct when nothing is announced on the board. Hidden tips stay in the total.
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
  `captures/`, where the protocol was mixed, and reported as such; volleys with at least one dart on the board):
  success if declared between the last entry and the volley `end`; a false empty is one declared after the first
  on-board dart and before the last entry. Targets: >= 99 % success, 0 false empty.
- CV12 Tip error: clicked tip versus matched predicted tip, both mapped with the reference homography of the photo
  (CV19); median and 95th percentile in mm, VISIBLE annotations only; photos without reference calibration counted
  apart.
- CV13 Confidence intervals: Wilson 95 % for rates, distribution-free order-statistic interval for quantiles.
- CV14 Breakdowns: near and far half (split line through the bull, perpendicular to the camera direction), single,
  double, treble, 25, bull (from truth), lighting class, tripod group (from the reference calibration of the photo,
  CV19), zoom bin.
- CV15 Reports contain numbers and group labels only: no image, path, file or session name. The drift report labels
  sessions `S01`, `S02`... in session-name order. The drift flag file `drift.json` (CV18) holds session names and is,
  like the split file, a local working file in `~/kosmo-runs/`: never pasted, never committed. The video frame manifest `frames.json` and
  the extracted frames folder (CV27) hold session names and are local working files too. After D30 reports name
  sessions by role only (`dev 1`, `eval 1`, CV26).
- CV16 Annotation tool: stdlib only, `127.0.0.1` only, no external resource, captures read-only, writes only in the
  annotations folder, atomic; every route resolves the session name against the listed sessions; never overwrites
  an annotation file that the strict reader rejects (a version 1 file is accepted and written back as version 2 on
  the first change, CV20). The drift flag file (`--drift`, CV18) is read-only for the tool: read again at every
  session list and view, never written; a missing, invalid or oversized file gives one warning and no flags, and flags
  measured on other segments than the current ones stay shown marked "(mesure périmée : relancer la mesure)". After
  D39 and D43: the stdlib-only rule covers every module except `snap.py` and the shared decoder `luma.py`, both
  imported lazily (`annotate/snapping.py`); without numpy and Pillow the tool starts, shows "Recalage indisponible"
  and refuses calibration, recalibration and control saves (CV31); snapping reads only the photo or listed frame
  already served by the tool, through `luma.decode_luma`, at most `SNAP_CACHE_IMAGES` (2) decoded images in memory; an
  image that cannot be decoded leaves the point at the click, `unsnapped` with reason `unreadable`, and never stops the
  tool.
  Control mode (T15d): `--split` (with `--lock`) and `--frames` are read again at every request and never written; a
  split that does not match its lock, or an unreadable `frames.json`, gives one list warning and switches control (or
  the video items) off; `GET /api/frame/<session>/<file>` serves only a file listed in `frames.json` for a listed
  session.
  Journey (T15e): the tool gives one active instruction, derived from the annotation file and the presence of frames
  only and recomputed at every request; drift flags are information only, never an instruction.
- CV17 Sessions in `~/kosmo-data/captures-eval/` (confirmation campaign, later) are always eval: never in the split,
  never in training or tuning; `measure --set campaign` reads them under the same `frozen.json` guard.
- CV18 Drift of a judged photo: compared with the reference photo of its segment (CV20), both decoded to luma in
  stored pixel order (D8). (1) Coarse: the box of the 4 segment points enlarged by 15 % is cropped from both,
  block-averaged so its longer side is at most `DRIFT_COARSE_PX` (512), and phase-correlated (zero mean, Hann window,
  normalised cross-power spectrum with a Gaussian low-pass, `numpy.fft`): shift `t0`. (2) Fine, per point `i`: square
  patch of side `DRIFT_PATCH_FRACTION` (0.06) times the longer image side, centred on `p_i` in the reference and on
  `p_i + t0` in the photo, phase-correlated with a 3-point parabolic sub-pixel fit per axis: `d_i = t0 + r_i`.
  (3) Drift px = max `|d_i|`; drift mm = CV9 error with `H_true` = homography of the points `p_i + d_i` and `H_pred` =
  segment homography (the error the stale calibration would cause). The reference photo itself has drift 0 without
  computation. The box margin (15 %), the 16 px minimum box side and the low-pass width (0.08) are fixed by the
  method, not tunables, so they are not in `defaults.json`. No usable reference gives status "unmeasurable" with one reason: `uncalibrated` (session without
  calibration), `size_mismatch` (photo size differs from the segment), `border` (a patch not inside both images),
  `low_peak` (a correlation peak below `DRIFT_MIN_PEAK`: occlusion or change), `unreadable` (missing, truncated or
  oversized file: Pillow's decompression bomb limit is kept). Drift is annotation quality control, independent of any
  model, so it reads every session, dev and eval, without the split lock (CV4 unaffected). The `drift` command writes a
  numbers-only report (CV15) and the local flag file `drift.json`. CV18 status (D30): not validated. Its results are information only until a measure passes the CV30 gate; the report starts with "Mesure de dérive non validée (diagnostic en cours) : ne pas recalibrer d'après ce rapport".
- CV19 Drift threshold and compensation: a photo is "beyond" when drift mm > `DRIFT_MAX_MM` (1.0 mm =
  `CAL_MAX_ERROR_MM` / 2, Kosmo, D18). Reference calibration of a photo = its compensated points `p_i + d_i` when
  measured and not beyond; none otherwise. Used for the `cal1`-`cal4` training labels, CV9, CV12 and CV14. Photos
  without reference calibration are dropped from the dataset and excluded from CV9 and CV12, each counted by reason.
  `dataset` and `measure` refuse to run while a judged photo of their set is beyond (message: run `drift`,
  recalibrate, run `drift` again); `--accept-drift` overrides and the run records the count. CV7 (and CV8) never use
  the clicked calibration: no dart or session is excluded from the main criterion. CV19 status (D30): suspended. No
  compensation, no "beyond" guard, no K5 recalibration loop until plan update P2 replaces this rule (T16b) on the
  diagnosis (CV29) and the gate (CV30); the value of D18 is reopened (Q13).
- CV20 Calibration segments (annotation format version 2): `calibrations` is an ordered list of segments
  `{fromDart, photo, width, height, points}`, at most `MAX_SEGMENTS` (64); the first starts at dart 1, `fromDart`
  strictly ascending, each applies from its `fromDart` until the next one; a segment after the first starts at a judged
  dart (CV5) and is clicked on that dart's photo. A version 1 file is read by the kept version 1 reader and migrated
  without loss (a version 1 `calibration` becomes the single segment from dart 1; `null` becomes an empty list).
  Writers wrote version 2 only until version 3 (CV25). Details in `docs/camera/annotation-format.md`.
- CV21 Public datasets (D20, D23, D27): only sets with an entry in `tools/camera/config/public-class-maps.json` are read,
  from `~/kosmo-data/public/<slug>/` (slug = Kosmo's folder name, `^[a-z0-9_-]{1,64}$`); the map holds only sets with
  status `accepted` or `conditional` in `docs/camera/datasets.md`, a set with a `pseudo-tip-box` target being
  `conditional` and any other `accepted` (checked by a test, not at run time); rejected sets have no entry and their
  folders are never opened. Targets: `tip` (the box centre is the tip; box rebuilt with `BOX_FRACTION`),
  `whole-dart-checked` (annotation ignored; image dropped when no `tip` centre lies inside the box, counted under its
  own rule), `pseudo-tip-box` (whole-dart box returned raw, tip only from CV23), `ignore`, `drop-image`; no calibration
  target (public calibration labels come only from CV23). Every category carrying an annotation in any subset must be
  in the map; a mapped name absent from the set, an unknown slug or subsets with different category lists refuse the
  set with a message naming them; a category with no annotation in any subset is ignored. Public images go to the
  train subset only, never to valid, dev measures or eval. Downloaded files are untrusted input (S10): each
  `_annotations.coco.json` is capped at `public_import.MAX_JSON_BYTES` (64 MiB) and must parse with unique image and
  category ids and no dangling `image_id` or `category_id`, else the set is refused; an image is dropped and counted per
  reason when its `file_name` is not a bare name (no `/`, `\`, `..`, NUL, absolute path) of a regular file (`lstat`)
  inside `<slug>/<subset>/` opened without following links (`O_NOFOLLOW`), when it exceeds
  `public_import.MAX_IMAGE_BYTES` (32 MiB), fails to decode (`Image.MAX_IMAGE_PIXELS` kept, decompression bomb,
  truncated), has a size different from the JSON, or has a box that is not finite or not inside the image. Both caps
  are checked before reading and a read never goes past them. Sources are never written.
- CV25 Control clicks (D30, annotation format version 3): `controls` is a list of `{dart, source, width, height,
  points}` plus `positionMs` when `source` is `video`; `source` is `photo` or `video`; `points` holds exactly 4
  entries in CV2 order, each a point `{x, y}` in stored pixels of that image or `null` (hidden); sorted by dart then
  photo before video, at most one control per (dart, source), at most `MAX_CONTROLS` (1024). A control belongs to a
  judged dart (CV5); a photo control has the size of the truth photo (`photoWidth` x `photoHeight`; video frames
  differ in size, not checked); a video control needs a volley video and a non-null `entry.videoPositionMs`. Controls are
  independent of segments (CV20) and never change them. Each control and each segment carries `snap`: 4 entries
  aligned with `points`, `"snapped"` or `"unsnapped"` (CV31) for a point, `null` exactly when the point is `null`;
  segments may also hold `"manual"` (clicked before snapping existed). A version 2 file is migrated without loss
  (`controls: []`, every segment `snap: ["manual", "manual", "manual", "manual"]`); writers write version 3 only.
  `segments_sha256` excludes `snap` (drift flags stay valid across the v2-to-v3 migration), `controls_sha256` includes
  it. Use: CV9 reference and diagnosis (CV28); on dev, label source only through T16b (P2). Details in
  `docs/camera/annotation-format.md`.
- CV26 Control sample (computed, never chosen by hand; roles of CV15): photo controls: every judged eval photo; per dev
  session the judged darts of rank 1, 1 + `CONTROL_DEV_STRIDE` (10), ... in dart order, plus the last judged dart, plus
  the reference photo of every segment (when it is a judged dart); video controls: the dev photo sample, plus the
  judged darts `CONTROL_EVAL_VIDEO_FIRST` (55) to `CONTROL_EVAL_VIDEO_LAST` (60) of every eval session (Kosmo, Q7, D36).
  Order of work: session list order, then dart order, photo before video. Roles: sessions named `dev 1`, `dev 2`,
  `eval 1`... in ascending CV3 unit key within each set of a session-mode split read through its lock.
- CV27 Video frame before the entry: target `entry.videoPositionMs` - `VIDEO_BEFORE_ENTRY_MS` (1000); the frame used is
  the last decoded frame whose time is at or before the target, its position floored to the millisecond, pixels in
  stored order (rotation metadata ignored, D8). Reasons for no frame, counted: `no_position`, `before_start` (target
  before the first frame), `too_close` (the target precedes the `photo` stamp of the nearest earlier dart of the volley
  that has one: the phone may still be moving from the previous tap), `past_end` (the target is a frame period or more
  after the last frame), `video_missing`, `video_unreadable`, `orientation_mismatch` (frame and photo orientations
  differ; never rotated). Saved as `<session>/video-NNN.jpg` (JPEG quality 95) with its size and SHA-256 in
  `frames.json` (CV15; strict reader `frames_file`, at most 4 MiB). Bounds (S13): a video larger than `VIDEO_MAX_BYTES`
  (512 MiB) or needing more than `VIDEO_MAX_DECODED_FRAMES` (20000) decoded frames is `video_unreadable`; any decode
  error is `video_unreadable` for that dart and the run continues (exit 0); the output prints
  `Images vidéo absentes : N` with the count per reason, and no session name.
- CV31 Assisted click, snap part (Kosmo, Q10, D39; design D43, D46; tool flow in T15n): `snap.snap_point(luma, start)`
  returns the sub-pixel crossing of a radial wire and a ring wire or colour edge within `SNAP_RADIUS_PX` of the start
  (status `snapped`), or the start itself (status `unsnapped`) with exactly one reason: `support`, `contrast`,
  `residual`, `angle`, `no_convergence`, `too_far`, `border`, or `ambiguous` (two or more distinct crossings of the
  board lattice within `SNAP_RADIUS_PX` of the start; with one in range only that one is accepted). The tool adds
  `unreadable` when the image cannot be decoded (`luma.decode_luma`). A snap never ends on a wrong crossing: the
  shape is two sharp wires or edges crossing at the found point, and an ambiguous start is refused. Quality is proven
  against the `board.py` truth on synthetic boards (CV4: never tuned on Kosmo's photos), on the domain of photos
  of 12 Mpx and video of 1080p (wires of at least 2 px, blur at most 0.5 px, noise at most 3 grey levels): at most
  0.25 px for every snapped point and at least 90 % of the starts snapped per case, the others staying at the click
  and counted. Repeatability `D` (CV28): the snap restarted from `SNAP_REPEAT_DIRECTIONS` starts at
  `SNAP_REPEAT_OFFSET_PX` around a snapped point; the largest board-plane distance in mm (through the homography of
  the control) to the stored point, a failed restart counting as infinite; target at most `SNAP_REPEAT_MAX_MM`. `D`
  measures repeatability, not accuracy. Constants `SNAP_*` in `defaults.json`, each pinned once in `test_config.py`.
- CV32 Snapping on real data (D48, D49, D54; lot 3b), diagnosis part (T17a): `snap.snap_detail(luma, start)` gives the
  result of `snap_point` plus `stage`, the exact check that refused it, and numeric descriptors (noise, polarity, stem
  of the lines beyond the crossing as the T-junction indicator, wire width, blur, contrast-to-noise, halo).
  `snap_report` reruns the snap from every stored point of controls and segments (photos and listed video frames),
  the stored point as start, read-only, and counts the refusals in Kosmo's categories, each stage in exactly one:
  « pas de croisement » (`coarse_no_ridge`, `coarse_no_pair`, `fine_support`, `fine_contrast`), « forme »
  (`coarse_not_reaching`, `fine_angle`, `fine_blur`, `stands_out`, `refine_shift`), « ambiguïté » (`ambiguous`),
  « hors rayon » (`coarse_too_far`, `too_far`), « autre » (`border`, `no_convergence`, `fit_failure`, `unreadable`).
  It also gives the descriptors per category, a radius sweep at `SNAP_SWEEP_FACTORS` times `SNAP_RADIUS_PX`
  (diagnosis only), the distance between consecutive controls' points and the time per snap. Every section is split
  by split (dev, eval, from the split lock) and by source (photo, vidéo); numbers and roles only (CV15). Planned
  duration of K7.6: stored points x starts per point x median time per snap of that run, starts per point = the snaps
  of the run per point plus `SNAP_REPEAT_DIRECTIONS`, plus `SNAP_LATTICE_STARTS` for a photo control point. The
  report gives causes; parameters are still chosen on synthetic boards (CV4).
