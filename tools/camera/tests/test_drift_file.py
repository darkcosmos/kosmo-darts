"""Drift flag file `drift.json`: strict stdlib reader and atomic writer (CV15, CV16, CV18)."""
import copy
import json
import subprocess
from pathlib import Path

import pytest

from annotate_support import stdlib_pythons
from kosmo_camera import drift_file as df

SHA = "ab" * 32


def _flags(session="20261101-100000", label="S01", maximum=1.5, beyond=(4, 5), suggested=(4,), unmeasurable=(9,)):
    return df.SessionFlags(session, label, SHA, maximum, beyond, suggested, unmeasurable)


def _file():
    return df.DriftFile(0.75, (_flags(), _flags("20261102-090000", "S02", None, (), (), ())))


def _dict():
    return json.loads(df.encode(_file()))


def _decode(mutate):
    data = _dict()
    mutate(data)
    return df.decode(json.dumps(data))


def test_round_trip_and_canonical_text():
    text = df.encode(_file())
    assert df.decode(text) == _file()
    assert text == df.encode(df.decode(text))
    assert text.endswith("\n") and list(json.loads(text)) == sorted(json.loads(text))


def test_file_of_exactly_max_bytes_is_read(tmp_path):
    text = df.encode(_file())
    padded = text + " " * (df.MAX_BYTES - len(text.encode("utf-8")))
    path = tmp_path / "drift.json"
    path.write_bytes(padded.encode("utf-8"))
    assert path.stat().st_size == df.MAX_BYTES
    assert df.read(path) == _file()


def test_file_one_byte_over_max_bytes_is_rejected_without_reading_past_the_cap(tmp_path, monkeypatch):
    text = df.encode(_file())
    path = tmp_path / "drift.json"
    path.write_bytes((text + " " * (df.MAX_BYTES + 1 - len(text.encode("utf-8")))).encode("utf-8"))
    sizes = []
    original = Path.open

    def spying_open(self, *args, **kwargs):
        handle = original(self, *args, **kwargs)
        read = handle.read

        def counted(size=-1):
            sizes.append(size)
            return read(size)
        handle.read = counted
        return handle

    monkeypatch.setattr(Path, "open", spying_open)
    with pytest.raises(df.DriftFileError, match="larger than"):
        df.read(path)
    assert sizes == [df.MAX_BYTES + 1]


def test_max_bytes_is_one_mebibyte():
    assert df.MAX_BYTES == 1024 * 1024


def test_missing_file_and_invalid_utf8_are_drift_file_errors(tmp_path):
    with pytest.raises(df.DriftFileError, match="cannot read"):
        df.read(tmp_path / "absent.json")
    path = tmp_path / "bad.json"
    path.write_bytes(b"\xff\xfe{}")
    with pytest.raises(df.DriftFileError, match="UTF-8"):
        df.read(path)


@pytest.mark.parametrize("where", ["root", "session"])
def test_unknown_key_is_rejected(where):
    def mutate(data):
        (data if where == "root" else data["sessions"][0])["extra"] = 1
    with pytest.raises(df.DriftFileError, match="unknown field 'extra'"):
        _decode(mutate)


@pytest.mark.parametrize("where, key", [("root", "thresholdMm"), ("session", "beyond"), ("session", "label")])
def test_missing_key_is_rejected(where, key):
    def mutate(data):
        del (data if where == "root" else data["sessions"][0])[key]
    with pytest.raises(df.DriftFileError, match=f"missing field '{key}'"):
        _decode(mutate)


def test_duplicate_key_is_rejected():
    with pytest.raises(df.DriftFileError, match="duplicate key"):
        df.decode('{"format": "kosmo-camera-drift", "format": "kosmo-camera-drift"}')


@pytest.mark.parametrize("text", ["[]", "null", "{", "", "﻿{}"])
def test_not_a_json_object_is_rejected(text):
    with pytest.raises(df.DriftFileError):
        df.decode(text)


@pytest.mark.parametrize("constant", ["NaN", "Infinity", "-Infinity"])
def test_non_finite_constants_are_rejected(constant):
    text = json.dumps(_dict()).replace('"thresholdMm": 0.75', f'"thresholdMm": {constant}')
    with pytest.raises(df.DriftFileError, match="invalid number"):
        df.decode(text)


def test_other_format_is_rejected():
    with pytest.raises(df.DriftFileError, match="format"):
        _decode(lambda data: data.update(format="kosmo-camera-annotations"))


@pytest.mark.parametrize("version", [0, 2, "1", True, None, 1.0])
def test_other_versions_are_rejected(version):
    with pytest.raises(df.DriftFileError, match="version"):
        _decode(lambda data: data.update(version=version))


@pytest.mark.parametrize("field, value", [
    ("thresholdMm", "1"), ("thresholdMm", True), ("thresholdMm", -1), ("thresholdMm", None),
])
def test_threshold_must_be_a_finite_number_of_at_least_zero(field, value):
    with pytest.raises(df.DriftFileError, match=field):
        _decode(lambda data: data.update({field: value}))


@pytest.mark.parametrize("field, value", [
    ("session", "20261101"), ("session", "../x"), ("session", 3),
    ("label", "S1"), ("label", "s01"), ("segmentsSha256", "AB" * 32), ("segmentsSha256", "ab" * 31),
    ("maxDriftMm", "1.0"), ("maxDriftMm", True), ("maxDriftMm", -0.1),
    ("beyond", "4"), ("beyond", [0]), ("beyond", [True]), ("beyond", [1.5]), ("beyond", [5, 4]), ("beyond", [4, 4]),
    ("suggested", [3, 2]), ("unmeasurable", [-1]),
])
def test_session_fields_are_checked(field, value):
    with pytest.raises(df.DriftFileError, match=field):
        _decode(lambda data: data["sessions"][0].update({field: value}))


def test_duplicate_sessions_and_labels_are_rejected():
    def same_session(data):
        data["sessions"][1]["session"] = data["sessions"][0]["session"]

    def same_label(data):
        data["sessions"][1]["label"] = data["sessions"][0]["label"]
    with pytest.raises(df.DriftFileError, match="duplicate session"):
        _decode(same_session)
    with pytest.raises(df.DriftFileError, match="duplicate label"):
        _decode(same_label)


def test_sessions_must_be_an_array():
    with pytest.raises(df.DriftFileError, match="sessions"):
        _decode(lambda data: data.update(sessions={}))


def test_deep_nesting_is_rejected():
    text = '{"format": ' + "[" * 50 + "]" * 50 + "}"
    with pytest.raises(df.DriftFileError):
        df.decode(text)


def test_encode_refuses_an_invalid_value():
    bad = df.DriftFile(float("nan"), ())
    with pytest.raises(df.DriftFileError):
        df.encode(bad)
    with pytest.raises(df.DriftFileError):
        df.encode(df.DriftFile(1.0, (_flags(beyond=(5, 4)),)))


def test_write_atomic_replaces_the_file_and_leaves_no_temporary_file(tmp_path):
    path = tmp_path / "drift.json"
    df.write_atomic(path, _file())
    df.write_atomic(path, df.DriftFile(1.0, ()))
    assert df.read(path) == df.DriftFile(1.0, ())
    assert [p.name for p in tmp_path.iterdir()] == ["drift.json"]


def test_write_atomic_keeps_the_old_file_when_the_new_one_is_invalid(tmp_path):
    path = tmp_path / "drift.json"
    df.write_atomic(path, _file())
    before = path.read_bytes()
    with pytest.raises(df.DriftFileError):
        df.write_atomic(path, df.DriftFile(float("inf"), ()))
    assert path.read_bytes() == before
    assert [p.name for p in tmp_path.iterdir()] == ["drift.json"]


def test_write_atomic_in_a_missing_folder_is_a_drift_file_error(tmp_path):
    with pytest.raises(df.DriftFileError, match="cannot write"):
        df.write_atomic(tmp_path / "absent" / "drift.json", _file())


def test_input_dataclasses_are_not_mutated_by_encode():
    original = _file()
    snapshot = copy.deepcopy(original)
    df.encode(original)
    assert original == snapshot


@pytest.mark.parametrize("python", stdlib_pythons())
def test_import_closure_is_stdlib_only(python):
    root = Path(__file__).resolve().parent.parent
    code = f"import sys; sys.path.insert(0, {str(root)!r}); import kosmo_camera.drift_file"
    result = subprocess.run([python, "-I", "-S", "-c", code], capture_output=True, text=True)
    assert result.returncode == 0, result.stderr
