# T15l: training.md, diagnosis steps (K7.4 to K7.6)

## Goal
Give Kosmo the copy-paste steps to run the diagnosis, paste the report in the chat and open the visual check
(D30 points 2 and 3).

## Files
- Changed `docs/camera/training.md`, section "Contrôle de calibration": adds K7.4 (run `diagnose` in the
  container), K7.5 (paste `diagnostic-report.md` in the chat, numbers and roles only), K7.6 (start the tool with
  `--diagnostic` and do the visual check), in French, copied from root plan "How Kosmo verifies", replacing the
  T15h line "K7.4 à K7.6 : le diagnostic arrive dans la prochaine livraison"; the checklist K7.C gains its visual
  check lines.
- Changed `tools/camera/tests/test_training_doc.py`: the `diagnose` command parses with its parser; the annotate
  command with `--diagnostic` parses; the `docker run` block keeps the S10 flags and the read-only data mount;
  K7.4 to K7.6 named; the guide says `diagnostic.json` is never pasted.

## Depends on
T15h, T15j, T15k.

## Rules
S10, CV15.

## Tests
- As listed in Files (doc test).

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
haiku. Why: documentation copied from the root plan; doc test extended on the T15h pattern.

## Implementation notes
