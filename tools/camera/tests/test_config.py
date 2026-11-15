"""Config reader (decision D9, A1): strict, one source of truth in config/defaults.json."""
import json

import pytest

from kosmo_camera import config


def _write(tmp_path, data):
    path = tmp_path / "defaults.json"
    path.write_text(json.dumps(data), encoding="utf-8")
    return path


def _valid():
    return json.loads(config.DEFAULTS_PATH.read_text(encoding="utf-8"))


def test_committed_defaults_load_and_are_exposed_as_constants():
    values = config.load_defaults()
    assert set(values) == set(config.SCHEMA)
    assert config.SPLIT_BLOCK_VOLLEYS == 5
    assert config.BOX_FRACTION == 0.025
    assert config.VALID_RATIO == 0.15
    assert config.MATCH_RADIUS_MM == 6.0
    assert config.CAL_MAX_ERROR_MM == 2.0
    assert config.TAU_LOW == 0.2
    assert config.TAU_SURE == 0.5
    assert config.SPLIT_TARGET_EVAL_SHARE == 0.30
    assert (config.SPLIT_ACCEPTED_MIN, config.SPLIT_ACCEPTED_MAX) == (0.25, 0.35)
    assert config.SPLIT_MIN_SESSIONS_PER_SET == 2
    assert config.SPLIT_MIN_EVAL_DARTS == 100
    # docs/camera/training.md "Groups"
    assert (config.LIGHT_DARK_BELOW, config.LIGHT_MEDIUM_MAX) == (70.0, 140.0)
    assert (config.ANGLE_FRONT_MAX_DEGREES, config.ANGLE_OBLIQUE_MAX_DEGREES) == (15.0, 35.0)
    assert (config.DISTANCE_FAR_MAX, config.DISTANCE_MID_MAX) == (0.35, 0.6)
    assert (config.ZOOM_1X_BELOW, config.ZOOM_2X_MAX) == (1.5, 3.0)


def test_unknown_key_raises(tmp_path):
    data = _valid()
    data["EXTRA"] = 1
    with pytest.raises(config.ConfigError, match="EXTRA"):
        config.load_defaults(_write(tmp_path, data))


def test_missing_key_raises(tmp_path):
    data = _valid()
    del data["TAU_LOW"]
    with pytest.raises(config.ConfigError, match="TAU_LOW"):
        config.load_defaults(_write(tmp_path, data))


@pytest.mark.parametrize("key, bad", [
    ("SPLIT_BLOCK_VOLLEYS", 5.0),
    ("SPLIT_BLOCK_VOLLEYS", True),
    ("TAU_SURE", "0.5"),
    ("TAU_SURE", True),
    ("MATCH_RADIUS_MM", None),
])
def test_wrong_type_raises(tmp_path, key, bad):
    data = _valid()
    data[key] = bad
    with pytest.raises(config.ConfigError, match=key):
        config.load_defaults(_write(tmp_path, data))


def test_not_an_object_raises(tmp_path):
    with pytest.raises(config.ConfigError):
        config.load_defaults(_write(tmp_path, [1, 2]))
