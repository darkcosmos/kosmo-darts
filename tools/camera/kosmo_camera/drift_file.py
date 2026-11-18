"""The drift flag file `drift.json` (CV15, CV18): which darts of which sessions are beyond the drift threshold.

Written by `drift` in the container, read by the stdlib annotation tool on the same machine. It holds session names, so
it is a local working file in `~/kosmo-runs/drift/`, regenerable, never pasted or committed (CV15). Stdlib only.

The reader is strict like the annotation reader: a file over `MAX_BYTES` (never read past the cap), duplicate or unknown
keys, wrong types, any other format or version, duplicate sessions or labels, and dart numbers that are not strictly
ascending are rejected. Writes are atomic.
"""
import json
import math
import os
import re
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from kosmo_camera import strict_json
from kosmo_camera.annotations import SESSION_NAME

MAX_BYTES = 1024 * 1024
MAX_DEPTH = 6
FORMAT = "kosmo-camera-drift"
VERSION = 1

_LABEL = re.compile(r"S\d{2,}")
_SHA256 = re.compile(r"[0-9a-f]{64}")
_ROOT_KEYS = ("format", "version", "thresholdMm", "sessions")
_SESSION_KEYS = ("session", "label", "segmentsSha256", "maxDriftMm", "beyond", "suggested", "unmeasurable")


class DriftFileError(ValueError):
    """The drift flag file is unreadable or breaks the format; starts with a JSON path."""


@dataclass(frozen=True)
class SessionFlags:
    session: str
    label: str  # S01, S02... in session-name order (the only name the report shows)
    segments_sha256: str  # `annotations.segments_sha256` when measured: tells the tool the flags are stale
    max_drift_mm: Optional[float]  # None when no photo of the session could be measured
    beyond: tuple  # ascending dart numbers whose drift is over the threshold
    suggested: tuple  # ascending dart numbers where a new segment is suggested (first dart of each run beyond)
    unmeasurable: tuple  # ascending dart numbers without a drift measure


@dataclass(frozen=True)
class DriftFile:
    threshold_mm: float
    sessions: tuple  # SessionFlags


def _fail(path, message):
    raise DriftFileError(f"{path}: {message}")


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


def _number(value, path):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or value < 0:
        _fail(path, "expected a finite number of at least 0")
    return float(value)


def _pattern(value, path, pattern, expected):
    if not isinstance(value, str) or not pattern.fullmatch(value):
        _fail(path, f"expected {expected}")
    return value


def _numbers(value, path):
    if not isinstance(value, list):
        _fail(path, "expected an array")
    previous = 0
    for i, number in enumerate(value):
        if isinstance(number, bool) or not isinstance(number, int) or number < 1:
            _fail(f"{path}[{i}]", "expected a dart number of at least 1")
        if number <= previous:
            _fail(f"{path}[{i}]", "dart numbers must be strictly ascending")
        previous = number
    return tuple(value)


def _session(value, path):
    _object(value, path, _SESSION_KEYS)
    maximum = value["maxDriftMm"]
    return SessionFlags(
        _pattern(value["session"], f"{path}.session", SESSION_NAME, "a capture session name"),
        _pattern(value["label"], f"{path}.label", _LABEL, "a label such as S01"),
        _pattern(value["segmentsSha256"], f"{path}.segmentsSha256", _SHA256, "64 lowercase hex characters"),
        None if maximum is None else _number(maximum, f"{path}.maxDriftMm"),
        _numbers(value["beyond"], f"{path}.beyond"),
        _numbers(value["suggested"], f"{path}.suggested"),
        _numbers(value["unmeasurable"], f"{path}.unmeasurable"))


def decode(text) -> DriftFile:
    """Validate the text of a drift file; raise DriftFileError on any deviation."""
    if text.startswith("﻿"):
        raise DriftFileError("$: byte order mark not allowed")
    try:
        root = json.loads(text, object_pairs_hook=strict_json.no_duplicates, parse_constant=strict_json.reject_constant)
    except (json.JSONDecodeError, RecursionError) as error:
        raise DriftFileError(f"$: invalid JSON: {error}") from error
    except strict_json.StrictJsonError as error:
        raise DriftFileError(str(error)) from error
    if strict_json.depth(root) > MAX_DEPTH:
        raise DriftFileError(f"$: nesting deeper than {MAX_DEPTH} levels")
    _object(root, "$", _ROOT_KEYS)
    if root["format"] != FORMAT:
        _fail("$.format", f"expected '{FORMAT}'")
    version = root["version"]
    if isinstance(version, bool) or version != VERSION or not isinstance(version, int):
        _fail("$.version", f"unsupported version {version!r}")
    threshold = _number(root["thresholdMm"], "$.thresholdMm")
    if not isinstance(root["sessions"], list):
        _fail("$.sessions", "expected an array")
    sessions = tuple(_session(item, f"$.sessions[{i}]") for i, item in enumerate(root["sessions"]))
    for field in ("session", "label"):
        names = [getattr(flags, field) for flags in sessions]
        if len(set(names)) != len(names):
            _fail("$.sessions", f"duplicate {field}")
    return DriftFile(threshold, sessions)


def read(path) -> DriftFile:
    """Read and validate a drift file of at most MAX_BYTES; never reads past the cap."""
    try:
        with Path(path).open("rb") as handle:
            data = handle.read(MAX_BYTES + 1)
    except OSError as error:
        raise DriftFileError(f"$: cannot read {path}: {error.strerror or error}") from error
    if len(data) > MAX_BYTES:
        raise DriftFileError(f"$: file is larger than {MAX_BYTES} bytes")
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError as error:
        raise DriftFileError(f"$: invalid UTF-8: {error.reason}") from error
    return decode(text)


def encode(drift_file) -> str:
    """The canonical text of a drift file (sorted keys); validated by decoding it again."""
    root = {"format": FORMAT, "version": VERSION, "thresholdMm": drift_file.threshold_mm,
            "sessions": [{"session": s.session, "label": s.label, "segmentsSha256": s.segments_sha256,
                          "maxDriftMm": s.max_drift_mm, "beyond": list(s.beyond), "suggested": list(s.suggested),
                          "unmeasurable": list(s.unmeasurable)} for s in drift_file.sessions]}
    try:
        text = json.dumps(root, sort_keys=True, indent=2, allow_nan=False) + "\n"
    except ValueError as error:
        raise DriftFileError(f"$: cannot encode: {error}") from error
    decode(text)
    return text


def replace_atomic(path, data) -> None:
    """Write bytes through a temporary file in the same folder and os.replace; the old file stays on any failure."""
    target = Path(path)
    try:
        fd, temp = tempfile.mkstemp(dir=target.parent, prefix=f".{target.name}.", suffix=".tmp")
    except OSError as error:
        raise DriftFileError(f"$: cannot write {target}: {error.strerror or error}") from error
    try:
        with os.fdopen(fd, "wb") as handle:
            handle.write(data)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temp, target)
    except OSError as error:
        _discard(temp)
        raise DriftFileError(f"$: cannot write {target}: {error.strerror or error}") from error
    except BaseException:
        _discard(temp)
        raise


def write_atomic(path, drift_file) -> None:
    """Encode and write atomically (the same procedure as `annotations.write_atomic`)."""
    data = encode(drift_file).encode("utf-8")
    if len(data) > MAX_BYTES:
        raise DriftFileError(f"$: file would be larger than {MAX_BYTES} bytes")
    replace_atomic(path, data)


def _discard(temp):
    try:
        os.unlink(temp)
    except FileNotFoundError:
        pass
