# T15l: training.md, steps K7.5 to K7.8 (fixed snap, acceptance and re-snap, diagnosis, optional visual check)

## Goal
Replace the placeholder « K7.5 et la suite arrivent dans la prochaine livraison » (T17d) with the copy-paste steps
K7.5 to K7.8 of root plan "How Kosmo verifies": Kosmo only runs commands and pastes reports, within the 10-minute
budget (D48, ADR 0018), no click.

## Files
- Changed `docs/camera/training.md`, section "Contrôle de calibration", in French, copied from root plan "How Kosmo
  verifies": K7.5 (git pull and image rebuild), K7.6 (the K7.4 command again: acceptance and re-snap, paste
  `snap-report.md`), K7.7 (run `diagnose` in the container with `--snapped`, paste `diagnostic-report.md`), K7.8
  (optional visual check, per Q15); the budget line « Temps d'attention total : environ 8 min, au plus 10 » with
  each step's attention time and the unattended compute time computed from K7.4's real timing.
- Same file: a paragraph « Recalage des clics » under K7.7 in plain French: points recalés automatiquement and non
  recalés per role and source; the dispersion `D` is measured by the machine; a non-recalé point keeps the click and
  its human precision `C`, counted apart; if the diagnosis is « non concluant » the squad revises the snap or
  proposes an automated alternative, never new clicks (D43, D48); `R` is information only.
- Same file: the checklist K7.C gains the visual check lines only if Q15 keeps K7.8 (marked « facultatif »).
- Changed `tools/camera/tests/test_training_doc.py`: the `diagnose` command with `--snapped` parses with its parser;
  the `docker run` block keeps the S10 flags and the read-only data mount; K7.5 to K7.8 named; the guide says
  `diagnostic.json` and `snapped-points.json` are never pasted; no step K7.4 to K7.7 asks for a click (the scan
  matches the imperatives « cliquer », « cliquez », « recliquer », « recliquez », case-insensitive, in the step
  bullets K7.4 to K7.7; exempt: the « Recalage des clics » paragraph and the done line of K7.3, which name clicks
  without asking for one); the budget line present and the attention times of K7.4 to K7.7 (written « attention :
  N min ») sum to its « environ » value and at most 10; K7.8, if present, is marked « facultatif » and its time
  stated as Q15 decides; the section title
  it cites equals the one `diagnose.py` writes (read from the module constant, not retyped).

## Depends on
T17d, T17c, T15o (and T15k only if Q15 keeps K7.8).

## Rules
S10, CV15, CV28 (`D`), CV29 ("non concluant" causes), CV31, CV32, D48.

## Tests
- As listed in Files (doc test).

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
haiku. Why: documentation copied from the root plan; doc test extended on the T15h pattern.

## Implementation notes
