# ADR 0020: Global per-image registration of the board (camera phase 0b-1 and the game app)

- Status: Proposed (Kosmo's strategy of 2026-11-18 on issue #42, plan decisions N1 to N12); accepted when the phase 0
  PR is merged. Date: 2026-11-18.
- Supersedes: ADR 0017 (per-image calibration), decision 3 (RF-DETR Nano with calibration classes) and the sections
  "Assisted click" and "Tripod drift" of ADR 0016, and the sections "Phase 0b-1" and "Re-snap file" of ADR 0018. ADR 0018 (decision section) and ADR 0019 stay in force,
  unchanged; the last consequence of ADR 0019 (H2 measured in the video, D50) no longer applies since the H1/H2
  diagnosis is stopped.

## Context

Measured on Kosmo's real data on 2026-11-18 (plan section "Findings"): his control clicks are right to about 0.5 mm;
the camera really moves by 0.3 to 3.5 mm within a session, in steps common to the 4 points; a calibration per session
instead of per photo changes the score of 4 darts out of 29 (14 %); the local snapping of clicks (`snap.py`, CV31)
snapped 0 of 697 photo points, because it was designed and tuned on unrepresentative synthetic boards; the drift
measure (CV18, K5) reported 20 to 85 mm and was wrong. A throwaway prototype of about 200 lines registering the whole
board on one real photo found the board, its rotation and the 20 in about 4 s, with no click, 0.13 to 0.78 mm from
Kosmo's clicks, and converged from a start 10 mm and 3 degrees off.

## Decision

1. The board is registered on every image, globally, with no learning and no assumption of a fixed camera:
   R1 find the board (saturated ring pixels, RANSAC ellipse, rough homography); R2 rotation (12 trials of the 18 degree
   pattern, best alignment kept); R3 dense alignment (8 degree-of-freedom homography on about 3 000 model edge points
   of 6 circles and 20 radii, edge search along the normal on a coarse-to-fine pyramid, Tukey-reweighted least squares,
   aligned on the wire centre; optional radial distortion term only if the residuals ask for it); R4 the 20 is the
   red-double sector nearest the top of the image once uprighted by the phone orientation (Kosmo: the board always
   hangs 20 up; no set-up confirmation, no digit reading); R5 tracking from the previous homography, R1 again when
   quality drops; R6 quality (share of kept points, residual in mm, consistency with the previous image): an image out
   of bounds is flagged, never scored with a doubtful calibration.
2. Only the normalised geometry of a steel-tip board is used (rings and 20 sectors): no logo, digit font or brand.
   Brand-specific colours and wire widths are noise for the robust loss. Tolerances are in mm, never in px, so the
   same code serves 12 Mpx photos, 1080p video frames and public images.
3. Precision is sized to the need, about 0.5 mm; no sub-pixel pursuit.
4. Registration is accepted only on real images, against Kosmo's existing clicks (no new click): the phase 1 gate of
   the plan (eval photo controls: median at most 0.5 mm, p95 at most 1.0 mm, at least 99 % registered, the rest
   flagged, no unflagged image above 2 mm, same on the listed video frames, at most 1 % of darts whose score differs
   from the click calibration). Synthetic boards serve unit tests only, never tuning or acceptance.
5. Dart detection builds on this registration: new dart by difference of two registered images (D1), tip by a small
   heatmap-regression CNN on a rectified crop at fixed px/mm (D2; RF-DETR Nano is no longer used), score and flag from
   the tip distance to the nearest wire against k sigma (D3).
6. The game app (phases 1 to 3 of the product) keeps ADR 0017's requirement: it calibrates on every image it scores,
   follows a moved or bumped tripod and a resumed game with no action from the player, and never scores an image it
   could not calibrate (flagged for manual entry). It is tested with deliberate tripod moves.

## Alternatives rejected

- Local snapping of each clicked point to its wire crossing (ADR 0016 "Assisted click", CV31): 0 of 697 real photo
  points snapped; it needs a click per point and a re-snap step, and chases 0.03 mm where 0.5 mm is enough.
- A drift measure against one reference photo per segment with compensation (ADR 0016 "Tripod drift", CV18, CV19):
  wrong on the real data and it still assumes a mostly fixed camera.
- Calibration points as detector classes (`cal1` to `cal4` of the RF-DETR model, ADR 0016 decision 3, ADR 0017
  consequences): needs labels on every training image, pseudo-labels on public images, and a learned model for a
  problem that pure geometry solves.
- One calibration per session: 14 % of darts change score.

## Consequences

- Stopped: `snap.py`, the assisted click of the annotation tool, `drift.py` and the drift flags, the H1/H2 diagnosis,
  the calibration pseudo-labels of public images, RF-DETR Nano. Their removal from the code is task C1 of the plan,
  scheduled by Kosmo's answer to question Q1; until then the code is frozen, not developed.
- Rules CV18, CV19 and CV31 are stopped; rules CV6, CV9, CV12 and CV14 are rewritten in phases 1 and 3 so that the
  reference and the predicted calibration both come from registration (R) and Kosmo's clicks.
- The annotation format v3 is read only; Kosmo's tips, calibrations and controls are kept as ground truth.
- Phase 1 delivers the registration and a `register_report` command run in the container (`--network none`, data
  read-only); the Python code is written to be ported as is to the app (Kotlin and LiteRT).
- Each phase starts with a throwaway prototype measured on the real development images Kosmo mounts read-only in the
  sandbox (never committed, never used for acceptance figures).
