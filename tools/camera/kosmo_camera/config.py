"""Strict reader of ``config/defaults.json``: the single source of the tool constants (decision D9, A1).

Stdlib only. Other modules read constants from here and never repeat the values.
"""
import json
from pathlib import Path

DEFAULTS_PATH = Path(__file__).resolve().parent.parent / "config" / "defaults.json"

# Expected type of every key; an integer key refuses floats, a float key accepts integers, booleans are refused.
# `tuple` is a non-empty list of numbers (read as a tuple of floats).
SCHEMA = {
    "ANGLE_FRONT_MAX_DEGREES": float,
    "ANGLE_OBLIQUE_MAX_DEGREES": float,
    "BOX_FRACTION": float,
    "CAL_MAX_ERROR_MM": float,
    "DISTANCE_FAR_MAX": float,
    "DISTANCE_MID_MAX": float,
    "DRIFT_CANDIDATES_MM": tuple,
    "DRIFT_COARSE_PX": int,
    "DRIFT_MAX_MM": float,
    "DRIFT_MIN_PEAK": float,
    "DRIFT_PATCH_FRACTION": float,
    "LIGHT_DARK_BELOW": float,
    "LIGHT_MEDIUM_MAX": float,
    "MATCH_RADIUS_MM": float,
    "SNAP_CONVERGE_PX": float,
    "SNAP_MAX_ITER": int,
    "SNAP_MAX_RESIDUAL_PX": float,
    "SNAP_MIN_ANGLE_DEG": float,
    "SNAP_MIN_CONTRAST": float,
    "SNAP_MIN_SUPPORT_PX": int,
    "SNAP_RADIUS_PX": float,
    "SNAP_REPEAT_DIRECTIONS": int,
    "SNAP_REPEAT_MAX_MM": float,
    "SNAP_REPEAT_OFFSET_PX": float,
    "SNAP_SIGMAS_PX": tuple,
    "SNAP_WINDOW_PX": int,
    "SPLIT_ACCEPTED_MAX": float,
    "SPLIT_ACCEPTED_MIN": float,
    "SPLIT_BLOCK_VOLLEYS": int,
    "SPLIT_MIN_EVAL_DARTS": int,
    "SPLIT_MIN_SESSIONS_PER_SET": int,
    "SPLIT_TARGET_EVAL_SHARE": float,
    "TAU_LOW": float,
    "TAU_SURE": float,
    "VALID_RATIO": float,
    "ZOOM_1X_BELOW": float,
    "ZOOM_2X_MAX": float,
}


class ConfigError(ValueError):
    """The defaults file does not match the schema."""


def _type_ok(value, expected):
    if isinstance(value, bool):
        return False
    if expected is tuple:
        return isinstance(value, list) and bool(value) and all(_type_ok(item, float) for item in value)
    if expected is float:
        return isinstance(value, (int, float))
    return isinstance(value, int)


def _convert(value, expected):
    if expected is tuple:
        return tuple(float(item) for item in value)
    return expected(value)


def load_defaults(path=DEFAULTS_PATH):
    """Return the validated values; raise ConfigError on an unknown key, a missing key or a wrong type."""
    with open(path, encoding="utf-8") as handle:
        data = json.load(handle)
    if not isinstance(data, dict):
        raise ConfigError("defaults must be a JSON object")
    unknown = sorted(set(data) - set(SCHEMA))
    if unknown:
        raise ConfigError(f"unknown key: {', '.join(unknown)}")
    missing = sorted(set(SCHEMA) - set(data))
    if missing:
        raise ConfigError(f"missing key: {', '.join(missing)}")
    for key, expected in SCHEMA.items():
        if not _type_ok(data[key], expected):
            raise ConfigError(f"wrong type for {key}: expected {expected.__name__}")
    return {key: _convert(data[key], expected) for key, expected in SCHEMA.items()}


_VALUES = load_defaults()

ANGLE_FRONT_MAX_DEGREES = _VALUES["ANGLE_FRONT_MAX_DEGREES"]
ANGLE_OBLIQUE_MAX_DEGREES = _VALUES["ANGLE_OBLIQUE_MAX_DEGREES"]
BOX_FRACTION = _VALUES["BOX_FRACTION"]
CAL_MAX_ERROR_MM = _VALUES["CAL_MAX_ERROR_MM"]
DISTANCE_FAR_MAX = _VALUES["DISTANCE_FAR_MAX"]
DISTANCE_MID_MAX = _VALUES["DISTANCE_MID_MAX"]
DRIFT_CANDIDATES_MM = _VALUES["DRIFT_CANDIDATES_MM"]
DRIFT_COARSE_PX = _VALUES["DRIFT_COARSE_PX"]
DRIFT_MAX_MM = _VALUES["DRIFT_MAX_MM"]
DRIFT_MIN_PEAK = _VALUES["DRIFT_MIN_PEAK"]
DRIFT_PATCH_FRACTION = _VALUES["DRIFT_PATCH_FRACTION"]
LIGHT_DARK_BELOW = _VALUES["LIGHT_DARK_BELOW"]
LIGHT_MEDIUM_MAX = _VALUES["LIGHT_MEDIUM_MAX"]
MATCH_RADIUS_MM = _VALUES["MATCH_RADIUS_MM"]
SNAP_CONVERGE_PX = _VALUES["SNAP_CONVERGE_PX"]
SNAP_MAX_ITER = _VALUES["SNAP_MAX_ITER"]
SNAP_MAX_RESIDUAL_PX = _VALUES["SNAP_MAX_RESIDUAL_PX"]
SNAP_MIN_ANGLE_DEG = _VALUES["SNAP_MIN_ANGLE_DEG"]
SNAP_MIN_CONTRAST = _VALUES["SNAP_MIN_CONTRAST"]
SNAP_MIN_SUPPORT_PX = _VALUES["SNAP_MIN_SUPPORT_PX"]
SNAP_RADIUS_PX = _VALUES["SNAP_RADIUS_PX"]
SNAP_REPEAT_DIRECTIONS = _VALUES["SNAP_REPEAT_DIRECTIONS"]
SNAP_REPEAT_MAX_MM = _VALUES["SNAP_REPEAT_MAX_MM"]
SNAP_REPEAT_OFFSET_PX = _VALUES["SNAP_REPEAT_OFFSET_PX"]
SNAP_SIGMAS_PX = _VALUES["SNAP_SIGMAS_PX"]
SNAP_WINDOW_PX = _VALUES["SNAP_WINDOW_PX"]
SPLIT_ACCEPTED_MAX = _VALUES["SPLIT_ACCEPTED_MAX"]
SPLIT_ACCEPTED_MIN = _VALUES["SPLIT_ACCEPTED_MIN"]
SPLIT_BLOCK_VOLLEYS = _VALUES["SPLIT_BLOCK_VOLLEYS"]
SPLIT_MIN_EVAL_DARTS = _VALUES["SPLIT_MIN_EVAL_DARTS"]
SPLIT_MIN_SESSIONS_PER_SET = _VALUES["SPLIT_MIN_SESSIONS_PER_SET"]
SPLIT_TARGET_EVAL_SHARE = _VALUES["SPLIT_TARGET_EVAL_SHARE"]
TAU_LOW = _VALUES["TAU_LOW"]
TAU_SURE = _VALUES["TAU_SURE"]
VALID_RATIO = _VALUES["VALID_RATIO"]
ZOOM_1X_BELOW = _VALUES["ZOOM_1X_BELOW"]
ZOOM_2X_MAX = _VALUES["ZOOM_2X_MAX"]
