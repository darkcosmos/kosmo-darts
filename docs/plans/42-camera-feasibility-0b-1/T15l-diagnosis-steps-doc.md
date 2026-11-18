# T15l: training.md, diagnosis steps (K7.4 to K7.6)

## Goal
Give Kosmo the copy-paste steps to run the diagnosis, paste the report in the chat and open the visual check
(D30 points 2 and 3).

## Files
- Changed `docs/camera/training.md`, section "Contrôle de calibration": adds K7.4 (run `diagnose` in the
  container), K7.5 (paste `diagnostic-report.md` in the chat, numbers and roles only), K7.6 (start the tool with
  `--diagnostic` and do the visual check), in French, copied from root plan "How Kosmo verifies", replacing the
  T15h line "K7.4 à K7.6 : le diagnostic arrive dans la prochaine livraison"; the checklist K7.C gains its visual
  check lines (alternation, keys 1, 2, 3, N, Espace, arrows in dart order, "Photo i / n" in that order, U24), and
  loses the sentence that says the visual check is not there yet (they were taken out of K7.C at T15h: U41).
- Same file: a short paragraph « Recalage des clics » under K7.4 explaining that section of the report (T15i) in
  plain French: points recalés and non recalés counted per role and source with their reasons; the dispersion `D`
  is measured by the machine (recalage relancé depuis 8 départs décalés de 3 px autour de chaque point), p95
  compared with the target 0,25 mm; it, not Kosmo's hand, bounds the reference (D39); a p95 above 0,25 mm or
  fewer than 30 complete snapped dev photo controls makes the verdict « non concluant », and then the squad
  revises the snap and reruns the diagnosis on the same stored clicks, no new clicks (D43); the "accord des clics
  de segment" line (`R`) is information only.
- Changed `tools/camera/tests/test_training_doc.py`: the `diagnose` command parses with its parser; the annotate
  command with `--diagnostic` parses; the `docker run` block keeps the S10 flags and the read-only data mount;
  K7.4 to K7.6 named; the guide says `diagnostic.json` is never pasted; the « Recalage des clics » paragraph is
  present and names « 0,25 mm » and « non concluant »; the section title it cites equals the one `diagnose.py`
  writes (read from the module constant, not retyped).

## Depends on
T15h, T15j, T15k.

## Rules
S10, CV15, CV28 (`D`), CV29 ("non concluant" causes), CV31.

## Tests
- As listed in Files (doc test).

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
haiku. Why: documentation copied from the root plan; doc test extended on the T15h pattern.

## Implementation notes
