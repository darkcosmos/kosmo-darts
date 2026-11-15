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
  tool except `split` and the annotation tool checks it.
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
  centres at 103 mm and at 166 mm) to the image with the clicked homography, back to the board with the predicted
  one; success if the largest error is at most `CAL_MAX_ERROR_MM` = 2.0 mm and all 4 points were found. Target 99 %.
  Why 2 mm: a quarter of the 8 mm treble and double rings, so a calibration error alone cannot move a dart centred in
  a ring out of it; to be confirmed by the report.
- CV10 Arrival of dart k (non-voided, segment not 0, both entry positions in the video) is detected if an arrival
  event falls between the previous non-voided entry (volley start for the first) and entry k. Every other arrival
  event is false. Targets: >= 98 % detected, false events <= 1 % of evaluated darts.
- CV11 Empty board (only on `captures-eval/` sessions captured with the removal protocol; not measurable on
  `captures/`, where the protocol was mixed, and reported as such; volleys with at least one dart on the board):
  success if declared between the last entry and the volley `end`; a false empty is one declared after the first
  on-board dart and before the last entry. Targets: >= 99 % success, 0 false empty.
- CV12 Tip error: clicked tip versus matched predicted tip, both mapped with the clicked homography; median and 95th
  percentile in mm, VISIBLE annotations only.
- CV13 Confidence intervals: Wilson 95 % for rates, distribution-free order-statistic interval for quantiles.
- CV14 Breakdowns: near and far half (split line through the bull, perpendicular to the camera direction), single,
  double, treble, 25, bull (from truth), lighting class, tripod group, zoom bin.
- CV15 Reports contain numbers and group labels only: no image, path, file or session name.
- CV16 Annotation tool: stdlib only, `127.0.0.1` only, no external resource, captures read-only, writes only in the
  annotations folder, atomic; every route resolves the session name against the listed sessions; never overwrites
  an annotation file that the strict reader rejects (a version 1 file is accepted and written back as version 2 on
  the first change, CV20).
- CV17 Sessions in `~/kosmo-data/captures-eval/` (confirmation campaign, later) are always eval: never in the split,
  never in training or tuning; `measure --set campaign` reads them under the same `frozen.json` guard.
- CV20 Calibration segments (annotation format version 2): `calibrations` is an ordered list of segments
  `{fromDart, photo, width, height, points}`, at most `MAX_SEGMENTS` (64); the first starts at dart 1, `fromDart`
  strictly ascending, each applies from its `fromDart` until the next one; a segment after the first starts at a judged
  dart (CV5) and is clicked on that dart's photo. A version 1 file is read by the kept version 1 reader and migrated
  without loss (a version 1 `calibration` becomes the single segment from dart 1; `null` becomes an empty list).
  Writers write version 2 only. Details in `docs/camera/annotation-format.md`.
