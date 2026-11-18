# T17d: training.md, step K7.4 (snapping diagnosis), manual steps retired, ADR 0018 and 0019 doc test

## Goal
Give Kosmo the one command of K7.4 and its paste point, mark the manual-entry steps as done and never to be redone
(D48), and assert the two new ADRs in the plan doc test.

## Files
- Changed `docs/camera/training.md`, section "Contrôle de calibration": K7.3 becomes the done line of root plan
  "How Kosmo verifies" (« fait, environ 1 h ; ne pas refaire ») with its instructions removed; the line
  « K7.4 à K7.6 : le diagnostic arrive dans la prochaine livraison » is replaced by K7.4 of "How Kosmo verifies" and
  the line « K7.5 et la suite arrivent dans la prochaine livraison »; K7.4 starts with `git fetch origin`, then
  `git switch <branch>` and `git pull`, where this task writes the real name of the lot 3b-1 PR branch (the branch
  it is built on) in place of `<branche du lot 3b-1>` (D54); K7.4 names the line « Durée prévue de K7.6 »; before K7.4, the « Lot 3b » line of
  root "How Kosmo verifies" with its budget (8 min, plus 2 min if K7.6 is rerun once, 10 min at worst, K7.8
  outside it while Q15 is not reopened, D54); section "Annotate": first line « Étape K2
  faite ; elle ne s'applique à aucune nouvelle session : leur vérité terrain est produite automatiquement (ADR
  0018) »; checklist K7.C: first line « Faite au lot 3a ; constats repris en D49 ; ne pas refaire » (the K7.C
  click checks are not redone; the visual check lines come with K7.8 only, T15l); section "Confirmation campaign
  protocol": the line « Aucune saisie manuelle : la vérité terrain d'une campagne est produite automatiquement ;
  vous ne validez que les cas signalés par l'outil, 10 min au plus par campagne (ADR 0018). »; a link to
  `docs/adr/0018-automatic-ground-truth.md` and `docs/adr/0019-capture-framing.md`.
- Changed `tools/camera/tests/test_training_doc.py`: K7.4 holds `git fetch origin`, then `git switch` followed by
  a name matching `^(feat|fix|chore|build|test|docs)/[a-z0-9.-]+$` (no `<` left), then `git pull`, in that order;
  K7.4 names « Durée prévue de K7.6 »; the « Lot 3b » budget line holds « 8 min », « 2 min » and « 10 min »;
  the K7.4 command parses with the `snap_report` parser, starts
  with `~/kosmo-tool-venv/bin/python`, has no `docker`; K7.3 and K7.4 hold no instruction to click (scan of the
  imperatives « cliquer », « cliquez », « recliquer », « recliquez », case-insensitive, in those two bullets; the
  K7.3 done line may name « clics »); K7.4 states « attention : 3 min »; the guide says `snap-report.md` is pasted
  and nothing else; K7.5 and K7.6 are not yet printed (only the line « K7.5 et la suite arrivent dans la
  prochaine livraison », T15l prints them with lot 3b-2).
- Changed `tools/camera/tests/test_plan_docs.py`: ADR 0018 and ADR 0019 exist with their title, status line and
  sections (as for ADR 0017; ADR 0018 includes « Re-snap file »).

## Depends on
T17a.

## Rules
D48 (ADR 0018), D51 (ADR 0019), D54, CV15, CV32.

## Tests
- As listed in Files (doc tests).

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
haiku. Why: documentation copied from the root plan and doc tests on the T15h and T15a patterns, no design choice.

## Implementation notes
