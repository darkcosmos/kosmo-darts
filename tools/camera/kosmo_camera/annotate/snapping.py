"""Tool side of the assisted click (CV31, CV16, T15n): stdlib only at import time.

`Snapper` imports `kosmo_camera.snap` (numpy) and `kosmo_camera.luma` (numpy and Pillow) lazily, the first time it is
asked. Only a `ModuleNotFoundError` for numpy or PIL means "Recalage indisponible"; any other import error (a bug in
`snap.py`, a broken install) propagates, so it is never hidden as an absent venv (A32). Images are decoded by
`luma.decode_luma` only (S8, S14): an image that cannot be read leaves the point at the click, `unsnapped` with reason
`unreadable`, and never raises. The decoded luma of at most `SNAP_CACHE_IMAGES` images is kept (LRU, keyed by resolved
path, size and modification time). Paths come from the session listing only, never from a request.
"""
import importlib
import os
import threading
from collections import OrderedDict
from typing import NamedTuple, Optional

from kosmo_camera import config

SNAPPED = "snapped"  # same values as annotations.SNAPPED and snap.SNAPPED
UNSNAPPED = "unsnapped"
UNREADABLE = "unreadable"  # tool-only reason (CV31): the image could not be decoded
_OPTIONAL_PACKAGES = ("numpy", "PIL")


class Outcome(NamedTuple):
    point: tuple  # the crossing when snapped, else the start (the click or the proposal)
    status: str  # SNAPPED or UNSNAPPED
    reason: Optional[str]  # None when snapped, else a `snap.REASONS` value or UNREADABLE


def _is_optional_dependency(error) -> bool:
    return (error.name or "").split(".")[0] in _OPTIONAL_PACKAGES


class Snapper:
    def __init__(self, snap_module="kosmo_camera.snap", luma_module="kosmo_camera.luma",
                 cache_images=config.SNAP_CACHE_IMAGES):
        self._names = (snap_module, luma_module)
        self._modules = None  # (snap, luma), or False once numpy or Pillow is known to be missing
        self._cache = OrderedDict()  # (resolved path, size, mtime) -> luma array
        self._cache_images = cache_images
        self._lock = threading.RLock()

    def _load(self):
        with self._lock:
            if self._modules is None:
                try:
                    self._modules = tuple(importlib.import_module(name) for name in self._names)
                except ModuleNotFoundError as error:
                    if not _is_optional_dependency(error):
                        raise
                    self._modules = False
            return self._modules or None

    @property
    def available(self) -> bool:
        return self._load() is not None

    def cached_images(self) -> int:
        with self._lock:
            return len(self._cache)

    # ---- decoding with the cache ---------------------------------------------------------------------------------

    def _luma(self, path, expected_size):
        """The luma array of an image, or None when it cannot be read; never an exception (S14)."""
        _, luma = self._load()
        try:
            resolved = os.path.realpath(path)
            info = os.stat(resolved)
        except OSError:
            return None
        key = (resolved, info.st_size, info.st_mtime_ns, None if expected_size is None else tuple(expected_size))
        with self._lock:
            if key in self._cache:
                self._cache.move_to_end(key)
                return self._cache[key]
            array, _ = luma.decode_luma(resolved, expected_size)
            if array is None:
                return None
            for stale in [k for k in self._cache if k[0] == resolved]:
                del self._cache[stale]  # the file changed: its old image is dropped
            self._cache[key] = array
            while len(self._cache) > self._cache_images:
                self._cache.popitem(last=False)
            return array

    # ---- the two operations --------------------------------------------------------------------------------------

    def snap(self, image_path, start, expected_size=None) -> Outcome:
        """The crossing near `start` on the image, or `start` unsnapped with its reason."""
        modules = self._load()
        if modules is None:
            raise RuntimeError("snapping is unavailable")
        start = (float(start[0]), float(start[1]))
        array = self._luma(image_path, expected_size)
        if array is None:
            return Outcome(start, UNSNAPPED, UNREADABLE)
        result = modules[0].snap_point(array, start)
        return Outcome(tuple(result.point), result.status, result.reason)

    def propose(self, image_path, points, expected_size=None) -> list:
        """Each point snapped from its old position on the image; a `None` point stays `None`."""
        return [None if point is None else self.snap(image_path, point, expected_size) for point in points]
