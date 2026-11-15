# T14: CLI, training doc commands, report template

## Goal
One command line for Kosmo with every step, the full run instructions in `docs/camera/training.md`, and the empty
feasibility report that developer-fix fills from Kosmo's reports.

## Files
- Added `tools/camera/kosmo_camera/__main__.py` and `cli.py` (`argparse`): `gpu-check`, `dataset`, `train`,
  `export [--fp16]` (FP16 through `ai-edge-quantizer`, optional), `measure --set dev|eval`, `tune`, `report`; common
  options `--data /data` (read-only), `--runs /runs`; every run writes in a new timestamped folder under `--runs`;
  `measure` chains T11, T12, T13 and prints only the report path.
- Changed `docs/camera/training.md`: sections "Build the image" (with network, `NANO_SHA256` step), "GPU check",
  "Run" (the exact `docker run` line of root plan "Approach, Training image" with `--tmpfs /tmp:rw,size=8g` and
  `--shm-size=4g`), "Annotate" (`python3.13 -m kosmo_camera.annotate`, open `http://127.0.0.1:8765` in Windows),
  "Dev loop" and "Eval run" (steps K1 to K6 of the root plan), "What to paste in the PR" (the Markdown report only).
- Added `docs/camera/feasibility-report.md`: template with sections Summary (go or no-go), Data, Model (SHA-256,
  size, rebuild command, input size, FP32 or FP16 and why for the Pixel 7), Calibration points and limit (why),
  Results per threshold, Breakdowns, Confidence threshold proposed, Conclusion, Proposal if no-go (issue list).
- Added tests `tools/camera/tests/test_cli.py`.

## Depends on
T10, T13.

## Rules
CV4, CV15. The CLI never writes under `--data`; `measure --set eval` calls `guard_eval` first.

## Tests
- End to end on a synthetic session with `--detector fake` (test-only option, hidden from `--help`): `measure --set
  dev` writes a report; `measure --set eval` without `frozen.json` exits non-zero with a clear message.
- Every subcommand of `--help` appears in `training.md` and every command in `training.md` parses with the CLI.
- `--data` folder unchanged after a full run (hashes).

## Done when
`.venv/bin/python -m pytest tools/camera -q` and `-m slow -q` green, `kosmo-detekt` exit 0, `./gradlew build` green
(unchanged Android build before the PR).

## Model
sonnet. Why: wiring of existing pieces and their documentation.

## Implementation notes
