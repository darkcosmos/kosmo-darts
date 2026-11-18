"""Helpers shared by the snap tests (T15m) and the end-to-end snap test of the tool (T15n)."""
from kosmo_camera import board, synth


def inner_crossings(truth):
    """True pixels of the radial wire crossing the inner edge of the double ring, for each calibration point."""
    scale = board.DOUBLE_INNER_MM / board.DOUBLE_OUTER_MM
    return synth.project(truth.homography, [(x * scale, y * scale) for x, y in board.CALIBRATION_POINTS_MM])
