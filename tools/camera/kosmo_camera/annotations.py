"""Annotation file of one capture session: calibration segments (CV2, CV20), control clicks (CV25) and dart tips of
judged darts (CV5).

Format: docs/camera/annotation-format.md. Stdlib only. Coordinates are image pixels in the stored pixel order
(origin top left, floats, EXIF orientation ignored, decision D8). The reader is strict: it rejects a file over 1 MiB
(never read past the cap), duplicate or unknown keys, missing fields, any other format or version, a `tip` that is
not present exactly when the status is VISIBLE, and duplicate or unordered dart numbers. Writes are atomic (CV16).

Versions (CV20, CV25): the reader knows versions 1, 2 and 3 and always returns the version 3 model. A version 1 body
is read by the kept version 1 rules, then `migrate_v1` turns its single `calibration` into the segment from dart 1
(`null` gives no segment), every other field copied unchanged. A version 2 body is read by the kept version 2 rules,
then `migrate_v2` adds an empty control list and the status `manual` on every point of every segment. The writer
writes version 3 only.
"""
import enum
import hashlib
import json
import math
import os
import re
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from kosmo_camera import strict_json

MAX_BYTES = 1024 * 1024
MAX_SEGMENTS = 64  # CV20: calibration segments per file
MAX_CONTROLS = 1024  # CV25: control clicks per file
MAX_DEPTH = 8
FORMAT = "kosmo-camera-annotations"
VERSION = 3  # the only version written
VERSION_2 = 2  # still read, then migrated (CV25)
VERSION_1 = 1  # still read, then migrated (CV20, CV25)
CALIBRATION_POINTS = 4  # CV2: cal1 to cal4
SNAPPED = "snapped"  # CV31: the point is the crossing found by the snapping
UNSNAPPED = "unsnapped"  # CV31: no clear crossing, the point is the click itself
MANUAL = "manual"  # CV25: segment point clicked before snapping existed (migrated from version 2)
MANUAL_SNAP = (MANUAL,) * CALIBRATION_POINTS
_SEGMENT_SNAP = (SNAPPED, UNSNAPPED, MANUAL)
_CONTROL_SNAP = (SNAPPED, UNSNAPPED)
PHOTO = "photo"  # control source: the dart photo
VIDEO = "video"  # control source: a frame of the volley video, at `positionMs`
SOURCES = (PHOTO, VIDEO)  # in file order: the photo control of a dart comes before its video control
_PHOTO = re.compile(r"dart-(\d{3}|[1-9]\d{3,})\.jpg")


def photo_name(number: int) -> str:
    """The dart photo name of the capture format ('dart-NNN.jpg'); matches `_PHOTO` and the truth `photo.file`."""
    return f"dart-{number:03d}.jpg"


SESSION_NAME = re.compile(r"\d{8}-\d{6}(-[2-9]|-[1-9]\d+)?")  # a capture folder name (shared by the file readers)
_SHA256 = re.compile(r"[0-9a-f]{64}")


class AnnotationError(ValueError):
    """The annotation file is unreadable, breaks the format or contradicts its truth file; starts with a JSON path."""


class Status(enum.Enum):
    VISIBLE = "VISIBLE"
    HIDDEN = "HIDDEN"
    OFF_BOARD = "OFF_BOARD"


@dataclass(frozen=True)
class Calibration:
    """One calibration segment (CV20): applies from dart `from_dart` until the `from_dart` of the next segment."""
    from_dart: int
    photo: str
    width: int
    height: int
    points: tuple  # 4 (x, y) tuples in CV2 order
    snap: tuple = MANUAL_SNAP  # 4 statuses aligned with points: SNAPPED, UNSNAPPED or MANUAL (CV25, CV31)


@dataclass(frozen=True)
class Control:
    """Control clicks of one image (CV25): the 4 calibration points of a dart photo or of a video frame, independent
    of the segments."""
    dart: int
    source: str  # PHOTO or VIDEO
    position_ms: Optional[int]  # frame position in the volley video, set exactly when source is VIDEO
    width: int
    height: int
    points: tuple  # 4 entries in CV2 order, each (x, y) or None (point hidden)
    snap: tuple  # 4 entries aligned with points: SNAPPED or UNSNAPPED for a point, None for a hidden one (CV31)


@dataclass(frozen=True)
class DartAnnotation:
    number: int
    status: Status
    tip: Optional[tuple]  # (x, y) exactly when status is VISIBLE


@dataclass(frozen=True)
class Annotations:
    """The version 3 model, the only one tools use."""
    session: str
    truth_sha256: str
    calibrations: tuple  # Calibration segments, first from dart 1, `from_dart` strictly ascending (CV20)
    darts: tuple  # DartAnnotation, strictly ascending numbers
    controls: tuple = ()  # Control, sorted by dart then PHOTO before VIDEO, (dart, source) unique (CV25)


@dataclass(frozen=True)
class CalibrationV2:
    """One segment of a version 2 file, before `migrate_v2` (no `snap`)."""
    from_dart: int
    photo: str
    width: int
    height: int
    points: tuple


@dataclass(frozen=True)
class AnnotationsV2:
    """A version 2 file as read by the kept version 2 rules, before `migrate_v2`."""
    session: str
    truth_sha256: str
    calibrations: tuple  # CalibrationV2
    darts: tuple


@dataclass(frozen=True)
class CalibrationV1:
    """The single `calibration` object of a version 1 file."""
    photo: str
    width: int
    height: int
    points: tuple


@dataclass(frozen=True)
class AnnotationsV1:
    """A version 1 file as read by the kept version 1 rules, before `migrate_v1`."""
    session: str
    truth_sha256: str
    calibration: Optional[CalibrationV1]
    darts: tuple


# ---- field checks -------------------------------------------------------------------------------------------------

def _fail(path, message):
    raise AnnotationError(f"{path}: {message}")


def _object(value, path, keys):
    if not isinstance(value, dict):
        _fail(path, "expected an object")
    for key in value:
        if key not in keys:
            _fail(path, f"unknown field '{key}'")
    for key in keys:
        if key not in value:
            _fail(path, f"missing field '{key}'")
    return value


def _pattern(value, path, pattern, expected):
    if not isinstance(value, str) or not pattern.fullmatch(value):
        _fail(path, f"expected {expected}")
    return value


def _int(value, path, minimum):
    if isinstance(value, bool) or not isinstance(value, int):
        _fail(path, "expected an integer")
    if value < minimum:
        _fail(path, f"expected at least {minimum}")
    return value


def _coordinate(value, path, limit=None):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        _fail(path, "expected a finite number")
    if value < 0 or (limit is not None and value > limit):
        _fail(path, f"expected a value from 0 to {limit}" if limit is not None else "expected a value of at least 0")
    return float(value)


def _point(value, path, width=None, height=None):
    _object(value, path, ("x", "y"))
    return _coordinate(value["x"], f"{path}.x", width), _coordinate(value["y"], f"{path}.y", height)


def _array(value, path):
    if not isinstance(value, list):
        _fail(path, "expected an array")
    return value


_CALIBRATION_KEYS = ("photo", "width", "height", "points")


def _calibration_fields(value, path):
    """`photo`, `width`, `height` and `points`, with the same rules in versions 1 and 2."""
    photo = _pattern(value["photo"], f"{path}.photo", _PHOTO, "a dart photo name 'dart-NNN.jpg'")
    width = _int(value["width"], f"{path}.width", 1)
    height = _int(value["height"], f"{path}.height", 1)
    points = _array(value["points"], f"{path}.points")
    if len(points) != CALIBRATION_POINTS:
        _fail(f"{path}.points", f"expected exactly {CALIBRATION_POINTS} points (CV2)")
    return photo, width, height, tuple(_point(p, f"{path}.points[{i}]", width, height) for i, p in enumerate(points))


def _calibration_v1(value, path):
    _object(value, path, _CALIBRATION_KEYS)
    return CalibrationV1(*_calibration_fields(value, path))


def _segment_list(value, path, extra_keys, build):
    """Segment list (CV20): at most MAX_SEGMENTS, first from dart 1, `fromDart` strictly ascending. `build(item,
    item_path, from_dart, photo, width, height, points)` makes one segment of the version being read."""
    items = _array(value, path)
    if len(items) > MAX_SEGMENTS:
        _fail(path, f"expected at most {MAX_SEGMENTS} segments (CV20)")
    segments = []
    for i, item in enumerate(items):
        item_path = f"{path}[{i}]"
        _object(item, item_path, ("fromDart",) + _CALIBRATION_KEYS + extra_keys)
        from_dart = _int(item["fromDart"], f"{item_path}.fromDart", 1)
        if i == 0 and from_dart != 1:
            _fail(f"{item_path}.fromDart", "the first segment must start at dart 1 (CV20)")
        if i > 0 and from_dart <= segments[-1].from_dart:
            _fail(f"{item_path}.fromDart", f"must be greater than the previous segment's {segments[-1].from_dart} "
                  "(CV20)")
        segments.append(build(item, item_path, from_dart, *_calibration_fields(item, item_path)))
    return tuple(segments)


def _calibrations_v2(value, path):
    """The version 2 segment rules, kept unchanged (CV20)."""
    return _segment_list(value, path, (), lambda item, item_path, *fields: CalibrationV2(*fields))


def _snap(value, path, points, allowed):
    """`snap` (CV25): exactly 4 entries aligned with `points`, a status of `allowed` for a point, null for a null
    point."""
    items = _array(value, path)
    if len(items) != CALIBRATION_POINTS:
        _fail(path, f"expected exactly {CALIBRATION_POINTS} entries aligned with points (CV25)")
    for i, (status, point) in enumerate(zip(items, points)):
        if point is None:
            if status is not None:
                _fail(f"{path}[{i}]", "must be null for a null point (CV25)")
        elif not isinstance(status, str) or status not in allowed:
            _fail(f"{path}[{i}]", f"expected one of {', '.join(allowed)} (CV25)")
    return tuple(items)


def _calibrations(value, path):
    """Version 3 segment list: the version 2 rules plus `snap`, whose statuses may also be `manual` (CV25)."""
    def build(item, item_path, from_dart, photo, width, height, points):
        return Calibration(from_dart, photo, width, height, points,
                           _snap(item["snap"], f"{item_path}.snap", points, _SEGMENT_SNAP))
    return _segment_list(value, path, ("snap",), build)


_CONTROL_KEYS = ("dart", "source", "positionMs", "width", "height", "points", "snap")


def _control(value, path):
    if not isinstance(value, dict):
        _fail(path, "expected an object")
    if "source" not in value:
        _fail(path, "missing field 'source'")
    source = value["source"]
    if not isinstance(source, str) or source not in SOURCES:
        _fail(f"{path}.source", f"expected one of {', '.join(SOURCES)} (CV25)")
    if ("positionMs" in value) != (source == VIDEO):
        _fail(f"{path}.positionMs", "must be present exactly when source is 'video' (CV25)")
    _object(value, path, _CONTROL_KEYS if source == VIDEO else tuple(k for k in _CONTROL_KEYS if k != "positionMs"))
    dart = _int(value["dart"], f"{path}.dart", 1)
    position_ms = _int(value["positionMs"], f"{path}.positionMs", 0) if source == VIDEO else None
    width = _int(value["width"], f"{path}.width", 1)
    height = _int(value["height"], f"{path}.height", 1)
    items = _array(value["points"], f"{path}.points")
    if len(items) != CALIBRATION_POINTS:
        _fail(f"{path}.points", f"expected exactly {CALIBRATION_POINTS} entries (CV2, CV25)")
    points = tuple(None if p is None else _point(p, f"{path}.points[{i}]", width, height) for i, p in enumerate(items))
    return Control(dart, source, position_ms, width, height, points,
                   _snap(value["snap"], f"{path}.snap", points, _CONTROL_SNAP))


def _controls(value, path):
    """Control list (CV25): at most MAX_CONTROLS, sorted by dart then photo before video, (dart, source) unique."""
    items = _array(value, path)
    if len(items) > MAX_CONTROLS:
        _fail(path, f"expected at most {MAX_CONTROLS} controls (CV25)")
    controls = tuple(_control(item, f"{path}[{i}]") for i, item in enumerate(items))
    for i in range(1, len(controls)):
        previous, current = _control_order(controls[i - 1]), _control_order(controls[i])
        if current == previous:
            _fail(f"{path}[{i}]", f"duplicate control for dart {controls[i].dart} and source '{controls[i].source}'"
                  " (CV25)")
        if current < previous:
            _fail(f"{path}[{i}]", "controls must be sorted by dart, then photo before video (CV25)")
    return controls


def _control_order(control):
    return control.dart, SOURCES.index(control.source)


def _dart(value, path):
    _object(value, path, ("number", "status", "tip"))
    number = _int(value["number"], f"{path}.number", 1)
    status_text = value["status"]
    if not isinstance(status_text, str) or status_text not in Status.__members__:
        _fail(f"{path}.status", f"expected one of {', '.join(Status.__members__)}")
    status = Status(status_text)
    tip = value["tip"]
    if (status is Status.VISIBLE) != (tip is not None):
        _fail(f"{path}.tip", "must be set exactly when status is VISIBLE")
    return DartAnnotation(number, status, None if tip is None else _point(tip, f"{path}.tip"))


def _darts(value, path):
    darts = tuple(_dart(item, f"{path}[{i}]") for i, item in enumerate(_array(value, path)))
    for i in range(1, len(darts)):
        if darts[i].number == darts[i - 1].number:
            _fail(f"{path}[{i}].number", f"duplicate dart number {darts[i].number}")
        if darts[i].number < darts[i - 1].number:
            _fail(f"{path}[{i}].number", "dart numbers must be in ascending order")
    return darts


def _session_and_sha(root):
    return (_pattern(root["session"], "$.session", SESSION_NAME, "a capture session name 'YYYYMMDD-HHMMSS[-N]'"),
            _pattern(root["truthSha256"], "$.truthSha256", _SHA256, "64 lowercase hexadecimal digits"))


def _annotations_v1(root) -> AnnotationsV1:
    """The version 1 rules, kept unchanged so Kosmo's version 1 files stay readable (CV20)."""
    _object(root, "$", ("format", "version", "session", "truthSha256", "calibration", "darts"))
    session, truth_sha256 = _session_and_sha(root)
    calibration = root["calibration"]
    return AnnotationsV1(session, truth_sha256,
                         None if calibration is None else _calibration_v1(calibration, "$.calibration"),
                         _darts(root["darts"], "$.darts"))


def _annotations_v2(root) -> AnnotationsV2:
    """The version 2 rules, kept unchanged so Kosmo's version 2 files stay readable (CV20, CV25)."""
    _object(root, "$", ("format", "version", "session", "truthSha256", "calibrations", "darts"))
    session, truth_sha256 = _session_and_sha(root)
    return AnnotationsV2(session, truth_sha256, _calibrations_v2(root["calibrations"], "$.calibrations"),
                         _darts(root["darts"], "$.darts"))


def _annotations_v3(root) -> Annotations:
    _object(root, "$", ("format", "version", "session", "truthSha256", "calibrations", "controls", "darts"))
    session, truth_sha256 = _session_and_sha(root)
    return Annotations(session, truth_sha256, _calibrations(root["calibrations"], "$.calibrations"),
                       _darts(root["darts"], "$.darts"), _controls(root["controls"], "$.controls"))


def migrate_v1(v1) -> AnnotationsV2:
    """Lossless migration (CV20): the v1 calibration becomes the single segment from dart 1, `None` no segment;
    session, `truthSha256` and darts are copied unchanged (the stale-truth warning keeps working)."""
    calibration = v1.calibration
    segments = () if calibration is None else (
        CalibrationV2(1, calibration.photo, calibration.width, calibration.height, calibration.points),)
    return AnnotationsV2(v1.session, v1.truth_sha256, segments, v1.darts)


def migrate_v2(v2) -> Annotations:
    """Lossless migration (CV25): no control, every point of every segment gets the status `manual` (clicked before
    snapping existed); every other field is copied unchanged."""
    segments = tuple(Calibration(c.from_dart, c.photo, c.width, c.height, c.points, MANUAL_SNAP)
                     for c in v2.calibrations)
    return Annotations(v2.session, v2.truth_sha256, segments, v2.darts, ())


# ---- parsing ------------------------------------------------------------------------------------------------------

def _parse(text):
    if text.startswith("\ufeff"):
        raise AnnotationError("$: byte order mark not allowed")
    try:
        return json.loads(text, object_pairs_hook=strict_json.no_duplicates,
                          parse_constant=strict_json.reject_constant)
    except (json.JSONDecodeError, RecursionError) as error:
        raise AnnotationError(f"$: invalid JSON: {error}") from error
    except strict_json.StrictJsonError as error:
        raise AnnotationError(str(error)) from error


def decode(text) -> Annotations:
    """Validate the text of an annotation file of version 1, 2 or 3; raise AnnotationError on any deviation.

    Always returns the version 3 model: a version 1 or 2 file is migrated in memory, never rewritten here (CV16,
    CV20, CV25).
    """
    root = _parse(text)
    if strict_json.depth(root) > MAX_DEPTH:
        raise AnnotationError(f"$: nesting deeper than {MAX_DEPTH} levels")
    if not isinstance(root, dict):
        raise AnnotationError("$: expected an object")
    if root.get("format") != FORMAT:
        raise AnnotationError(f"$.format: expected '{FORMAT}'")
    version = root.get("version")
    if isinstance(version, bool) or not isinstance(version, int) or version not in (VERSION_1, VERSION_2, VERSION):
        raise AnnotationError(f"$.version: unsupported version {version!r}")
    if version == VERSION_1:
        return migrate_v2(migrate_v1(_annotations_v1(root)))
    if version == VERSION_2:
        return migrate_v2(_annotations_v2(root))
    return _annotations_v3(root)


def read(path) -> Annotations:
    """Read and validate an annotation file of at most MAX_BYTES; never reads past the cap."""
    try:
        with Path(path).open("rb") as handle:
            data = handle.read(MAX_BYTES + 1)
    except OSError as error:
        raise AnnotationError(f"$: cannot read {path}: {error.strerror or error}") from error
    if len(data) > MAX_BYTES:
        raise AnnotationError(f"$: file is larger than {MAX_BYTES} bytes")
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError as error:
        raise AnnotationError(f"$: invalid UTF-8: {error.reason}") from error
    return decode(text)


# ---- encoding and writing -----------------------------------------------------------------------------------------

def _point_json(point):
    return {"x": float(point[0]), "y": float(point[1])}


def _segment_geometry_json(segment):
    return {"fromDart": segment.from_dart, "photo": segment.photo, "width": segment.width, "height": segment.height,
            "points": [_point_json(p) for p in segment.points]}


def _calibrations_json(annotations):
    return [{**_segment_geometry_json(c), "snap": list(c.snap)} for c in annotations.calibrations]


def _control_json(control):
    item = {"dart": control.dart, "source": control.source}
    if control.source == VIDEO or control.position_ms is not None:  # a photo control with a position fails decode
        item["positionMs"] = control.position_ms
    item.update(width=control.width, height=control.height,
                points=[None if p is None else _point_json(p) for p in control.points], snap=list(control.snap))
    return item


def _controls_json(annotations):
    return [_control_json(c) for c in annotations.controls]


def encode(annotations) -> str:
    """Canonical version 3 text: keys in format order, two-space indentation, no trailing newline. Refuses an invalid
    value, so a written file is always readable by `decode`."""
    root = {
        "format": FORMAT,
        "version": VERSION,
        "session": annotations.session,
        "truthSha256": annotations.truth_sha256,
        "calibrations": _calibrations_json(annotations),
        "controls": _controls_json(annotations),
        "darts": [{"number": d.number, "status": d.status.value if isinstance(d.status, Status) else d.status,
                   "tip": None if d.tip is None else _point_json(d.tip)} for d in annotations.darts],
    }
    try:
        text = json.dumps(root, indent=2, ensure_ascii=False, allow_nan=False)
    except (TypeError, ValueError) as error:
        raise AnnotationError(f"$: cannot encode: {error}") from error
    decode(text)
    return text


def _canonical_sha256(value):
    text = json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False)
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def segments_sha256(annotations) -> str:
    """SHA-256 of the segment list encoded without `snap` (sorted keys, no spaces): identifies the calibrations a
    drift measure used, whatever the darts and controls (CV18, CV20, CV25). `snap` is left out so the hash of a
    segment is the same before and after the version 2 to 3 migration (flags measured on version 2 stay current)."""
    return _canonical_sha256([_segment_geometry_json(c) for c in annotations.calibrations])


def controls_sha256(annotations) -> str:
    """SHA-256 of the encoded control list, `snap` included (sorted keys, no spaces): identifies the control clicks a
    diagnosis used, whatever the segments and darts (CV25)."""
    return _canonical_sha256(_controls_json(annotations))


def control_for(annotations, dart_number, source):
    """The Control of the dart for that source (PHOTO or VIDEO), None when Kosmo has not clicked it (CV25)."""
    for control in annotations.controls:
        if control.dart == dart_number and control.source == source:
            return control
    return None


def segment_for(annotations, dart_number):
    """(index, Calibration) of the segment that applies to the dart: the last one with `from_dart <= dart_number`;
    None when there is none (CV20). The only implementation: every tool imports it."""
    found = None
    for index, segment in enumerate(annotations.calibrations):
        if segment.from_dart > dart_number:
            break
        found = (index, segment)
    return found


def write_atomic(path, annotations) -> None:
    """Write through a temporary file in the same folder and os.replace (CV16); the old file stays on any failure."""
    data = encode(annotations).encode("utf-8")
    target = Path(path)
    fd, temp = tempfile.mkstemp(dir=target.parent, prefix=f".{target.name}.", suffix=".tmp")
    try:
        with os.fdopen(fd, "wb") as handle:
            handle.write(data)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temp, target)
    except OSError as error:
        _discard(temp)
        raise AnnotationError(f"$: cannot write {target}: {error.strerror or error}") from error
    except BaseException:
        _discard(temp)
        raise


def _discard(temp):
    try:
        os.unlink(temp)
    except FileNotFoundError:
        pass


# ---- consistency with the truth file ------------------------------------------------------------------------------

def validate_against(annotations, session) -> list:
    """Check against the session's truth (truth.Session); raise AnnotationError on an error, return the warnings.

    Errors: other session name, dart number absent from the truth, dart not judged (CV5), segment photo that is not a
    photo of the session, segment after the first whose `fromDart` is not a judged dart or whose photo is not that
    dart's photo (CV20), tip outside the truth photo size, photo control whose size differs from the truth photo size (video frames
    differ in size, not checked), control on a dart that is absent or not judged, video
    control whose volley has no video or whose entry has no `videoPositionMs` (CV25). Warning: `truthSha256` differs
    from the truth file (the session may have been copied again; editing stays allowed).
    """
    if annotations.session != session.session:
        _fail("$.session", f"expected '{session.session}'")
    darts = {dart.number: dart for volley in session.volleys for dart in volley.darts}
    volleys = {dart.number: volley for volley in session.volleys for dart in volley.darts}
    for i, annotation in enumerate(annotations.darts):
        dart = darts.get(annotation.number)
        if dart is None:
            _fail(f"$.darts[{i}].number", f"dart {annotation.number} does not exist in the truth file")
        if not dart.judged:
            _fail(f"$.darts[{i}].number", f"dart {annotation.number} is not judged (voided or no photo, CV5)")
        if annotation.tip is not None:
            _point({"x": annotation.tip[0], "y": annotation.tip[1]}, f"$.darts[{i}].tip",
                   session.photo_width, session.photo_height)
    _segments_against(annotations.calibrations, darts)
    _controls_against(annotations.controls, darts, volleys, session)
    if annotations.truth_sha256 != session.sha256:
        return [f"$.truthSha256: differs from the truth file ({session.sha256}); the session was copied again"]
    return []


def _segments_against(segments, darts):
    photos = {dart.photo.file for dart in darts.values() if dart.photo is not None}
    for i, segment in enumerate(segments):
        path = f"$.calibrations[{i}]"
        if segment.photo not in photos:
            _fail(f"{path}.photo", f"'{segment.photo}' is not a photo of the session")
        if i == 0:
            continue  # the first segment may use any photo of the session, as version 1 did
        dart = darts.get(segment.from_dart)
        if dart is None:
            _fail(f"{path}.fromDart", f"dart {segment.from_dart} does not exist in the truth file")
        if not dart.judged:
            _fail(f"{path}.fromDart", f"dart {segment.from_dart} is not judged (voided or no photo, CV5)")
        if segment.photo != dart.photo.file:
            _fail(f"{path}.photo", f"expected '{dart.photo.file}', the photo of dart {segment.from_dart} (CV20)")


def _controls_against(controls, darts, volleys, session):
    for i, control in enumerate(controls):
        path = f"$.controls[{i}].dart"
        dart = darts.get(control.dart)
        if dart is None:
            _fail(path, f"dart {control.dart} does not exist in the truth file")
        if not dart.judged:
            _fail(path, f"dart {control.dart} is not judged (voided or no photo, CV5)")
        if control.source != VIDEO:
            if (control.width, control.height) != (session.photo_width, session.photo_height):
                _fail(f"$.controls[{i}].width",
                      f"a photo control is {session.photo_width} x {session.photo_height}, the photo size of the "
                      f"truth file (CV25)")
            continue
        if not volleys[control.dart].video:
            _fail(path, f"the volley of dart {control.dart} has no video (CV25)")
        if dart.entry.video_position_ms is None:
            _fail(path, f"the entry of dart {control.dart} has no videoPositionMs (CV25)")
