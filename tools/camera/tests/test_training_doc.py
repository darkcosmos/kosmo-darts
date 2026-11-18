"""The "Contrôle de calibration" section of docs/camera/training.md (K7): its commands parse with the real parsers (S10)."""
import argparse
import re
import shlex
import subprocess
import sys
from pathlib import Path

import pytest

from test_requirements import _parse

DOC = Path(__file__).resolve().parents[3] / "docs" / "camera" / "training.md"
VIDEO_MARK = "kosmo_camera.video_frames"
ANNOTATE_MARK = "kosmo_camera.annotate"
SNAP_MARK = "kosmo_camera.snap_report"
BRANCH = re.compile(r"^(feat|fix|chore|build|test|docs)/[a-z0-9.-]+$")


class Parsed(Exception):
    def __init__(self, namespace):
        super().__init__("parsed")
        self.namespace = namespace


def section():
    text = DOC.read_text(encoding="utf-8")
    match = re.search(r"^## Contrôle de calibration\n(.*?)(?=^## )", text, re.DOTALL | re.MULTILINE)
    assert match, "section 'Contrôle de calibration' not found"
    return match.group(1)


def k71b_step():
    step = section().split("K7.1b.", 1)[1].split("K7.2.", 1)[0]
    assert step.strip(), "K7.1b not found"
    return step


def k73_step():
    step = section().split("\nK7.3. ", 1)[1].split("\nK7.4. ", 1)[0]
    assert step.strip(), "K7.3 not found"
    return step


def k74_step():
    step = section().split("\nK7.4. ", 1)[1].split("\nK7.5 et la suite", 1)[0]
    assert step.strip(), "K7.4 not found"
    return step


def budget_line():
    lines = [line for line in section().splitlines() if line.startswith("Lot 3b (")]
    assert len(lines) == 1, "the lot 3b budget line is missing"
    return lines[0]


def blocks():
    return re.findall(r"```bash\n(.*?)```", section(), re.DOTALL)


def command_with(mark):
    found = [b.replace("\\\n", " ").strip() for b in blocks() if mark in b]
    assert len(found) == 1, mark
    assert "\n" not in found[0]  # one copy-paste line
    return found[0]


def parse_with(monkeypatch, main, argv):
    original = argparse.ArgumentParser.parse_args

    def capture(self, args=None, namespace=None):
        raise Parsed(original(self, args, namespace))

    monkeypatch.setattr(argparse.ArgumentParser, "parse_args", capture)
    with pytest.raises(Parsed) as caught:
        main(argv)
    return caught.value.namespace


def test_the_video_frames_command_parses_with_its_parser(monkeypatch):
    from kosmo_camera import video_frames
    words = shlex.split(command_with(VIDEO_MARK))
    argv = words[words.index("kosmo_camera.video_frames") + 1:]
    namespace = parse_with(monkeypatch, video_frames.main, argv)
    assert (str(namespace.captures), str(namespace.annotations), str(namespace.split), str(namespace.out)) \
        == ("/data/captures", "/data/annotations", "/runs/split.json", "/runs/frames")


def test_k73_is_a_done_line_with_no_command_and_no_annotate_block():
    assert not any(ANNOTATE_MARK in b for b in blocks())  # K7.3 instructions retired (D48, ADR 0018)
    step = k73_step()
    assert "fait, environ 1 h" in step and "Ne pas refaire" in step
    assert "```" not in step


def test_the_k74_command_parses_with_the_snap_report_parser_without_lock(monkeypatch):
    from kosmo_camera import snap_report
    command = command_with(SNAP_MARK)
    assert command.startswith("~/kosmo-tool-venv/bin/python -m kosmo_camera.snap_report ")
    assert "docker" not in command and "--lock" not in command
    words = shlex.split(command)
    argv = words[words.index("kosmo_camera.snap_report") + 1:]
    namespace = parse_with(monkeypatch, snap_report.main, argv)
    assert namespace.lock == snap_report.DEFAULT_LOCK and namespace.lock.is_file()  # the committed default
    paths = (namespace.captures, namespace.annotations, namespace.split, namespace.frames, namespace.out)
    assert tuple(str(p) for p in paths) == ("~/kosmo-data/captures", "~/kosmo-data/annotations",
                                            "~/kosmo-runs/split.json", "~/kosmo-runs/frames", "~/kosmo-runs/snap")


def test_k74_git_commands_fetch_then_switch_to_the_real_branch_then_pull():
    block = re.findall(r"```bash\n(.*?)```", k74_step(), re.DOTALL)[0]
    lines = block.strip().splitlines()
    assert len(lines) == 3 and lines[0] == "git fetch origin" and lines[2] == "git pull"
    switch = lines[1].split(" ")
    assert switch[:2] == ["git", "switch"] and len(switch) == 3
    assert BRANCH.match(switch[2]), switch[2]
    assert "<" not in k74_step()  # the placeholder of the plan is replaced by the real branch


@pytest.mark.parametrize("step", [k73_step, k74_step])
def test_k73_and_k74_hold_no_instruction_to_click(step):
    text = step().lower()
    for verb in ("cliquer", "cliquez", "recliquer", "recliquez"):
        assert verb not in text, verb


def test_k74_states_the_attention_time_the_planned_duration_and_pastes_only_the_report():
    step = k74_step()
    assert "attention : 3 min" in step
    assert "« Durée prévue de K7.6 : environ N min »" in step
    assert "~/kosmo-runs/snap/snap-report.md" in step and "rien d'autre n'est demandé" in step


def test_the_lot_3b_budget_line_gives_8_min_plus_2_and_10_at_worst_with_k78_outside():
    line = budget_line()
    for figure in ("8 min", "2 min", "10 min"):
        assert figure in line, figure
    assert "K7.8 hors budget" in line


def test_k75_to_k78_are_not_printed_and_only_the_k75_line_announces_them():
    text = section()
    assert re.search(r"^K7\.[5-8]\.", text, re.MULTILINE) is None
    assert "\nK7.5 et la suite arrivent dans la prochaine livraison.\n" in text
    assert text.count("K7.8") == 1  # the budget line only (Q15, D54)


def test_the_k2_k7c_and_campaign_done_lines_and_the_adr_links_are_written():
    text = DOC.read_text(encoding="utf-8")
    assert "\n## Annotate\n\nÉtape K2 faite ; elle ne s'applique à aucune nouvelle session : leur vérité terrain " \
           "est produite automatiquement (ADR 0018).\n" in text
    assert "K7.C. Faite au lot 3a ; constats repris en D49 ; ne pas refaire." in section()
    assert "Aucune saisie manuelle : la vérité terrain d'une campagne est produite automatiquement ; vous ne validez " \
           "que les cas signalés par l'outil, 10 min au plus par campagne (ADR 0018)." in text
    for target in ("../adr/0018-automatic-ground-truth.md", "../adr/0019-capture-framing.md"):
        assert f"]({target})" in text, target
        assert (DOC.parent / target).resolve().is_file(), target


def test_k71b_is_one_command_that_creates_the_venv_installs_the_hashed_file_and_prints_the_pins():
    pytest.importorskip("numpy")  # the test runs the printed pins command with the current interpreter
    pytest.importorskip("PIL")
    step = k71b_step()
    commands = [s for s in re.findall(r"`([^`]+)`", step) if "&&" in s]
    assert len(commands) == 1 and commands[0].startswith("python3 -m venv")
    parts = commands[0].split(" && ")
    assert len(parts) == 3
    assert shlex.split(parts[0]) == ["python3", "-m", "venv", "~/kosmo-tool-venv"]
    assert parts[1] == ("~/kosmo-tool-venv/bin/pip install --require-hashes --only-binary=:all: "
                        "-r tools/camera/requirements-tool.txt")
    pins = _parse("requirements-tool.txt")  # every line pinned with a sha256 hash, or the parse fails
    assert set(pins) == {"numpy", "pillow"}
    words = shlex.split(parts[2])
    assert words[:2] == ["~/kosmo-tool-venv/bin/python", "-c"]
    printed = subprocess.run([sys.executable, "-c", words[2]], capture_output=True, text=True, check=True).stdout.strip()
    expected = f"numpy {pins['numpy'][0]} - Pillow {pins['pillow'][0]}"
    assert printed == expected
    assert f"`{expected}`" in step


def test_the_docker_run_block_keeps_the_sandbox_flags_and_mounts_the_data_read_only():
    words = shlex.split(command_with(VIDEO_MARK))
    assert words[:3] == ["docker", "run", "--rm"]
    assert words[words.index("--network") + 1] == "none"
    assert words[words.index("--cap-drop") + 1] == "ALL"
    assert words[words.index("--security-opt") + 1] == "no-new-privileges"
    assert "--read-only" in words
    mounts = [words[i + 1] for i, w in enumerate(words) if w == "-v"]
    assert "~/kosmo-data:/data:ro" in mounts
    assert all(not m.startswith("~/kosmo-data") or m.endswith(":ro") for m in mounts)
    assert "--privileged" not in words and "--network=host" not in words


def test_the_section_names_the_steps_the_expected_texts_and_retires_k5():
    text = " ".join(section().split())
    for step in ("K7.0", "K7.1", "K7.1b", "K7.2", "K7.3", "K7.4", "K7.C"):
        assert step in text, step
    assert "K5.2" not in text and "drift.json" not in text  # K5 is retired (D30)
    assert "Étape K5 suspendue le 15/11/2026" in text
    for expected in ("Recalage indisponible", "non recalé", "Points prêts", "Contrôle indisponible",
                     "Images vidéo absentes", "Continuer", "Entrée"):
        assert expected in text, expected
    from kosmo_camera.annotate import flow, journey  # the texts the tool really shows, not retyped
    for exact in (journey.NO_SPLIT_TEXT, flow.CONTROL_OFF, journey.NO_FRAMES_TEXT, journey.NO_VIDEO_PLANNED, flow.UNAVAILABLE):
        assert exact in text, exact
    assert "frames.json" in text and "restent sur votre ordinateur : ne les collez jamais" in text  # CV15
    assert "git switch feat/camera-feasibility-0b-1-detection" in text
