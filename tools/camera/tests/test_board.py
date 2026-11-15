"""Board geometry (CV1, CV2), kept in sync with BoardGeometry.kt and the truth convention of capture-format.md."""
import math
import re
from pathlib import Path

import pytest

from kosmo_camera import board

KOTLIN = next(
    parent / "game-engine/src/main/kotlin/io/github/darkcosmos/kosmodarts/engine/board/BoardGeometry.kt"
    for parent in Path(__file__).resolve().parents
    if (parent / "game-engine/src/main/kotlin/io/github/darkcosmos/kosmodarts/engine/board/BoardGeometry.kt").is_file()
)


def _polar(radius, clockwise_degrees):
    angle = math.radians(clockwise_degrees)
    return radius * math.sin(angle), radius * math.cos(angle)


def test_constants_equal_the_kotlin_geometry_cv1():
    source = KOTLIN.read_text(encoding="utf-8")
    consts = {name: float(value) for name, value in re.findall(r"const val (\w+_(?:MM)) = ([0-9.]+)", source)}
    assert consts == {
        "BULL_RADIUS_MM": board.BULL_RADIUS_MM,
        "OUTER_BULL_RADIUS_MM": board.OUTER_BULL_RADIUS_MM,
        "TRIPLE_INNER_MM": board.TRIPLE_INNER_MM,
        "TRIPLE_OUTER_MM": board.TRIPLE_OUTER_MM,
        "DOUBLE_INNER_MM": board.DOUBLE_INNER_MM,
        "DOUBLE_OUTER_MM": board.DOUBLE_OUTER_MM,
    }
    order = re.search(r"SECTOR_ORDER: List<Int> = listOf\(([^)]*)\)", source).group(1)
    assert [int(n) for n in order.split(",")] == list(board.SECTOR_ORDER)
    angle = float(re.search(r"SECTOR_ANGLE_DEGREES = ([0-9.]+)", source).group(1))
    assert angle == board.SECTOR_ANGLE_DEGREES


def test_zone_at_the_centre_of_each_of_the_82_zones():
    seen = set()
    inner_single = (board.OUTER_BULL_RADIUS_MM + board.TRIPLE_INNER_MM) / 2
    outer_single = (board.TRIPLE_OUTER_MM + board.DOUBLE_INNER_MM) / 2
    for index, segment in enumerate(board.SECTOR_ORDER):
        degrees = index * board.SECTOR_ANGLE_DEGREES
        for radius, multiplier in (
            (inner_single, "SINGLE"),
            (outer_single, "SINGLE"),
            ((board.TRIPLE_INNER_MM + board.TRIPLE_OUTER_MM) / 2, "TRIPLE"),
            ((board.DOUBLE_INNER_MM + board.DOUBLE_OUTER_MM) / 2, "DOUBLE"),
        ):
            zone = board.zone_at(*_polar(radius, degrees))
            assert zone == (segment, multiplier)
            seen.add((segment, multiplier, radius))
    assert len(seen) == 80
    assert board.zone_at(10.0, 0.0) == (25, "SINGLE")
    assert board.zone_at(0.0, 0.0) == (50, "SINGLE")


def test_20_is_on_top_and_6_on_the_right():
    assert board.zone_at(0.0, 130.0) == (20, "SINGLE")
    assert board.zone_at(130.0, 0.0)[0] == 6
    assert board.zone_at(0.0, -130.0)[0] == 3
    assert board.zone_at(-130.0, 0.0)[0] == 11


@pytest.mark.parametrize("radius, expected", [
    (board.BULL_RADIUS_MM - 1e-6, (50, "SINGLE")),
    (board.BULL_RADIUS_MM, (25, "SINGLE")),
    (board.OUTER_BULL_RADIUS_MM - 1e-6, (25, "SINGLE")),
    (board.OUTER_BULL_RADIUS_MM, (20, "SINGLE")),
    (board.TRIPLE_INNER_MM - 1e-6, (20, "SINGLE")),
    (board.TRIPLE_INNER_MM, (20, "TRIPLE")),
    (board.TRIPLE_OUTER_MM - 1e-6, (20, "TRIPLE")),
    (board.TRIPLE_OUTER_MM, (20, "SINGLE")),
    (board.DOUBLE_INNER_MM - 1e-6, (20, "SINGLE")),
    (board.DOUBLE_INNER_MM, (20, "DOUBLE")),
    (170.0, (20, "DOUBLE")),
    (170.0001, (0, "SINGLE")),
])
def test_ring_borders_belong_to_the_zone_they_start_bc5(radius, expected):
    assert board.zone_at(0.0, radius) == expected


def test_outside_the_board_is_segment_zero():
    assert board.zone_at(500.0, 0.0) == (0, "SINGLE")
    assert board.zone_at(0.0, -170.0001) == (0, "SINGLE")


def test_sector_edge_belongs_to_the_sector_it_starts_clockwise_bc5():
    # The edge at 45 degrees clockwise from the top (x == y) starts the sector 4 (index 3), ends the 18.
    assert board.zone_at(100.0, 100.0)[0] == 4
    assert board.zone_at(100.0, 100.0001)[0] == 18
    assert board.zone_at(100.0001, 100.0)[0] == 4


def test_every_sector_edge_is_in_exactly_one_adjacent_sector():
    count = len(board.SECTOR_ORDER)
    for k in range(count):
        edge = 9.0 + k * board.SECTOR_ANGLE_DEGREES
        before = board.SECTOR_ORDER[k]
        after = board.SECTOR_ORDER[(k + 1) % count]
        assert board.zone_at(*_polar(130.0, edge))[0] in (before, after)
        assert board.zone_at(*_polar(130.0, edge - 0.01))[0] == before
        assert board.zone_at(*_polar(130.0, edge + 0.01))[0] == after


def test_calibration_points_are_on_the_double_outer_edge_between_two_sectors_cv2():
    points = board.CALIBRATION_POINTS_MM
    assert len(points) == 4
    wires = [(5, 20), (13, 6), (17, 3), (8, 11)]
    expected_math_degrees = [99, 9, 279, 189]
    for (x, y), (a, b), degrees in zip(points, wires, expected_math_degrees):
        assert math.hypot(x, y) == pytest.approx(170.0)
        assert math.degrees(math.atan2(y, x)) % 360 == pytest.approx(degrees)
        # On the wire: clockwise angle from the top is a sector edge, between the sectors a and b.
        clockwise = math.degrees(math.atan2(x, y)) % 360
        k = round((clockwise - 9.0) / board.SECTOR_ANGLE_DEGREES)
        assert clockwise == pytest.approx(9.0 + k * board.SECTOR_ANGLE_DEGREES)
        pair = {board.SECTOR_ORDER[k % 20], board.SECTOR_ORDER[(k + 1) % 20]}
        assert pair == {a, b}
