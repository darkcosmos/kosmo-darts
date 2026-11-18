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
    # Drift (CV18, CV19, D18): the one place that pins DRIFT_MAX_MM, so that a change is intentional.
    assert config.DRIFT_MAX_MM == 1.0 and config.DRIFT_MAX_MM == config.CAL_MAX_ERROR_MM / 2
    assert config.DRIFT_CANDIDATES_MM == (0.5, 1.0, 2.0)
    assert (config.DRIFT_COARSE_PX, config.DRIFT_PATCH_FRACTION, config.DRIFT_MIN_PEAK) == (512, 0.06, 0.2)
    # Control sample and video frames (CV26, CV27): the one place that pins them.
    assert (config.CONTROL_DEV_STRIDE, config.CONTROL_EVAL_VIDEO_FIRST, config.CONTROL_EVAL_VIDEO_LAST) == (10, 55, 60)
    assert (config.VIDEO_BEFORE_ENTRY_MS, config.VIDEO_MAX_DECODED_FRAMES) == (1000, 20000)
    assert config.VIDEO_MAX_BYTES == 512 * 1024 * 1024
    # Assisted click (CV31, D43): the one place that pins the SNAP_* constants (initial values of the root plan).
    assert (config.SNAP_RADIUS_PX, config.SNAP_WINDOW_PX, config.SNAP_SIGMAS_PX) == (10.0, 16, (1.0, 2.0, 3.0))
    assert (config.SNAP_MIN_ANGLE_DEG, config.SNAP_MIN_SUPPORT_PX, config.SNAP_MIN_CONTRAST) == (30.0, 8, 4.0)
    assert (config.SNAP_MAX_RESIDUAL_PX, config.SNAP_CONVERGE_PX, config.SNAP_MAX_ITER) == (0.75, 0.05, 5)
    assert (config.SNAP_REPEAT_DIRECTIONS, config.SNAP_REPEAT_OFFSET_PX, config.SNAP_REPEAT_MAX_MM) == (8, 3.0, 0.25)
    # Tool flow of the assisted click (CV31, T15n): images kept decoded, click replacement radius in source px.
    assert (config.SNAP_CACHE_IMAGES, config.CLICK_REPLACE_RADIUS_PX) == (2, 100.0)
    # Snapping diagnosis (CV32, D54, T17a): radius sweep factors and lattice starts per photo control point.
    assert (config.SNAP_SWEEP_FACTORS, config.SNAP_LATTICE_STARTS) == ((1.0, 2.0, 4.0), 4)


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


@pytest.mark.parametrize("bad", [[], 1.0, "0.5", [0.5, "1"], [0.5, True], [0.5, None]])
def test_candidate_list_must_be_a_non_empty_list_of_numbers(tmp_path, bad):
    data = _valid()
    data["DRIFT_CANDIDATES_MM"] = bad
    with pytest.raises(config.ConfigError, match="DRIFT_CANDIDATES_MM"):
        config.load_defaults(_write(tmp_path, data))


def test_candidate_list_is_read_as_a_tuple_of_floats(tmp_path):
    data = _valid()
    data["DRIFT_CANDIDATES_MM"] = [1, 2.5]
    assert config.load_defaults(_write(tmp_path, data))["DRIFT_CANDIDATES_MM"] == (1.0, 2.5)


def test_not_an_object_raises(tmp_path):
    with pytest.raises(config.ConfigError):
        config.load_defaults(_write(tmp_path, [1, 2]))
