"""Shared strict JSON helpers and atomic write (CV25, CV16, CV26, CV27): stdlib only."""
import json
import os
import subprocess
from pathlib import Path

import pytest

from annotate_support import stdlib_pythons
from kosmo_camera import annotations, frames_file, strict_json


def test_duplicate_key_and_non_finite_constant_are_refused():
    with pytest.raises(strict_json.StrictJsonError):
        json.loads('{"a": 1, "a": 2}', object_pairs_hook=strict_json.no_duplicates)
    with pytest.raises(strict_json.StrictJsonError):
        json.loads('{"a": NaN}', parse_constant=strict_json.reject_constant)


def test_depth_counts_nesting_without_recursion():
    assert strict_json.depth({"a": [1, {"b": []}]}) == 4
    assert strict_json.depth(1) == 0
    assert strict_json.depth([[[[]]]] * 3) == 4


@pytest.mark.parametrize("reader,error", [(annotations.decode, annotations.AnnotationError),
                                          (frames_file.decode, frames_file.FramesFileError)])
def test_each_reader_turns_the_shared_errors_into_its_own(reader, error):
    for text in ('{"a": 1, "a": 2}', '{"a": NaN}'):
        with pytest.raises(error):
            reader(text)


def test_one_public_session_name_pattern_serves_the_readers():
    assert annotations.SESSION_NAME.fullmatch("20261031-140322-2")
    assert not annotations.SESSION_NAME.fullmatch("20261031-140322-1")
    assert frames_file.SESSION_NAME is annotations.SESSION_NAME


def test_replace_atomic_replaces_the_file_and_leaves_no_temporary_file(tmp_path):
    path = tmp_path / "file.json"
    strict_json.replace_atomic(path, b"first")
    strict_json.replace_atomic(path, b"second")
    assert path.read_bytes() == b"second"
    assert [p.name for p in tmp_path.iterdir()] == ["file.json"]


def test_replace_atomic_keeps_the_old_file_when_the_replace_fails(tmp_path, monkeypatch):
    path = tmp_path / "file.json"
    strict_json.replace_atomic(path, b"old")

    def fail(*_):
        raise OSError("disk full")

    monkeypatch.setattr(os, "replace", fail)
    with pytest.raises(strict_json.AtomicWriteError, match="cannot write"):
        strict_json.replace_atomic(path, b"new")
    assert path.read_bytes() == b"old"
    assert [p.name for p in tmp_path.iterdir()] == ["file.json"]


def test_replace_atomic_in_a_missing_folder_is_an_atomic_write_error(tmp_path):
    with pytest.raises(strict_json.AtomicWriteError, match="cannot write"):
        strict_json.replace_atomic(tmp_path / "absent" / "file.json", b"data")


@pytest.mark.parametrize("python", stdlib_pythons())
def test_import_closure_is_stdlib_only(python):
    root = Path(__file__).resolve().parent.parent
    code = f"import sys; sys.path.insert(0, {str(root)!r}); import kosmo_camera.strict_json"
    result = subprocess.run([python, "-I", "-S", "-c", code], capture_output=True, text=True)
    assert result.returncode == 0, result.stderr
