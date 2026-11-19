"""Plan documents of issue #42 (stdlib only): ADR statuses and sections, the registration plan, cancelled task files."""
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[3]
ADR_16 = ROOT / "docs" / "adr" / "0016-camera-feasibility-tooling.md"
ADR_17 = ROOT / "docs" / "adr" / "0017-per-image-calibration.md"
ADR_20 = ROOT / "docs" / "adr" / "0020-global-per-image-registration.md"
PLAN = ROOT / "docs" / "plans" / "42-camera-feasibility-0b-1.md"
PLANS = ROOT / "docs" / "plans" / "42-camera-feasibility-0b-1"
RULES_CAMERA = ROOT / "docs" / "rules" / "camera.md"

ADR_16_STATUS = (
    "Status: Superseded by ADR 0020 on 2026-11-18: the drift measure and its compensation are stopped (rules CV18, "
    "CV19); code removed by task C1. Was: not validated and suspended on 2026-11-15 (D30)."
)
ADR_16_ASSISTED_STATUS = (
    "Status: Superseded by ADR 0020 on 2026-11-18: the snapping of clicks is stopped (rule CV31, 0 of 697 real photo "
    "points snapped); code removed by task C1."
)
ADR_17_TITLE = "# ADR 0017: Per-image calibration and automatic recalibration in the game app (camera phases 1 to 3)"
ADR_17_SECTIONS = ["Context", "Decision", "Verification", "Phase 0b-1 report", "Alternatives rejected", "Consequences"]
ADR_17_STATUS = ("- Status: Superseded by ADR 0020 on 2026-11-18 (its requirement on the game app is carried in ADR "
                 "0020 decision 6). Was: Proposed (Kosmo's requirement of 2026-11-15, D30 point 6). Date: 2026-11-15.")
ADR_20_TITLE = "# ADR 0020: Global per-image registration of the board (camera phase 0b-1 and the game app)"
ADR_20_SECTIONS = ["Context", "Decision", "Alternatives rejected", "Consequences"]
CANCELLED_TASKS = ["T9", "T9b", "T9c", "T9d", "T9o", "T10", "T11", "T12", "T13", "T13b", "T14", "T14b",
                   "T15i", "T15j", "T15k", "T15l", "T16a", "T16b", "T16c"]
CANCELLED_PREFIX = "CANCELLED (2026-11-18):"
PLAN_SECTIONS = ["Goal", "Scope and out of scope", "Approach", "Files and types added or changed",
                 "Data read, stored or exposed", "Screens and states", "Test plan", "How Kosmo verifies", "Tasks",
                 "Decisions", "Open questions"]
PLAN_MAX_LINES = 300


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


def test_the_adr_0016_assisted_click_section_starts_with_the_superseded_status_line():
    body = section(ADR_16.read_text(encoding="utf-8"), "Assisted click")

    assert first_non_blank(body) == ADR_16_ASSISTED_STATUS


def test_adr_0017_keeps_its_title_and_is_superseded_by_adr_0020():
    text = ADR_17.read_text(encoding="utf-8")

    assert text.splitlines()[0] == ADR_17_TITLE
    assert ADR_17_STATUS in flat(ADR_17)


@pytest.mark.parametrize("heading", ADR_17_SECTIONS)
def test_adr_0017_has_the_sections_of_the_plan_text(heading):
    assert f"\n## {heading}\n" in ADR_17.read_text(encoding="utf-8")


@pytest.mark.parametrize("task_id", CANCELLED_TASKS)
def test_each_task_file_not_built_starts_with_the_cancelled_line(task_id):
    files = sorted(PLANS.glob(f"{task_id}-*.md"))
    assert len(files) == 1, f"expected one task file for {task_id}, found {files}"
    lines = files[0].read_text(encoding="utf-8").splitlines()

    assert lines[0].startswith("# ")
    assert first_non_blank(lines[1:]).startswith(CANCELLED_PREFIX)


def flat(path):
    """A document with every run of white space (line breaks of the wrapped markdown included) as one space."""
    return " ".join(path.read_text(encoding="utf-8").split())


def test_adr_0016_decision_2_has_no_amended_pointer_and_keeps_the_assisted_click_section_as_history():
    text = ADR_16.read_text(encoding="utf-8")

    assert "Amended: see section Assisted click" not in flat(ADR_16)
    assert "\n## Assisted click\n" in text


def test_adr_0016_decision_2_lists_the_stdlib_core_without_drift_file():
    core = "(`config`, `truth`, `split`, `annotations`, `board`, `strict_json`)"

    assert f"Stdlib-only core {core}" in flat(ADR_16)


def test_cv16_says_the_tool_is_stdlib_local_and_enters_the_tips_only():
    text = flat(RULES_CAMERA)

    assert ("CV16 Annotation tool (N24, C1 to C1c): stdlib only, `127.0.0.1` only, no external resource. Kosmo enters "
            "the dart tips only (one point per judged dart, CV5, on its photo or listed video frame).") in text


def test_cv16_says_calibration_control_snapping_drift_and_journey_are_not_in_the_tool():
    sentence = ("calibration and control entry, snapping, drift and the journey are not in the tool. Captures are "
                "read-only.")

    assert sentence in flat(RULES_CAMERA)


def test_cv16_says_the_frame_route_serves_only_a_listed_file_of_a_listed_session():
    sentence = ("`GET /api/frame/<session>/<file>` serves only a file listed in `frames.json` for a listed session.")

    assert sentence in flat(RULES_CAMERA)


def test_cv16_says_the_removed_options_exit_with_status_2():
    assert "(`--split`, `--lock`, `--drift`) exit with status 2." in flat(RULES_CAMERA)


def test_cv20_and_cv25_are_read_and_kept_and_no_longer_entered_in_the_tool():
    assert flat(RULES_CAMERA).count("No longer entered in the tool (C1, Q1 B); read and kept.") == 2


def test_adr_0020_exists_with_its_title_proposed_status_and_supersedes_line():
    text = ADR_20.read_text(encoding="utf-8")

    assert text.splitlines()[0] == ADR_20_TITLE
    assert "- Status: Proposed (Kosmo's strategy of 2026-11-18 on issue #42" in text
    supersedes = ("- Supersedes: ADR 0017 (per-image calibration), decision 3 (RF-DETR Nano with calibration classes) "
                  "and the sections \"Assisted click\" and \"Tripod drift\" of ADR 0016, and the sections \"Phase 0b-1\" and \"Re-snap file\" of ADR 0018.")
    assert supersedes in flat(ADR_20)


@pytest.mark.parametrize("heading", ADR_20_SECTIONS)
def test_adr_0020_has_its_sections(heading):
    assert f"\n## {heading}\n" in ADR_20.read_text(encoding="utf-8")


def test_the_registration_plan_has_its_sections_in_order_and_stays_short():
    lines = PLAN.read_text(encoding="utf-8").splitlines()
    headings = [line[3:] for line in lines if line.startswith("## ")]

    assert headings == PLAN_SECTIONS
    assert len(lines) <= PLAN_MAX_LINES


DONE_TASKS = ["T1", "T2", "T3", "T4", "T4b", "T5", "T5b", "T6a", "T6b", "T6c", "T6d", "T7", "T8", "T8b", "T9p",
              "T10a", "T15a", "T15b", "T15c", "T15d", "T15e", "T15f", "T15g", "T15h", "T15m", "T15n"]


def test_no_phase_1_task_file_is_written_before_kosmo_mounts_the_images():
    """N19: only done, cancelled and C1 to C1c task files exist; the phase 1 prototype row has no file (N18)."""
    ids = sorted(path.name.split("-", 1)[0] for path in PLANS.glob("*.md"))

    assert ids == sorted(DONE_TASKS + CANCELLED_TASKS + ["C1", "C1b", "C1c"])
    assert "| P1 | Phase 1 prototype: registration R on the real images, a number vs Kosmo's clicks | not written (N19) |" \
        in PLAN.read_text(encoding="utf-8")


def test_the_plan_records_kosmo_answers_q1_b_q2_a_q3_a_and_has_no_open_question_left():
    text = " ".join(PLAN.read_text(encoding="utf-8").split())

    assert "| N23 | Kosmo on #42, 2026-11-19: plan approved; Q1 B" in text
    assert "between R and the clicks, click noise included (Q2 A)" in text  # phase 1 gate wording
    assert "the dev sessions only (17:44 and 18:03, Q3 A): 4 series of 3 consecutive photos per session" in text
    assert "- Q1 to Q3: answered by Kosmo on 2026-11-19 (N23)." in text


ADR_18 = ROOT / "docs" / "adr" / "0018-automatic-ground-truth.md"
ADR_19 = ROOT / "docs" / "adr" / "0019-capture-framing.md"
ADR_18_TITLE = ("# ADR 0018: Automatic ground truth and a 10-minute manual budget for the camera work "
                "(phase 0b-1 and later)")
ADR_18_STATUS = "- Status: Accepted (Kosmo's firm constraint of 2026-11-18, D48). Date: 2026-11-18."
ADR_18_SECTIONS = ["Context", "Decision", "Phase 0b-1", "Re-snap file", "Alternatives rejected", "Consequences"]
ADR_19_TITLE = "# ADR 0019: The camera preview shows exactly the analysed or recorded field (Kosmo Capture and game app)"
ADR_19_STATUS = "- Status: Accepted as a requirement (Kosmo, 2026-11-18, D51); no implementation yet. Date: 2026-11-18."
ADR_19_SECTIONS = ["Context", "Decision", "Alternatives rejected", "Consequences"]


def test_adr_0018_exists_with_its_title_and_its_accepted_status():
    text = ADR_18.read_text(encoding="utf-8")

    assert text.splitlines()[0] == ADR_18_TITLE
    assert ADR_18_STATUS in text


@pytest.mark.parametrize("heading", ADR_18_SECTIONS)
def test_adr_0018_has_the_sections_of_the_plan_text(heading):
    assert f"\n## {heading}\n" in ADR_18.read_text(encoding="utf-8")


def test_adr_0019_exists_with_its_title_and_its_accepted_requirement_status():
    text = ADR_19.read_text(encoding="utf-8")

    assert text.splitlines()[0] == ADR_19_TITLE
    assert ADR_19_STATUS in text


@pytest.mark.parametrize("heading", ADR_19_SECTIONS)
def test_adr_0019_has_the_sections_of_the_plan_text(heading):
    assert f"\n## {heading}\n" in ADR_19.read_text(encoding="utf-8")


@pytest.mark.parametrize("rule", ["CV18", "CV19", "CV31"])
def test_camera_rules_mark_the_stopped_rules_and_their_removed_code(rule):
    assert f"- {rule} Stopped on 2026-11-18 (ADR 0020). Code removed by C1." in flat(RULES_CAMERA)


def test_camera_rules_record_the_retired_and_reserved_rule_numbers():
    text = flat(RULES_CAMERA)

    assert "CV22 to CV24 and CV28 to CV30, defined only in the replaced plan, are retired" in text
    assert "these numbers are not reused. New rules start at CV33." in text


@pytest.mark.parametrize("heading", ["Phase 0b-1", "Re-snap file"])
def test_adr_0018_marks_the_sections_superseded_by_adr_0020(heading):
    body = section(ADR_18.read_text(encoding="utf-8"), heading)

    assert first_non_blank(body).startswith("Superseded by ADR 0020 on 2026-11-18")
