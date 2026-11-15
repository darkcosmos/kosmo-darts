"""Groups of CV14: tripod angle, side and distance, lighting class, zoom bin (docs/camera/training.md "Groups")."""
import pytest

pytest.importorskip("av")
pytest.importorskip("PIL")

from kosmo_camera import config, geometry, groups, synth  # noqa: E402  (after the importorskip above, on purpose)
from kosmo_camera.board import CALIBRATION_POINTS_MM  # noqa: E402

SIZE = (640, 480)


def _cal(yaw=0.0, pitch=0.0, scale=1.0):
    pixels = synth.project(synth.View(yaw=yaw, pitch=pitch).homography(SIZE), CALIBRATION_POINTS_MM)
    cx, cy = SIZE[0] / 2, SIZE[1] / 2
    return [(cx + (x - cx) * scale, cy + (y - cy) * scale) for x, y in pixels]


@pytest.mark.parametrize("yaw, angle, side", [
    (0.0, "front", None), (25.0, "oblique", "right"), (-25.0, "oblique", "left"),
    (45.0, "steep", "right"), (-45.0, "steep", "left"),
])
def test_tripod_angle_and_side_follow_the_yaw(yaw, angle, side):
    group = groups.tripod_group(_cal(yaw), SIZE, 1.0)
    assert (group.angle, group.side) == (angle, side)


def test_the_side_is_where_the_camera_is_above_or_below():
    # synth: a positive pitch puts the camera below the board centre, so the bottom of the board is the near side
    group = groups.tripod_group(_cal(pitch=25.0), SIZE, 1.0)
    assert (group.angle, group.side) == ("oblique", "below")
    assert groups.tripod_group(_cal(pitch=-25.0), SIZE, 1.0).side == "above"


@pytest.mark.parametrize("scale, distance", [(1.2, "near"), (0.8, "mid"), (0.4, "far")])
def test_distance_follows_the_apparent_size(scale, distance):
    # a front view fills 0.7 of the short side at scale 1
    assert groups.tripod_group(_cal(scale=scale), SIZE, 1.0).distance == distance


def test_zoom_2_halves_the_apparent_distance_ratio_and_null_zoom_counts_as_1():
    cal = _cal(scale=1.2)  # ratio 0.84
    assert groups.tripod_group(cal, SIZE, 1.0).distance == "near"
    assert groups.tripod_group(cal, SIZE, 2.0).distance == "mid"  # 0.42
    assert groups.tripod_group(cal, SIZE, 4.0).distance == "far"  # 0.21
    assert groups.tripod_group(cal, SIZE, None) == groups.tripod_group(cal, SIZE, 1.0)
    assert groups.tripod_group(cal, SIZE, 0) == groups.tripod_group(cal, SIZE, 1.0)


@pytest.mark.parametrize("zoom, expected", [
    (None, "1x"), (1.0, "1x"), (1.49, "1x"), (1.5, "2x"), (2.0, "2x"), (3.0, "2x"), (3.01, "zoom"), (8.0, "zoom"),
])
def test_zoom_bins(zoom, expected):
    assert groups.zoom_bin(zoom) == expected


def test_bin_limits_come_from_the_config():
    assert groups._angle_bin(config.ANGLE_FRONT_MAX_DEGREES - 0.01) == "front"
    assert groups._angle_bin(config.ANGLE_FRONT_MAX_DEGREES) == "oblique"
    assert groups._angle_bin(config.ANGLE_OBLIQUE_MAX_DEGREES) == "oblique"
    assert groups._angle_bin(config.ANGLE_OBLIQUE_MAX_DEGREES + 0.01) == "steep"
    assert groups._distance_bin(config.DISTANCE_FAR_MAX - 0.01) == "far"
    assert groups._distance_bin(config.DISTANCE_FAR_MAX) == "mid"
    assert groups._distance_bin(config.DISTANCE_MID_MAX) == "mid"
    assert groups._distance_bin(config.DISTANCE_MID_MAX + 0.01) == "near"


@pytest.mark.parametrize("brightness, expected", [(0.3, "dark"), (1.0, "medium"), (2.0, "bright")])
def test_lighting_class_of_rendered_boards(synthetic_board, brightness, expected):
    view = synth.View()
    image, truth = synth.render_board(view, [], SIZE, synth.Lighting(brightness=brightness), 0)
    assert groups.lighting_class(image, truth.homography) == expected


def test_lighting_class_uses_only_the_board_pixels(synthetic_board):
    image, truth = synthetic_board()
    corrupted = image.copy()
    corrupted[:20, :] = 255  # a bright strip of wall outside the circle
    assert groups.lighting_class(corrupted, truth.homography) == groups.lighting_class(image, truth.homography)


def test_lighting_class_edges(monkeypatch):
    h = synth.View().homography(SIZE)
    grey = lambda value: __import__("numpy").full((SIZE[1], SIZE[0]), value, dtype="uint8")
    assert groups.lighting_class(grey(69), h) == "dark"
    assert groups.lighting_class(grey(70), h) == "medium"
    assert groups.lighting_class(grey(140), h) == "medium"
    assert groups.lighting_class(grey(141), h) == "bright"
    with pytest.raises(ValueError):
        groups.lighting_class(grey(100), geometry.homography(CALIBRATION_POINTS_MM, [(x + 5000, y) for x, y in _cal()]))
