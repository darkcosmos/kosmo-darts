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


def test_the_k73_annotate_command_is_started_by_the_tool_venv_and_parses_with_the_annotate_parser(monkeypatch):
    from kosmo_camera.annotate import __main__ as annotate
    command = command_with(ANNOTATE_MARK)
    assert command.startswith("~/kosmo-tool-venv/bin/python -m kosmo_camera.annotate ")
    words = shlex.split(command)
    argv = words[words.index("kosmo_camera.annotate") + 1:]
    namespace = parse_with(monkeypatch, annotate.main, argv)
    assert namespace.split == "~/kosmo-runs/split.json" and namespace.frames == "~/kosmo-runs/frames"
    assert namespace.captures == "~/kosmo-data/captures" and namespace.annotations == "~/kosmo-data/annotations"


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


def test_the_section_names_the_steps_the_expected_texts_and_retires_k5_and_k7():
    text = " ".join(section().split())
    for step in ("K7.0", "K7.1", "K7.1b", "K7.2", "K7.3", "K7.C", "K7.4 à K7.6"):
        assert step in text, step
    assert "K5.2" not in text and "drift.json" not in text  # K5 is retired (D30)
    assert "Étape K5 suspendue le 15/11/2026" in text
    assert "Étapes K7 arrêtées le 18/11/2026 (nouvelle stratégie de l'issue #42)" in text  # ADR 0020
    assert "aucun nouveau clic ne vous sera demandé" in text  # N5
    assert "K7 est arrêtée à son tour" in text and "La suite est l'étape K7" not in text
    assert "l'outil devient une visionneuse (tâche C1, selon votre réponse à la question Q1 de #42)" in text
    assert "K7.4 à K7.6 : annulées le 18/11/2026" in text and "prochaine livraison" not in text
    for expected in ("Recalage indisponible", "non recalé", "Points prêts", "Contrôle indisponible",
                     "Images vidéo absentes", "Continuer", "Entrée"):
        assert expected in text, expected
    from kosmo_camera.annotate import flow, journey  # the texts the tool really shows, not retyped
    for exact in (journey.NO_SPLIT_TEXT, flow.CONTROL_OFF, journey.NO_FRAMES_TEXT, journey.NO_VIDEO_PLANNED, flow.UNAVAILABLE):
        assert exact in text, exact
    assert "frames.json" in text and "restent sur votre ordinateur : ne les collez jamais" in text  # CV15
    assert "git switch feat/camera-feasibility-0b-1-detection" in text
