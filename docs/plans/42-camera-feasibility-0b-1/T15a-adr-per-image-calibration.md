# T15a: ADR 0017 (per-image calibration in the game app) and K5 suspended

## Goal
Record now Kosmo's product requirement for the camera phases after 0b (D30 point 6) as ADR 0017, and mark the K5
drift loop of `training.md` and the ADR 0016 "Tripod drift" decisions as suspended so nobody follows a stale
instruction or decision (D30, D31, A28).

## Files
- Added `docs/adr/0017-per-image-calibration.md`: the text of root plan "ADR 0017 text", copied as is.
- Changed `docs/camera/training.md`, section "Dérive et recalibration": insert at the top of the section, before
  K5.0, the paragraph of root plan "How Kosmo verifies", item "K5 (suspended)", in French, as given there. The K5
  steps below stay unchanged (their doc test still parses them; T15h replaces the section).
- Changed `docs/rules/camera.md`: CV18 and CV19 each gain the sentence of root plan "Rules", items "CV18 status"
  and "CV19 status" (copied).
- Changed `docs/adr/0016-camera-feasibility-tooling.md`, section "Tripod drift" (A28): first line of the section,
  copied from root plan "ADR 0017 text", item "ADR 0016 status line". Plan update P2 closes it (replaced or
  confirmed).
- Added `tools/camera/tests/test_plan_docs.py` (stdlib only, doc test): the ADR 0016 "Tripod drift" section starts
  with that status line; ADR 0017 exists with its title and the sections of root "ADR 0017 text"; every held task
  file (T9, T9b, T9o, T9c, T9d, T10, T13, T13b, T14, T14b) has as first line after its title a line starting with
  "HELD by D30:" (A29).

## Depends on
- (none: docs only; first task of batch 3)

## Rules
CV18, CV19 (status sentences), D30, D31, D32. No production code.

## Tests
- `test_plan_docs.py` as listed in Files; each check fails when its line is removed (checked once by hand, noted).
- `test_training_doc.py` stays green unchanged (the K5 blocks are kept).

## Done when
`.venv/bin/python -m pytest tools/camera -q` and `.venv/bin/python -m compileall -q tools/camera` green (previous count
plus the `test_plan_docs.py` tests), `kosmo-detekt` exit 0.

## Model
haiku. Why: text copied from the root plan, no design choice; the doc test only checks fixed lines.

## Implementation notes

- Done as written. Docs only, no production code.
- `docs/adr/0017-per-image-calibration.md` (new): text of "ADR 0017 text". Sections are `## Context`, `## Decision`,
  `## Verification`, `## Phase 0b-1 report`, `## Alternatives rejected`, `## Consequences`; `Status` stays a bullet
  as in ADR 0016. Wording copied; bullets reflowed into paragraphs.
- `docs/adr/0016-camera-feasibility-tooling.md`: the "ADR 0016 status line" is the first line of "## Tripod drift".
- `docs/rules/camera.md`: "CV18 status" appended to the end of CV18, "CV19 status" to the end of CV19 (the CV19 text
  continues to the lines after its first paragraph).
- `docs/camera/training.md`: the "K5 (suspended)" paragraph is the first paragraph of "## Dérive et recalibration",
  before the K5 intro and K5.0. K5.0 to K5.4 are unchanged (`test_training_doc.py` green unchanged).
- The `HELD by D30:` first line already exists in the ten held task files (T9, T9b, T9o, T9c, T9d, T10, T13, T13b,
  T14, T14b): no edit there, the test only asserts it.
- `tools/camera/tests/test_plan_docs.py`: 18 cases (ADR 0016 status line 1, ADR 0017 title and status 1, ADR 0017
  sections 6, held task lines 10). Stdlib only. Written before the docs: red on the 8 ADR cases, green on the 10
  held cases (their lines already existed).
- Each check seen to fail when its line is removed, then pass again. Done on a scratch copy of `docs/` and
  `tools/camera/` (the working tree was not touched): ADR 0016 status line removed: 1 failed (`-k tripod`); ADR 0017
  title removed: 1 failed; ADR 0017 status bullet removed: 1 failed; each of the six `## ` section headings removed:
  1 failed each (`-k sections_of_the_plan_text`); `HELD by D30:` line removed from T9 and from T14b: 1 failed each
  (node id `[T9]`, `[T14b]`), 1 passed with the line back.
- Checks on the head of this task: `.venv/bin/python -m pytest -q -m "not slow"` from `tools/camera`: 756 passed,
  3 deselected (738 before, plus the 18 cases). Full run, `-o addopts=""`: 759 passed. `.venv/bin/python -m pytest
  tools/camera -q` from the repo root: 756 passed, 3 deselected. `.venv/bin/python -m compileall -q tools/camera`:
  exit 0. `kosmo-detekt`: exit 0.
- System `python3` (3.14.4): no pytest installed. The 18 cases were run with a stub `pytest` module providing only
  `mark.parametrize`, under `python3 -I`: 18 passed, 0 failed. The module imports nothing outside the stdlib but
  `pytest` itself.
- Later tasks: T15h replaces the K5 section and its doc test, not this paragraph; `test_plan_docs.py` keeps the
  "HELD by D30:" check until the held tasks are replaced.
