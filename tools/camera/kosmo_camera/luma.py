"""The one JPEG-to-luma decoder of the camera tools (A33, S14, D45): drift (CV18), snapping (CV31), diagnosis.

numpy and Pillow. `decode_luma` never raises: a missing, non-regular, oversized, truncated or random file, a
decompression bomb or a size other than the expected one gives `(None, reason)` (S8). The file size is checked before
reading and the file is never read past `MAX_IMAGE_BYTES`. `Image.MAX_IMAGE_PIXELS` keeps its default (Pillow raises
above twice the limit and warns above it; the warning is raised as an error here) and truncated images are never
accepted. Luma is decoded in stored pixel order, EXIF orientation ignored (D8).

Memory (T15m review): the decoder converts the decoded JPEG with `convert("L")` directly, no other RGB copy. At 12 MP
the peak is the RGB decode (3 B/px, 36 MB) plus the L image (1 B/px, 12 MB), then the L image plus the float32 result
(4 B/px, 48 MB), plus the file bytes (at most 32 MiB, 4 to 6 MB for a phone photo). A caller keeping decoded images
holds 48 MB per 12 MP image (hence `SNAP_CACHE_IMAGES` 2). `Image.draft("L")` would skip the RGB decode but changes the
luma values (JPEG Y plane instead of Pillow's RGB to L, up to 11 levels apart near colour edges), so it is not used:
the drift measures keep the values they had before the move.
"""
import io
import os
import stat
import warnings

import numpy
from PIL import Image

MAX_IMAGE_BYTES = 32 * 1024 * 1024  # same cap as public_import.MAX_IMAGE_BYTES

UNREADABLE = "unreadable"
SIZE_MISMATCH = "size_mismatch"
REASONS = (UNREADABLE, SIZE_MISMATCH)


def _read_capped(path):
    """Bytes of a regular file of at most `MAX_IMAGE_BYTES`, or None; never follows a link, never reads past the cap."""
    cap = MAX_IMAGE_BYTES
    try:
        before = os.lstat(path)
        if not stat.S_ISREG(before.st_mode) or before.st_size > cap:
            return None
        descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_CLOEXEC", 0))
        with os.fdopen(descriptor, "rb") as handle:
            if not stat.S_ISREG(os.fstat(handle.fileno()).st_mode):
                return None
            # A file grown after the size check is cut at the cap: its bytes are then a truncated JPEG, refused below.
            data = handle.read(cap)
    except OSError:
        return None
    return data


def _grey(data, expected_size):
    """The L image, or a reason; the decoded RGB image is dropped when this returns."""
    with warnings.catch_warnings():
        warnings.simplefilter("error", Image.DecompressionBombWarning)
        with Image.open(io.BytesIO(data)) as image:
            if expected_size is not None and tuple(image.size) != tuple(expected_size):
                return None, SIZE_MISMATCH
            return image.convert("L"), None


def decode_luma(path, expected_size=None):
    """(float32 luma array of shape (height, width), None), or (None, reason) with reason in REASONS.

    `expected_size` (width, height), when given, is checked from the header before decoding the pixels."""
    data = _read_capped(path)
    if data is None:
        return None, UNREADABLE
    try:
        grey, reason = _grey(data, expected_size)
    except (OSError, ValueError, SyntaxError, Image.DecompressionBombError, Image.DecompressionBombWarning):
        return None, UNREADABLE
    del data
    if grey is None:
        return None, reason
    return numpy.asarray(grey, dtype=numpy.float32), None
