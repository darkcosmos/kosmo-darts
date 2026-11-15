"""Strict reader of Kosmo Capture ``truth.json`` version 2 (CV5).

Format: docs/camera/capture-format.md. Stdlib only. Rejects a file over 10 MiB (never read past the cap), nesting
deeper than 32 levels, duplicate keys, unknown or missing fields, any other format or version, and a segment or
multiplier that breaks rule D2 of docs/rules/x01.md.
"""
import hashlib
import json
import math
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

MAX_BYTES = 10 * 1024 * 1024
MAX_DEPTH = 32
FORMAT = "kosmo-capture-truth"
VERSION = 2
MULTIPLIERS = ("SINGLE", "DOUBLE", "TRIPLE")
BULL_SEGMENTS = (25, 50)
MISS_SEGMENT = 0
MAX_NUMBER_SEGMENT = 20
_UTC = re.compile(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z")


class TruthError(ValueError):
    """The truth file is unreadable or does not match the format; the message starts with a JSON path."""


@dataclass(frozen=True)
class Stamp:
    utc: str
    monotonic_nanos: int
    video_position_ms: Optional[int] = None  # always None for a start stamp


@dataclass(frozen=True)
class Photo(Stamp):
    file: str = ""


@dataclass(frozen=True)
class Zoom:
    requested_ratio: Optional[float]
    ratio: Optional[float]


@dataclass(frozen=True)
class Dart:
    number: int
    segment: int
    multiplier: str
    voided: bool
    entry: Stamp
    voided_at: Optional[Stamp]
    photo: Optional[Photo]
    gravity: Optional[tuple]

    @property
    def judged(self) -> bool:
        """CV5: a dart is judged when it is not voided and its photo exists."""
        return not self.voided and self.photo is not None


@dataclass(frozen=True)
class Volley:
    index: int
    video: str
    start: Stamp
    end: Optional[Stamp]
    empty: bool
    darts: tuple


@dataclass(frozen=True)
class Session:
    version: int
    session: str
    app_id: str
    app_version: str
    device_manufacturer: str
    device_model: str
    device_sdk: int
    started_at: Stamp
    ended_at: Optional[Stamp]
    end_reason: Optional[str]
    video_requested_quality: str
    video_width: int
    video_height: int
    photo_width: int
    photo_height: int
    photo_timestamp_source: str
    zoom: Zoom
    volleys: tuple
    sha256: str


# ---- field checks -------------------------------------------------------------------------------------------------

def _fail(path, message):
    raise TruthError(f"{path}: {message}")


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


def _string(value, path):
    if not isinstance(value, str):
        _fail(path, "expected a string")
    return value


def _choice(value, path, allowed):
    if value not in allowed or not isinstance(value, str):
        _fail(path, f"expected one of {', '.join(allowed)}")
    return value


def _int(value, path, minimum=None):
    if isinstance(value, bool) or not isinstance(value, int):
        _fail(path, "expected an integer")
    if minimum is not None and value < minimum:
        _fail(path, f"expected at least {minimum}")
    return value


def _bool(value, path):
    if not isinstance(value, bool):
        _fail(path, "expected a boolean")
    return value


def _float(value, path):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        _fail(path, "expected a finite number")
    return float(value)


def _nullable(value, path, reader):
    return None if value is None else reader(value, path)


def _utc(value, path):
    if not _UTC.fullmatch(_string(value, path)):
        _fail(path, "expected an ISO 8601 UTC time with three fraction digits and Z")
    return value


def _stamp(value, path, with_video=False):
    _object(value, path, ("utc", "monotonicNanos") + (("videoPositionMs",) if with_video else ()))
    video = _nullable(value["videoPositionMs"], f"{path}.videoPositionMs",
                      lambda v, p: _int(v, p, 0)) if with_video else None
    return Stamp(_utc(value["utc"], f"{path}.utc"), _int(value["monotonicNanos"], f"{path}.monotonicNanos", 0), video)


def _photo(value, path):
    _object(value, path, ("file", "utc", "monotonicNanos", "videoPositionMs"))
    stamp = _stamp({k: value[k] for k in ("utc", "monotonicNanos", "videoPositionMs")}, path, with_video=True)
    return Photo(stamp.utc, stamp.monotonic_nanos, stamp.video_position_ms, _string(value["file"], f"{path}.file"))


def _gravity(value, path):
    _object(value, path, ("x", "y", "z"))
    return tuple(_float(value[axis], f"{path}.{axis}") for axis in ("x", "y", "z"))


def _ratio(value, path):
    number = _float(value, path)
    if number <= 0:
        _fail(path, "expected a ratio greater than 0")
    return number


def _zoom(value, path):
    _object(value, path, ("requestedRatio", "ratio"))
    return Zoom(_nullable(value["requestedRatio"], f"{path}.requestedRatio", _ratio),
                _nullable(value["ratio"], f"{path}.ratio", _ratio))


def _segment_and_multiplier(value, path):
    segment = _int(value["segment"], f"{path}.segment")
    if segment != MISS_SEGMENT and segment not in BULL_SEGMENTS and not 1 <= segment <= MAX_NUMBER_SEGMENT:
        _fail(f"{path}.segment", "expected 0, 1 to 20, 25 or 50")
    multiplier = _choice(value["multiplier"], f"{path}.multiplier", MULTIPLIERS)
    if (segment == MISS_SEGMENT or segment in BULL_SEGMENTS) and multiplier != "SINGLE":
        _fail(f"{path}.multiplier", "rule D2: miss and bulls take SINGLE only")
    return segment, multiplier


def _dart(value, path):
    _object(value, path, ("number", "segment", "multiplier", "voided", "entry", "voidedAt", "photo", "gravity"))
    segment, multiplier = _segment_and_multiplier(value, path)
    voided = _bool(value["voided"], f"{path}.voided")
    voided_at = _nullable(value["voidedAt"], f"{path}.voidedAt", lambda v, p: _stamp(v, p, True))
    if voided != (voided_at is not None):
        _fail(f"{path}.voided", "must be true exactly when voidedAt is not null")
    return Dart(_int(value["number"], f"{path}.number", 1), segment, multiplier, voided,
                _stamp(value["entry"], f"{path}.entry", True), voided_at,
                _nullable(value["photo"], f"{path}.photo", _photo),
                _nullable(value["gravity"], f"{path}.gravity", _gravity))


def _array(value, path):
    if not isinstance(value, list):
        _fail(path, "expected an array")
    return value


def _volley(value, path):
    _object(value, path, ("index", "video", "start", "end", "empty", "darts"))
    darts = tuple(_dart(item, f"{path}.darts[{i}]") for i, item in enumerate(_array(value["darts"], f"{path}.darts")))
    return Volley(_int(value["index"], f"{path}.index", 1), _string(value["video"], f"{path}.video"),
                  _stamp(value["start"], f"{path}.start"),
                  _nullable(value["end"], f"{path}.end", lambda v, p: _stamp(v, p, True)),
                  _bool(value["empty"], f"{path}.empty"), darts)


def _check_end(root, ended_at, end_reason, volleys):
    if (ended_at is None) != (end_reason is None):
        _fail("$.endReason", "must be null exactly when endedAt is null")
    if end_reason is not None:
        _choice(end_reason, "$.endReason", ("STOP", "INTERRUPTED"))
    if not volleys:
        _fail("$.volleys", "expected at least one volley")


def _session(root, digest):
    _object(root, "$", ("format", "version", "session", "app", "device", "clock", "startedAt", "endedAt",
                        "endReason", "video", "photo", "zoom", "volleys"))
    app = _object(root["app"], "$.app", ("id", "version"))
    device = _object(root["device"], "$.device", ("manufacturer", "model", "sdk"))
    video = _object(root["video"], "$.video", ("requestedQuality", "width", "height"))
    photo = _object(root["photo"], "$.photo", ("width", "height", "timestampSource"))
    ended_at = _nullable(root["endedAt"], "$.endedAt", _stamp)
    end_reason = root["endReason"]
    volleys = tuple(_volley(item, f"$.volleys[{i}]") for i, item in enumerate(_array(root["volleys"], "$.volleys")))
    _check_end(root, ended_at, end_reason, volleys)
    _choice(root["clock"], "$.clock", ("elapsedRealtimeNanos",))
    return Session(
        VERSION, _string(root["session"], "$.session"),
        _string(app["id"], "$.app.id"), _string(app["version"], "$.app.version"),
        _string(device["manufacturer"], "$.device.manufacturer"), _string(device["model"], "$.device.model"),
        _int(device["sdk"], "$.device.sdk", 0), _stamp(root["startedAt"], "$.startedAt"), ended_at, end_reason,
        _choice(video["requestedQuality"], "$.video.requestedQuality", ("UHD", "FHD")),
        _int(video["width"], "$.video.width", 1), _int(video["height"], "$.video.height", 1),
        _int(photo["width"], "$.photo.width", 1), _int(photo["height"], "$.photo.height", 1),
        _choice(photo["timestampSource"], "$.photo.timestampSource", ("REALTIME", "UNKNOWN")),
        _zoom(root["zoom"], "$.zoom"), volleys, digest)


# ---- parsing ------------------------------------------------------------------------------------------------------

def _no_duplicates(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise TruthError(f"$: duplicate key '{key}'")
        result[key] = value
    return result


def _reject_constant(name):
    raise TruthError(f"$: invalid number {name}")


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


def _read_bytes(path):
    try:
        with Path(path).open("rb") as handle:
            data = handle.read(MAX_BYTES + 1)
    except OSError as error:
        raise TruthError(f"$: cannot read {path}: {error.strerror or error}") from error
    if len(data) > MAX_BYTES:
        raise TruthError(f"$: file is larger than {MAX_BYTES} bytes")
    return data


def _parse(data):
    try:
        text = data.decode("utf-8")
        if text.startswith("\ufeff"):
            raise TruthError("$: byte order mark not allowed")
        return json.loads(text, object_pairs_hook=_no_duplicates, parse_constant=_reject_constant)
    except UnicodeDecodeError as error:
        raise TruthError(f"$: invalid UTF-8: {error.reason}") from error
    except (json.JSONDecodeError, RecursionError) as error:
        raise TruthError(f"$: invalid JSON: {error}") from error


def read_truth(path) -> Session:
    """Read and validate a version 2 truth file; raise TruthError on any deviation."""
    data = _read_bytes(path)
    root = _parse(data)
    if _depth(root) > MAX_DEPTH:
        raise TruthError(f"$: nesting deeper than {MAX_DEPTH} levels")
    if not isinstance(root, dict):
        raise TruthError("$: expected an object")
    if root.get("format") != FORMAT:
        raise TruthError(f"$.format: expected '{FORMAT}'")
    version = root.get("version")
    if isinstance(version, bool) or not isinstance(version, int) or version != VERSION:
        shown = version if isinstance(version, int) and not isinstance(version, bool) else repr(version)
        raise TruthError(f"$.version: unsupported version {shown}")
    return _session(root, hashlib.sha256(data).hexdigest())
