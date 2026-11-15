"""Dev/eval split (CV3, CV4, CV5, CV15, CV17) on generated sessions only."""
import json
import random
from pathlib import Path

import pytest

from kosmo_camera import split as sp
from kosmo_camera.split import SessionInput, SplitError


def make(name, darts, per_volley=3, path=None):
    """A session with `darts` judged darts in volleys of up to `per_volley`."""
    volleys, index = [], 1
    while darts > 0:
        n = min(per_volley, darts)
        volleys.append((index, n))
        darts -= n
        index += 1
    return SessionInput(name, tuple(volleys), path)


def ten(darts=51):
    return [make("s%02d" % i, darts) for i in range(10)]


def share(split):
    c = split.counts
    return c["eval"]["darts"] / (c["eval"]["darts"] + c["dev"]["darts"])


def sessions_in(split, which):
    return {u.session for u in split.units if u.set == which}


def test_determinism_any_order_same_bytes_cv3():
    base = ten()
    shuffled = list(base)
    random.Random(7).shuffle(shuffled)
    assert sp.encode(sp.compute_split(base)) == sp.encode(sp.compute_split(shuffled))


def test_key_of_a_session_does_not_depend_on_the_others_cv3():
    assert sp.unit_key(make("a", 5)) == sp.unit_key("a")
    assert sp.unit_key("a") == sp.unit_key("a", None)
    assert sp.unit_key("a") != sp.unit_key("a", 0)
    assert sp.unit_key("a", 0) != sp.unit_key("a", 1)


def test_session_mode_on_ten_sessions_cv3():
    split = sp.compute_split(ten(50))
    assert split.mode == "session"
    assert 0.25 <= share(split) <= 0.35
    assert len(sessions_in(split, "eval")) >= 2 and len(sessions_in(split, "dev")) >= 2
    assert split.counts["ignoredSessions"] == 0


@pytest.mark.parametrize("sessions", [
    [make("a", 60), make("b", 60), make("c", 60)],
    [make("big", 500)] + [make("s%d" % i, 10) for i in range(5)],
    [make("s%d" % i, 8) for i in range(10)],
])
def test_fallback_to_block_mode_cv3(sessions):
    split = sp.compute_split(sessions)
    assert split.mode == "block"
    assert any(not ok for _, ok in split.session_criteria)
    assert all(u.first_volley is not None for u in split.units)


def test_blocks_of_five_volleys_last_shorter_and_each_volley_in_one_block():
    session = make("a", 33)  # 11 volleys of 3 darts
    split = sp.compute_split([session, make("b", 33)])
    ranges = sorted((u.first_volley, u.last_volley) for u in split.units if u.session == "a")
    assert ranges == [(1, 5), (6, 10), (11, 11)]
    for volley in range(1, 12):
        assert sp.set_of("a", volley, split) in ("dev", "eval")
        assert sum(1 for u in split.units if u.session == "a"
                   and u.first_volley <= volley <= u.last_volley) == 1


def test_boundary_share_exactly_a_quarter_and_35_percent_accepted():
    # Counts built directly through the criteria helper; totals of 1000 keep eval above 100 darts.
    for eval_darts in (250, 350):
        units = [sp.Unit("e1", None, None, "eval", eval_darts // 2),
                 sp.Unit("e2", None, None, "eval", eval_darts - eval_darts // 2),
                 sp.Unit("d1", None, None, "dev", 500),
                 sp.Unit("d2", None, None, "dev", 1000 - eval_darts - 500)]
        counts = sp._counts(units, 0)
        assert counts["eval"]["darts"] / 1000 in (0.25, 0.35)
        assert all(ok for _, ok in sp._criteria(units, counts))


def test_boundary_exactly_two_sessions_per_set_accepted():
    units = [sp.Unit("e1", None, None, "eval", 50), sp.Unit("e2", None, None, "eval", 50),
             sp.Unit("d1", None, None, "dev", 100), sp.Unit("d2", None, None, "dev", 100),
             sp.Unit("d3", None, None, "dev", 100)]
    # eval share 100/400 = 0.25, two sessions in eval, three in dev
    counts = sp._counts(units, 0)
    assert all(ok for _, ok in sp._criteria(units, counts))
    one = units[1:]
    counts = sp._counts(one, 0)
    assert not dict(sp._criteria(one, counts))["at least 2 sessions per set"]


def test_eval_under_100_darts_fails_session_mode():
    split = sp.compute_split([make("s%d" % i, 20) for i in range(10)])  # 200 darts, eval about 60
    assert split.mode == "block"
    assert not dict(split.session_criteria)["eval has at least 100 judged darts"]


def test_block_mode_no_volley_in_both_sets_and_darts_stay_with_their_volley():
    sessions = [make("a", 300, per_volley=3), make("b", 40), make("c", 40)]
    split = sp.compute_split(sessions)
    assert split.mode == "block"
    for s in sessions:
        for index, _ in s.volleys:
            matches = [u for u in split.units if u.session == s.name
                       and u.first_volley <= index <= u.last_volley]
            assert len(matches) == 1  # exactly one block, so one set, for every dart of the volley
    total = sum(s.judged for s in sessions)
    assert split.counts["dev"]["darts"] + split.counts["eval"]["darts"] == total


def test_sessions_without_judged_dart_are_ignored_cv3_cv5():
    empty = [SessionInput("e%d" % i, ((1, 0), (2, 0))) for i in range(3)]
    plain = sp.compute_split(ten(50))
    with_empty = sp.compute_split(ten(50) + empty)
    assert with_empty.units == plain.units and with_empty.mode == plain.mode
    assert with_empty.counts["ignoredSessions"] == 3
    with pytest.raises(SplitError):
        sp.set_of("e0", 1, with_empty)


def test_empty_sessions_never_count_for_two_sessions_per_set():
    three = [make("a", 60), make("b", 60), make("c", 60)]
    empty = [SessionInput("e%d" % i, ()) for i in range(2)]
    alone, mixed = sp.compute_split(three), sp.compute_split(three + empty)
    assert alone.mode == mixed.mode == "block"
    assert alone.session_criteria == mixed.session_criteria
    assert "more optimistic" in sp.summary(mixed, "0" * 64)
    assert mixed.counts["ignoredSessions"] == 2
    assert json.loads(sp.encode(mixed))["mode"] == "block"


def test_encode_is_canonical_and_decode_round_trips():
    split = sp.compute_split(ten(50))
    data = sp.encode(split)
    root = json.loads(data)
    assert root["format"] == "kosmo-camera-split" and root["version"] == 1
    assert data == (json.dumps(root, sort_keys=True, indent=2) + "\n").encode()
    assert sp.decode(data) == split
    block = sp.compute_split([make("a", 60), make("b", 60), make("c", 60)])
    assert sp.decode(sp.encode(block)) == block


@pytest.mark.parametrize("mutate", [
    lambda r: r.update(extra=1),
    lambda r: r.update(version=2),
    lambda r: r.update(mode="other"),
    lambda r: r["units"][0].update(set="test"),
    lambda r: r["units"][0].update(firstVolley=1),
    lambda r: r["counts"]["dev"].update(units=99),
    lambda r: r["units"].reverse(),
    lambda r: r["units"].append(dict(r["units"][0])),
])
def test_decode_is_strict(mutate):
    root = json.loads(sp.encode(sp.compute_split(ten(50))))
    mutate(root)
    with pytest.raises(SplitError):
        sp.decode(json.dumps(root).encode())


def test_decode_rejects_garbage_and_duplicate_keys():
    with pytest.raises(SplitError):
        sp.decode(b"not json")
    with pytest.raises(SplitError):
        sp.decode(b'{"format": 1, "format": 2}')


def test_lock_matches_and_detects_changes(tmp_path):
    data = sp.encode(sp.compute_split(ten(50)))
    lock_path = tmp_path / "split-lock.json"
    lock_path.write_bytes(sp.make_lock(data))
    lock = sp.read_lock(lock_path)
    assert sp.check_lock(data, lock) == sp.decode(data)
    changed = data.replace(b"dev", b"dEv", 1)
    with pytest.raises(SplitError):
        sp.check_lock(changed, lock)
    with pytest.raises(SplitError):
        sp.check_lock(data, dict(lock, mode="block"))
    with pytest.raises(SplitError):
        sp.check_lock(data, dict(lock, counts={"dev": {"units": 0, "darts": 0},
                                               "eval": {"units": 0, "darts": 0}}))


@pytest.mark.parametrize("mutate", [
    lambda r: r.update(unknown=1),
    lambda r: r.update(splitSha256="abc"),
    lambda r: r.update(splitSha256="A" * 64),
    lambda r: r.update(mode="x"),
    lambda r: r.update(version=2),
    lambda r: r.pop("counts"),
])
def test_malformed_lock_fails(tmp_path, mutate):
    data = sp.encode(sp.compute_split(ten(50)))
    root = json.loads(sp.make_lock(data))
    mutate(root)
    path = tmp_path / "split-lock.json"
    path.write_text(json.dumps(root))
    with pytest.raises(SplitError):
        sp.read_lock(path)


def test_eval_campaign_session_is_refused_cv17(tmp_path):
    inside = make("x", 10, path=tmp_path / "captures-eval" / "x")
    with pytest.raises(SplitError):
        sp.compute_split(ten() + [inside])
    (tmp_path / "captures-eval").mkdir()
    with pytest.raises(SplitError):
        sp.list_sessions(tmp_path / "captures-eval")


def _truth_session(root, name, darts):
    golden = json.loads((Path(__file__).parent / "data" / "truth-v2-golden.json").read_text())
    golden["session"] = name
    template = golden["volleys"][0]["darts"][0]
    volley = golden["volleys"][0]
    volley["darts"] = [dict(template, number=i + 1) for i in range(darts)]
    golden["volleys"] = [volley]
    folder = root / name
    folder.mkdir()
    (folder / "truth.json").write_text(json.dumps(golden))


def test_list_sessions_and_command_print_no_session_name_cv15(tmp_path, capsys):
    captures = tmp_path / "captures"
    captures.mkdir()
    for i in range(4):
        _truth_session(captures, "2026-secret-%d" % i, 2)
    (captures / "not-a-session").mkdir()
    assert [s.judged for s in sp.list_sessions(captures)] == [2, 2, 2, 2]
    out, lock = tmp_path / "out" / "split.json", tmp_path / "out" / "split-lock.json"
    assert sp.main(["--captures", str(captures), "--out", str(out), "--lock", str(lock)]) == 0
    printed = capsys.readouterr().out
    assert "secret" not in printed and str(tmp_path) not in printed
    assert "mode:" in printed and "ignored sessions" in printed and "split sha256:" in printed
    sp.check_lock(out.read_bytes(), sp.read_lock(lock))
    assert sp.main(["--captures", str(captures), "--out", str(out)]) == 1  # computed once
