"""Plan documents of issue #42 (stdlib only): ADR 0016 status line, ADR 0017 sections, held task files (T15a, A29)."""
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[3]
ADR_16 = ROOT / "docs" / "adr" / "0016-camera-feasibility-tooling.md"
ADR_17 = ROOT / "docs" / "adr" / "0017-per-image-calibration.md"
PLANS = ROOT / "docs" / "plans" / "42-camera-feasibility-0b-1"
RULES_CAMERA = ROOT / "docs" / "rules" / "camera.md"

ADR_16_STATUS = (
    "Status: Not validated and suspended on 2026-11-15 (D30): compensation, threshold and guard are replaced at "
    "plan update P2; see ADR 0017 and rules CV18 to CV19, CV30."
)
ADR_17_TITLE = "# ADR 0017: Per-image calibration and automatic recalibration in the game app (camera phases 1 to 3)"
ADR_17_SECTIONS = ["Context", "Decision", "Verification", "Phase 0b-1 report", "Alternatives rejected", "Consequences"]
HELD_TASKS = ["T9", "T9b", "T9o", "T9c", "T9d", "T10", "T13", "T13b", "T14", "T14b"]
HELD_PREFIX = "HELD by D30:"


def section(text, heading):
    """The lines under the "## heading" of a markdown text, up to the next "## " heading."""
    lines = text.splitlines()
    start = lines.index(f"## {heading}") + 1
    body = []
    for line in lines[start:]:
        if line.startswith("## "):
            break
        body.append(line)
    return body


def first_non_blank(lines):
    return next(line for line in lines if line.strip())


def test_the_adr_0016_tripod_drift_section_starts_with_the_suspended_status_line():
    body = section(ADR_16.read_text(encoding="utf-8"), "Tripod drift")

    assert first_non_blank(body) == ADR_16_STATUS


def test_adr_0017_exists_with_its_title_and_its_proposed_status():
    text = ADR_17.read_text(encoding="utf-8")

    assert text.splitlines()[0] == ADR_17_TITLE
    assert "- Status: Proposed (Kosmo's requirement of 2026-11-15, D30 point 6). Date: 2026-11-15." in text


@pytest.mark.parametrize("heading", ADR_17_SECTIONS)
def test_adr_0017_has_the_sections_of_the_plan_text(heading):
    assert f"\n## {heading}\n" in ADR_17.read_text(encoding="utf-8")


@pytest.mark.parametrize("task_id", HELD_TASKS)
def test_each_held_task_file_starts_with_the_held_by_d30_line(task_id):
    files = sorted(PLANS.glob(f"{task_id}-*.md"))
    assert len(files) == 1, f"expected one task file for {task_id}, found {files}"
    lines = files[0].read_text(encoding="utf-8").splitlines()

    assert lines[0].startswith("# ")
    assert first_non_blank(lines[1:]).startswith(HELD_PREFIX)


def flat(path):
    """A document with every run of white space (line breaks of the wrapped markdown included) as one space."""
    return " ".join(path.read_text(encoding="utf-8").split())


def test_a31_adr_0016_decision_2_says_it_is_amended_by_the_assisted_click_section():
    text = ADR_16.read_text(encoding="utf-8")

    assert "Amended: see section Assisted click (D43, D44)." in flat(ADR_16)
    assert "\n## Assisted click\n" in text


def test_a31_adr_0016_decision_2_lists_the_stdlib_core_with_drift_file_and_strict_json():
    core = "(`config`, `truth`, `split`, `annotations`, `board`, `drift_file`, `strict_json`)"

    assert f"Stdlib-only core {core}" in flat(ADR_16)


def test_a31_cv16_carries_the_after_d39_and_d43_sentence():
    sentence = ("After D39 and D43: the stdlib-only rule covers every module except `snap.py` and the shared decoder "
                "`luma.py`")

    assert sentence in flat(RULES_CAMERA)


def test_a31_cv16_carries_the_control_mode_sentence_with_split_frames_and_the_frame_route():
    sentence = ("Control mode (T15d): `--split` (with `--lock`) and `--frames` are read again at every request and "
                "never written; a split that does not match its lock, or an unreadable `frames.json`, gives one list "
                "warning and switches control (or the video items) off; `GET /api/frame/<session>/<file>` serves "
                "only a file listed in `frames.json` for a listed session.")

    assert sentence in flat(RULES_CAMERA)
