# T17b: Snapping fix for the cause found on the real clicks, with realistic synthetic cases

Status: content of "Cause and fix" confirmed or rewritten at plan update P3 from Kosmo's pasted K7.4 report (D52).
P3 records here the confirmed causes, the chosen fix and the rejected alternatives (shape-check change, larger
radius, changed wire profile test), with the one-case-per-cause list, and returns to design review before T17b
is built.

## Independence (CV32, D54)
P3 and this task read only the dev figures of the K7.4 report and the segment-point figures; the eval control
figures are never used to choose a cause, a fix or a parameter (they decide the acceptance in T17c).

## Goal
Make the snap work on Kosmo's real targets for the dominant refusal causes of the T17a report, without ever ending
on a wrong crossing (CV31 criterion 2), and prove it on synthetic cases that reproduce those causes (D49).

## Cause and fix (primary hypothesis, to confirm at P3)
- T junction at the outer double corner: the radial wire stops on the double wire. The T7 boards already end the
  radial wire there (`synth.py` `_colours`), so the T junction alone may not explain it; the fix follows the
  T17a `stage` counts and descriptors (wire width, blur, contrast, halo, radius, proposals beyond the radius).
- Fix outline by cause: T junction or one-sided support accepted when the ring wire is continuous and the radial
  wire reaches it from one side (shape check per flank); radius at the click raised if « hors rayon » dominates,
  with the ambiguity test kept on the larger radius; real wire profiles (specular centre, dark flanks, sharpening
  halo) accepted by the wire test when the T17a descriptors show them.

## Files
- Changed `tools/camera/kosmo_camera/snap.py`; `config/defaults.json`, `config.py`, `test_config.py` (changed
  `SNAP_*` values pinned once, T3).
- Changed `tools/camera/kosmo_camera/synth.py`: realistic options reproducing the causes found (for example a
  specular wire profile, a sharpening halo, JPEG at the real quality, 1080p video frames cropped 16:9).
- Changed `docs/rules/camera.md` CV31 (shape check and domain as fixed here); ADR 0016 "Assisted click" one line.
- Changed `tests/test_snap.py`.

## Depends on
T17a; Kosmo's K7.4 report; plan update P3.

## Rules
CV31, CV32 (independence, D54), CV4 (parameters tuned on synthetic boards only; the real report gives causes, never values).

## Tests
- Every T15m case keeps its bounds (0.25 px, at least 90 % snapped per realistic case, never a wrong crossing,
  noise 5 included, the noise-5 regression).
- New realistic cases, one per cause found (list fixed at P3), each named after it: 200 starts per case over 10
  seeded boards (seeds 0 to 9), at least 90 % snapped, at most 0.25 px.
- Wrong-crossing traps on the new shapes: a dart shaft crossing the radial wire within the radius, a T junction
  next to a crossing, a halo ridge parallel to the wire: refused or on the true crossing.

## Done when
Python checks of root "Test plan" green, the Python 3.14 `TOOL_TESTS` run green, `kosmo-detekt` exit 0.

## Model
opus. Why: case 3, the shape rule of CV31 changes according to a cause known only from the real report, and it
bounds the reference truth.

## Implementation notes
