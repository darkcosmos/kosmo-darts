"""Control sample and roles (CV26, CV15): stdlib only, no image."""
import json
from types import SimpleNamespace

import pytest

from kosmo_camera import annotations as an, control, split as sp
from kosmo_camera.split import Split, SplitError, Unit


def _dart(number, voided=False, photo=True):
    return SimpleNamespace(number=number, voided=voided, judged=not voided and photo,
                           photo=SimpleNamespace(file=f"dart-{number:03d}.jpg") if photo else None)


def _session(count, per_volley=3, voided=(), photoless=()):
    darts = [_dart(n, n in voided, n not in photoless) for n in range(1, count + 1)]
    volleys = [SimpleNamespace(darts=tuple(darts[i:i + per_volley])) for i in range(0, count, per_volley)]
    return SimpleNamespace(volleys=tuple(volleys))


def _annotations(*photos):
    segments = tuple(an.Calibration(1 + i, p, 4080, 3072, ((1.0, 1.0),) * 4) for i, p in enumerate(photos))
    return SimpleNamespace(calibrations=segments)


def test_eval_session_samples_every_judged_dart_and_video_55_to_60_cv26():
    s = control.sample(_session(70, voided=(57,), photoless=(3,)), None, control.EVAL)
    assert s.photo_darts == tuple(n for n in range(1, 71) if n not in (3, 57))
    assert s.video_darts == (55, 56, 58, 59, 60)


def test_eval_video_window_is_by_dart_number_not_by_judged_rank_cv26():
    # dart 10 is voided: dart 55 is the 54th judged dart, yet the window is still the darts numbered 55 to 60
    s = control.sample(_session(70, voided=(10,)), None, control.EVAL)
    assert s.video_darts == (55, 56, 57, 58, 59, 60)
    assert s.photo_darts == tuple(n for n in range(1, 71) if n != 10)


def test_short_eval_session_has_no_video_control_cv26():
    assert control.sample(_session(40), None, control.EVAL).video_darts == ()


def test_dev_session_samples_ranks_1_11_21_and_the_last_cv26():
    s = control.sample(_session(25), None, control.DEV)
    assert s.photo_darts == (1, 11, 21, 25)
    assert s.video_darts == s.photo_darts  # the video sample is the dev photo sample


def test_dev_rank_counts_judged_darts_only_cv26():
    s = control.sample(_session(14, voided=(1, 2), photoless=(5,)), None, control.DEV)
    assert s.photo_darts == (3, 14)  # ranks 1 and 11 are darts 3 and 14, which is also the last


def test_dev_last_dart_is_not_repeated_when_it_is_a_stride_rank_cv26():
    assert control.sample(_session(21), None, control.DEV).photo_darts == (1, 11, 21)


def test_segment_reference_photos_are_added_cv26():
    s = control.sample(_session(25), _annotations("dart-001.jpg", "dart-014.jpg"), control.DEV)
    assert s.photo_darts == (1, 11, 14, 21, 25) and s.video_darts == s.photo_darts


def test_segment_photo_of_a_voided_dart_is_never_sampled_cv26():
    s = control.sample(_session(25, voided=(14,)), _annotations("dart-014.jpg"), control.DEV)
    assert 14 not in s.photo_darts


def test_session_without_judged_dart_has_an_empty_sample_and_same_output_twice():
    empty = _session(3, voided=(1, 2, 3))
    assert control.sample(empty, None, control.DEV) == control.Sample((), ())
    assert control.sample(_session(25), None, control.DEV) == control.sample(_session(25), None, control.DEV)


def test_unknown_set_is_refused():
    with pytest.raises(ValueError):
        control.sample(_session(3), None, "other")


def _split(**sets):
    units = tuple(Unit(name, None, None, set_name) for set_name, names in sets.items() for name in names)
    counts = {s: {"units": sum(1 for u in units if u.set == s), "darts": 0} for s in sp.SETS}
    counts["ignoredSessions"] = 0
    return Split("session", units, counts)


def test_roles_follow_the_cv3_key_order_not_the_name_order_cv15():
    names = ["20261101-100000", "20261102-100000", "20261103-100000", "20261104-100000"]
    result = control.roles(_split(dev=names[:2], eval=names[2:]))
    for set_name, group in (("dev", names[:2]), ("eval", names[2:])):
        by_key = sorted(group, key=sp.unit_key)
        assert [result[n] for n in by_key] == [control.Role(set_name, 1), control.Role(set_name, 2)]
    assert sorted(role.label for role in result.values()) == ["dev 1", "dev 2", "eval 1", "eval 2"]


def test_roles_refuse_a_block_split():
    block = Split("block", (Unit("20261101-100000", 1, 5, "dev"),), {})
    with pytest.raises(SplitError):
        control.roles(block)


def _files(tmp_path, split):
    data = sp.encode(split)
    (tmp_path / "split.json").write_bytes(data)
    (tmp_path / "lock.json").write_bytes(sp.make_lock(data))
    return tmp_path / "split.json", tmp_path / "lock.json"


def test_read_split_returns_the_locked_split(tmp_path):
    split = _split(dev=["20261101-100000"], eval=["20261102-100000"])
    split_path, lock_path = _files(tmp_path, split)
    assert control.read_split(split_path, lock_path).units == split.units


def test_read_split_refuses_a_split_that_differs_from_the_lock_cv3(tmp_path):
    split_path, lock_path = _files(tmp_path, _split(dev=["20261101-100000"], eval=["20261102-100000"]))
    other = _split(dev=["20261102-100000"], eval=["20261101-100000"])
    split_path.write_bytes(sp.encode(other))
    with pytest.raises(SplitError, match="does not match the lock"):
        control.read_split(split_path, lock_path)


def test_read_split_reports_a_missing_file_as_a_split_error(tmp_path):
    with pytest.raises(SplitError):
        control.read_split(tmp_path / "absent.json", tmp_path / "lock.json")
    (tmp_path / "split.json").write_text(json.dumps({}), encoding="utf-8")
    with pytest.raises(SplitError):
        control.read_split(tmp_path / "split.json", tmp_path / "absent-lock.json")
