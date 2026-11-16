# T15h: training.md, control clicking steps (K7.0 to K7.3), K5 retired

## Goal
Give Kosmo the copy-paste steps to rebuild the image, extract the video frames and do the control clicks, and
retire the K5 drift loop from the guide (D30, D31).

## Files
- Changed `docs/camera/training.md`: the section "Dérive et recalibration" is replaced by a section "Contrôle de
  calibration" holding K7.0 to K7.3 and the checklist K7.C, in French, copied from root plan "How Kosmo verifies"
  (commands as written there; the `docker run` line keeps `--network none`, `--cap-drop ALL`,
  `no-new-privileges`, `--read-only` and `~/kosmo-data` mounted `:ro`). One sentence says the former K5 loop is
  retired and why (D30). Until T15l, a line "K7.4 à K7.6 : le diagnostic arrive dans la prochaine livraison" so the
  tool's final instruction (K7.4) points to an existing place.
- Changed `tools/camera/tests/test_training_doc.py`: the same checks moved to the new section (no check dropped):
  the `video_frames` command parses with its parser, the annotate command (with `--split`, `--frames`) parses with
  its parser, the `docker run` block keeps the flags and read-only data mount, the steps K7.0 to K7.3 and the
  expected texts are named.

## Depends on
T15c, T15g.

## Rules
S10 (container flags), CV15 (the guide says which files are local and never pasted: `frames.json`, the frames
folder).

## Tests
- As listed in Files (doc test).

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
haiku. Why: documentation copied from the root plan and the `--help` of two commands; doc test moved, not
designed.

## Implementation notes
