# T16b (CONDITIONAL): Per-photo calibration in the label build

Status: conditional. Built if the T15o verdict says the photos moved (H2, H3 or "photos déplacées") and Kosmo
confirms at plan update P2. Finalized at P2.

## Goal
Every dart label uses the calibration of its own photo instead of the segment calibration plus the CV19
compensation, so that labels stay right when the phone moved (D30 point 4, CV9 per photo).

## Files
- Changed `tools/camera/kosmo_camera/` label build (the module that maps tips to board coordinates, named at P2):
  per photo, the calibration source in this order: the re-snapped control points of that photo
  (`snapped-points.json`, T17c, D49; a control with a non-snapped point uses its click and is counted apart), else
  the automatic per-photo calibration when it passed the CV30 gate (T16a, T16c or the CV18 method), else none: the
  dart is left out with the reason "calibration de la photo non validée", counted in the report. No source asks
  Kosmo for a click (D48).
- Changed `docs/rules/camera.md`: CV19 replaced (sub-threshold compensation retired).
- Changed tests of the label build.

## Depends on
T15o, T17c; T16a when the verdict includes H1; plan update P2.

## Rules
CV9, CV19 (replaced), CV25, CV30, CV4.

## Tests
- Order of sources on a scripted session: re-snapped control points win over automatic, a non-snapped point
  uses the click and is counted; automatic used only when validated; none
  gives the reason and the count.
- Labels of a T7 session with planned drift: within 0.5 mm of the truth with per-photo calibration.
- Eval photos: control clicks used only as the CV9 reference in the report, never to build training labels
  (CV4), checked by a test.

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: wiring of a source order fixed at P2 into the existing label build, with tests.

## Implementation notes
