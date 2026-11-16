# T14b: Training doc and feasibility report template

HELD by D30: revised at P2; documents the held tasks (root plan "Tasks").

## Goal
Write Kosmo's run instructions in `docs/camera/training.md` (French) for the T14 CLI, with "Trois entraînements" as
one numbered block, and the empty feasibility report that developer-fix fills from Kosmo's reports. Split out of
T14 for size (D29).

## Files
- Changed `docs/camera/training.md` (commands in the K1 to K6 order of root "How Kosmo verifies"):
  - "Build the image" and "GPU check" (T2, PR A) switch to the `gpu-check` subcommand and say to rebuild the image.
  - "Run": the exact `docker run` line of root "Approach, Training image" (`--tmpfs /tmp:rw,size=8g`,
    `--shm-size=4g`). "Annotate": `python3 -m kosmo_camera.annotate`, open `http://127.0.0.1:8765` in Windows.
  - "Dérive et recalibration" (T6d): its `python -m kosmo_camera.drift` line becomes the `drift` subcommand; adds
    what `--accept-drift` does and that it is recorded.
  - "Jeux publics": folders in `~/kosmo-data/public/<dossier>/`, read-only; statuses and attribution in
    `datasets.md`; class map; pseudo-labels of CV23 in two sentences.
  - "Trois entraînements" (K5.6, UX of the design review): the glosses "A : photos de Kosmo + jeux publics de
    détection ; B : A + pointes v17 pseudo-étiquetées"; why two sessions and cross-validation in three sentences
    (sessions named "dev 1" and "dev 2" only); one numbered block of the 13 steps of root "Approach, Three
    trainings, run chain", each with its command, its indicative duration and what to paste (steps 1, 4, 8, 12);
    the line « Une version non construite n'arrête pas la chaîne : passer à l'étape suivante » ; step 13 run only
    when step 12 prints "Étape 13 : à lancer"; the run folder `~/kosmo-runs/chaine` with "ne rien déplacer ni
    modifier dedans"; what "Étape n à refaire" means (rerun step n and the following ones; completed folds are
    kept when unchanged); the training count (8, 9 with step 13) and total about 5 to 6 h; eval never used here.
  - "Dev loop" (K5.7: `tune` then `measure --set dev --run`), "Eval run" (K6, final model only), "What to paste in
    the PR" (Markdown reports only: last drift report, comparison section, dev report, eval report).
- Added `docs/camera/feasibility-report.md`: template with sections Summary (go or no-go), Data (cross-validation
  by session, roles and judged darts), Model (SHA-256, size, rebuild command, input size, FP32 or FP16 and why for
  the Pixel 7), Public data (sets, statuses and licences, attribution in `datasets.md`, calibration pseudo-labels
  and why option (a), v17 figures and drops per rule), Version selection (three versions, McNemar steps with `b`,
  `c`, `p`, retained version; trainings count and durations; CV22 to CV24 limits), Calibration points and limit,
  Results per threshold, Breakdowns, Confidence threshold proposed (out-of-training tuning only, T1 of the design
  review), Limits (from T13b), Path proposal or confirmation campaign (D12), Conclusion.
- Added tests `tools/camera/tests/test_training_doc.py`.

## Depends on
T14 (CLI parser).

## Rules
CV4, CV15, CV19, CV21 to CV24, D25, D26, D28, A19. French prose, commands as T14 parses them; no session name.

## Tests
- Every subcommand of the CLI `--help` appears in `training.md`; every command line in `training.md` parses with
  the T14 parser.
- The "Trois entraînements" block holds 13 numbered steps in the root order, each with a duration and a paste
  instruction where the root says so, and the not-built line; the template holds every section above.

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0, `./gradlew build` green (unchanged Android build
before the PR).

## Model
haiku. Why: documentation copied from the root plan and the T14 CLI, with doc parse tests; no design choice.

## Implementation notes
