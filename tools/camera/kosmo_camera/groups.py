"""Groups of the breakdowns (CV14); bins fixed in docs/camera/training.md "Groups", limits read from config.py."""
import math
from typing import NamedTuple

import numpy

from kosmo_camera import config
from kosmo_camera.board import DOUBLE_OUTER_MM
from kosmo_camera.geometry import apply, board_from_image, camera_direction_mm

FRONT = "front"
OBLIQUE = "oblique"
STEEP = "steep"
_NEAR_PROBE_MM = 50.0  # how far along the camera direction the side probe goes


class TripodGroup(NamedTuple):
    angle: str  # front, oblique or steep
    side: object  # left, right, above or below; None for front
    distance: str  # far, mid or near


def _zoom(zoom_ratio):
    return 1.0 if not zoom_ratio else float(zoom_ratio)


def zoom_bin(zoom_ratio):
    """"1x" below `ZOOM_1X_BELOW`, "2x" up to `ZOOM_2X_MAX` included, else "zoom"; a null ratio counts as 1."""
    zoom = _zoom(zoom_ratio)
    if zoom < config.ZOOM_1X_BELOW:
        return "1x"
    return "2x" if zoom <= config.ZOOM_2X_MAX else "zoom"


def _board_ellipse(h_board_to_image):
    """Axes (major, minor) in pixels, full lengths, of the image of the 170 mm circle."""
    inverse = numpy.linalg.inv(h_board_to_image)
    circle = numpy.diag([1.0, 1.0, -DOUBLE_OUTER_MM ** 2])
    conic = inverse.T @ circle @ inverse
    a, b, k = conic[:2, :2], conic[:2, 2], conic[2, 2]
    centre = -numpy.linalg.solve(a, b)
    scale = abs(float(centre @ a @ centre - k))
    eigenvalues = numpy.sort(numpy.linalg.eigvalsh(a))
    minor, major = (2 * math.sqrt(scale / abs(value)) for value in eigenvalues[::-1])
    return major, minor


def _angle_bin(degrees):
    if degrees < config.ANGLE_FRONT_MAX_DEGREES:
        return FRONT
    return OBLIQUE if degrees <= config.ANGLE_OBLIQUE_MAX_DEGREES else STEEP


def _side(h_board_to_image):
    """Dominant image direction of the board side nearer to the camera; image y grows downwards."""
    direction = camera_direction_mm(h_board_to_image)
    if direction is None:
        return None
    centre, probe = apply(h_board_to_image, [(0.0, 0.0), tuple(direction * _NEAR_PROBE_MM)])
    dx, dy = probe - centre
    if abs(dx) >= abs(dy):
        return "right" if dx > 0 else "left"
    return "below" if dy > 0 else "above"


def _distance_bin(ratio):
    if ratio < config.DISTANCE_FAR_MAX:
        return "far"
    return "mid" if ratio <= config.DISTANCE_MID_MAX else "near"


def tripod_group(cal_pixels, image_size, zoom_ratio):
    """Angle, side and distance bins from the 4 calibration pixels of the photo (CV2).

    Angle: `acos(minor / major)` of the 170 mm circle ellipse. Distance: major axis over the smaller image side,
    divided by the zoom ratio. The side is None for a front view."""
    h_board_to_image = numpy.linalg.inv(board_from_image(cal_pixels))
    major, minor = _board_ellipse(h_board_to_image)
    angle = _angle_bin(math.degrees(math.acos(min(1.0, minor / major))))
    side = None if angle == FRONT else _side(h_board_to_image)
    ratio = major / min(image_size) / _zoom(zoom_ratio)
    return TripodGroup(angle, side, _distance_bin(ratio))


def lighting_class(image, h_true):
    """"dark", "medium" or "bright" from the median luma (0 to 255) of the pixels inside the 170 mm circle.

    `image` is an (height, width) grey or (height, width, 3) RGB array; `h_true` maps board mm to pixels."""
    pixels = numpy.asarray(image, dtype=float)
    luma = pixels if pixels.ndim == 2 else pixels @ numpy.array([0.299, 0.587, 0.114])
    height, width = luma.shape
    ys, xs = numpy.mgrid[0:height, 0:width]
    board = apply(numpy.linalg.inv(h_true), numpy.column_stack([xs.ravel() + 0.5, ys.ravel() + 0.5]))
    inside = (board ** 2).sum(axis=1) <= DOUBLE_OUTER_MM ** 2
    if not inside.any():
        raise ValueError("the board is not visible in the image")
    median = float(numpy.median(luma.ravel()[inside]))
    if median < config.LIGHT_DARK_BELOW:
        return "dark"
    return "medium" if median <= config.LIGHT_MEDIUM_MAX else "bright"
