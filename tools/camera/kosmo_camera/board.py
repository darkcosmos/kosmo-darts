"""Board geometry in millimetres (rules CV1, CV2), stdlib only.

Same values and border ownership as ``game-engine/.../engine/board/BoardGeometry.kt`` (rules BC1, BC5); a test parses
the Kotlin file to stay in sync. Origin at the bull centre, y towards the 20, x towards the 6.
"""
import math

BULL_RADIUS_MM = 6.35
OUTER_BULL_RADIUS_MM = 15.9
TRIPLE_INNER_MM = 99.0
TRIPLE_OUTER_MM = 107.0
DOUBLE_INNER_MM = 162.0
DOUBLE_OUTER_MM = 170.0

# Numbers of the sectors, clockwise from the top (the 20 is centred on the +y axis).
SECTOR_ORDER = (20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5)
SECTOR_ANGLE_DEGREES = 18.0

_FULL_TURN_DEGREES = 360.0

# CV2: the 4 corners where the outer edge of the double ring meets the wires 5|20, 13|6, 17|3 and 8|11, in this order
# (mathematical angles 99, 9, 279 and 189 degrees).
CALIBRATION_POINTS_MM = tuple(
    (DOUBLE_OUTER_MM * math.cos(math.radians(angle)), DOUBLE_OUTER_MM * math.sin(math.radians(angle)))
    for angle in (99.0, 9.0, 279.0, 189.0)
)


def _sector_index(x_mm, y_mm):
    """Index in SECTOR_ORDER; sector edges are at 9 + 18k degrees clockwise from the top, lower edge inclusive."""
    degrees = (math.degrees(math.atan2(x_mm, y_mm)) + _FULL_TURN_DEGREES) % _FULL_TURN_DEGREES
    shifted = degrees + SECTOR_ANGLE_DEGREES / 2
    return math.floor(shifted / SECTOR_ANGLE_DEGREES) % len(SECTOR_ORDER)


def zone_at(x_mm, y_mm):
    """Return ``(segment, multiplier)`` under the point (rule BC5): segment 0 outside the double ring, 25 and 50 for
    the bulls (multiplier "SINGLE" for 0, 25 and 50). Every inner limit belongs to the zone it starts; the outer limit
    of the double ring (170 mm) is the only inclusive one."""
    radius = math.hypot(x_mm, y_mm)
    if radius < BULL_RADIUS_MM:
        return 50, "SINGLE"
    if radius < OUTER_BULL_RADIUS_MM:
        return 25, "SINGLE"
    if radius > DOUBLE_OUTER_MM:
        return 0, "SINGLE"
    if TRIPLE_INNER_MM <= radius < TRIPLE_OUTER_MM:
        multiplier = "TRIPLE"
    elif radius >= DOUBLE_INNER_MM:
        multiplier = "DOUBLE"
    else:
        multiplier = "SINGLE"
    return SECTOR_ORDER[_sector_index(x_mm, y_mm)], multiplier
