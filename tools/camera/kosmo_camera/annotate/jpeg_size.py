"""Pixel size of a JPEG read from its start-of-frame marker, stdlib only (decision D8).

The size is the stored pixel order: the EXIF orientation is ignored, like everywhere else in the tool. Only the marker
segments are read, never the image data, and at most MAX_SEGMENTS of them.
"""
from pathlib import Path

MAX_SEGMENTS = 512
_STANDALONE = frozenset([0x01, *range(0xD0, 0xD8)])  # TEM and RSTn carry no length
_NOT_FRAME = frozenset([0xC4, 0xC8, 0xCC])  # DHT, JPG, DAC share the SOFn range but are not frame headers


class JpegError(ValueError):
    """The file is not a JPEG whose size can be read; the message is in French (shown to Kosmo)."""


def _read(handle, count):
    data = handle.read(count)
    if len(data) != count:
        raise JpegError("Photo tronquée : taille JPEG illisible.")
    return data


def _marker(handle):
    byte = _read(handle, 1)[0]
    if byte != 0xFF:
        raise JpegError("Photo invalide : marqueur JPEG attendu.")
    while byte == 0xFF:  # fill bytes
        byte = _read(handle, 1)[0]
    return byte


def read_size(handle) -> tuple:
    """(width, height) of the JPEG in an open binary file."""
    if _read(handle, 2) != b"\xff\xd8":
        raise JpegError("Photo invalide : ce n'est pas un JPEG.")
    for _ in range(MAX_SEGMENTS):
        marker = _marker(handle)
        if marker in _STANDALONE:
            continue
        if marker in (0xD8, 0xD9, 0xDA):
            raise JpegError("Photo invalide : aucun en-tête de taille avant les données.")
        length = int.from_bytes(_read(handle, 2), "big")
        if length < 2:
            raise JpegError("Photo invalide : segment JPEG incorrect.")
        if 0xC0 <= marker <= 0xCF and marker not in _NOT_FRAME:
            if length < 7:
                raise JpegError("Photo invalide : en-tête de taille incorrect.")
            frame = _read(handle, 5)
            height = int.from_bytes(frame[1:3], "big")
            width = int.from_bytes(frame[3:5], "big")
            if width < 1 or height < 1:
                raise JpegError("Photo invalide : taille nulle.")
            return width, height
        _read(handle, length - 2)
    raise JpegError("Photo invalide : trop de segments avant la taille.")


def jpeg_size(path) -> tuple:
    """(width, height) of the JPEG file at `path`."""
    try:
        with Path(path).open("rb") as handle:
            return read_size(handle)
    except OSError as error:
        raise JpegError(f"Photo illisible : {error.strerror or error}") from error
