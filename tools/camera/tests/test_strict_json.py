"""Shared strict JSON helpers (CV25, CV16, CV26): stdlib only."""
import json

import pytest

from kosmo_camera import annotations, drift_file, frames_file, strict_json


def test_duplicate_key_and_non_finite_constant_are_refused():
    with pytest.raises(strict_json.StrictJsonError):
        json.loads('{"a": 1, "a": 2}', object_pairs_hook=strict_json.no_duplicates)
    with pytest.raises(strict_json.StrictJsonError):
        json.loads('{"a": NaN}', parse_constant=strict_json.reject_constant)


def test_depth_counts_nesting_without_recursion():
    assert strict_json.depth({"a": [1, {"b": []}]}) == 4
    assert strict_json.depth(1) == 0
    assert strict_json.depth([[[[]]]] * 3) == 4


@pytest.mark.parametrize("reader,error", [(annotations.decode, annotations.AnnotationError),
                                          (drift_file.decode, drift_file.DriftFileError),
                                          (frames_file.decode, frames_file.FramesFileError)])
def test_each_reader_turns_the_shared_errors_into_its_own(reader, error):
    for text in ('{"a": 1, "a": 2}', '{"a": NaN}'):
        with pytest.raises(error):
            reader(text)


def test_one_public_session_name_pattern_serves_the_readers():
    assert annotations.SESSION_NAME.fullmatch("20261031-140322-2")
    assert not annotations.SESSION_NAME.fullmatch("20261031-140322-1")
    assert frames_file.SESSION_NAME is annotations.SESSION_NAME is drift_file.SESSION_NAME
