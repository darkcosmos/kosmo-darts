# T15l: training.md, steps K7.5 to K7.7 (fixed snap, acceptance and re-snap, diagnosis; K7.8 deferred)

## Goal
Replace the placeholder « K7.5 et la suite arrivent dans la prochaine livraison » (T17d) with the copy-paste steps
K7.5 to K7.7 of root plan "How Kosmo verifies": Kosmo only runs commands and pastes reports, within the 10-minute
budget (D48, ADR 0018), no click.

## Files
- Changed `docs/camera/training.md`, section "Contrôle de calibration", in French, copied from root plan "How Kosmo
  verifies" (order of D55, P5 settled): K7.5 (one line `git fetch origin && git switch <branch> && git pull`, the
  real lot 3b-2 PR branch written by this task in place of `<branche du lot 3b-2>`, no rebuild; attention 1 min),
  K7.6 (one line `git pull && ` then the K7.4 command: automatic choice on dev, acceptance and re-snap, paste
  `snap-report.md`; compute time from root "How Kosmo verifies" with the success-path time T17b measured, and the
  rerun from the « Durée prévue d'une relance de K7.6 » line; attention 2 min), K7.7 (only after « Recalage
  accepté »: one line `docker build` (K7.1 image) `&&` the `diagnose` run with `--snapped`, with the sentence that
  the rebuild restarts neither Docker nor WSL and the request not to restart them during a squad session, D54;
  paste `diagnostic-report.md`; attention 2 min). The K7.4 paragraph and the lot 3b line lose their internal
  references (ux finding U6: no D48 to D55, Q15, T15m): the budget line reads « Temps d'attention : 8 min, plus 2 min si
  K7.6 est relancée une fois, soit 10 min au pire » and the compute line gives the real counts and durations only.
  K7.8 is not written (Q15 option 2, D54).
- Same file: a paragraph « Recalage des clics » under K7.7 in plain French: points recalés automatiquement and non
  recalés per role and source; the dispersion `D` is measured by the machine; a non-recalé point keeps the click and
  its human precision `C`, counted apart; if the diagnosis is « non concluant » the squad revises the snap or
  proposes an automated alternative, never new clicks (D43, D48); `R` is information only.
- Changed `tools/camera/tests/test_training_doc.py`: the `diagnose` command with `--snapped` parses with its parser;
  the `docker run` block keeps the S10 flags and the read-only data mount; K7.5 to K7.7 named, no K7.8; K7.5
  holds `git fetch origin`, `git switch` with a name matching the branch pattern of T17d (no `<` left), `git pull`,
  in that order, and no `docker build`; K7.6 starts with `git pull &&`, its command parses with the `snap_report`
  parser, it holds « Durée prévue d'une relance de K7.6 » and no « 30 min »; K7.7 holds `docker build` before
  `docker run` in one `&&` line and the sentence « ne redémarre ni Docker ni WSL »; no « D4 », « D5 », « Q15 »,
  « T15m » in the K7.4 to K7.7 bullets and the lot 3b line; the guide says
  `diagnostic.json` and `snapped-points.json` are never pasted; no step K7.4 to K7.7 asks for a click (the scan
  matches the imperatives « cliquer », « cliquez », « recliquer », « recliquez », case-insensitive, in the step
  bullets K7.4 to K7.7; exempt: the « Recalage des clics » paragraph and the done line of K7.3, which name clicks
  without asking for one); the budget line present and the attention times of K7.4 to K7.7 (written « attention :
  N min ») sum to 8, and 8 plus the K7.6 attention time is at most 10; the section title
  it cites equals the one `diagnose.py` writes (read from the module constant, not retyped).

## Depends on
T17d, T17b (timing note), T17c, T15o.

## Rules
S10, CV15, CV28 (`D`), CV29 ("non concluant" causes), CV31, CV32, D48, D54, D55.

## Tests
- As listed in Files (doc test).

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
haiku. Why: documentation copied from the root plan; doc test extended on the T15h pattern.

## Implementation notes
