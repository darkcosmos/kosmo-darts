"""Strict reader of ``config/defaults.json``: the single source of the tool constants (decision D9, A1).

Stdlib only. Other modules read constants from here and never repeat the values.
"""
import json
from pathlib import Path

DEFAULTS_PATH = Path(__file__).resolve().parent.parent / "config" / "defaults.json"

# Expected type of every key; an integer key refuses floats, a float key accepts integers, booleans are refused.
SCHEMA = {
    "ANGLE_FRONT_MAX_DEGREES": float,
    "ANGLE_OBLIQUE_MAX_DEGREES": float,
    "BOX_FRACTION": float,
    "CAL_MAX_ERROR_MM": float,
    "DISTANCE_FAR_MAX": float,
    "DISTANCE_MID_MAX": float,
    "LIGHT_DARK_BELOW": float,
    "LIGHT_MEDIUM_MAX": float,
    "MATCH_RADIUS_MM": float,
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
    if expected is float:
        return isinstance(value, (int, float))
    return isinstance(value, int)


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
    return {key: expected(data[key]) for key, expected in SCHEMA.items()}


_VALUES = load_defaults()

ANGLE_FRONT_MAX_DEGREES = _VALUES["ANGLE_FRONT_MAX_DEGREES"]
ANGLE_OBLIQUE_MAX_DEGREES = _VALUES["ANGLE_OBLIQUE_MAX_DEGREES"]
BOX_FRACTION = _VALUES["BOX_FRACTION"]
CAL_MAX_ERROR_MM = _VALUES["CAL_MAX_ERROR_MM"]
DISTANCE_FAR_MAX = _VALUES["DISTANCE_FAR_MAX"]
DISTANCE_MID_MAX = _VALUES["DISTANCE_MID_MAX"]
LIGHT_DARK_BELOW = _VALUES["LIGHT_DARK_BELOW"]
LIGHT_MEDIUM_MAX = _VALUES["LIGHT_MEDIUM_MAX"]
MATCH_RADIUS_MM = _VALUES["MATCH_RADIUS_MM"]
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
