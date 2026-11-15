"""Homography and calibration metric (CV1, CV2, CV9, CV14).

Conventions: a "true" homography ``H_true`` maps board mm to image pixels (what `synth` renders with, and what the
reference calibration of a photo gives); the predicted one, `board_from_image`, maps image pixels to board mm.
Board axes: x towards the 6, y towards the 20.
"""
import math

import numpy

from kosmo_camera.board import CALIBRATION_POINTS_MM, SECTOR_ANGLE_DEGREES, SECTOR_ORDER
from kosmo_camera.config import CAL_MAX_ERROR_MM

MIN_POINTS = 4
_RANK_TOLERANCE = 1e-9  # smallest usable singular value of the DLT system, relative to the largest
_SECTOR_CENTRE_RADII_MM = (103.0, 166.0)  # CV9: the middle of the treble ring and of the double ring
NEAR = "near"
FAR = "far"


class DegenerateError(ValueError):
    """The points do not determine a homography (for example 3 of 4 collinear)."""


def _normalisation(points):
    centre = points.mean(axis=0)
    spread = numpy.sqrt(((points - centre) ** 2).sum(axis=1)).mean()
    if spread == 0:
        raise DegenerateError("all points are identical")
    scale = math.sqrt(2) / spread
    return numpy.array([[scale, 0, -scale * centre[0]], [0, scale, -scale * centre[1]], [0, 0, 1.0]])


def homography(src, dst):
    """3x3 matrix mapping `src` points to `dst` points (normalised DLT), scaled so that H[2, 2] is 1.

    Raises `DegenerateError` with fewer than 4 points or when the points do not determine a homography."""
    src = numpy.asarray(src, dtype=float)
    dst = numpy.asarray(dst, dtype=float)
    if src.shape != dst.shape or src.ndim != 2 or src.shape[1] != 2:
        raise ValueError("src and dst must be matching lists of (x, y)")
    if len(src) < MIN_POINTS:
        raise DegenerateError(f"at least {MIN_POINTS} points are needed")
    t_src, t_dst = _normalisation(src), _normalisation(dst)
    ones = numpy.ones((len(src), 1))
    a = (t_src @ numpy.hstack([src, ones]).T).T
    b = (t_dst @ numpy.hstack([dst, ones]).T).T
    rows = []
    for (x, y, w), (u, v, _) in zip(a, b):
        rows.append([0, 0, 0, -w * x, -w * y, -w * w, v * x, v * y, v * w])
        rows.append([w * x, w * y, w * w, 0, 0, 0, -u * x, -u * y, -u * w])
    _, singular, vt = numpy.linalg.svd(numpy.array(rows), full_matrices=True)
    if singular[7] <= _RANK_TOLERANCE * singular[0]:
        raise DegenerateError("the points are degenerate (collinear)")
    h = numpy.linalg.inv(t_dst) @ vt[-1].reshape(3, 3) @ t_src
    if abs(h[2, 2]) < 1e-12:
        raise DegenerateError("the homography sends the origin to infinity")
    return h / h[2, 2]


def apply(h, points):
    """Apply a homography to a list of (x, y); returns an array of shape (n, 2)."""
    pts = numpy.asarray(points, dtype=float).reshape(-1, 2)
    out = (h @ numpy.hstack([pts, numpy.ones((len(pts), 1))]).T).T
    return out[:, :2] / out[:, 2:3]


def board_from_image(cal_pixels):
    """H image pixels to board mm from the 4 calibration pixels, in the order of `CALIBRATION_POINTS_MM` (CV2)."""
    return homography(cal_pixels, CALIBRATION_POINTS_MM)


def _check_points_mm():
    """The 41 board points of CV9: the bull centre, the 20 sector centres at 103 mm and at 166 mm."""
    points = [(0.0, 0.0)]
    for radius in _SECTOR_CENTRE_RADII_MM:
        for index in range(len(SECTOR_ORDER)):
            angle = math.radians(90.0 - SECTOR_ANGLE_DEGREES * index)
            points.append((radius * math.cos(angle), radius * math.sin(angle)))
    return numpy.array(points)


CHECK_POINTS_MM = _check_points_mm()


def calibration_error_mm(h_true, h_pred):
    """CV9: largest board distance in mm between the 41 check points and the same points sent to the image by
    `h_true` (board to image) and back to the board by `h_pred` (image to board)."""
    back = apply(h_pred, apply(h_true, CHECK_POINTS_MM))
    return float(numpy.sqrt(((back - CHECK_POINTS_MM) ** 2).sum(axis=1)).max())


def calibration_success(h_true, found_points):
    """CV9: all 4 points found and the calibration error at most `CAL_MAX_ERROR_MM`.

    `found_points` are the pixels found for the 4 calibration points (fewer than 4 means a point was missed)."""
    if found_points is None or len(found_points) < MIN_POINTS:
        return False
    try:
        h_pred = board_from_image(found_points)
    except DegenerateError:
        return False
    return calibration_error_mm(h_true, h_pred) <= CAL_MAX_ERROR_MM


def camera_direction_mm(h_true):
    """Unit board direction towards the camera (the way the depth decreases), or None for a front view.

    The depth of a board point is the third homogeneous coordinate, linear in (x, y): its gradient points away."""
    gradient = -numpy.array([h_true[2, 0], h_true[2, 1]]) * math.copysign(1.0, h_true[2, 2])
    norm = float(numpy.hypot(*gradient))
    if norm < 1e-12 * max(1.0, abs(h_true[2, 2])):
        return None
    return gradient / norm


def near_far(h_true, point_mm):
    """CV14: "near" for a board point on the camera side of the line through the bull perpendicular to the camera
    direction, else "far". A front view (no direction) is split by the board x axis: x < 0 is near."""
    direction = camera_direction_mm(h_true)
    if direction is None:
        direction = numpy.array([-1.0, 0.0])
    return NEAR if float(numpy.dot(direction, point_mm)) > 0 else FAR
