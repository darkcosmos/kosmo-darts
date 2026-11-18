"""Synthetic dartboards and sessions for the tests (rules CV1, CV2, CV5, CV10, CV11, CV20). Generated data only.

A board is drawn flat in board millimetres with `board.py` values, then warped by a homography built from yaw, pitch,
distance and roll (a pinhole camera looking at the board plane). Darts are drawn from their tip pixel. Everything is
seeded: the same inputs give the same bytes. Needs numpy, Pillow and PyAV (the CPU environment of the tools).
"""
import datetime
import functools
import hashlib
import json
import math
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

import av
import numpy
from PIL import Image, ImageDraw

from kosmo_camera import annotations as ann
from kosmo_camera.board import (BULL_RADIUS_MM, CALIBRATION_POINTS_MM, DOUBLE_INNER_MM, DOUBLE_OUTER_MM,
                                OUTER_BULL_RADIUS_MM, SECTOR_ANGLE_DEGREES, SECTOR_ORDER, TRIPLE_INNER_MM,
                                TRIPLE_OUTER_MM)

CANVAS_PX_PER_MM = 3
_CANVAS_HALF_MM = 200
_WALL = (60, 60, 64)
_BLACK = (20, 20, 22)
_CREAM = (226, 210, 170)
_RED = (200, 30, 40)
_GREEN = (20, 140, 70)
_WIRE = (170, 170, 170)

VIDEO_FPS = 10
FRAME_MS = 1000 // VIDEO_FPS
_ARRIVAL_LEAD_MS = 300  # a dart appears this long before its entry (CV10 window)
_ENTRY_STEP_MS = 1000
_FIRST_ARRIVAL_MS = 500
_PHOTO_DELAY_MS = 42
_VOID_DELAY_MS = 400
_REMOVAL_DELAY_MS = 800  # CV11: the darts leave this long after the last entry
_END_DELAY_MS = 700  # the volley ends this long after the removal
_START_UTC = datetime.datetime(2026, 11, 1, 10, 0, 0, tzinfo=datetime.timezone.utc)
_START_NANOS = 5_000_000_000
_GAP_MS = 1000
_DART_DIRECTION = (0.35, -1.0)  # image direction from the tip along the barrel
_CORNER_BOX_PX = 40


@dataclass(frozen=True)
class View:
    """Camera looking at the board: angles in degrees, distance in mm; drift is applied to the image after the warp."""
    yaw: float = 20.0
    pitch: float = 15.0
    distance: float = 700.0
    roll: float = 0.0
    drift_dx: float = 0.0
    drift_dy: float = 0.0
    drift_roll: float = 0.0

    def homography(self, size):
        """3x3 matrix from board mm (x towards the 6, y towards the 20) to pixels of an image of `size` (w, h)."""
        width, height = size
        yaw, pitch, roll = (math.radians(a) for a in (self.yaw, self.pitch, self.roll))
        ry = numpy.array([[math.cos(yaw), 0, math.sin(yaw)], [0, 1, 0], [-math.sin(yaw), 0, math.cos(yaw)]])
        rx = numpy.array([[1, 0, 0], [0, math.cos(pitch), -math.sin(pitch)], [0, math.sin(pitch), math.cos(pitch)]])
        rz = numpy.array([[math.cos(roll), -math.sin(roll), 0], [math.sin(roll), math.cos(roll), 0], [0, 0, 1]])
        rotation = rz @ rx @ ry
        focal = 0.7 * min(width, height) * self.distance / (2 * DOUBLE_OUTER_MM)
        intrinsics = numpy.array([[focal, 0, width / 2], [0, -focal, height / 2], [0, 0, 1]])
        columns = numpy.column_stack([rotation[:, 0], rotation[:, 1], [0, 0, self.distance]])
        return _drift_matrix(self, size) @ intrinsics @ columns


def _drift_matrix(view, size):
    width, height = size
    angle = math.radians(view.drift_roll)
    centre = numpy.array([width / 2, height / 2])
    rotation = numpy.array([[math.cos(angle), -math.sin(angle)], [math.sin(angle), math.cos(angle)]])
    offset = centre - rotation @ centre + numpy.array([view.drift_dx, view.drift_dy])
    return numpy.array([[rotation[0, 0], rotation[0, 1], offset[0]],
                        [rotation[1, 0], rotation[1, 1], offset[1]], [0, 0, 1]])


@dataclass(frozen=True)
class Lighting:
    brightness: float = 1.0
    gradient: float = 0.0  # left to right shading, -1 to 1
    noise: float = 2.0  # standard deviation in grey levels


@dataclass(frozen=True)
class BoardTruth:
    """What the renderer knows: the homography, the tip pixels (same order as the darts) and the CV2 pixels."""
    homography: numpy.ndarray
    tips: tuple
    calibration: tuple


def project(homography, points_mm):
    """Apply a homography to board points; returns a tuple of (x, y) pixels."""
    result = []
    for x, y in points_mm:
        u, v, w = homography @ numpy.array([x, y, 1.0])
        result.append((float(u / w), float(v / w)))
    return tuple(result)


def unproject(homography, pixel):
    """Board mm under a pixel."""
    x, y, w = numpy.linalg.inv(homography) @ numpy.array([pixel[0], pixel[1], 1.0])
    return float(x / w), float(y / w)


def zone_centre_mm(segment, multiplier):
    """A point well inside the zone: the middle of the ring slice, or outside the board for the miss (0)."""
    if segment == 0:
        return 0.0, -190.0
    if segment == 50:
        return 0.0, 0.0
    if segment == 25:
        return 0.0, (BULL_RADIUS_MM + OUTER_BULL_RADIUS_MM) / 2
    radius = {"SINGLE": (OUTER_BULL_RADIUS_MM + TRIPLE_INNER_MM) / 2,
              "TRIPLE": (TRIPLE_INNER_MM + TRIPLE_OUTER_MM) / 2,
              "DOUBLE": (DOUBLE_INNER_MM + DOUBLE_OUTER_MM) / 2}[multiplier]
    angle = math.radians(SECTOR_ANGLE_DEGREES * SECTOR_ORDER.index(segment))
    return radius * math.sin(angle), radius * math.cos(angle)


@functools.lru_cache(maxsize=1)
def _flat_board():
    """The board seen from the front, drawn in board mm at CANVAS_PX_PER_MM."""
    scale = CANVAS_PX_PER_MM
    side = 2 * _CANVAS_HALF_MM * scale
    image = Image.new("RGB", (side, side), _WALL)
    draw = ImageDraw.Draw(image)

    def disc(radius, colour):
        c = _CANVAS_HALF_MM * scale
        draw.ellipse((c - radius * scale, c - radius * scale, c + radius * scale, c + radius * scale), fill=colour)

    def wedge(index, radius, colour):
        c = _CANVAS_HALF_MM * scale
        start = -90 + SECTOR_ANGLE_DEGREES * index - SECTOR_ANGLE_DEGREES / 2
        draw.pieslice((c - radius * scale, c - radius * scale, c + radius * scale, c + radius * scale),
                      start, start + SECTOR_ANGLE_DEGREES, fill=colour)

    disc(DOUBLE_OUTER_MM + 25, _BLACK)
    for index in range(len(SECTOR_ORDER)):
        single, ring = (_BLACK, _RED) if index % 2 == 0 else (_CREAM, _GREEN)
        wedge(index, DOUBLE_OUTER_MM, ring)
        wedge(index, DOUBLE_INNER_MM, single)
        wedge(index, TRIPLE_OUTER_MM, ring)
        wedge(index, TRIPLE_INNER_MM, single)
    disc(OUTER_BULL_RADIUS_MM, _GREEN)
    disc(BULL_RADIUS_MM, _RED)
    centre = _CANVAS_HALF_MM * scale
    for index in range(len(SECTOR_ORDER)):
        angle = math.radians(SECTOR_ANGLE_DEGREES * (index - 0.5))
        inner, outer = OUTER_BULL_RADIUS_MM * scale, DOUBLE_OUTER_MM * scale
        draw.line((centre + inner * math.sin(angle), centre - inner * math.cos(angle),
                   centre + outer * math.sin(angle), centre - outer * math.cos(angle)), fill=_WIRE, width=2)
    return image


def _warped_board(homography, size):
    scale = CANVAS_PX_PER_MM
    canvas_from_mm = numpy.array([[scale, 0, _CANVAS_HALF_MM * scale], [0, -scale, _CANVAS_HALF_MM * scale],
                                  [0, 0, 1.0]])
    matrix = canvas_from_mm @ numpy.linalg.inv(homography)
    matrix = matrix / matrix[2, 2]
    coefficients = tuple(float(v) for v in matrix.reshape(-1)[:8])
    return _flat_board().transform(size, Image.Transform.PERSPECTIVE, coefficients, Image.Resampling.BILINEAR,
                                   fillcolor=_WALL)


@dataclass(frozen=True)
class BoardStyle:
    """Look of the analytic renderer (T15m): wire width in board mm and colours. The default wire is as wide as the
    radial wires of the canvas board (2 canvas px)."""
    wire_mm: float = 2 / CANVAS_PX_PER_MM
    wire: tuple = _WIRE
    rings: tuple = (_RED, _GREEN)  # double and treble colours of the even and odd sectors (index in SECTOR_ORDER)
    singles: tuple = (_BLACK, _CREAM)
    surround: tuple = _BLACK
    wall: tuple = _WALL


# Sub-samples per pixel side near an edge. 4 x 4 moved a wire nearly parallel to a pixel axis by up to 0.15 px (its
# coverage steps by 1/4); 8 x 8 keeps every crossing within 0.03 px of `project` on the T15m boards.
_SUPERSAMPLE = 8
_SURROUND_MM = DOUBLE_OUTER_MM + 25
_RING_WIRES_MM = (TRIPLE_INNER_MM, TRIPLE_OUTER_MM, DOUBLE_INNER_MM, DOUBLE_OUTER_MM)
_EDGES_MM = (BULL_RADIUS_MM, OUTER_BULL_RADIUS_MM) + _RING_WIRES_MM + (_SURROUND_MM,)
_ROWS_PER_CHUNK = 128


def _board_mm(inverse, xs, ys):
    """Board mm under continuous pixel coordinates (pixel (i, j) covers [j, j + 1) x [i, i + 1), as `project`)."""
    u = inverse[0, 0] * xs + inverse[0, 1] * ys + inverse[0, 2]
    v = inverse[1, 0] * xs + inverse[1, 1] * ys + inverse[1, 2]
    w = inverse[2, 0] * xs + inverse[2, 1] * ys + inverse[2, 2]
    return u / w, v / w


def _polar(x_mm, y_mm):
    """Radius and the angular offset (degrees, -9 to 9) to the nearest sector edge, and the sector index."""
    radius = numpy.hypot(x_mm, y_mm)
    degrees = numpy.mod(numpy.degrees(numpy.arctan2(x_mm, y_mm)), 360.0)
    shifted = degrees + SECTOR_ANGLE_DEGREES / 2
    sector = numpy.floor(shifted / SECTOR_ANGLE_DEGREES).astype(numpy.int64) % len(SECTOR_ORDER)
    offset = numpy.mod(shifted, SECTOR_ANGLE_DEGREES)
    offset = numpy.minimum(offset, SECTOR_ANGLE_DEGREES - offset)
    return radius, offset, sector


def _edge_distance_mm(x_mm, y_mm):
    """Distance in board mm to the nearest wire, ring or sector edge."""
    radius, offset, _ = _polar(x_mm, y_mm)
    radial = numpy.where((radius >= OUTER_BULL_RADIUS_MM) & (radius <= DOUBLE_OUTER_MM),
                         radius * numpy.sin(numpy.radians(offset)), numpy.inf)
    rings = numpy.min(numpy.abs(radius[..., None] - numpy.array(_EDGES_MM)), axis=-1)
    return numpy.minimum(radial, rings)


def _colours(x_mm, y_mm, style, ring_wires):
    """RGB float colour of the board at each point (CV1 geometry of `board.py`)."""
    radius, offset, sector = _polar(x_mm, y_mm)
    odd = sector % 2
    ring = numpy.array(style.rings, dtype=numpy.float64)[odd]
    single = numpy.array(style.singles, dtype=numpy.float64)[odd]
    out = numpy.empty(radius.shape + (3,))
    out[...] = style.wall
    layers = ((_SURROUND_MM, style.surround), (DOUBLE_OUTER_MM, ring), (DOUBLE_INNER_MM, single),
              (TRIPLE_OUTER_MM, ring), (TRIPLE_INNER_MM, single), (OUTER_BULL_RADIUS_MM, _GREEN),
              (BULL_RADIUS_MM, _RED))
    for limit, colour in layers:  # outer to inner, each limit inclusive like the canvas discs
        inside = radius <= limit
        out[inside] = colour[inside] if isinstance(colour, numpy.ndarray) else colour
    half = style.wire_mm / 2
    wire = (radius >= OUTER_BULL_RADIUS_MM) & (radius <= DOUBLE_OUTER_MM) & (
        radius * numpy.sin(numpy.radians(offset)) < half)
    if ring_wires:
        wire |= numpy.min(numpy.abs(radius[..., None] - numpy.array(_RING_WIRES_MM)), axis=-1) < half
    out[wire] = style.wire
    return out


def _analytic_rows(inverse, size, rows, style, ring_wires):
    width = size[0]
    ys, xs = numpy.meshgrid(numpy.arange(rows.start, rows.stop) + 0.5, numpy.arange(width) + 0.5, indexing="ij")
    x_mm, y_mm = _board_mm(inverse, xs, ys)
    pixels = _colours(x_mm, y_mm, style, ring_wires)
    step_x = numpy.hypot(*numpy.subtract(_board_mm(inverse, xs + 1.0, ys), (x_mm, y_mm)))
    step_y = numpy.hypot(*numpy.subtract(_board_mm(inverse, xs, ys + 1.0), (x_mm, y_mm)))
    near = _edge_distance_mm(x_mm, y_mm) < style.wire_mm / 2 + 1.5 * numpy.maximum(step_x, step_y)
    rows_near, columns_near = numpy.nonzero(near)
    offsets = (numpy.arange(_SUPERSAMPLE) + 0.5) / _SUPERSAMPLE - 0.5
    sub_y, sub_x = numpy.meshgrid(offsets, offsets, indexing="ij")
    sample_x = xs[rows_near, columns_near][:, None] + sub_x.reshape(-1)[None, :]
    sample_y = ys[rows_near, columns_near][:, None] + sub_y.reshape(-1)[None, :]
    pixels[rows_near, columns_near] = _colours(*_board_mm(inverse, sample_x, sample_y), style, ring_wires).mean(axis=1)
    return pixels


def _analytic_board(homography, size, style, ring_wires):
    """The board drawn from `board.py` geometry at every pixel, edges supersampled: exact to well below 0.1 px."""
    inverse = numpy.linalg.inv(homography)
    width, height = size
    pixels = numpy.empty((height, width, 3))
    for start in range(0, height, _ROWS_PER_CHUNK):
        rows = range(start, min(height, start + _ROWS_PER_CHUNK))
        pixels[rows.start:rows.stop] = _analytic_rows(inverse, size, rows, style, ring_wires)
    return Image.fromarray(numpy.clip(numpy.rint(pixels), 0, 255).astype(numpy.uint8))


def _draw_dart(draw, tip, size):
    """Barrel and flight drawn from the tip, away from it along a fixed image direction."""
    norm = math.hypot(*_DART_DIRECTION)
    dx, dy = _DART_DIRECTION[0] / norm, _DART_DIRECTION[1] / norm
    barrel, flight = 0.07 * size[0], 0.045 * size[0]
    base = (tip[0] + dx * barrel, tip[1] + dy * barrel)
    end = (base[0] + dx * flight, base[1] + dy * flight)
    width = max(2, round(size[0] / 160))
    draw.line((tip[0], tip[1], base[0], base[1]), fill=(210, 210, 215), width=width)
    side = (-dy * flight * 0.45, dx * flight * 0.45)
    draw.polygon([base, (end[0] + side[0], end[1] + side[1]), (end[0] - side[0], end[1] - side[1])],
                 fill=(230, 230, 40))


def _shade(pixels, lighting, rng):
    height, width = pixels.shape[:2]
    ramp = 1.0 + lighting.gradient * (numpy.linspace(-0.5, 0.5, width)[None, :, None])
    shaded = pixels.astype(numpy.float32) * lighting.brightness * ramp
    if lighting.noise:
        shaded += rng.normal(0.0, lighting.noise, shaded.shape).astype(numpy.float32)
    return numpy.clip(shaded + 0.5, 0, 255).astype(numpy.uint8)


def render_board(view, darts, size, lighting=Lighting(), seed=0, ring_wires=False, style=None):
    """Render the board in perspective with darts at the given board positions (mm).

    Returns ``(image, truth)``: an RGB uint8 array of shape (height, width, 3) and a `BoardTruth` with the homography,
    the tip pixel of every dart and the 4 calibration pixels (CV2). By default the board is the canvas drawing warped
    by Pillow (T7 output, unchanged). With `ring_wires` or a `style` (T15m), the board is computed from `board.py`
    geometry at every pixel, edges supersampled, so that its wire crossings are where `project` puts them to well
    below 0.1 px; `ring_wires` adds the double and treble ring wires (centred on their radius, `style.wire_mm` wide,
    like the radial wires)."""
    homography = view.homography(size)
    if ring_wires or style is not None:
        image = _analytic_board(homography, size, style or BoardStyle(), ring_wires)
    else:
        image = _warped_board(homography, size)
    tips = project(homography, darts)
    draw = ImageDraw.Draw(image)
    for tip in tips:
        _draw_dart(draw, tip, size)
    pixels = _shade(numpy.asarray(image), lighting, numpy.random.default_rng([seed, size[0], size[1]]))
    return pixels, BoardTruth(homography, tips, project(homography, CALIBRATION_POINTS_MM))


# ---- sessions -----------------------------------------------------------------------------------------------------

@dataclass(frozen=True)
class DartSpec:
    segment: int
    multiplier: str = "SINGLE"
    voided: bool = False  # CV5: not judged
    photo: bool = True  # False: the photo is missing (null in the truth file), not judged either


@dataclass(frozen=True)
class Drift:
    """From dart `from_dart` on (until the next drift), photos are shifted by (dx, dy) px and rolled by `roll` degrees."""
    from_dart: int
    dx: float = 0.0
    dy: float = 0.0
    roll: float = 0.0


@dataclass(frozen=True)
class SessionSpec:
    darts: tuple
    name: str = "20261101-100000"
    view: View = View()
    lighting: Lighting = Lighting()
    photo_size: tuple = (640, 480)
    video_size: tuple = (640, 360)
    darts_per_volley: int = 3
    drifts: tuple = ()
    segments: tuple = (1,)  # `fromDart` of every calibration segment written (CV20), the first one being 1
    corner_occlusion: Optional[tuple] = None  # (dart number, corner index 0 to 3): box over that corner in that photo
    video_occluder: Optional[tuple] = None  # (first frame, last frame): a person-sized block on every volley video


@dataclass(frozen=True)
class SyntheticSession:
    folder: Path
    truth_path: Path
    annotations_path: Path
    spec: SessionSpec
    views: dict  # dart number -> View of its photo (drift included)
    homographies: dict  # dart number -> true 3x3 matrix of its photo
    tips: dict  # dart number -> true tip pixel in its photo
    calibration: dict  # dart number -> the 4 true calibration pixels of its photo
    positions_mm: dict  # dart number -> true board position
    arrival_frames: dict  # dart number -> frame index of its arrival on its volley video
    video_frames: dict  # volley index (1-based) -> number of frames
    video_empty_from: dict  # volley index -> first frame of the empty board after the removal


def _drift_for(spec, number):
    found = None
    for drift in sorted(spec.drifts, key=lambda d: d.from_dart):
        if drift.from_dart <= number:
            found = drift
    return found


def _view_for(spec, number):
    drift = _drift_for(spec, number)
    if drift is None:
        return spec.view
    return View(spec.view.yaw, spec.view.pitch, spec.view.distance, spec.view.roll, drift.dx, drift.dy, drift.roll)


def _stamp(global_ms, video_ms=None):
    moment = _START_UTC + datetime.timedelta(milliseconds=global_ms)
    stamp = {"utc": moment.strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z",
             "monotonicNanos": _START_NANOS + global_ms * 1_000_000}
    if video_ms is not None:
        stamp["videoPositionMs"] = video_ms
    return stamp


def _split(spec):
    """Dart numbers (1-based) grouped by volley."""
    numbers = list(range(1, len(spec.darts) + 1))
    return [numbers[i:i + spec.darts_per_volley] for i in range(0, len(numbers), spec.darts_per_volley)]


def _entry_ms(position):
    return _FIRST_ARRIVAL_MS + _ARRIVAL_LEAD_MS + _ENTRY_STEP_MS * position


def _timeline(count):
    """Video milliseconds of a volley with `count` darts: (removal, end)."""
    removal = _entry_ms(count - 1) + _REMOVAL_DELAY_MS
    return removal, removal + _END_DELAY_MS


def _truth_dart(spec, number, volley_start, position):
    dart = spec.darts[number - 1]
    entry = _entry_ms(position)
    photo = None
    if dart.photo:
        photo = {"file": f"dart-{number:03d}.jpg",
                 **_stamp(volley_start + entry + _PHOTO_DELAY_MS, entry + _PHOTO_DELAY_MS)}
    void = _stamp(volley_start + entry + _VOID_DELAY_MS, entry + _VOID_DELAY_MS) if dart.voided else None
    return {"number": number, "segment": dart.segment, "multiplier": dart.multiplier, "voided": dart.voided,
            "entry": _stamp(volley_start + entry, entry), "voidedAt": void, "photo": photo,
            "gravity": {"x": 0.1, "y": -9.8, "z": 0.25} if photo else None}


def _truth_root(spec):
    volleys, start = [], 0
    for index, numbers in enumerate(_split(spec), start=1):
        _, end = _timeline(len(numbers))
        volleys.append({"index": index, "video": f"volley-{index:03d}.mp4", "start": _stamp(start),
                        "end": _stamp(start + end, end), "empty": False,
                        "darts": [_truth_dart(spec, n, start, p) for p, n in enumerate(numbers)]})
        start += end + _GAP_MS
    width, height = spec.video_size
    return {"format": "kosmo-capture-truth", "version": 2, "session": spec.name,
            "app": {"id": "io.github.darkcosmos.kosmodarts.capture", "version": "0.0.0-synthetic"},
            "device": {"manufacturer": "Synthetic", "model": "Generated", "sdk": 37},
            "clock": "elapsedRealtimeNanos", "startedAt": _stamp(0), "endedAt": _stamp(start - _GAP_MS),
            "endReason": "STOP", "video": {"requestedQuality": "FHD", "width": width, "height": height},
            "photo": {"width": spec.photo_size[0], "height": spec.photo_size[1], "timestampSource": "REALTIME"},
            "zoom": {"requestedRatio": 1.0, "ratio": 1.0}, "volleys": volleys}


def _write_photo(spec, seed, number, folder, positions):
    view = _view_for(spec, number)
    numbers = [n for n in positions if n <= number and n >= _volley_first(spec, number)]
    image, truth = render_board(view, [positions[n] for n in numbers], spec.photo_size, spec.lighting,
                                seed * 1000 + number)
    occlusion = spec.corner_occlusion
    if occlusion is not None and occlusion[0] == number:
        x, y = truth.calibration[occlusion[1]]
        half = _CORNER_BOX_PX // 2
        image = image.copy()
        image[max(0, round(y) - half):round(y) + half, max(0, round(x) - half):round(x) + half] = 40
    Image.fromarray(image).save(folder / f"dart-{number:03d}.jpg", quality=90)
    return view, truth, truth.tips[numbers.index(number)]


def _volley_first(spec, number):
    return next(volley[0] for volley in _split(spec) if number in volley)


def _write_video(spec, seed, index, numbers, positions, path):
    """Empty board, each dart appearing at its arrival frame, removal, empty board again; returns the frame counts."""
    removal, end = _timeline(len(numbers))
    frames = end // FRAME_MS + 1
    arrivals = {n: (_FIRST_ARRIVAL_MS + _ENTRY_STEP_MS * p) // FRAME_MS for p, n in enumerate(numbers)}
    removal_frame = removal // FRAME_MS
    width, height = spec.video_size
    container = av.open(str(path), "w", format="mp4")
    try:
        stream = container.add_stream("mpeg4", rate=VIDEO_FPS)
        stream.width, stream.height, stream.pix_fmt, stream.bit_rate = width, height, "yuv420p", 4_000_000
        stream.codec_context.thread_count = 1
        for frame_index in range(frames):
            present = [positions[n] for n in numbers if arrivals[n] <= frame_index < removal_frame]
            image, _ = render_board(spec.view, present, spec.video_size, spec.lighting,
                                    seed * 100000 + index * 1000 + frame_index)
            if spec.video_occluder and spec.video_occluder[0] <= frame_index <= spec.video_occluder[1]:
                image = image.copy()
                image[int(height * 0.05):, int(width * 0.38):int(width * 0.62)] = 35
            frame = av.VideoFrame.from_ndarray(image, format="rgb24")
            for packet in stream.encode(frame):
                container.mux(packet)
        for packet in stream.encode():
            container.mux(packet)
    finally:
        container.close()
    return arrivals, frames, removal_frame


def _annotations(spec, truth_sha, calibration, tips, judged):
    segments = []
    for i, from_dart in enumerate(spec.segments):
        photo_number = judged[0] if i == 0 else from_dart
        segments.append(ann.Calibration(from_dart, f"dart-{photo_number:03d}.jpg", *spec.photo_size,
                                        calibration[photo_number]))
    width, height = spec.photo_size
    darts = []
    for number in judged:
        x, y = tips[number]
        inside = 0 <= x <= width and 0 <= y <= height
        darts.append(ann.DartAnnotation(number, ann.Status.VISIBLE if inside else ann.Status.OFF_BOARD,
                                        (x, y) if inside else None))
    return ann.Annotations(spec.name, truth_sha, tuple(segments), tuple(darts))


def make_session(directory, spec, seed=0):
    """Write a whole synthetic session under `directory`: `<name>/truth.json` (version 2), `dart-NNN.jpg`,
    `volley-NNN.mp4`, and the matching annotations (version 2) in `annotations/<name>.json`."""
    directory = Path(directory)
    folder = directory / spec.name
    folder.mkdir(parents=True)
    (directory / "annotations").mkdir(exist_ok=True)
    positions = {n: zone_centre_mm(d.segment, d.multiplier) for n, d in enumerate(spec.darts, start=1)}
    truth_bytes = json.dumps(_truth_root(spec), indent=2).encode("utf-8")
    truth_path = folder / "truth.json"
    truth_path.write_bytes(truth_bytes)
    views, homographies, tips, calibration = {}, {}, {}, {}
    for number, dart in enumerate(spec.darts, start=1):
        if not dart.photo:
            continue
        view, truth, tip = _write_photo(spec, seed, number, folder, positions)
        views[number], homographies[number] = view, truth.homography
        tips[number], calibration[number] = tip, truth.calibration
    arrival_frames, video_frames, empty_from = {}, {}, {}
    for index, numbers in enumerate(_split(spec), start=1):
        arrivals, frames, removal_frame = _write_video(spec, seed, index, numbers, positions,
                                                       folder / f"volley-{index:03d}.mp4")
        arrival_frames.update(arrivals)
        video_frames[index], empty_from[index] = frames, removal_frame
    judged = [n for n, d in enumerate(spec.darts, start=1) if d.photo and not d.voided]
    annotations = _annotations(spec, hashlib.sha256(truth_bytes).hexdigest(), calibration, tips, judged)
    annotations_path = directory / "annotations" / f"{spec.name}.json"
    ann.write_atomic(annotations_path, annotations)
    return SyntheticSession(folder, truth_path, annotations_path, spec, views, homographies, tips, calibration,
                            positions, arrival_frames, video_frames, empty_from)
