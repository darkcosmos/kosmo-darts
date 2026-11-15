"""Annotation file of one capture session: calibration points (CV2) and dart tips of judged darts (CV5).

Format: docs/camera/annotation-format.md. Stdlib only. Coordinates are photo pixels in the stored JPEG pixel order
(origin top left, floats, EXIF orientation ignored, decision D8). The reader is strict: it rejects a file over 1 MiB
(never read past the cap), duplicate or unknown keys, missing fields, any other format or version, a `tip` that is
not present exactly when the status is VISIBLE, and duplicate or unordered dart numbers. Writes are atomic (CV16).
"""
import enum
import json
import math
import os
import re
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

MAX_BYTES = 1024 * 1024
MAX_DEPTH = 8
FORMAT = "kosmo-camera-annotations"
VERSION = 1
CALIBRATION_POINTS = 4  # CV2: cal1 to cal4
_PHOTO = re.compile(r"dart-(\d{3}|[1-9]\d{3,})\.jpg")
_SESSION = re.compile(r"\d{8}-\d{6}(-[2-9]|-[1-9]\d+)?")
_SHA256 = re.compile(r"[0-9a-f]{64}")


class AnnotationError(ValueError):
    """The annotation file is unreadable, breaks the format or contradicts its truth file; starts with a JSON path."""


class Status(enum.Enum):
    VISIBLE = "VISIBLE"
    HIDDEN = "HIDDEN"
    OFF_BOARD = "OFF_BOARD"


@dataclass(frozen=True)
class Calibration:
    photo: str
    width: int
    height: int
    points: tuple  # 4 (x, y) tuples in CV2 order


@dataclass(frozen=True)
class DartAnnotation:
    number: int
    status: Status
    tip: Optional[tuple]  # (x, y) exactly when status is VISIBLE


@dataclass(frozen=True)
class Annotations:
    session: str
    truth_sha256: str
    calibration: Optional[Calibration]
    darts: tuple  # DartAnnotation, strictly ascending numbers


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


def _calibration(value, path):
    _object(value, path, ("photo", "width", "height", "points"))
    photo = _pattern(value["photo"], f"{path}.photo", _PHOTO, "a dart photo name 'dart-NNN.jpg'")
    width = _int(value["width"], f"{path}.width", 1)
    height = _int(value["height"], f"{path}.height", 1)
    points = _array(value["points"], f"{path}.points")
    if len(points) != CALIBRATION_POINTS:
        _fail(f"{path}.points", f"expected exactly {CALIBRATION_POINTS} points (CV2)")
    return Calibration(photo, width, height,
                       tuple(_point(p, f"{path}.points[{i}]", width, height) for i, p in enumerate(points)))


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


def _annotations(root):
    _object(root, "$", ("format", "version", "session", "truthSha256", "calibration", "darts"))
    calibration = root["calibration"]
    return Annotations(
        _pattern(root["session"], "$.session", _SESSION, "a capture session name 'YYYYMMDD-HHMMSS[-N]'"),
        _pattern(root["truthSha256"], "$.truthSha256", _SHA256, "64 lowercase hexadecimal digits"),
        None if calibration is None else _calibration(calibration, "$.calibration"),
        _darts(root["darts"], "$.darts"))


# ---- parsing ------------------------------------------------------------------------------------------------------

def _no_duplicates(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise AnnotationError(f"$: duplicate key '{key}'")
        result[key] = value
    return result


def _reject_constant(name):
    raise AnnotationError(f"$: invalid number {name}")


def _depth(value):
    """Nesting depth, computed iteratively so a hostile file cannot exhaust the stack."""
    deepest = 0
    stack = [(value, 1)]
    while stack:
        item, level = stack.pop()
        if isinstance(item, (dict, list)):
            deepest = max(deepest, level)
            children = item.values() if isinstance(item, dict) else item
            stack.extend((child, level + 1) for child in children)
    return deepest


def _parse(text):
    if text.startswith("\ufeff"):
        raise AnnotationError("$: byte order mark not allowed")
    try:
        return json.loads(text, object_pairs_hook=_no_duplicates, parse_constant=_reject_constant)
    except (json.JSONDecodeError, RecursionError) as error:
        raise AnnotationError(f"$: invalid JSON: {error}") from error


def decode(text) -> Annotations:
    """Validate the text of an annotation file; raise AnnotationError on any deviation."""
    root = _parse(text)
    if _depth(root) > MAX_DEPTH:
        raise AnnotationError(f"$: nesting deeper than {MAX_DEPTH} levels")
    if not isinstance(root, dict):
        raise AnnotationError("$: expected an object")
    if root.get("format") != FORMAT:
        raise AnnotationError(f"$.format: expected '{FORMAT}'")
    version = root.get("version")
    if isinstance(version, bool) or not isinstance(version, int) or version != VERSION:
        raise AnnotationError(f"$.version: unsupported version {version!r}")
    return _annotations(root)


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


def encode(annotations) -> str:
    """Canonical text: keys in format order, two-space indentation, no trailing newline. Refuses an invalid value."""
    calibration = annotations.calibration
    root = {
        "format": FORMAT,
        "version": VERSION,
        "session": annotations.session,
        "truthSha256": annotations.truth_sha256,
        "calibration": None if calibration is None else {
            "photo": calibration.photo, "width": calibration.width, "height": calibration.height,
            "points": [_point_json(p) for p in calibration.points]},
        "darts": [{"number": d.number, "status": d.status.value if isinstance(d.status, Status) else d.status,
                   "tip": None if d.tip is None else _point_json(d.tip)} for d in annotations.darts],
    }
    try:
        text = json.dumps(root, indent=2, ensure_ascii=False, allow_nan=False)
    except (TypeError, ValueError) as error:
        raise AnnotationError(f"$: cannot encode: {error}") from error
    decode(text)
    return text


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

    Errors: other session name, dart number absent from the truth, dart not judged (CV5), calibration photo that is
    not a photo of the session, tip outside the truth photo size. Warning: `truthSha256` differs from the truth file
    (the session may have been copied again; editing stays allowed).
    """
    if annotations.session != session.session:
        _fail("$.session", f"expected '{session.session}'")
    darts = {dart.number: dart for volley in session.volleys for dart in volley.darts}
    for i, annotation in enumerate(annotations.darts):
        dart = darts.get(annotation.number)
        if dart is None:
            _fail(f"$.darts[{i}].number", f"dart {annotation.number} does not exist in the truth file")
        if not dart.judged:
            _fail(f"$.darts[{i}].number", f"dart {annotation.number} is not judged (voided or no photo, CV5)")
        if annotation.tip is not None:
            _point({"x": annotation.tip[0], "y": annotation.tip[1]}, f"$.darts[{i}].tip",
                   session.photo_width, session.photo_height)
    if annotations.calibration is not None:
        photos = {dart.photo.file for dart in darts.values() if dart.photo is not None}
        if annotations.calibration.photo not in photos:
            _fail("$.calibration.photo", f"'{annotations.calibration.photo}' is not a photo of the session")
    if annotations.truth_sha256 != session.sha256:
        return [f"$.truthSha256: differs from the truth file ({session.sha256}); the session was copied again"]
    return []
