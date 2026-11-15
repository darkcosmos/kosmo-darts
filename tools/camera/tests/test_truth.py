"""Truth v2 reader (CV5, docs/camera/capture-format.md, rule D2 of docs/rules/x01.md)."""
import hashlib
import json
import subprocess
import sys
from pathlib import Path

import pytest

from annotate_support import stdlib_pythons
from kosmo_camera import truth
from kosmo_camera.truth import TruthError, read_truth

GOLDEN = Path(__file__).parent / "data" / "truth-v2-golden.json"


def _golden():
    return json.loads(GOLDEN.read_text(encoding="utf-8"))


def _write(tmp_path, data):
    path = tmp_path / "truth.json"
    text = data if isinstance(data, str) else json.dumps(data)
    path.write_text(text, encoding="utf-8")
    return path


def _read_modified(tmp_path, mutate):
    data = _golden()
    mutate(data)
    return read_truth(_write(tmp_path, data))


def test_golden_file_reads_with_spot_checked_values():
    session = read_truth(GOLDEN)
    assert session.session == "20261031-140322"
    assert session.version == 2
    assert session.end_reason == "STOP"
    assert session.zoom.requested_ratio == 2.4 and session.zoom.ratio == 2.4
    assert session.started_at.monotonic_nanos == 5000000000
    assert len(session.volleys) == 2
    first, second = session.volleys
    assert second.empty and second.darts == ()
    triple, voided = first.darts
    assert (triple.segment, triple.multiplier, triple.number) == (20, "TRIPLE", 1)
    assert triple.photo.file == "dart-001.jpg" and triple.photo.video_position_ms == 892
    assert triple.gravity == (0.1, -9.8, 0.25)
    assert voided.voided and voided.voided_at is not None
    assert voided.photo is None and voided.gravity is None


def test_judged_is_non_voided_with_a_photo_cv5():
    first = read_truth(GOLDEN).volleys[0]
    triple, voided = first.darts
    assert triple.judged is True
    assert voided.judged is False


def test_judged_false_for_non_voided_dart_without_photo(tmp_path):
    def mutate(data):
        data["volleys"][0]["darts"][0]["photo"] = None
    dart = _read_modified(tmp_path, mutate).volleys[0].darts[0]
    assert not dart.voided and dart.photo is None
    assert dart.judged is False


def test_sha256_is_the_hash_of_the_file_bytes():
    assert read_truth(GOLDEN).sha256 == hashlib.sha256(GOLDEN.read_bytes()).hexdigest()


def test_zoom_may_be_null(tmp_path):
    def mutate(data):
        data["zoom"] = {"requestedRatio": None, "ratio": None}
    zoom = _read_modified(tmp_path, mutate).zoom
    assert zoom.requested_ratio is None and zoom.ratio is None


def test_zoom_must_be_positive_and_finite(tmp_path):
    for bad in (0, -1.0):
        def mutate(data, bad=bad):
            data["zoom"]["ratio"] = bad
        with pytest.raises(TruthError, match=r"\$\.zoom\.ratio"):
            _read_modified(tmp_path, mutate)


def test_killed_session_reads(tmp_path):
    def mutate(data):
        data["endedAt"] = None
        data["endReason"] = None
        data["volleys"][-1]["end"] = None
    session = _read_modified(tmp_path, mutate)
    assert session.ended_at is None and session.end_reason is None
    assert session.volleys[-1].end is None


def test_end_reason_null_exactly_when_ended_at_null(tmp_path):
    def mutate(data):
        data["endReason"] = None
    with pytest.raises(TruthError, match=r"\$\.endReason"):
        _read_modified(tmp_path, mutate)


@pytest.mark.parametrize("version", [1, 3, 0, "2", True])
def test_other_versions_are_rejected(tmp_path, version):
    def mutate(data):
        data["version"] = version
    with pytest.raises(TruthError, match=r"\$\.version"):
        _read_modified(tmp_path, mutate)


def test_version_1_message_names_the_version(tmp_path):
    def mutate(data):
        data["version"] = 1
        del data["zoom"]
    with pytest.raises(TruthError, match="unsupported version 1"):
        _read_modified(tmp_path, mutate)


def test_other_format_is_rejected(tmp_path):
    def mutate(data):
        data["format"] = "something-else"
    with pytest.raises(TruthError, match=r"\$\.format"):
        _read_modified(tmp_path, mutate)


def test_duplicate_key_is_rejected(tmp_path):
    text = GOLDEN.read_text(encoding="utf-8").replace('"sdk": 37', '"sdk": 37,\n    "sdk": 38')
    with pytest.raises(TruthError, match="duplicate key 'sdk'"):
        read_truth(_write(tmp_path, text))


def test_unknown_field_is_rejected_with_its_path(tmp_path):
    def mutate(data):
        data["volleys"][0]["darts"][0]["extra"] = 1
    with pytest.raises(TruthError, match=r"\$\.volleys\[0\]\.darts\[0\].*extra"):
        _read_modified(tmp_path, mutate)


def test_missing_field_is_rejected_with_its_path(tmp_path):
    def mutate(data):
        del data["volleys"][0]["darts"][1]["gravity"]
    with pytest.raises(TruthError, match=r"\$\.volleys\[0\]\.darts\[1\].*gravity"):
        _read_modified(tmp_path, mutate)


def test_triple_bull_is_rejected(tmp_path):
    for segment in (25, 50):
        def mutate(data, segment=segment):
            data["volleys"][0]["darts"][0]["segment"] = segment
        with pytest.raises(TruthError, match=r"darts\[0\]\.multiplier"):
            _read_modified(tmp_path, mutate)


def test_miss_with_multiplier_is_rejected(tmp_path):
    def mutate(data):
        dart = data["volleys"][0]["darts"][0]
        dart["segment"], dart["multiplier"] = 0, "DOUBLE"
    with pytest.raises(TruthError, match=r"darts\[0\]\.multiplier"):
        _read_modified(tmp_path, mutate)


@pytest.mark.parametrize("segment", [21, -1, 26, 49, 51, 1.5, True, "20"])
def test_invalid_segment_is_rejected(tmp_path, segment):
    def mutate(data):
        data["volleys"][0]["darts"][0]["segment"] = segment
    with pytest.raises(TruthError, match=r"darts\[0\]\.segment"):
        _read_modified(tmp_path, mutate)


@pytest.mark.parametrize("segment,multiplier", [(0, "SINGLE"), (25, "SINGLE"), (50, "SINGLE"), (1, "DOUBLE")])
def test_valid_segment_multiplier_pairs_read(tmp_path, segment, multiplier):
    def mutate(data):
        dart = data["volleys"][0]["darts"][0]
        dart["segment"], dart["multiplier"] = segment, multiplier
    dart = _read_modified(tmp_path, mutate).volleys[0].darts[0]
    assert (dart.segment, dart.multiplier) == (segment, multiplier)


def test_voided_must_match_voided_at(tmp_path):
    def mutate(data):
        data["volleys"][0]["darts"][1]["voided"] = False
    with pytest.raises(TruthError, match=r"darts\[1\]\.voided"):
        _read_modified(tmp_path, mutate)


def test_text_over_10_mib_is_rejected_without_reading_past_the_cap(tmp_path):
    path = tmp_path / "big.json"
    path.write_bytes(b" " * (truth.MAX_BYTES + 1))
    with pytest.raises(TruthError, match="larger than"):
        read_truth(path)


def test_file_of_exactly_the_cap_is_parsed_not_refused_for_size(tmp_path):
    path = tmp_path / "cap.json"
    path.write_bytes(b" " * truth.MAX_BYTES)
    with pytest.raises(TruthError) as error:
        read_truth(path)
    assert "larger than" not in str(error.value)


def test_reader_never_reads_more_than_cap_plus_one(monkeypatch, tmp_path):
    path = tmp_path / "big.json"
    path.write_bytes(b" " * (truth.MAX_BYTES + 1000))
    sizes = []
    real_open = Path.open

    def spying_open(self, *args, **kwargs):
        handle = real_open(self, *args, **kwargs)
        real_read = handle.read

        def read(size=-1):
            sizes.append(size)
            return real_read(size)
        handle.read = read
        return handle

    monkeypatch.setattr(Path, "open", spying_open)
    with pytest.raises(TruthError):
        read_truth(path)
    assert sizes and all(0 <= size <= truth.MAX_BYTES + 1 for size in sizes)


def test_truncated_text_is_rejected(tmp_path):
    text = GOLDEN.read_text(encoding="utf-8")[:300]
    with pytest.raises(TruthError, match="invalid JSON"):
        read_truth(_write(tmp_path, text))


def test_invalid_utf8_and_bom_are_rejected(tmp_path):
    path = tmp_path / "bad.json"
    path.write_bytes(b"\xff\xfe{}")
    with pytest.raises(TruthError):
        read_truth(path)
    path.write_bytes(b"\xef\xbb\xbf" + GOLDEN.read_bytes())
    with pytest.raises(TruthError):
        read_truth(path)


def test_nesting_deeper_than_32_is_rejected(tmp_path):
    with pytest.raises(TruthError, match="nesting"):
        read_truth(_write(tmp_path, "[" * 40 + "]" * 40))


def test_non_finite_numbers_are_rejected(tmp_path):
    text = GOLDEN.read_text(encoding="utf-8").replace('"x": 0.1', '"x": NaN')
    with pytest.raises(TruthError):
        read_truth(_write(tmp_path, text))


def test_missing_file_is_a_truth_error(tmp_path):
    with pytest.raises(TruthError, match="cannot read"):
        read_truth(tmp_path / "absent.json")


def test_stamp_requires_non_negative_nanos_and_iso_utc(tmp_path):
    def negative(data):
        data["startedAt"]["monotonicNanos"] = -1
    with pytest.raises(TruthError, match=r"\$\.startedAt\.monotonicNanos"):
        _read_modified(tmp_path, negative)

    def bad_utc(data):
        data["startedAt"]["utc"] = "2026-10-31 14:03:22"
    with pytest.raises(TruthError, match=r"\$\.startedAt\.utc"):
        _read_modified(tmp_path, bad_utc)


@pytest.mark.parametrize("python", stdlib_pythons())
def test_import_closure_is_stdlib_only(python):
    root = Path(__file__).resolve().parent.parent
    # -I ignores PYTHONPATH, so the package root is put on sys.path by the command itself.
    code = f"import sys; sys.path.insert(0, {str(root)!r}); import kosmo_camera.truth, kosmo_camera.config"
    result = subprocess.run([python, "-I", "-S", "-c", code], capture_output=True, text=True)
    assert result.returncode == 0, result.stderr
