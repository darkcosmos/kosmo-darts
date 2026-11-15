# T14: CLI, training doc commands, report template

## Goal
One command line for Kosmo with every step, the full run instructions in `docs/camera/training.md`, and the empty
feasibility report that developer-fix fills from Kosmo's reports.

## Files
- Added `tools/camera/kosmo_camera/__main__.py` and `cli.py` (`argparse`): `gpu-check`, `drift` (calls T8b `main`,
  writes the fixed `<runs>/drift/` outputs, not a timestamped folder), `dataset [--public none|all|SLUG,...]`
  (default `none`; public root `<data>/public`), `train`, `compare --without RUN --with RUN` (two export runs;
  measures both on the dev photos of the shared valid subset with T11 and T13, calls `compare.select_version`,
  writes T13b `build_comparison` and `retained.json` (model path and SHA-256) in a new run folder, prints the report
  path and "Version retenue : sans|avec jeux publics"; `--with` optional: without it, the without-public version
  alone, CV22),
  `export [--fp16]` (FP16 through `ai-edge-quantizer`, optional), `measure --set dev|eval|campaign`, `tune`, `report`
  (T13b); `split` and `annotate` stay their own stdlib modules (T4b, T6a) and are referenced in `--help` and docs; common
  options `--data /data` (read-only), `--runs /runs`; every run writes in a new timestamped folder under `--runs`;
  `measure` chains T8b, T11, T12, T13, T13b and prints only the report path; `--set eval|campaign` calls
  `guard_eval`; `dataset` and `measure` call `drift.require_clean` first and accept `--accept-drift` (CV19).
- Changed `tools/camera/Dockerfile` (T10a, `config/` already copied by T8b): entry point `python -m kosmo_camera`,
  header comment updated, nothing else.
- Changed `docs/camera/training.md` (commands in the K1 to K6 order of root "How Kosmo verifies"): "Build the image"
  and "GPU check" (written by T2 in PR A) switch to the `gpu-check` subcommand and say to rebuild the image for
  PR B; new sections "Run" (the exact `docker run` line of root plan "Approach, Training image" with `--tmpfs /tmp:rw,size=8g` and
  `--shm-size=4g`), "Annotate" (`python3 -m kosmo_camera.annotate`, open `http://127.0.0.1:8765` in Windows),
  "Dérive et recalibration" (written by T6d for batch 1: its `docker run ... python -m kosmo_camera.drift` line
  becomes the `drift` subcommand, and it says to rebuild the image; adds what `--accept-drift` does and that it is
  recorded), "Jeux publics" (download in COCO JSON into `~/kosmo-data/public/<slug>/`, attribution in
  `datasets.md`, class map), "Avec ou sans jeux publics" (K5.6: the two `dataset`, `train`, `export` chains and
  `compare`; eval never used here), "Dev loop" (K5.7, on the retained model of `retained.json`) and "Eval run" (K6,
  retained model only), "What to paste in the PR" (the Markdown reports only: last drift report, comparison
  section, then dev and eval reports).
- Added `docs/camera/feasibility-report.md`: template with sections Summary (go or no-go), Data, Model (SHA-256,
  size, rebuild command, input size, FP32 or FP16 and why for the Pixel 7), Public data (sets and licences,
  attribution in `datasets.md`, comparison figures and retained version, CV22), Calibration points and limit (why),
  Results per threshold, Breakdowns, Confidence threshold proposed, Limits (from T13b), Path proposal or confirmation
  campaign (D12), Conclusion.
- Added tests `tools/camera/tests/test_cli.py`.

## Depends on
T6d, T9, T10, T13b.

## Rules
CV4, CV15, CV17, CV18, CV19, CV21, CV22. `compare` refuses eval and campaign sets (eval only in K6, retained model). The CLI never writes under `--data`; `measure --set eval|campaign` calls `guard_eval` first;
`campaign` reads `captures-eval/` only.

## Tests
- End to end on a synthetic session with `--detector fake` (test-only option, hidden from `--help`): `measure --set
  dev` writes a report; `measure --set eval` without `frozen.json` or with a split not matching `split-lock.json` exits non-zero with a
  clear message.
- Synthetic session with a beyond photo: `drift` prints "Tout sous le seuil : non"; `dataset` exits non-zero with the
  CV19 message; `dataset --accept-drift` succeeds and its manifest records the excluded count.
- Every subcommand of `--help` appears in `training.md` and every command in `training.md` parses with the CLI.
- `--data` folder unchanged after a full run (hashes).
- Public data: `dataset --public all` with a synthetic public set in `<data>/public` and a test map; `compare` on two
  fake-detector exports writes the comparison and `retained.json`; `compare` without `--with` says "sans jeux
  publics seulement"; exports from datasets with different `kosmoPartSha256` exit non-zero.
- `test_dockerfile.py` (T10a) extended: entry point is `python -m kosmo_camera`; `gpu-check` calls `gpu_check`.

## Done when
Python checks of root "Test plan" with `-m slow` green, `kosmo-detekt` exit 0, `./gradlew build` green
(unchanged Android build before the PR).

## Model
sonnet. Why: wiring of existing pieces and their documentation.

## Implementation notes
