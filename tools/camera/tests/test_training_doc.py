"""The "Dérive et recalibration" section of docs/camera/training.md (K5): its commands parse with the real parsers (S10)."""
import argparse
import re
import shlex
from pathlib import Path

import pytest

DOC = Path(__file__).resolve().parents[3] / "docs" / "camera" / "training.md"
DRIFT_MARK = "python -m kosmo_camera.drift"
ANNOTATE_MARK = "python3 -m kosmo_camera.annotate"


class Parsed(Exception):
    def __init__(self, namespace):
        super().__init__("parsed")
        self.namespace = namespace


def section():
    text = DOC.read_text(encoding="utf-8")
    match = re.search(r"^## Dérive et recalibration\n(.*?)(?=^## )", text, re.DOTALL | re.MULTILINE)
    assert match, "section 'Dérive et recalibration' not found"
    return match.group(1)


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


def test_the_drift_command_parses_with_the_drift_parser(monkeypatch):
    from kosmo_camera import drift
    words = shlex.split(command_with(DRIFT_MARK))
    argv = words[words.index("kosmo_camera.drift") + 1:]
    namespace = parse_with(monkeypatch, drift.main, argv)
    assert (str(namespace.captures), str(namespace.annotations), str(namespace.runs)) \
        == ("/data/captures", "/data/annotations", "/runs")


def test_the_annotate_command_parses_with_the_annotate_parser(monkeypatch):
    from kosmo_camera.annotate import __main__ as annotate
    words = shlex.split(command_with(ANNOTATE_MARK))
    argv = words[words.index("kosmo_camera.annotate") + 1:]
    namespace = parse_with(monkeypatch, annotate.main, argv)
    assert namespace.drift == "~/kosmo-runs/drift/drift.json"
    assert namespace.captures == "~/kosmo-data/captures" and namespace.annotations == "~/kosmo-data/annotations"


def test_the_docker_run_block_keeps_the_sandbox_flags_and_mounts_the_data_read_only():
    words = shlex.split(command_with(DRIFT_MARK))
    assert words[:3] == ["docker", "run", "--rm"]
    assert words[words.index("--network") + 1] == "none"
    assert words[words.index("--cap-drop") + 1] == "ALL"
    assert words[words.index("--security-opt") + 1] == "no-new-privileges"
    assert "--read-only" in words
    mounts = [words[i + 1] for i, w in enumerate(words) if w == "-v"]
    assert "~/kosmo-data:/data:ro" in mounts
    assert all(not m.startswith("~/kosmo-data") or m.endswith(":ro") for m in mounts)
    assert "--privileged" not in words and "--network=host" not in words


def test_the_section_names_the_steps_the_report_and_the_expected_texts():
    text = " ".join(section().split())
    for step in ("K5.0", "K5.1", "K5.2", "K5.3", "K5.4"):
        assert step in text, step
    assert "~/kosmo-runs/drift/drift-report.md" in text
    for expected in ("Tout sous le seuil : oui", "(mesure périmée)", "Recalibrer à partir de cette fléchette (R)",
                     "ancien 1", "tout de suite"):
        assert expected in text, expected
    assert "git switch feat/camera-feasibility-0b-1-detection" in text
