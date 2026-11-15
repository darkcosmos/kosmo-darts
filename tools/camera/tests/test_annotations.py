"""Annotation file format (docs/camera/annotation-format.md): CV2 calibration order, CV5 judged darts, CV16 writes."""
import copy
import json
import os
import re
import subprocess
import sys
from pathlib import Path

import pytest

from kosmo_camera import annotations as an
from kosmo_camera.annotations import AnnotationError, Annotations, Calibration, DartAnnotation, Status
from kosmo_camera.truth import read_truth

DATA = Path(__file__).parent / "data"
GOLDEN = DATA / "annotations-v1-golden.json"
TRUTH_GOLDEN = DATA / "truth-v2-golden.json"
DOC = Path(__file__).resolve().parents[3] / "docs" / "camera" / "annotation-format.md"
TRUTH_SHA = "24c0d987d33c4c93aba546caf5d77fa49ee86b3eb2dc62c4763734f3d5083fcf"


def _full():
    return Annotations(
        session="20261031-140322",
        truth_sha256=TRUTH_SHA,
        calibration=Calibration("dart-001.jpg", 4080, 3072,
                                ((2040.5, 610.25), (3301.0, 1290.0), (2702.75, 2650.0), (1102.0, 2300.5))),
        darts=(DartAnnotation(1, Status.VISIBLE, (2010.5, 640.0)),
               DartAnnotation(2, Status.HIDDEN, None),
               DartAnnotation(3, Status.OFF_BOARD, None)))


def _golden_dict():
    return json.loads(GOLDEN.read_text(encoding="utf-8"))


def _decode_modified(mutate):
    data = _golden_dict()
    mutate(data)
    return an.decode(json.dumps(data))


# ---- round trip and golden text -----------------------------------------------------------------------------------

def test_full_file_round_trips():
    full = _full()
    assert an.decode(an.encode(full)) == full


def test_partial_file_round_trips_without_calibration_and_with_some_darts():
    partial = Annotations("20261031-140322", TRUTH_SHA, None, (DartAnnotation(2, Status.HIDDEN, None),))
    assert an.decode(an.encode(partial)) == partial


def test_empty_annotation_in_progress_is_valid():
    empty = Annotations("20261031-140322", TRUTH_SHA, None, ())
    assert an.decode(an.encode(empty)) == empty


def test_golden_text_key_order_indentation_and_no_trailing_newline():
    text = GOLDEN.read_text(encoding="utf-8")
    assert an.encode(_full()) == text
    assert not text.endswith("\n")
    assert text.splitlines()[1] == '  "format": "kosmo-camera-annotations",'


def test_golden_reads_with_cv2_calibration_order():
    a = an.read(GOLDEN)
    assert a.calibration.points[0] == (2040.5, 610.25)  # cal1 on wire 5|20 (CV2)
    assert a.calibration.points[3] == (1102.0, 2300.5)  # cal4 on wire 8|11 (CV2)
    assert [d.status for d in a.darts] == [Status.VISIBLE, Status.HIDDEN, Status.OFF_BOARD]


def test_doc_example_equals_golden_text():
    doc = DOC.read_text(encoding="utf-8")
    blocks = re.findall(r"```json\n(.*?)\n```", doc, flags=re.DOTALL)
    assert blocks == [GOLDEN.read_text(encoding="utf-8")]


def test_integer_coordinates_read_as_floats():
    def mutate(data):
        data["darts"][0]["tip"] = {"x": 10, "y": 20}
    assert _decode_modified(mutate).darts[0].tip == (10.0, 20.0)


def test_encode_refuses_an_invalid_value():
    bad = Annotations("20261031-140322", TRUTH_SHA, None, (DartAnnotation(1, Status.HIDDEN, (1.0, 2.0)),))
    with pytest.raises(AnnotationError):
        an.encode(bad)


# ---- strict reader ------------------------------------------------------------------------------------------------

def test_file_over_1_mib_is_rejected_without_reading_past_the_cap(monkeypatch, tmp_path):
    path = tmp_path / "big.json"
    path.write_bytes(b" " * (an.MAX_BYTES + 1000))
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
    with pytest.raises(AnnotationError, match="larger than"):
        an.read(path)
    assert sizes and all(0 <= size <= an.MAX_BYTES + 1 for size in sizes)


def test_file_of_exactly_1_mib_is_parsed_not_refused_for_size(tmp_path):
    path = tmp_path / "cap.json"
    path.write_bytes(b" " * an.MAX_BYTES)
    with pytest.raises(AnnotationError) as error:
        an.read(path)
    assert "larger than" not in str(error.value)


def test_missing_file_is_an_annotation_error(tmp_path):
    with pytest.raises(AnnotationError, match="cannot read"):
        an.read(tmp_path / "absent.json")


def test_other_format_is_rejected():
    def mutate(data):
        data["format"] = "kosmo-capture-truth"
    with pytest.raises(AnnotationError, match=r"\$\.format"):
        _decode_modified(mutate)


@pytest.mark.parametrize("version", [0, 2, "1", True, None])
def test_other_versions_are_rejected(version):
    def mutate(data):
        data["version"] = version
    with pytest.raises(AnnotationError, match="unsupported version"):
        _decode_modified(mutate)


@pytest.mark.parametrize("where", ["root", "calibration", "point", "dart", "tip"])
def test_unknown_key_is_rejected(where):
    def mutate(data):
        target = {"root": data, "calibration": data["calibration"], "point": data["calibration"]["points"][0],
                  "dart": data["darts"][0], "tip": data["darts"][0]["tip"]}[where]
        target["extra"] = 1
    with pytest.raises(AnnotationError, match="unknown field 'extra'"):
        _decode_modified(mutate)


def test_missing_key_is_rejected():
    def mutate(data):
        del data["darts"][1]["tip"]
    with pytest.raises(AnnotationError, match=r"\$\.darts\[1\]: missing field 'tip'"):
        _decode_modified(mutate)


def test_duplicate_key_is_rejected():
    text = GOLDEN.read_text(encoding="utf-8").replace('"version": 1,', '"version": 1,\n  "version": 1,')
    with pytest.raises(AnnotationError, match="duplicate key 'version'"):
        an.decode(text)


def test_tip_with_hidden_is_rejected():
    def mutate(data):
        data["darts"][1]["tip"] = {"x": 1.0, "y": 1.0}
    with pytest.raises(AnnotationError, match=r"\$\.darts\[1\]\.tip"):
        _decode_modified(mutate)


def test_tip_with_off_board_is_rejected():
    def mutate(data):
        data["darts"][2]["tip"] = {"x": 1.0, "y": 1.0}
    with pytest.raises(AnnotationError, match=r"\$\.darts\[2\]\.tip"):
        _decode_modified(mutate)


def test_missing_tip_with_visible_is_rejected():
    def mutate(data):
        data["darts"][0]["tip"] = None
    with pytest.raises(AnnotationError, match=r"\$\.darts\[0\]\.tip"):
        _decode_modified(mutate)


def test_unknown_status_is_rejected():
    def mutate(data):
        data["darts"][1]["status"] = "hidden"
    with pytest.raises(AnnotationError, match=r"\$\.darts\[1\]\.status"):
        _decode_modified(mutate)


@pytest.mark.parametrize("count", [3, 5])
def test_calibration_needs_exactly_4_points_cv2(count):
    def mutate(data):
        points = data["calibration"]["points"]
        data["calibration"]["points"] = (points * 2)[:count]
    with pytest.raises(AnnotationError, match="exactly 4 points"):
        _decode_modified(mutate)


@pytest.mark.parametrize("x, y", [(-0.5, 10.0), (10.0, -1.0), (4080.5, 10.0), (10.0, 3072.01)])
def test_calibration_point_outside_the_photo_is_rejected(x, y):
    def mutate(data):
        data["calibration"]["points"][1] = {"x": x, "y": y}
    with pytest.raises(AnnotationError, match=r"\$\.calibration\.points\[1\]"):
        _decode_modified(mutate)


def test_calibration_point_on_the_photo_edge_is_accepted():
    def mutate(data):
        data["calibration"]["points"][0] = {"x": 0.0, "y": 0.0}
        data["calibration"]["points"][1] = {"x": 4080.0, "y": 3072.0}
    assert _decode_modified(mutate).calibration.points[1] == (4080.0, 3072.0)


def test_negative_tip_is_rejected():
    def mutate(data):
        data["darts"][0]["tip"]["x"] = -1.0
    with pytest.raises(AnnotationError, match=r"\$\.darts\[0\]\.tip\.x"):
        _decode_modified(mutate)


@pytest.mark.parametrize("constant", ["NaN", "Infinity", "-Infinity"])
def test_non_finite_numbers_are_rejected(constant):
    text = GOLDEN.read_text(encoding="utf-8").replace('"x": 2010.5', f'"x": {constant}')
    with pytest.raises(AnnotationError, match="invalid number"):
        an.decode(text)


def test_boolean_coordinate_is_rejected():
    def mutate(data):
        data["darts"][0]["tip"]["y"] = True
    with pytest.raises(AnnotationError, match=r"\$\.darts\[0\]\.tip\.y"):
        _decode_modified(mutate)


def test_duplicate_dart_number_is_rejected():
    def mutate(data):
        data["darts"][1]["number"] = 1
    with pytest.raises(AnnotationError, match="duplicate dart number 1"):
        _decode_modified(mutate)


def test_dart_numbers_out_of_order_are_rejected():
    def mutate(data):
        data["darts"].reverse()
    with pytest.raises(AnnotationError, match="ascending"):
        _decode_modified(mutate)


def test_dart_number_below_1_is_rejected():
    def mutate(data):
        data["darts"][0]["number"] = 0
    with pytest.raises(AnnotationError, match=r"\$\.darts\[0\]\.number"):
        _decode_modified(mutate)


@pytest.mark.parametrize("name", ["../dart-001.jpg", "dart-01.jpg", "dart-0001.jpg", "dart-001.png", "volley-001.mp4"])
def test_calibration_photo_name_must_be_a_dart_photo(name):
    def mutate(data):
        data["calibration"]["photo"] = name
    with pytest.raises(AnnotationError, match=r"\$\.calibration\.photo"):
        _decode_modified(mutate)


def test_calibration_photo_beyond_999_is_accepted():
    def mutate(data):
        data["calibration"]["photo"] = "dart-1000.jpg"
    assert _decode_modified(mutate).calibration.photo == "dart-1000.jpg"


@pytest.mark.parametrize("session", ["../x", "20261031-140322-0", "2026-10-31", ""])
def test_session_name_must_follow_the_capture_pattern(session):
    def mutate(data):
        data["session"] = session
    with pytest.raises(AnnotationError, match=r"\$\.session"):
        _decode_modified(mutate)


def test_truth_sha256_must_be_64_lowercase_hex():
    def mutate(data):
        data["truthSha256"] = TRUTH_SHA.upper()
    with pytest.raises(AnnotationError, match=r"\$\.truthSha256"):
        _decode_modified(mutate)


def test_calibration_size_must_be_positive():
    def mutate(data):
        data["calibration"]["width"] = 0
    with pytest.raises(AnnotationError, match=r"\$\.calibration\.width"):
        _decode_modified(mutate)


def test_invalid_utf8_bom_truncation_and_deep_nesting_are_rejected(tmp_path):
    path = tmp_path / "bad.json"
    for data in (b"\xff\xfe{}", b"\xef\xbb\xbf" + GOLDEN.read_bytes(), GOLDEN.read_bytes()[:100],
                 b"[" * 40 + b"]" * 40, b"[]"):
        path.write_bytes(data)
        with pytest.raises(AnnotationError):
            an.read(path)


# ---- consistency with the truth file ------------------------------------------------------------------------------

def _session(tmp_path):
    """Golden truth plus dart 3 (not voided, photo null) and dart 4 (judged, dart-004.jpg)."""
    data = json.loads(TRUTH_GOLDEN.read_text(encoding="utf-8"))
    darts = data["volleys"][0]["darts"]
    three = copy.deepcopy(darts[0])
    three.update(number=3, photo=None)
    four = copy.deepcopy(darts[0])
    four.update(number=4)
    four["photo"]["file"] = "dart-004.jpg"
    darts.extend([three, four])
    path = tmp_path / "truth.json"
    path.write_text(json.dumps(data), encoding="utf-8")
    return read_truth(path)


def _consistent(session):
    return Annotations(session.session, session.sha256,
                       Calibration("dart-004.jpg", 4080, 3072, ((1.0, 1.0), (2.0, 2.0), (3.0, 3.0), (4.0, 4.0))),
                       (DartAnnotation(1, Status.VISIBLE, (4080.0, 3072.0)), DartAnnotation(4, Status.HIDDEN, None)))


def test_consistent_annotation_has_no_warning(tmp_path):
    session = _session(tmp_path)
    assert an.validate_against(_consistent(session), session) == []


def test_unknown_dart_number_is_rejected(tmp_path):
    session = _session(tmp_path)
    a = _consistent(session)
    a = Annotations(a.session, a.truth_sha256, a.calibration, a.darts + (DartAnnotation(9, Status.OFF_BOARD, None),))
    with pytest.raises(AnnotationError, match="dart 9 does not exist"):
        an.validate_against(a, session)


def test_voided_dart_is_rejected_cv5(tmp_path):
    session = _session(tmp_path)
    a = _consistent(session)
    a = Annotations(a.session, a.truth_sha256, a.calibration, (DartAnnotation(2, Status.HIDDEN, None),))
    with pytest.raises(AnnotationError, match="dart 2 is not judged"):
        an.validate_against(a, session)


def test_dart_with_null_photo_is_rejected_cv5(tmp_path):
    session = _session(tmp_path)
    a = _consistent(session)
    a = Annotations(a.session, a.truth_sha256, a.calibration, (DartAnnotation(3, Status.OFF_BOARD, None),))
    with pytest.raises(AnnotationError, match="dart 3 is not judged"):
        an.validate_against(a, session)


def test_calibration_photo_not_in_the_session_is_rejected(tmp_path):
    session = _session(tmp_path)
    a = _consistent(session)
    calibration = Calibration("dart-003.jpg", 4080, 3072, a.calibration.points)
    with pytest.raises(AnnotationError, match=r"\$\.calibration\.photo"):
        an.validate_against(Annotations(a.session, a.truth_sha256, calibration, a.darts), session)


def test_tip_outside_the_session_photo_size_is_rejected(tmp_path):
    session = _session(tmp_path)
    a = _consistent(session)
    a = Annotations(a.session, a.truth_sha256, None, (DartAnnotation(1, Status.VISIBLE, (4080.5, 10.0)),))
    with pytest.raises(AnnotationError, match=r"\$\.darts\[0\]\.tip"):
        an.validate_against(a, session)


def test_other_session_name_is_rejected(tmp_path):
    session = _session(tmp_path)
    a = _consistent(session)
    with pytest.raises(AnnotationError, match=r"\$\.session"):
        an.validate_against(Annotations("20261031-140323", a.truth_sha256, a.calibration, a.darts), session)


def test_truth_sha256_mismatch_is_a_warning_not_an_error(tmp_path):
    session = _session(tmp_path)
    a = _consistent(session)
    warnings = an.validate_against(Annotations(a.session, "0" * 64, a.calibration, a.darts), session)
    assert len(warnings) == 1 and "truthSha256" in warnings[0]


# ---- atomic write (CV16) ------------------------------------------------------------------------------------------

def test_write_atomic_writes_the_encoded_text_and_leaves_no_temp_file(tmp_path):
    path = tmp_path / "20261031-140322.json"
    an.write_atomic(path, _full())
    assert path.read_text(encoding="utf-8") == GOLDEN.read_text(encoding="utf-8")
    assert sorted(p.name for p in tmp_path.iterdir()) == [path.name]


def test_exception_during_write_leaves_the_previous_file_intact(monkeypatch, tmp_path):
    path = tmp_path / "20261031-140322.json"
    an.write_atomic(path, _full())
    before = path.read_bytes()

    def failing_fsync(fd):
        raise OSError("disk full")
    monkeypatch.setattr(os, "fsync", failing_fsync)
    partial = Annotations("20261031-140322", TRUTH_SHA, None, ())
    with pytest.raises(AnnotationError, match="cannot write"):
        an.write_atomic(path, partial)
    assert path.read_bytes() == before
    assert sorted(p.name for p in tmp_path.iterdir()) == [path.name]


def test_write_atomic_refuses_an_invalid_value_and_keeps_the_file(tmp_path):
    path = tmp_path / "20261031-140322.json"
    an.write_atomic(path, _full())
    before = path.read_bytes()
    bad = Annotations("20261031-140322", TRUTH_SHA, None, (DartAnnotation(1, Status.VISIBLE, None),))
    with pytest.raises(AnnotationError):
        an.write_atomic(path, bad)
    assert path.read_bytes() == before


def test_import_closure_is_stdlib_only():
    root = Path(__file__).resolve().parent.parent
    # -I ignores PYTHONPATH, so the package root is put on sys.path by the command itself.
    code = f"import sys; sys.path.insert(0, {str(root)!r}); import kosmo_camera.annotations"
    result = subprocess.run([sys.executable, "-I", "-S", "-c", code], capture_output=True, text=True)
    assert result.returncode == 0, result.stderr
