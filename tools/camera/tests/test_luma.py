"""The one JPEG-to-luma decoder (A33, S14, D45; CV18, CV31): same luma as before the move, never an exception."""
import io
import os
import time

import pytest

numpy = pytest.importorskip("numpy")
pytest.importorskip("PIL")
from PIL import Image  # noqa: E402

from kosmo_camera import luma, public_import  # noqa: E402

SIZE = (64, 48)


def _jpeg(path, size=SIZE, seed=3):
    rng = numpy.random.default_rng(seed)
    pixels = rng.integers(0, 256, (size[1], size[0], 3), dtype=numpy.uint8)
    Image.fromarray(pixels).save(path, quality=90)
    return path


def _before_the_move(path):
    """The decoder of drift.py before T15m, kept here as the reference of the luma values."""
    with Image.open(path) as image:
        return numpy.asarray(image.convert("L"), dtype=numpy.float32)


def test_s14_a_jpeg_decodes_to_the_same_luma_as_before_the_move(tmp_path):
    path = _jpeg(tmp_path / "a.jpg")
    decoded, reason = luma.decode_luma(path)
    assert reason is None and decoded.dtype == numpy.float32 and decoded.shape == (SIZE[1], SIZE[0])
    assert numpy.array_equal(decoded, _before_the_move(path))


def test_s14_the_expected_size_is_accepted_and_another_size_is_refused(tmp_path):
    path = _jpeg(tmp_path / "a.jpg")
    assert luma.decode_luma(path, SIZE)[1] is None
    assert luma.decode_luma(path, (SIZE[0], SIZE[1] + 1)) == (None, luma.SIZE_MISMATCH)


@pytest.mark.parametrize("limit", [100, SIZE[0] * SIZE[1] - 1])  # above twice the limit (error), above it (warning)
def test_s14_decompression_bomb_is_unreadable_never_an_exception(tmp_path, monkeypatch, limit):
    path = _jpeg(tmp_path / "a.jpg")
    default = Image.MAX_IMAGE_PIXELS
    monkeypatch.setattr(Image, "MAX_IMAGE_PIXELS", limit)  # lowered in this test only; monkeypatch restores it
    assert luma.decode_luma(path) == (None, luma.UNREADABLE)
    monkeypatch.undo()
    assert Image.MAX_IMAGE_PIXELS == default
    assert luma.decode_luma(path)[1] is None


@pytest.mark.parametrize("content", [b"", b"not a jpeg", bytes(range(256)) * 40, "truncated"])
def test_s8_empty_random_or_truncated_bytes_are_unreadable(tmp_path, content):
    path = tmp_path / "a.jpg"
    if content == "truncated":
        content = _jpeg(tmp_path / "whole.jpg").read_bytes()[:400]
    path.write_bytes(content)
    assert luma.decode_luma(path) == (None, luma.UNREADABLE)


def test_s8_missing_file_directory_and_link_are_unreadable(tmp_path):
    target = _jpeg(tmp_path / "a.jpg")
    link = tmp_path / "link.jpg"
    link.symlink_to(target)
    for path in (tmp_path / "missing.jpg", tmp_path, link):
        assert luma.decode_luma(path) == (None, luma.UNREADABLE)


@pytest.mark.skipif(not hasattr(os, "mkfifo"), reason="needs a FIFO")
def test_s8_a_fifo_is_refused_without_blocking(tmp_path):
    fifo = tmp_path / "pipe.jpg"
    os.mkfifo(fifo)
    assert luma.decode_luma(fifo) == (None, luma.UNREADABLE)


def test_s14_file_of_exactly_the_cap_is_accepted_and_cap_plus_one_refused(tmp_path, monkeypatch):
    path = _jpeg(tmp_path / "a.jpg")
    size = path.stat().st_size
    monkeypatch.setattr(luma, "MAX_IMAGE_BYTES", size)
    assert luma.decode_luma(path)[1] is None
    monkeypatch.setattr(luma, "MAX_IMAGE_BYTES", size - 1)  # the file is now cap + 1 bytes
    assert luma.decode_luma(path) == (None, luma.UNREADABLE)


def test_s14_the_file_is_never_read_past_the_cap(tmp_path, monkeypatch):
    path = _jpeg(tmp_path / "a.jpg")
    cap = path.stat().st_size
    monkeypatch.setattr(luma, "MAX_IMAGE_BYTES", cap)
    requested = []
    real_fdopen = os.fdopen

    class Recording(io.BufferedReader):
        def read(self, size=-1):
            requested.append(size)
            return super().read(size)

    def fdopen(descriptor, mode="r", *args, **kwargs):
        return Recording(real_fdopen(descriptor, "rb", buffering=0))

    monkeypatch.setattr(luma.os, "fdopen", fdopen)
    assert luma.decode_luma(path)[1] is None
    assert requested and all(0 <= size <= cap for size in requested)


def test_s14_the_cap_is_the_public_import_one():
    assert luma.MAX_IMAGE_BYTES == public_import.MAX_IMAGE_BYTES == 32 * 1024 * 1024


@pytest.mark.slow
def test_s14_a_12_mp_jpeg_decodes_in_under_one_second(tmp_path):
    path = tmp_path / "big.jpg"
    rows = numpy.linspace(0, 255, 4000, dtype=numpy.float32)[None, :] * numpy.ones((3000, 1), dtype=numpy.float32)
    Image.fromarray(numpy.dstack([rows, rows[::-1], rows]).astype(numpy.uint8)).save(path, quality=90)
    start = time.perf_counter()
    decoded, reason = luma.decode_luma(path, (4000, 3000))
    assert reason is None and decoded.shape == (3000, 4000)
    assert time.perf_counter() - start < 1.0
