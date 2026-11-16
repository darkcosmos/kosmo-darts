# T14: CLI and run chain

HELD by D30: the drift guard parts ("refused while beyond") are replaced at P2; chains the held tasks.

## Goal
One command line for Kosmo with every step, including the 13-step run chain of root "Approach, Three trainings, run
chain" (D25, D26, A19). The training doc and the report template are T14b.

## Files
- Added `tools/camera/kosmo_camera/__main__.py` and `cli.py` (`argparse`): `gpu-check`; `drift` (calls T8b `main`,
  writes the fixed `<runs>/drift/` outputs); run chain, all with `--run DIR` (the chain folder, fixed paths, no
  intermediate file argument): `dataset --public none|A|B` (T9; `none` creates the chain, public root
  `<data>/public`), `train --version sans|A|B [--fold cv1|cv2|full]` (T10 `train_folds`), `predict --version`
  (T9o), `pseudo-cal` (T9c; prints thresholds, images labelled and dropped per rule), `pseudo-tip` (T9d; prints
  `tauTip`, acceptance rates, drops per rule, median and p95 px), `compare` (T11 and T13 on the out-of-training
  predictions of every built version, `compare.select_version`, writes T13b `build_comparison` and
  `retained.json` (version, `full` model path and SHA-256 or "à construire") in `compare/`; prints the report path,
  the glosses, each McNemar step (`b`, `c`, `p`, decision), the number and total duration of trainings, "Version
  retenue : sans jeux publics|A|B" and "Étape 13 : à lancer" or "Étape 13 : inutile"); `export [--fp16]` (FP16
  through `ai-edge-quantizer`, optional); `measure --set dev|eval|campaign`; `tune`; `report` (T13b). `split` and
  `annotate` stay their own stdlib modules (T4b, T6a), referenced in `--help`. Common options `--data /data`
  (read-only), `--runs /runs`; `measure`, `export`, `tune` and `report` write a new timestamped folder; the chain
  writes only in its `--run` folder. `measure --set dev --run DIR`: photo metrics from `predictions/<retained>.json`
  and events from the held-out session's fold model (never a `full` model); `--set eval|campaign` calls
  `guard_eval` and uses the `full` model of `frozen.json`. `dataset` and `measure` call `drift.require_clean` first
  and accept `--accept-drift` (CV19).
- Exit codes: a not-built version prints "Version <A|B> non construite : <raison>" and exits 0; `StaleStep` and the
  CV24 refusals print their French message and exit non-zero.
- Changed `tools/camera/Dockerfile` (T10a, `config/` already copied by T8b): entry point `python -m kosmo_camera`,
  header comment updated, nothing else.
- Added tests `tools/camera/tests/test_cli.py`.

## Depends on
T6d, T9d, T10, T13b (and through them T9, T9o, T13).

## Rules
CV4, CV15, CV17, CV18, CV19, CV21 to CV24, D25, D26, A19. `compare` refuses eval and campaign sets (eval only in
K6, final model). The CLI never writes under `--data`; `campaign` reads `captures-eval/` only.

## Tests
- End to end on a synthetic session with `--detector fake` (test-only option, hidden from `--help`): `measure --set
  dev` writes a report; `measure --set eval` without `frozen.json` or with a split not matching `split-lock.json` exits non-zero with a
  clear message.
- Synthetic session with a beyond photo: `drift` prints "Tout sous le seuil : non"; `dataset` exits non-zero with the
  CV19 message; `dataset --accept-drift` succeeds and its manifest records the excluded count.
- `--data` folder unchanged after a full run (hashes).
- Run chain with `--detector fake`, a fake trainer (`--trainer fake`, test-only, hidden from `--help`) and a test
  map over synthetic sets in `<data>/public` (one `tip` set, one `pseudo-tip-box` set): the 13 steps in order build
  `sans`, `A`, `B`, 8 trainings then 9 after step 13 when B is retained; `compare` prints the McNemar steps, the
  retained version and the step-13 line; steps out of order exit non-zero (`dataset --public A` before
  `pseudo-cal`); a file changed between steps exits non-zero with "Étape n à refaire"; no `pseudo-tip-box` folder:
  steps 9 to 11 print "Version B non construite : …", exit 0, and `compare` lists B with its reason (6 trainings);
  block-mode lock or one dev session: step 1 exits non-zero with its message and writes nothing.
- `measure --set dev --run` reads the predictions and fold models; a `full` model is never loaded (recorded).
- `test_dockerfile.py` (T10a) extended: entry point is `python -m kosmo_camera`; `gpu-check` calls `gpu_check`.

## Done when
Python checks of root "Test plan" with `-m slow` green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: wiring of existing pieces with end-to-end tests; the documentation moved to T14b for size.

## Implementation notes
