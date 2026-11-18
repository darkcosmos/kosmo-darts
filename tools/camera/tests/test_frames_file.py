"""Strict reader and atomic writer of frames.json (CV27, CV15): stdlib only."""
import copy
import json
import subprocess
from pathlib import Path

import pytest

from annotate_support import stdlib_pythons
from kosmo_camera import frames_file as ff
from kosmo_camera.frames_file import Frame, FramesFile, FramesFileError, Missing, SessionFrames

SHA = "a" * 64


def _model():
    return FramesFile(1000, (
        SessionFrames("20261101-100000", (Frame(2, 1800, 640, 360, "video-002.jpg", SHA),
                                          Frame(11, 20500, 640, 360, "video-011.jpg", SHA)),
                      (Missing(1, "before_start"), Missing(5, "too_close"))),
        SessionFrames("20261102-100000", (), (Missing(3, "video_missing"),))))


def _root():
    return json.loads(ff.encode(_model()))


def _decode(root):
    return ff.decode(json.dumps(root))


def test_model_round_trips_and_is_canonical():
    text = ff.encode(_model())
    assert ff.decode(text) == _model()
    assert ff.encode(ff.decode(text)) == text


def test_session_without_any_frame_keeps_only_missing():
    assert ff.decode(ff.encode(_model())).sessions[1].frames == ()


def _mutations():
    def put(*path_value):
        *path, value = path_value

        def apply(root):
            target = root
            for key in path[:-1]:
                target = target[key]
            target[path[-1]] = value
        return apply

    def drop(*path):
        def apply(root):
            target = root
            for key in path[:-1]:
                target = target[key]
            del target[path[-1]]
        return apply
    return {
        "unknown root key": put("extra", 1),
        "unknown frame key": put("sessions", 0, "frames", 0, "extra", 1),
        "missing key": drop("sessions", 0, "frames", 0, "sha256"),
        "wrong format": put("format", "other"),
        "wrong version": put("version", 2),
        "bool version": put("version", True),
        "negative before": put("beforeEntryMs", -1),
        "bad session name": put("sessions", 0, "session", "20261101"),
        "float position": put("sessions", 0, "frames", 0, "positionMs", 1.5),
        "zero width": put("sessions", 0, "frames", 0, "width", 0),
        "bad sha": put("sessions", 0, "frames", 0, "sha256", "A" * 64),
        "path in file name": put("sessions", 0, "frames", 0, "file", "../video-002.jpg"),
        "file of another dart": put("sessions", 0, "frames", 0, "file", "video-003.jpg"),
        "unknown reason": put("sessions", 0, "missing", 0, "reason", "because"),
        "dart zero": put("sessions", 0, "missing", 0, "dart", 0),
        "unsorted frames": put("sessions", 0, "frames", 1, "dart", 1),
        "dart both framed and missing": put("sessions", 0, "missing", 0, "dart", 2),
        "duplicate session": put("sessions", 1, "session", "20261101-100000"),
    }


@pytest.mark.parametrize("name", list(_mutations()))
def test_strict_reader_rejects(name):
    root = copy.deepcopy(_root())
    if name == "unsorted frames":
        root["sessions"][0]["frames"][1]["file"] = "video-001.jpg"
    _mutations()[name](root)
    with pytest.raises(FramesFileError):
        _decode(root)


@pytest.mark.parametrize("text", ["", "{", "[]", '{"format": 1, "format": 2}', '{"beforeEntryMs": NaN}',
                                  "﻿{}"])
def test_strict_reader_rejects_bad_text(text):
    with pytest.raises(FramesFileError):
        ff.decode(text)


def test_nesting_beyond_the_limit_is_rejected():
    with pytest.raises(FramesFileError, match="nesting"):
        ff.decode('{"a":' * 8 + "1" + "}" * 8)


def _padded(size):
    """A valid manifest of exactly `size` bytes (padding through the final whitespace)."""
    text = ff.encode(_model())
    return text.encode("utf-8") + b" " * (size - len(text))


def test_cap_is_inclusive_and_one_more_byte_is_rejected(tmp_path):
    path = tmp_path / "frames.json"
    path.write_bytes(_padded(ff.MAX_BYTES))
    assert ff.read(path) == _model()
    path.write_bytes(_padded(ff.MAX_BYTES + 1))
    with pytest.raises(FramesFileError, match="larger than"):
        ff.read(path)


def test_read_reports_missing_file_and_bad_encoding(tmp_path):
    with pytest.raises(FramesFileError, match="cannot read"):
        ff.read(tmp_path / "absent.json")
    path = tmp_path / "frames.json"
    path.write_bytes(b"\xff\xfe")
    with pytest.raises(FramesFileError, match="UTF-8"):
        ff.read(path)


def test_write_atomic_leaves_only_the_file_and_keeps_the_old_one_on_failure(tmp_path):
    path = tmp_path / "frames.json"
    ff.write_atomic(path, _model())
    assert ff.read(path) == _model()
    assert [p.name for p in tmp_path.iterdir()] == ["frames.json"]
    bad = FramesFile(1000, (SessionFrames("bad name", (), ()),))
    with pytest.raises(FramesFileError):
        ff.write_atomic(path, bad)
    assert ff.read(path) == _model()
    assert [p.name for p in tmp_path.iterdir()] == ["frames.json"]


@pytest.mark.parametrize("python", stdlib_pythons())
def test_import_closure_is_stdlib_only(python):
    root = Path(__file__).resolve().parent.parent
    code = f"import sys; sys.path.insert(0, {str(root)!r}); import kosmo_camera.frames_file, kosmo_camera.control"
    result = subprocess.run([python, "-I", "-S", "-c", code], capture_output=True, text=True)
    assert result.returncode == 0, result.stderr
