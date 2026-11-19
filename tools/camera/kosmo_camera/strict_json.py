"""Strict JSON helpers shared by the file readers (CV25, CV16, CV26): stdlib only.

Used as `json.loads(text, object_pairs_hook=no_duplicates, parse_constant=reject_constant)`, then `depth(root)` against
the file's own limit. The hooks raise `StrictJsonError`; each reader turns it into its own error type.
`replace_atomic` is the atomic byte write shared by the camera file writers (`frames_file`, `video_frames`).
"""
import os
import tempfile
from pathlib import Path


class AtomicWriteError(ValueError):
    """An atomic write failed; the old file, if any, is unchanged and no temporary file is left."""


class StrictJsonError(ValueError):
    """A duplicate key or a non-finite constant (NaN, Infinity) in a JSON text."""


def no_duplicates(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise StrictJsonError(f"$: duplicate key '{key}'")
        result[key] = value
    return result


def reject_constant(name):
    raise StrictJsonError(f"$: invalid number {name}")


def depth(value):
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


def replace_atomic(path, data) -> None:
    """Write bytes through a temporary file in the same folder and os.replace; the old file stays on any failure."""
    target = Path(path)
    try:
        fd, temp = tempfile.mkstemp(dir=target.parent, prefix=f".{target.name}.", suffix=".tmp")
    except OSError as error:
        raise AtomicWriteError(f"$: cannot write {target}: {error.strerror or error}") from error
    try:
        with os.fdopen(fd, "wb") as handle:
            handle.write(data)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temp, target)
    except OSError as error:
        _discard(temp)
        raise AtomicWriteError(f"$: cannot write {target}: {error.strerror or error}") from error
    except BaseException:
        _discard(temp)
        raise


def _discard(temp):
    try:
        os.unlink(temp)
    except FileNotFoundError:
        pass
