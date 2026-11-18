"""Control sample and session roles (CV26, CV15): which photos and video frames Kosmo control-clicks.

Stdlib only. The sample is computed from the truth file and the segments, never chosen by hand, so the same inputs give
the same list. Roles name a session by its place in the locked split (`dev 1`, `eval 2`...), never by its name.
"""
from dataclasses import dataclass
from pathlib import Path

from kosmo_camera import config, split as split_module

DEV = "dev"
EVAL = "eval"


@dataclass(frozen=True)
class Sample:
    photo_darts: tuple  # ascending dart numbers of the photo controls
    video_darts: tuple  # ascending dart numbers of the video controls


@dataclass(frozen=True)
class Role:
    """The place of a session in the locked split: its set and its number in that set (1-based)."""
    set_name: str
    number: int

    @property
    def label(self) -> str:
        return f"{self.set_name} {self.number}"


def roles(split) -> dict:
    """Session name to `Role` (`dev 1`, `dev 2`, `eval 1`...) in ascending CV3 unit key within each set (CV24)."""
    if split.mode != "session":
        raise split_module.SplitError("roles need a session-mode split (CV24)")
    result = {}
    for set_name in split_module.SETS:
        names = sorted((u.session for u in split.units if u.set == set_name), key=split_module.unit_key)
        for number, name in enumerate(names, start=1):
            result[name] = Role(set_name, number)
    return result


def sample(session, annotations, set_name) -> Sample:
    """The CV26 sample of one truth session (`truth.Session`) of the set `set_name`; `annotations` may be None."""
    judged = [dart for volley in session.volleys for dart in volley.darts if dart.judged]
    numbers = sorted(dart.number for dart in judged)
    if set_name == EVAL:
        first, last = config.CONTROL_EVAL_VIDEO_FIRST, config.CONTROL_EVAL_VIDEO_LAST
        return Sample(tuple(numbers), tuple(n for n in numbers if first <= n <= last))
    if set_name != DEV:
        raise ValueError(f"unknown set '{set_name}'")
    picked = set(numbers[::config.CONTROL_DEV_STRIDE]) | set(numbers[-1:])
    if annotations is not None:
        by_photo = {dart.photo.file: dart.number for dart in judged}
        picked |= {by_photo[c.photo] for c in annotations.calibrations if c.photo in by_photo}
    photos = tuple(sorted(picked))
    return Sample(photos, photos)


def read_split(split_path, lock_path):
    """The split read from its file and checked against the lock (K0); `SplitError` with the CV3 message on a mismatch."""
    path = Path(split_path)
    try:
        if path.stat().st_size > split_module.MAX_BYTES:
            raise split_module.SplitError("split is too large")
        data = path.read_bytes()
        lock = split_module.read_lock(lock_path)
    except OSError as error:
        raise split_module.SplitError(f"cannot read the split or its lock: {error.strerror or error}") from error
    return split_module.check_lock(data, lock)
