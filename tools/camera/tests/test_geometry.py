"""Homography, CV9 calibration metric and CV14 near and far halves (CV1, CV2, CV9, CV14)."""
import numpy
import pytest

pytest.importorskip("av")
pytest.importorskip("PIL")

from kosmo_camera import geometry, synth  # noqa: E402  (after the importorskip above, on purpose)
from kosmo_camera.board import CALIBRATION_POINTS_MM  # noqa: E402

SIZE = (640, 480)


def _true(view=synth.View(yaw=20.0, pitch=10.0)):
    h = view.homography(SIZE)
    return h, synth.project(h, CALIBRATION_POINTS_MM)


def test_dlt_recovers_a_known_homography_from_4_exact_points():
    h, pixels = _true()
    found = geometry.homography(CALIBRATION_POINTS_MM, pixels)
    assert numpy.allclose(found, h / h[2, 2], rtol=1e-9, atol=1e-9)
    back = geometry.apply(geometry.board_from_image(pixels), pixels)
    assert numpy.allclose(back, CALIBRATION_POINTS_MM, atol=1e-9)


def test_dlt_is_invariant_to_point_scaling():
    pixels = numpy.array(_true()[1])
    reference = geometry.homography(CALIBRATION_POINTS_MM, pixels)
    scaled = geometry.homography(numpy.array(CALIBRATION_POINTS_MM) * 1000.0, pixels * 0.001)
    assert numpy.allclose(geometry.apply(scaled, numpy.array(CALIBRATION_POINTS_MM) * 1000.0),
                          geometry.apply(reference, CALIBRATION_POINTS_MM) * 0.001, atol=1e-9)


def test_dlt_accepts_more_than_4_points():
    h = _true()[0]
    points = list(CALIBRATION_POINTS_MM) + [(10.0, 20.0), (-60.0, 5.0)]
    found = geometry.homography(points, synth.project(h, points))
    assert numpy.allclose(found, h / h[2, 2], rtol=1e-8, atol=1e-8)


def test_dlt_degenerate_input_raises():
    collinear = [(0.0, 0.0), (1.0, 1.0), (2.0, 2.0), (5.0, 1.0)]
    with pytest.raises(geometry.DegenerateError):
        geometry.homography(collinear, [(0.0, 0.0), (1.0, 0.0), (2.0, 0.0), (3.0, 1.0)])
    with pytest.raises(geometry.DegenerateError):
        geometry.homography(CALIBRATION_POINTS_MM[:3], CALIBRATION_POINTS_MM[:3])


def test_check_points_are_the_41_of_cv9():
    points = geometry.CHECK_POINTS_MM
    assert points.shape == (41, 2)
    radii = numpy.round(numpy.hypot(points[:, 0], points[:, 1]), 6)
    assert sorted(set(radii)) == [0.0, 103.0, 166.0]
    assert tuple(points[1]) == pytest.approx((0.0, 103.0), abs=1e-9)  # the 20 is at the top


def test_calibration_error_is_zero_for_identical_homographies():
    h, pixels = _true()
    assert geometry.calibration_error_mm(h, geometry.board_from_image(pixels)) < 1e-9


def test_one_pixel_shift_gives_an_error_in_the_expected_band():
    h, pixels = _true()
    moved = list(pixels)
    moved[0] = (moved[0][0] + 1.0, moved[0][1])
    error = geometry.calibration_error_mm(h, geometry.board_from_image(moved))
    # 1 px is about 0.3 to 0.4 mm at this scale (170 mm over about 190 px)
    assert 0.3 < error < 3.0


def test_calibration_success_threshold_is_inclusive(monkeypatch):
    h, pixels = _true()
    moved = list(pixels)
    moved[2] = (moved[2][0], moved[2][1] + 1.5)
    error = geometry.calibration_error_mm(h, geometry.board_from_image(moved))
    monkeypatch.setattr(geometry, "CAL_MAX_ERROR_MM", error)
    assert geometry.calibration_success(h, moved) is True
    monkeypatch.setattr(geometry, "CAL_MAX_ERROR_MM", error - 1e-6)
    assert geometry.calibration_success(h, moved) is False


def test_calibration_success_with_the_real_limit():
    h, pixels = _true()
    assert geometry.calibration_success(h, pixels) is True
    far = [(x + 30.0, y) for x, y in pixels]
    assert geometry.calibration_success(h, far) is False


def test_calibration_success_is_false_with_fewer_than_4_points():
    h, pixels = _true()
    assert geometry.calibration_success(h, pixels[:3]) is False
    assert geometry.calibration_success(h, []) is False
    assert geometry.calibration_success(h, None) is False


def test_calibration_success_is_false_for_degenerate_points():
    h = _true()[0]
    assert geometry.calibration_success(h, [(10.0, 10.0), (20.0, 20.0), (30.0, 30.0), (40.0, 40.0)]) is False


def test_camera_on_the_left_makes_the_left_half_near():
    h = synth.View(yaw=-25.0, pitch=0.0).homography(SIZE)
    assert geometry.near_far(h, (-50.0, 0.0)) == "near"
    assert geometry.near_far(h, (50.0, 0.0)) == "far"
    assert geometry.near_far(h, (-1.0, 90.0)) == "near"  # the split line is vertical through the bull


def test_camera_on_the_right_makes_the_right_half_near():
    h = synth.View(yaw=25.0, pitch=0.0).homography(SIZE)
    assert geometry.near_far(h, (50.0, 0.0)) == "near"
    assert geometry.near_far(h, (-50.0, 0.0)) == "far"


def test_vertical_tilt_makes_the_larger_half_near():
    h = synth.View(yaw=0.0, pitch=25.0).homography(SIZE)
    sides = {geometry.near_far(h, (0.0, 80.0)), geometry.near_far(h, (0.0, -80.0))}
    assert sides == {"near", "far"}
    near_y = 80.0 if geometry.near_far(h, (0.0, 80.0)) == "near" else -80.0
    # the near half is larger in the image: a 10 mm step spans more pixels there
    step = lambda y: abs(synth.project(h, [(0.0, y + 10.0)])[0][1] - synth.project(h, [(0.0, y)])[0][1])
    assert step(near_y) > step(-near_y)


def test_front_view_is_split_consistently():
    h = synth.View(yaw=0.0, pitch=0.0).homography(SIZE)
    first = geometry.near_far(h, (-50.0, 10.0))
    assert first != geometry.near_far(h, (50.0, 10.0))
    assert first == geometry.near_far(h, (-50.0, -90.0))
