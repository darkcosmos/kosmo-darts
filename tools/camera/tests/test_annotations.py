"""Annotation file format (docs/camera/annotation-format.md): CV2 calibration order, CV5 judged darts, CV16 writes,
CV20 calibration segments of version 2 and the lossless migration of version 1."""
import copy
import hashlib
import json
import os
import re
import subprocess
import sys
from pathlib import Path

import random

import pytest

from annotate_support import stdlib_pythons
from kosmo_camera import annotations as an
from kosmo_camera.annotations import AnnotationError, Annotations, Calibration, DartAnnotation, Status
from kosmo_camera.truth import read_truth

DATA = Path(__file__).parent / "data"
GOLDEN = DATA / "annotations-v1-golden.json"
GOLDEN_V2 = DATA / "annotations-v2-golden.json"
TOOL_V1 = DATA / "annotations-v1-tool-written.json"
TRUTH_GOLDEN = DATA / "truth-v2-golden.json"
DOC = Path(__file__).resolve().parents[3] / "docs" / "camera" / "annotation-format.md"
TRUTH_SHA = "24c0d987d33c4c93aba546caf5d77fa49ee86b3eb2dc62c4763734f3d5083fcf"


def _full():
    return Annotations(
        session="20261031-140322",
        truth_sha256=TRUTH_SHA,
        calibrations=(Calibration(1, "dart-001.jpg", 4080, 3072,
                                  ((2040.5, 610.25), (3301.0, 1290.0), (2702.75, 2650.0), (1102.0, 2300.5))),),
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
    partial = Annotations("20261031-140322", TRUTH_SHA, (), (DartAnnotation(2, Status.HIDDEN, None),))
    assert an.decode(an.encode(partial)) == partial


def test_empty_annotation_in_progress_is_valid():
    empty = Annotations("20261031-140322", TRUTH_SHA, (), ())
    assert an.decode(an.encode(empty)) == empty


def test_golden_text_key_order_indentation_and_no_trailing_newline():
    text = GOLDEN_V2.read_text(encoding="utf-8")
    assert an.encode(_full()) == text
    assert not text.endswith("\n")
    assert text.splitlines()[1] == '  "format": "kosmo-camera-annotations",'


def test_golden_reads_with_cv2_calibration_order():
    a = an.read(GOLDEN_V2)
    assert a.calibrations[0].points[0] == (2040.5, 610.25)  # cal1 on wire 5|20 (CV2)
    assert a.calibrations[0].points[3] == (1102.0, 2300.5)  # cal4 on wire 8|11 (CV2)
    assert [d.status for d in a.darts] == [Status.VISIBLE, Status.HIDDEN, Status.OFF_BOARD]


def test_doc_examples_equal_the_v2_and_v1_golden_texts_cv20():
    doc = DOC.read_text(encoding="utf-8")
    blocks = re.findall(r"```json\n(.*?)\n```", doc, flags=re.DOTALL)
    assert blocks == [GOLDEN_V2.read_text(encoding="utf-8"), GOLDEN.read_text(encoding="utf-8")]


def test_integer_coordinates_read_as_floats():
    def mutate(data):
        data["darts"][0]["tip"] = {"x": 10, "y": 20}
    assert _decode_modified(mutate).darts[0].tip == (10.0, 20.0)


def test_encode_refuses_an_invalid_value():
    bad = Annotations("20261031-140322", TRUTH_SHA, (), (DartAnnotation(1, Status.HIDDEN, (1.0, 2.0)),))
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


@pytest.mark.parametrize("version", [0, 3, "1", True, None, 1.0])
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
    assert _decode_modified(mutate).calibrations[0].points[1] == (4080.0, 3072.0)


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


@pytest.mark.parametrize("number", [1, 42, 999, 1000])
def test_photo_name_is_a_valid_dart_photo_name(number):
    from kosmo_camera.annotations import _PHOTO, photo_name
    assert _PHOTO.fullmatch(photo_name(number))
    assert photo_name(7) == "dart-007.jpg"


@pytest.mark.parametrize("name", ["../dart-001.jpg", "dart-01.jpg", "dart-0001.jpg", "dart-001.png", "volley-001.mp4"])
def test_calibration_photo_name_must_be_a_dart_photo(name):
    def mutate(data):
        data["calibration"]["photo"] = name
    with pytest.raises(AnnotationError, match=r"\$\.calibration\.photo"):
        _decode_modified(mutate)


def test_calibration_photo_beyond_999_is_accepted():
    def mutate(data):
        data["calibration"]["photo"] = "dart-1000.jpg"
    assert _decode_modified(mutate).calibrations[0].photo == "dart-1000.jpg"


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


# ---- version 2 and the migration of version 1 (CV20) --------------------------------------------------------------

def _to_v1(annotations):
    """Test-only inverse of the migration: the v1 dict a v2 model with at most one segment from dart 1 came from."""
    assert len(annotations.calibrations) <= 1
    v2 = json.loads(an.encode(annotations))
    segments = v2.pop("calibrations")
    darts = v2.pop("darts")
    calibration = None
    if segments:
        calibration = dict(segments[0])
        assert calibration.pop("fromDart") == 1
    return {**v2, "version": 1, "calibration": calibration, "darts": darts}


def _v2_dict():
    return json.loads(GOLDEN_V2.read_text(encoding="utf-8"))


def _decode_v2_modified(mutate):
    data = _v2_dict()
    mutate(data)
    return an.decode(json.dumps(data))


def _segment(from_dart, photo=None):
    return {"fromDart": from_dart, "photo": photo or f"dart-{from_dart:03d}.jpg", "width": 4080, "height": 3072,
            "points": [{"x": 1.0, "y": 2.0}, {"x": 3.0, "y": 4.0}, {"x": 5.0, "y": 6.0}, {"x": 7.0, "y": 8.0}]}


def test_writer_writes_version_2_only_cv20():
    assert an.VERSION == 2
    assert json.loads(an.encode(_full()))["version"] == 2


def test_v1_golden_migrates_to_the_v2_golden_byte_for_byte_cv20():
    migrated = an.decode(GOLDEN.read_text(encoding="utf-8"))
    assert migrated == _full()
    assert an.encode(migrated) == GOLDEN_V2.read_text(encoding="utf-8")


def test_v1_golden_round_trips_through_v2_to_the_same_v1_dict_cv20():
    v2_text = an.encode(an.decode(GOLDEN.read_text(encoding="utf-8")))
    assert _to_v1(an.decode(v2_text)) == _golden_dict()


def test_migration_copies_every_field_and_starts_the_segment_at_dart_1_cv20():
    points = ((1.0, 2.0), (3.0, 4.0), (5.0, 6.0), (7.0, 8.0))
    darts = (DartAnnotation(4, Status.VISIBLE, (9.0, 9.5)), DartAnnotation(7, Status.HIDDEN, None))
    v1 = an.AnnotationsV1("20261031-140322-2", "ab" * 32, an.CalibrationV1("dart-004.jpg", 100, 50, points), darts)
    migrated = an.migrate_v1(v1)
    assert migrated == Annotations("20261031-140322-2", "ab" * 32,
                                   (Calibration(1, "dart-004.jpg", 100, 50, points),), darts)
    assert an.migrate_v1(an.AnnotationsV1(v1.session, v1.truth_sha256, None, ())).calibrations == ()


def test_v1_with_null_calibration_gives_no_segment_and_encodes_back_cv20():
    v1 = _golden_dict()
    v1["calibration"] = None
    migrated = an.decode(json.dumps(v1, indent=2))
    assert migrated.calibrations == ()
    v2_text = an.encode(migrated)
    assert json.loads(v2_text)["calibrations"] == []
    assert _to_v1(an.decode(v2_text)) == v1


def _random_v1(rng):
    width, height = rng.randint(1, 5000), rng.randint(1, 5000)

    def point(precision):
        return {"x": round(rng.uniform(0, width), precision), "y": round(rng.uniform(0, height), precision)}
    calibration = None
    if rng.random() < 0.8:
        calibration = {"photo": f"dart-{rng.randint(1, 1500):03d}.jpg", "width": width, "height": height,
                       "points": [point(rng.randint(0, 6)) for _ in range(4)]}
    darts = []
    for number in sorted(rng.sample(range(1, 400), rng.randint(0, 30))):
        status = rng.choice(list(Status))
        darts.append({"number": number, "status": status.value,
                      "tip": point(rng.randint(0, 6)) if status is Status.VISIBLE else None})
    suffix = rng.choice(["", "-2", f"-{rng.randint(10, 99)}"])
    return {"format": an.FORMAT, "version": 1, "session": f"2026{rng.randint(1000, 1231)}-{rng.randint(100000, 235959)}"
            f"{suffix}", "truthSha256": "".join(rng.choice("0123456789abcdef") for _ in range(64)),
            "calibration": calibration, "darts": darts}


@pytest.mark.parametrize("seed", range(50))
def test_seeded_v1_files_migrate_encode_decode_and_invert_to_the_original_cv20(seed):
    v1 = _random_v1(random.Random(seed))
    migrated = an.decode(json.dumps(v1, indent=2))
    v2_text = an.encode(migrated)
    reread = an.decode(v2_text)
    assert reread == migrated
    assert _to_v1(reread) == v1


def test_v1_file_written_by_the_part_a_tool_round_trips_to_the_same_v1_dict_cv20():
    text = TOOL_V1.read_text(encoding="utf-8")
    v1 = json.loads(text)
    assert v1["version"] == 1
    migrated = an.read(TOOL_V1)
    assert len(migrated.calibrations) == 1 and migrated.calibrations[0].from_dart == 1
    assert sorted(d.status.value for d in migrated.darts) == ["HIDDEN"] * 3 + ["OFF_BOARD"] * 3 + ["VISIBLE"] * 3
    assert _to_v1(an.decode(an.encode(migrated))) == v1


def test_max_segments_accepted_and_one_more_rejected_cv20():
    def with_segments(count):
        def mutate(data):
            data["calibrations"] = [_segment(n) for n in range(1, count + 1)]
        return mutate
    assert len(_decode_v2_modified(with_segments(an.MAX_SEGMENTS)).calibrations) == an.MAX_SEGMENTS
    with pytest.raises(AnnotationError, match=rf"\$\.calibrations: expected at most {an.MAX_SEGMENTS} segments"):
        _decode_v2_modified(with_segments(an.MAX_SEGMENTS + 1))


def test_encode_refuses_more_than_max_segments_cv20():
    calibration = _full().calibrations[0]
    segments = tuple(Calibration(n, calibration.photo, calibration.width, calibration.height, calibration.points)
                     for n in range(1, an.MAX_SEGMENTS + 2))
    with pytest.raises(AnnotationError, match="at most"):
        an.encode(Annotations("20261031-140322", TRUTH_SHA, segments, ()))


def _set(path_keys, value):
    def mutate(data):
        target = data
        for key in path_keys[:-1]:
            target = target[key]
        target[path_keys[-1]] = value
    return mutate


@pytest.mark.parametrize("mutate, message", [
    (_set(["calibrations", 0, "fromDart"], 2), r"\$\.calibrations\[0\]\.fromDart: the first segment must start at dart 1"),
    (_set(["calibrations"], [_segment(1), _segment(5), _segment(5)]),
     r"\$\.calibrations\[2\]\.fromDart: must be greater than"),
    (_set(["calibrations"], [_segment(1), _segment(5), _segment(3)]),
     r"\$\.calibrations\[2\]\.fromDart: must be greater than"),
    (_set(["calibrations", 0, "fromDart"], 0), r"\$\.calibrations\[0\]\.fromDart: expected at least 1"),
    (_set(["calibrations", 0, "fromDart"], 1.0), r"\$\.calibrations\[0\]\.fromDart: expected an integer"),
    (_set(["calibrations", 0, "fromDart"], True), r"\$\.calibrations\[0\]\.fromDart: expected an integer"),
    (_set(["calibrations", 0, "extra"], 1), r"\$\.calibrations\[0\]: unknown field 'extra'"),
    (_set(["calibration"], None), r"\$: unknown field 'calibration'"),
    (_set(["calibrations"], None), r"\$\.calibrations: expected an array"),
    (_set(["calibrations", 0, "points"], []), r"\$\.calibrations\[0\]\.points: expected exactly 4 points"),
    (_set(["calibrations", 0, "photo"], "../dart-001.jpg"), r"\$\.calibrations\[0\]\.photo"),
    (_set(["version"], 3), r"\$\.version: unsupported version 3"),
    (_set(["version"], 0), r"\$\.version: unsupported version 0"),
], ids=["first-fromDart-2", "equal-fromDart", "descending-fromDart", "fromDart-0", "fromDart-float", "fromDart-bool",
        "unknown-segment-key", "calibration-key-in-v2", "calibrations-null", "points", "photo", "version-3",
        "version-0"])
def test_v2_strict_rejections_with_their_json_path_cv20(mutate, message):
    with pytest.raises(AnnotationError, match=message):
        _decode_v2_modified(mutate)


def test_v2_missing_from_dart_is_rejected_cv20():
    def mutate(data):
        del data["calibrations"][0]["fromDart"]
    with pytest.raises(AnnotationError, match=r"\$\.calibrations\[0\]: missing field 'fromDart'"):
        _decode_v2_modified(mutate)


def test_calibrations_key_in_v1_is_rejected_cv20():
    def mutate(data):
        data["calibrations"] = []
    with pytest.raises(AnnotationError, match=r"\$: unknown field 'calibrations'"):
        _decode_modified(mutate)


def test_v2_with_an_empty_calibration_list_is_valid_cv20():
    assert _decode_v2_modified(_set(["calibrations"], [])).calibrations == ()


def test_segment_for_picks_the_last_segment_starting_at_or_before_the_dart_cv20():
    first, second = (Calibration(n, f"dart-{n:03d}.jpg", 4080, 3072, ((1.0, 1.0),) * 4) for n in (1, 4))
    a = Annotations("20261031-140322", TRUTH_SHA, (first, second), ())
    assert an.segment_for(a, 1) == (0, first)
    assert an.segment_for(a, 3) == (0, first)  # before the fromDart of segment 1
    assert an.segment_for(a, 4) == (1, second)  # at its fromDart
    assert an.segment_for(a, 9) == (1, second)  # after it
    assert an.segment_for(Annotations("20261031-140322", TRUTH_SHA, (), ()), 1) is None


def test_segments_sha256_hashes_the_encoded_calibration_list_cv20():
    full = _full()
    encoded = json.loads(an.encode(full))["calibrations"]
    expected = hashlib.sha256(json.dumps(encoded, sort_keys=True, separators=(",", ":")).encode("utf-8")).hexdigest()
    assert an.segments_sha256(full) == expected
    moved = Calibration(1, "dart-001.jpg", 4080, 3072, ((1.0, 1.0),) * 4)
    assert an.segments_sha256(Annotations(full.session, full.truth_sha256, (moved,), full.darts)) != expected
    other_darts = Annotations(full.session, full.truth_sha256, full.calibrations, ())
    assert an.segments_sha256(other_darts) == expected  # only the segments count
    empty = Annotations(full.session, full.truth_sha256, (), ())
    assert an.segments_sha256(empty) == hashlib.sha256(b"[]").hexdigest()


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
                       (Calibration(1, "dart-004.jpg", 4080, 3072, ((1.0, 1.0), (2.0, 2.0), (3.0, 3.0), (4.0, 4.0))),),
                       (DartAnnotation(1, Status.VISIBLE, (4080.0, 3072.0)), DartAnnotation(4, Status.HIDDEN, None)))


def test_consistent_annotation_has_no_warning(tmp_path):
    session = _session(tmp_path)
    assert an.validate_against(_consistent(session), session) == []


def test_unknown_dart_number_is_rejected(tmp_path):
    session = _session(tmp_path)
    a = _consistent(session)
    a = Annotations(a.session, a.truth_sha256, a.calibrations, a.darts + (DartAnnotation(9, Status.OFF_BOARD, None),))
    with pytest.raises(AnnotationError, match="dart 9 does not exist"):
        an.validate_against(a, session)


def test_voided_dart_is_rejected_cv5(tmp_path):
    session = _session(tmp_path)
    a = _consistent(session)
    a = Annotations(a.session, a.truth_sha256, a.calibrations, (DartAnnotation(2, Status.HIDDEN, None),))
    with pytest.raises(AnnotationError, match="dart 2 is not judged"):
        an.validate_against(a, session)


def test_dart_with_null_photo_is_rejected_cv5(tmp_path):
    session = _session(tmp_path)
    a = _consistent(session)
    a = Annotations(a.session, a.truth_sha256, a.calibrations, (DartAnnotation(3, Status.OFF_BOARD, None),))
    with pytest.raises(AnnotationError, match="dart 3 is not judged"):
        an.validate_against(a, session)


def test_calibration_photo_not_in_the_session_is_rejected(tmp_path):
    session = _session(tmp_path)
    a = _consistent(session)
    calibration = Calibration(1, "dart-003.jpg", 4080, 3072, a.calibrations[0].points)
    with pytest.raises(AnnotationError, match=r"\$\.calibrations\[0\]\.photo"):
        an.validate_against(Annotations(a.session, a.truth_sha256, (calibration,), a.darts), session)


def _with_second_segment(a, from_dart, photo):
    second = Calibration(from_dart, photo, 4080, 3072, a.calibrations[0].points)
    return Annotations(a.session, a.truth_sha256, (a.calibrations[0], second), a.darts)


def test_second_segment_on_a_judged_dart_with_its_photo_is_accepted_cv20(tmp_path):
    session = _session(tmp_path)
    assert an.validate_against(_with_second_segment(_consistent(session), 4, "dart-004.jpg"), session) == []


@pytest.mark.parametrize("from_dart, message", [
    (2, r"\$\.calibrations\[1\]\.fromDart: dart 2 is not judged"),  # voided (CV5)
    (3, r"\$\.calibrations\[1\]\.fromDart: dart 3 is not judged"),  # no photo (CV5)
    (9, r"\$\.calibrations\[1\]\.fromDart: dart 9 does not exist"),
])
def test_second_segment_on_a_dart_that_is_not_judged_is_rejected_cv20(tmp_path, from_dart, message):
    session = _session(tmp_path)
    with pytest.raises(AnnotationError, match=message):
        an.validate_against(_with_second_segment(_consistent(session), from_dart, "dart-001.jpg"), session)


def test_second_segment_with_another_dart_photo_is_rejected_cv20(tmp_path):
    session = _session(tmp_path)
    with pytest.raises(AnnotationError, match=r"\$\.calibrations\[1\]\.photo: expected 'dart-004\.jpg'"):
        an.validate_against(_with_second_segment(_consistent(session), 4, "dart-001.jpg"), session)


def test_tip_outside_the_session_photo_size_is_rejected(tmp_path):
    session = _session(tmp_path)
    a = _consistent(session)
    a = Annotations(a.session, a.truth_sha256, (), (DartAnnotation(1, Status.VISIBLE, (4080.5, 10.0)),))
    with pytest.raises(AnnotationError, match=r"\$\.darts\[0\]\.tip"):
        an.validate_against(a, session)


def test_other_session_name_is_rejected(tmp_path):
    session = _session(tmp_path)
    a = _consistent(session)
    with pytest.raises(AnnotationError, match=r"\$\.session"):
        an.validate_against(Annotations("20261031-140323", a.truth_sha256, a.calibrations, a.darts), session)


def test_truth_sha256_mismatch_is_a_warning_not_an_error(tmp_path):
    session = _session(tmp_path)
    a = _consistent(session)
    warnings = an.validate_against(Annotations(a.session, "0" * 64, a.calibrations, a.darts), session)
    assert len(warnings) == 1 and "truthSha256" in warnings[0]


# ---- atomic write (CV16) ------------------------------------------------------------------------------------------

def test_write_atomic_writes_the_encoded_text_and_leaves_no_temp_file(tmp_path):
    path = tmp_path / "20261031-140322.json"
    an.write_atomic(path, _full())
    assert path.read_text(encoding="utf-8") == GOLDEN_V2.read_text(encoding="utf-8")
    assert sorted(p.name for p in tmp_path.iterdir()) == [path.name]


def test_exception_during_write_leaves_the_previous_file_intact(monkeypatch, tmp_path):
    path = tmp_path / "20261031-140322.json"
    an.write_atomic(path, _full())
    before = path.read_bytes()

    def failing_fsync(fd):
        raise OSError("disk full")
    monkeypatch.setattr(os, "fsync", failing_fsync)
    partial = Annotations("20261031-140322", TRUTH_SHA, (), ())
    with pytest.raises(AnnotationError, match="cannot write"):
        an.write_atomic(path, partial)
    assert path.read_bytes() == before
    assert sorted(p.name for p in tmp_path.iterdir()) == [path.name]


def test_write_atomic_refuses_an_invalid_value_and_keeps_the_file(tmp_path):
    path = tmp_path / "20261031-140322.json"
    an.write_atomic(path, _full())
    before = path.read_bytes()
    bad = Annotations("20261031-140322", TRUTH_SHA, (), (DartAnnotation(1, Status.VISIBLE, None),))
    with pytest.raises(AnnotationError):
        an.write_atomic(path, bad)
    assert path.read_bytes() == before


@pytest.mark.parametrize("python", stdlib_pythons())
def test_import_closure_is_stdlib_only(python):
    root = Path(__file__).resolve().parent.parent
    # -I ignores PYTHONPATH, so the package root is put on sys.path by the command itself.
    code = f"import sys; sys.path.insert(0, {str(root)!r}); import kosmo_camera.annotations"
    result = subprocess.run([python, "-I", "-S", "-c", code], capture_output=True, text=True)
    assert result.returncode == 0, result.stderr
