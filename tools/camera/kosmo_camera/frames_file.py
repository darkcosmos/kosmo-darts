"""The video frame manifest `frames.json` (CV27, CV15): which dart has which extracted frame, and why the others do not.

Written by `video_frames` in the container, read by the stdlib annotation tool on the same machine. It holds session
names, so it is a local working file, regenerable, never pasted or committed (CV15). Stdlib only.

The reader is strict like `drift_file`: a file over `MAX_BYTES` (never read past the cap), duplicate or unknown keys,
wrong types, any other format or version, duplicate sessions, unsorted dart numbers, a dart both framed and missing, a
file name that is not `video-NNN.jpg` of its dart and an unknown reason are rejected. Writes are atomic.
"""
import json
import re
from dataclasses import dataclass

from kosmo_camera import drift_file

MAX_BYTES = 4 * 1024 * 1024
MAX_DEPTH = 6
FORMAT = "kosmo-camera-frames"
VERSION = 1

NO_POSITION = "no_position"
BEFORE_START = "before_start"
TOO_CLOSE = "too_close"
PAST_END = "past_end"
VIDEO_MISSING = "video_missing"
VIDEO_UNREADABLE = "video_unreadable"
ORIENTATION_MISMATCH = "orientation_mismatch"
REASONS = (NO_POSITION, BEFORE_START, TOO_CLOSE, PAST_END, VIDEO_MISSING, VIDEO_UNREADABLE, ORIENTATION_MISMATCH)

_SESSION = re.compile(r"\d{8}-\d{6}(-[2-9]|-[1-9]\d+)?")  # same pattern as annotations._SESSION (capture folder name)
_FILE = re.compile(r"video-(\d{3,})\.jpg")
_SHA256 = re.compile(r"[0-9a-f]{64}")
_ROOT_KEYS = ("format", "version", "beforeEntryMs", "sessions")
_SESSION_KEYS = ("session", "frames", "missing")
_FRAME_KEYS = ("dart", "positionMs", "width", "height", "file", "sha256")
_MISSING_KEYS = ("dart", "reason")


class FramesFileError(ValueError):
    """The frame manifest is unreadable or breaks the format; starts with a JSON path."""


@dataclass(frozen=True)
class Frame:
    dart: int
    position_ms: int  # exact position of the frame in the volley video, floored to the millisecond
    width: int
    height: int
    file: str  # `video-NNN.jpg` inside the session folder, NNN the dart number
    sha256: str  # of the JPEG file


@dataclass(frozen=True)
class Missing:
    dart: int
    reason: str  # one of REASONS


@dataclass(frozen=True)
class SessionFrames:
    session: str
    frames: tuple  # Frame, ascending dart
    missing: tuple  # Missing, ascending dart


@dataclass(frozen=True)
class FramesFile:
    before_entry_ms: int
    sessions: tuple  # SessionFrames


def video_file_name(dart_number) -> str:
    return f"video-{dart_number:03d}.jpg"


def _fail(path, message):
    raise FramesFileError(f"{path}: {message}")


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


def _int(value, path, minimum):
    if isinstance(value, bool) or not isinstance(value, int) or value < minimum:
        _fail(path, f"expected an integer of at least {minimum}")
    return value


def _pattern(value, path, pattern, expected):
    if not isinstance(value, str) or not pattern.fullmatch(value):
        _fail(path, f"expected {expected}")
    return value


def _list(value, path):
    if not isinstance(value, list):
        _fail(path, "expected an array")
    return value


def _frame(value, path):
    _object(value, path, _FRAME_KEYS)
    dart = _int(value["dart"], f"{path}.dart", 1)
    name = _pattern(value["file"], f"{path}.file", _FILE, "a file name 'video-NNN.jpg'")
    if name != video_file_name(dart):
        _fail(f"{path}.file", f"expected '{video_file_name(dart)}', the frame of dart {dart}")
    return Frame(dart, _int(value["positionMs"], f"{path}.positionMs", 0), _int(value["width"], f"{path}.width", 1),
                 _int(value["height"], f"{path}.height", 1), name,
                 _pattern(value["sha256"], f"{path}.sha256", _SHA256, "64 lowercase hex characters"))


def _missing(value, path):
    _object(value, path, _MISSING_KEYS)
    reason = value["reason"]
    if reason not in REASONS:
        _fail(f"{path}.reason", "expected one of " + ", ".join(REASONS))
    return Missing(_int(value["dart"], f"{path}.dart", 1), reason)


def _ascending(items, path):
    previous = 0
    for i, item in enumerate(items):
        if item.dart <= previous:
            _fail(f"{path}[{i}].dart", "dart numbers must be strictly ascending")
        previous = item.dart


def _session(value, path):
    _object(value, path, _SESSION_KEYS)
    frames = tuple(_frame(item, f"{path}.frames[{i}]") for i, item in enumerate(_list(value["frames"], f"{path}.frames")))
    missing = tuple(_missing(item, f"{path}.missing[{i}]")
                    for i, item in enumerate(_list(value["missing"], f"{path}.missing")))
    _ascending(frames, f"{path}.frames")
    _ascending(missing, f"{path}.missing")
    both = {f.dart for f in frames} & {m.dart for m in missing}
    if both:
        _fail(path, f"dart {min(both)} is both framed and missing")
    return SessionFrames(_pattern(value["session"], f"{path}.session", _SESSION, "a capture session name"),
                         frames, missing)


def decode(text) -> FramesFile:
    """Validate the text of a frame manifest; raise FramesFileError on any deviation."""
    if text.startswith("﻿"):
        raise FramesFileError("$: byte order mark not allowed")
    try:
        root = json.loads(text, object_pairs_hook=drift_file._no_duplicates, parse_constant=drift_file._reject_constant)
    except (json.JSONDecodeError, RecursionError) as error:
        raise FramesFileError(f"$: invalid JSON: {error}") from error
    except drift_file.DriftFileError as error:
        raise FramesFileError(str(error)) from error
    if drift_file._depth(root) > MAX_DEPTH:
        raise FramesFileError(f"$: nesting deeper than {MAX_DEPTH} levels")
    _object(root, "$", _ROOT_KEYS)
    if root["format"] != FORMAT:
        _fail("$.format", f"expected '{FORMAT}'")
    version = root["version"]
    if isinstance(version, bool) or version != VERSION or not isinstance(version, int):
        _fail("$.version", f"unsupported version {version!r}")
    before = _int(root["beforeEntryMs"], "$.beforeEntryMs", 0)
    sessions = tuple(_session(item, f"$.sessions[{i}]") for i, item in enumerate(_list(root["sessions"], "$.sessions")))
    names = [s.session for s in sessions]
    if len(set(names)) != len(names):
        _fail("$.sessions", "duplicate session")
    return FramesFile(before, sessions)


def read(path) -> FramesFile:
    """Read and validate a frame manifest of at most MAX_BYTES; never reads past the cap."""
    try:
        with open(path, "rb") as handle:
            data = handle.read(MAX_BYTES + 1)
    except OSError as error:
        raise FramesFileError(f"$: cannot read {path}: {error.strerror or error}") from error
    if len(data) > MAX_BYTES:
        raise FramesFileError(f"$: file is larger than {MAX_BYTES} bytes")
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError as error:
        raise FramesFileError(f"$: invalid UTF-8: {error.reason}") from error
    return decode(text)


def encode(frames_file) -> str:
    """The canonical text of a frame manifest (sorted keys); validated by decoding it again."""
    root = {"format": FORMAT, "version": VERSION, "beforeEntryMs": frames_file.before_entry_ms,
            "sessions": [{"session": s.session,
                          "frames": [{"dart": f.dart, "positionMs": f.position_ms, "width": f.width,
                                      "height": f.height, "file": f.file, "sha256": f.sha256} for f in s.frames],
                          "missing": [{"dart": m.dart, "reason": m.reason} for m in s.missing]}
                         for s in frames_file.sessions]}
    text = json.dumps(root, sort_keys=True, indent=2, allow_nan=False) + "\n"
    decode(text)
    return text


def write_atomic(path, frames_file) -> None:
    """Encode and write atomically through a temporary file in the same folder; the old file stays on any failure."""
    data = encode(frames_file).encode("utf-8")
    if len(data) > MAX_BYTES:
        raise FramesFileError(f"$: file would be larger than {MAX_BYTES} bytes")
    try:
        drift_file.replace_atomic(path, data)
    except drift_file.DriftFileError as error:
        raise FramesFileError(str(error)) from error
