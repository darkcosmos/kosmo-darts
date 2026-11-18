"""Snapping diagnosis on stored clicks (CV32, CV15, D49, D54; T17a): synthetic sessions written by the test, nothing real.

Runs in the WSL tool venv (numpy and Pillow, no PyAV): the T7 sessions are written with the volley videos switched off.
"""
import dataclasses
import hashlib
import math
import subprocess
import sys
from pathlib import Path

import pytest

numpy = pytest.importorskip("numpy")
pytest.importorskip("PIL")
from PIL import Image  # noqa: E402

from kosmo_camera import annotations as ann, config, frames_file as ff, snap, snap_report as sr  # noqa: E402
from kosmo_camera import split as sp, synth  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
DEV_A = "20261101-100000"  # dev 1: photo controls (one hidden point, one unreadable photo) and a listed video frame
DEV_C = "20261103-100000"  # dev 2: a segment, no control
DEV_D = "20261104-100000"  # dev 3: no annotation file
EVAL_B = "20261102-100000"  # eval 1: a photo control and a video control whose frame is not listed
NAMES = (DEV_A, DEV_C, DEV_D, EVAL_B)
VIDEO_SIZE = (640, 360)


def _no_video(*args, **kwargs):
    return {}, 0, 0  # arrivals, frames, removal frame: the report never reads a volley video


def _darts(count):
    return tuple(synth.DartSpec(s, "SINGLE") for s in (20, 1, 18)[:count])


def _control(dart, points, snaps, source=ann.PHOTO, position_ms=None, size=(640, 480)):
    return ann.Control(dart, source, position_ms, *size, tuple(points), tuple(snaps))


def _video_frame(frames_dir, name, dart, position_ms):
    """A rendered 640 x 360 board written as the listed frame of `dart`; its 4 calibration points."""
    pixels, truth = synth.render_board(synth.View(), [], VIDEO_SIZE, synth.Lighting(), 3)
    folder = frames_dir / name
    folder.mkdir(parents=True, exist_ok=True)
    path = folder / ff.video_file_name(dart)
    Image.fromarray(pixels).save(path, quality=95)
    sha = hashlib.sha256(path.read_bytes()).hexdigest()
    return ff.Frame(dart, position_ms, *VIDEO_SIZE, path.name, sha), truth.calibration


def _split_files(tmp, units):
    counts = {s: {"units": sum(1 for u in units if u.set == s), "darts": 0} for s in sp.SETS}
    data = sp.encode(sp.Split("session", units, counts | {"ignoredSessions": 0}))
    (tmp / "split.json").write_bytes(data)
    (tmp / "lock.json").write_bytes(sp.make_lock(data))


@pytest.fixture(scope="module")
def data(tmp_path_factory):
    root = tmp_path_factory.mktemp("snapdata")
    captures, frames_dir = root / "captures", root / "frames"
    captures.mkdir()
    frames_dir.mkdir()
    sessions = {}
    with pytest.MonkeyPatch.context() as patch:
        patch.setattr(synth, "_write_video", _no_video)
        for name, count in ((DEV_A, 3), (DEV_C, 2), (DEV_D, 2), (EVAL_B, 2)):
            sessions[name] = synth.make_session(captures, synth.SessionSpec(_darts(count), name=name), seed=5)
    a, b = sessions[DEV_A], sessions[EVAL_B]
    frame, frame_points = _video_frame(frames_dir, DEV_A, 3, 4200)
    one = list(a.calibration[1])
    one[2] = None  # hidden by a dart
    controls_a = (_control(1, one, (ann.SNAPPED, ann.UNSNAPPED, None, ann.SNAPPED)),
                  _control(2, a.calibration[2], (ann.UNSNAPPED,) * 4),
                  _control(3, a.calibration[3], (ann.UNSNAPPED,) * 4),
                  _control(3, frame_points, (ann.UNSNAPPED,) * 4, ann.VIDEO, 4200, VIDEO_SIZE))
    (a.folder / "dart-003.jpg").write_bytes(b"not a jpeg")  # unreadable photo
    controls_b = (_control(1, b.calibration[1], (ann.UNSNAPPED,) * 4),
                  _control(2, frame_points, (ann.UNSNAPPED,) * 4, ann.VIDEO, 5000, VIDEO_SIZE))  # frame not listed
    for session, controls in ((a, controls_a), (b, controls_b)):
        annotations = ann.read(session.annotations_path)
        ann.write_atomic(session.annotations_path, dataclasses.replace(annotations, controls=controls))
    sessions[DEV_D].annotations_path.unlink()
    ff.write_atomic(frames_dir / "frames.json", ff.FramesFile(config.VIDEO_BEFORE_ENTRY_MS,
                                                              (ff.SessionFrames(DEV_A, (frame,), ()),)))
    units = tuple(sp.Unit(n, None, None, "eval" if n == EVAL_B else "dev") for n in sorted(NAMES))
    _split_files(root, units)
    split = sp.check_lock((root / "split.json").read_bytes(), sp.read_lock(root / "lock.json"))
    text, times, run_s, outcomes = sr.report(captures, captures / "annotations", split, frames_dir)
    return {"root": root, "captures": captures, "frames": frames_dir, "split": split, "text": text, "times": times,
            "outcomes": outcomes}


def _table_rows(text, first):
    """Cells of the rows of the table whose first row starts with `first`."""
    lines = text.splitlines()
    for i, line in enumerate(lines):
        if line.startswith(f"| {first} |"):
            rows = []
            for row in lines[i + 2:]:
                if not row.startswith("|"):
                    break
                rows.append([c.strip() for c in row.strip("|").split("|")])
            return rows
    raise AssertionError(f"no table '{first}'")


def _group_rows(text):
    """Every row of every table with one column per group then dev, eval and total."""
    header = "| " + " | ".join(sr._group_label(g) for g in sr.GROUPS) + " | dev | eval | total |"
    rows, lines = [], text.splitlines()
    for i, line in enumerate(lines):
        if line.endswith(header):
            for row in lines[i + 2:]:
                if not row.startswith("|"):
                    break
                rows.append([c.strip() for c in row.strip("|").split("|")])
    return rows


def _row(text, label):
    (row,) = [r for r in _group_rows(text) if r[0] == label]
    return dict(zip([sr._group_label(g) for g in sr.GROUPS] + ["dev", "eval", "total"], map(int, row[1:])))


# ---- mapping -------------------------------------------------------------------------------------------------------

def test_cv32_every_stage_and_unreadable_maps_to_exactly_one_kosmo_category():
    assert set(sr.CATEGORY_OF_STAGE) == set(snap.STAGES) | {sr.UNREADABLE}
    assert set(sr.CATEGORY_OF_STAGE.values()) == set(sr.CATEGORIES)
    assert sr.CATEGORIES == ("pas de croisement", "forme", "ambiguïté", "hors rayon", "autre")
    assert (sr.CATEGORY_OF_STAGE["too_far"], sr.CATEGORY_OF_STAGE["coarse_too_far"]) == ("hors rayon",) * 2
    assert sr.CATEGORY_OF_STAGE["ambiguous"] == "ambiguïté"
    assert {s for s, c in sr.CATEGORY_OF_STAGE.items() if c == "autre"} == {"border", "no_convergence",
                                                                            "fit_failure", "unreadable"}


# ---- counts --------------------------------------------------------------------------------------------------------

def test_cv32_counts_add_up_per_source_and_role_with_hidden_points_apart(data):
    text = data["text"]
    points = _row(text, "points enregistrés")
    assert points == {"dev · photo · contrôle": 11, "dev · photo · segment": 8, "dev · vidéo · contrôle": 4,
                      "eval · photo · contrôle": 4, "eval · photo · segment": 4, "eval · vidéo · contrôle": 4,
                      "dev": 23, "eval": 12, "total": 35}
    assert _row(text, "points masqués (comptés à part)")["dev · photo · contrôle"] == 1
    assert _row(text, "points masqués (comptés à part)")["total"] == 1
    assert len(data["outcomes"]) == 35 and len(data["times"]) == 3 * 27  # 3 radii per readable point
    roles = {(r[0], r[1], r[2]): int(r[3]) for r in _table_rows(text, "rôle")}
    assert roles == {("dev 1", "photo", "contrôle"): 11, ("dev 1", "photo", "segment"): 4,
                     ("dev 1", "vidéo", "contrôle"): 4, ("dev 2", "photo", "segment"): 4,
                     ("eval 1", "photo", "contrôle"): 4, ("eval 1", "photo", "segment"): 4,
                     ("eval 1", "vidéo", "contrôle"): 4}
    for row in _table_rows(text, "rôle"):
        assert sum(map(int, row[4:])) == int(row[3])  # snapped plus each category: every point once


def test_cv32_unreadable_and_unlisted_images_are_counted_unreadable(data):
    text = data["text"]
    unreadable = _row(text, "points illisibles")
    assert (unreadable["dev · photo · contrôle"], unreadable["eval · vidéo · contrôle"], unreadable["total"]) == (4, 4, 8)
    assert _row(text, "points sans image vidéo listée")["eval · vidéo · contrôle"] == 4
    assert _row(text, "images illisibles ou absentes")["total"] == 2
    assert _row(text, "unreadable")["total"] == 8


def test_cv32_sessions_without_annotations_or_controls_are_counted(data):
    rows = {r[0]: r[1:] for r in _table_rows(data["text"], "sessions")}
    assert rows["sessions du split"] == ["3", "1", "4"]
    assert rows["sans annotations"] == ["1", "0", "1"]
    assert rows["sans contrôle"] == ["1", "0", "1"]
    assert "Manifeste des images vidéo : présent" in data["text"]


def test_cv32_every_section_shows_dev_and_eval_apart_and_they_add_up_to_the_total_d54(data):
    text = data["text"]
    rows = _group_rows(text)
    assert len(rows) > 20
    for row in rows:
        values = list(map(int, row[1:]))
        groups, dev, evaluation, total = values[:-3], values[-3], values[-2], values[-1]
        assert dev == sum(v for g, v in zip(sr.GROUPS, groups) if g[0] == "dev")
        assert evaluation == sum(v for g, v in zip(sr.GROUPS, groups) if g[0] == "eval")
        assert dev + evaluation == total
    for section in ("## Descripteurs", "## Rayon", "## Propositions"):
        body = text.split(section)[1].split("\n## ")[0]
        assert "dev · " in body and "eval · " in body
    causes = sum(_row(text, f"**{c}**")["total"] for c in sr.CATEGORIES) + _row(text, sr.SNAPPED)["total"]
    assert causes == 35


def test_cv15_report_holds_numbers_and_roles_only(data):
    text = data["text"]
    for forbidden in NAMES + ("/", "\\", ".jpg", ".json", "dart-", "video-", str(data["root"])):
        assert forbidden not in text, forbidden


# ---- empty frames and a session with no control --------------------------------------------------------------------

def test_cv32_an_empty_frames_folder_counts_every_video_point_unreadable(data, tmp_path):
    empty = tmp_path / "frames"
    empty.mkdir()
    text, times, _, outcomes = sr.report(data["captures"], data["captures"] / "annotations", data["split"], empty)
    assert "Manifeste des images vidéo : absent" in text
    assert _row(text, "points sans image vidéo listée")["total"] == 8
    assert _row(text, "points illisibles")["total"] == 12 and len(outcomes) == 35


# ---- radius sweep --------------------------------------------------------------------------------------------------

def test_cv32_a_start_15_px_from_a_clean_crossing_is_out_of_radius_at_1x_and_snapped_at_2x(tmp_path):
    pixels, truth = synth.render_board(synth.View(), [], (1600, 1200), synth.Lighting(noise=2.0), 0)
    path = tmp_path / "board.png"
    Image.fromarray(pixels).convert("L").save(path)  # lossless: the luma of test_snap's photo-1600 case
    p = truth.calibration[0]
    point = sr._Point("dev", "dev 1", sr.PHOTO_LABEL, sr.CONTROL_KIND, path, path, (1600, 1200), (p[0] + 15.0, p[1]),
                      ann.UNSNAPPED)
    (outcome,), unreadable = sr.run_snaps([point], [])
    assert sr.CATEGORY_OF_STAGE[outcome.stage] == "hors rayon" and not unreadable
    assert outcome.snapped_at[:2] == (False, True)  # at 4x the inner crossings come in range too


# ---- planned duration of K7.6 --------------------------------------------------------------------------------------

def _args(data, out):
    return ["--captures", str(data["captures"]), "--annotations", str(data["captures"] / "annotations"),
            "--split", str(data["root"] / "split.json"), "--lock", str(data["root"] / "lock.json"),
            "--frames", str(data["frames"]), "--out", str(out)]


def test_cv32_planned_k76_duration_follows_the_formula_with_an_injected_clock_d54(data, tmp_path, monkeypatch,
                                                                                     capsys):
    ticks = iter(range(10 ** 6))
    monkeypatch.setattr(sr, "_clock", lambda: float(next(ticks)))  # every snap lasts exactly 1 s
    assert sr.main(_args(data, tmp_path / "out")) == 0
    stored, photo_control = 35, 15  # stored points (hidden apart), of which photo control points
    starts = stored * (len(config.SNAP_SWEEP_FACTORS) + config.SNAP_REPEAT_DIRECTIONS) + 4 * photo_control
    expected = f"Durée prévue de K7.6 : environ {math.ceil(starts * 1.0 / 60)} min"
    assert expected == "Durée prévue de K7.6 : environ 8 min"
    out = capsys.readouterr().out.splitlines()
    assert out[0] == f"Rapport : {tmp_path / 'out' / sr.REPORT_NAME}"
    assert "Temps médian par recalage : 1000.0 ms" in out and out[-1] == expected
    report = (tmp_path / "out" / sr.REPORT_NAME).read_text(encoding="utf-8")
    durations = report.split("## Durées")[1]
    assert expected in durations and "Recalages exécutés : 81" in durations


def test_cv32_main_refuses_a_bad_folder_or_a_split_that_does_not_match_the_lock(data, tmp_path):
    args = _args(data, tmp_path / "out")
    args[args.index("--frames") + 1] = str(tmp_path / "none")
    assert sr.main(args) == 2
    (tmp_path / "split.json").write_bytes(b"{}")
    args = _args(data, tmp_path / "out")
    args[args.index("--split") + 1] = str(tmp_path / "split.json")
    assert sr.main(args) == 2 and not (tmp_path / "out").exists()


def test_cv32_main_runs_with_numpy_and_pillow_only_no_av_d44(data, tmp_path):
    code = ("import sys; sys.modules['av'] = None\n"
            "from kosmo_camera import snap_report\n"
            "sys.exit(snap_report.main(sys.argv[1:]))")
    result = subprocess.run([sys.executable, "-c", code] + _args(data, tmp_path / "out"), capture_output=True,
                            text=True, cwd=ROOT)
    assert result.returncode == 0, result.stderr
    assert (tmp_path / "out" / sr.REPORT_NAME).is_file()
    assert result.stdout.splitlines()[-1].startswith("Durée prévue de K7.6 : environ ")
