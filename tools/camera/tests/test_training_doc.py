"""The "Visionneuse (K7)" section of docs/camera/training.md and every command of the guide and of the tool README:
they parse with the real parsers (S10, U16 of C1b), and name no option removed by C1."""
import argparse
import importlib
import re
import shlex
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[3]
DOC = ROOT / "docs" / "camera" / "training.md"
README = ROOT / "tools" / "camera" / "README.md"
VIDEO_MARK = "kosmo_camera.video_frames"
ANNOTATE_MARK = "kosmo_camera.annotate"
ANNOTATE_REMOVED_OPTIONS = ("--split", "--lock", "--drift")  # removed from the annotation tool by C1 (N24)
MODULE_MAINS = {"kosmo_camera.annotate": "kosmo_camera.annotate.__main__",
                "kosmo_camera.video_frames": "kosmo_camera.video_frames"}
NO_PARSER_MODULES = {"kosmo_camera.gpu_check"}  # run as `python -m`, no argparse parser to check


class Parsed(Exception):
    def __init__(self, namespace):
        super().__init__("parsed")
        self.namespace = namespace


def section():
    text = DOC.read_text(encoding="utf-8")
    match = re.search(r"^## Visionneuse \(K7\)\n(.*?)(?=^## )", text, re.DOTALL | re.MULTILINE)
    assert match, "section 'Visionneuse (K7)' not found"
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


def test_the_video_frames_command_parses_with_its_parser(monkeypatch):
    from kosmo_camera import video_frames
    words = shlex.split(command_with(VIDEO_MARK))
    argv = words[words.index("kosmo_camera.video_frames") + 1:]
    namespace = parse_with(monkeypatch, video_frames.main, argv)
    assert (str(namespace.captures), str(namespace.annotations), str(namespace.split), str(namespace.out)) \
        == ("/data/captures", "/data/annotations", "/runs/split.json", "/runs/frames")


def test_the_k73_annotate_command_is_started_by_python3_and_parses_with_the_annotate_parser(monkeypatch):
    from kosmo_camera.annotate import __main__ as annotate
    command = command_with(ANNOTATE_MARK)
    assert command.startswith("python3 -m kosmo_camera.annotate ")
    words = shlex.split(command)
    argv = words[words.index("kosmo_camera.annotate") + 1:]
    namespace = parse_with(monkeypatch, annotate.main, argv)
    assert namespace.frames == "~/kosmo-runs/frames" and "split" not in vars(namespace)  # ADR 0020: no --split
    assert namespace.captures == "~/kosmo-data/captures" and namespace.annotations == "~/kosmo-data/annotations"


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
    for step in ("K7.0", "K7.1", "K7.2", "K7.3", "K7.C", "K7.4 à K7.6"):
        assert step in text, step
    assert "K5.2" not in text and "drift.json" not in text  # K5 is retired (D30)
    assert "Étape K5 suspendue le 15/11/2026" in text
    assert "Étapes K7 arrêtées le 18/11/2026 (nouvelle stratégie de l'issue #42)" in text  # ADR 0020
    assert "aucun nouveau clic de calibration ou de contrôle ne vous sera demandé" in text  # N5
    assert "K7 est arrêtée à son tour" in text and "La suite est l'étape K7" not in text
    assert "Les clics de K7.1b et K7.3 (recalage, calibration, contrôle) sont arrêtés." in text  # Q1 B (N23)
    assert ("l'outil garde l'affichage de vos annotations et la saisie des pointes, pour de futures campagnes courtes "
            "(autres types de fléchettes, 10 min au plus)") in text  # Q1 B (N23)
    assert "l'outil devient une visionneuse (tâche C1" not in text  # option A was not chosen
    assert "K7.4 à K7.6 : annulées le 18/11/2026" in text and "prochaine livraison" not in text
    assert "frames.json" in text and "restent sur votre ordinateur : ne les collez jamais" in text  # CV15
    assert "git switch docs/camera-registration-strategy" in text
    assert "kosmo-tool-venv" not in text and "requirements-tool" not in text and "Recalage" not in text  # C1 removed them


def commands_of(path):
    """The commands that name a kosmo_camera module in the bash blocks of a document, one per line."""
    commands = []
    for block in re.findall(r"```bash\n(.*?)```", path.read_text(encoding="utf-8"), re.DOTALL):
        commands += [line.strip() for line in block.replace("\\\n", " ").splitlines() if "kosmo_camera." in line]
    return commands


@pytest.mark.parametrize("path", [DOC, README], ids=["training.md", "README.md"])
def test_every_command_names_a_parser_that_accepts_it_and_no_removed_option(path, monkeypatch):
    commands = commands_of(path)
    assert commands, f"no command in {path.name}"
    for command in commands:
        words = shlex.split(command)
        index = next(i for i, word in enumerate(words) if word.startswith("kosmo_camera."))
        module, argv = words[index], words[index + 1:]
        if module in NO_PARSER_MODULES:
            continue
        assert module in MODULE_MAINS, f"no parser known for {module} in {command}"
        if module == "kosmo_camera.annotate":  # `--split` stays an option of video_frames, not of the annotation tool
            assert not set(argv) & set(ANNOTATE_REMOVED_OPTIONS), command
        main = getattr(importlib.import_module(MODULE_MAINS[module]), "main")
        parse_with(monkeypatch, main, argv)


CHECKLIST_STRINGS = [
    "Toutes les pointes sont saisies. Précédente (P) ou la liste pour corriger.",
    "Pas d'image vidéo pour cette fléchette.",
    "Image vidéo (V)",
    "Image vidéo (lecture seule)",
    "+ pointe",
    "□ 1 à 4 : points du segment (lecture seule)",
    "○ C : contrôle (lecture seule)",
    "○ C : contrôle — aucun pour cette fléchette",
]


def test_the_visual_checklist_quotes_the_page_strings_word_for_word_and_names_the_removed_keys():
    text = " ".join(section().split())
    page = (ROOT / "tools" / "camera" / "kosmo_camera" / "annotate" / "static" / "app.js").read_text(encoding="utf-8")
    for string in CHECKLIST_STRINGS:
        assert string in text, string
        assert string in page, string
    assert "Enregistré à HH:MM:SS" in text
    assert "`R`, `K` et `1` à `4`" in text and "« recalé »" in text  # the old keys and the old word are checked
