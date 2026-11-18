"""Video frame before the entry and its manifest (CV27, CV15, S13): videos generated with PyAV, nothing real."""
import dataclasses
import hashlib
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

import pytest

av = pytest.importorskip("av")
numpy = pytest.importorskip("numpy")
PIL_Image = pytest.importorskip("PIL.Image")

from kosmo_camera import config, frames_file as ff, split as sp, synth, video_frames as vf  # noqa: E402
from kosmo_camera.truth import read_truth  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
NAME = "20261101-100000"
OTHER = "20261102-100000"


def _write_video(path, frames=20, fps=10, size=(64, 48)):
    """Frame i is a flat grey of level 10 * i + 20, so a decoded frame tells which one it is."""
    container = av.open(str(path), "w", format="mp4")
    try:
        stream = container.add_stream("mpeg4", rate=fps)
        stream.width, stream.height, stream.pix_fmt = size[0], size[1], "yuv420p"
        for i in range(frames):
            image = numpy.full((size[1], size[0], 3), 10 * i + 20, dtype=numpy.uint8)
            for packet in stream.encode(av.VideoFrame.from_ndarray(image, format="rgb24")):
                container.mux(packet)
        for packet in stream.encode():
            container.mux(packet)
    finally:
        container.close()
    return path


def _index(frame):
    return round((float(frame.pixels.mean()) - 20) / 10)


@pytest.fixture(scope="module")
def video(tmp_path_factory):
    return _write_video(tmp_path_factory.mktemp("video") / "v.mp4")


def test_frame_at_picks_the_earlier_frame_and_reports_its_position_cv27(video):
    between = vf.frame_at(video, 250)
    assert (between.position_ms, _index(between)) == (200, 2)
    exact = vf.frame_at(video, 300)
    assert (exact.position_ms, _index(exact)) == (300, 3)
    assert vf.frame_at(video, 0).position_ms == 0
    assert vf.frame_at(video, 0).pixels.shape == (48, 64, 3)


def test_position_is_floored_to_the_millisecond_cv27(tmp_path):
    thirty = _write_video(tmp_path / "v30.mp4", frames=5, fps=30)
    assert vf.frame_at(thirty, 40).position_ms == 33  # 33.33 ms floored


def test_last_frame_and_past_end_cv27(video):
    assert vf.frame_at(video, 1999).position_ms == 1900  # inside the last frame period
    with pytest.raises(vf.FrameError) as error:
        vf.frame_at(video, 2000)
    assert error.value.reason == ff.PAST_END


def test_before_start_cv27(video):
    with pytest.raises(vf.FrameError) as error:
        vf.frame_at(video, -1)
    assert error.value.reason == ff.BEFORE_START


def test_missing_video_and_a_folder_in_its_place_are_video_missing(tmp_path):
    for path in (tmp_path / "absent.mp4", tmp_path):
        with pytest.raises(vf.FrameError) as error:
            vf.frame_at(path, 100)
        assert error.value.reason == ff.VIDEO_MISSING


def test_file_of_random_bytes_and_truncated_video_are_unreadable_s13(video, tmp_path):
    noise = tmp_path / "noise.mp4"
    noise.write_bytes(os.urandom(4096))
    cut = tmp_path / "cut.mp4"
    cut.write_bytes(video.read_bytes()[:video.stat().st_size // 2])
    empty = tmp_path / "empty.mp4"
    empty.write_bytes(b"")
    for path in (noise, cut, empty):
        with pytest.raises(vf.FrameError) as error:
            vf.frame_at(path, 500)
        assert error.value.reason == ff.VIDEO_UNREADABLE


def test_decoded_frame_budget_and_file_size_cap_are_unreadable_s13(video, monkeypatch):
    monkeypatch.setattr(config, "VIDEO_MAX_DECODED_FRAMES", 5)
    with pytest.raises(vf.FrameError) as error:
        vf.frame_at(video, 1500)
    assert error.value.reason == ff.VIDEO_UNREADABLE
    assert vf.frame_at(video, 300).position_ms == 300  # a frame found inside the budget is still returned
    monkeypatch.setattr(config, "VIDEO_MAX_DECODED_FRAMES", 20000)
    monkeypatch.setattr(config, "VIDEO_MAX_BYTES", video.stat().st_size - 1)
    with pytest.raises(vf.FrameError) as error:
        vf.frame_at(video, 300)
    assert error.value.reason == ff.VIDEO_UNREADABLE
    monkeypatch.setattr(config, "VIDEO_MAX_BYTES", video.stat().st_size)
    assert vf.frame_at(video, 300).position_ms == 300


# ---- a whole session ----------------------------------------------------------------------------------------------

@pytest.fixture(scope="module")
def captures(tmp_path_factory):
    """The T7 synthetic session with a 2.5 s step between entries, so that a frame 1 s before the entry is later than
    the photo of the previous dart (the default 1 s step makes every dart after the first `too_close`)."""
    with pytest.MonkeyPatch.context() as patch:
        patch.setattr(synth, "_ENTRY_STEP_MS", 2500)
        directory = tmp_path_factory.mktemp("captures")
        synth.make_session(directory, synth.SessionSpec(_darts(), name=NAME, segments=(1, 4)), seed=7)
    return directory


def _darts():
    return (synth.DartSpec(20, "TRIPLE"), synth.DartSpec(5, "SINGLE", voided=True), synth.DartSpec(19, "DOUBLE"),
            synth.DartSpec(25), synth.DartSpec(0, photo=False), synth.DartSpec(1, "SINGLE"))


def _copy(captures, tmp_path, name=NAME):
    root = tmp_path / "captures"
    root.mkdir(exist_ok=True)
    shutil.copytree(captures / NAME, root / name)
    return root


def _extract(folder, out, numbers=(1, 2, 3, 4, 5, 6), mutate=None, **kwargs):
    truth = read_truth(folder / "truth.json")
    if mutate is not None:
        truth = mutate(truth)
    return vf.extract_session("session", truth, folder, numbers, out, **kwargs)


def test_frames_of_a_session_are_written_with_their_manifest_entries_cv27(captures, tmp_path):
    result = _extract(captures / NAME, tmp_path)
    assert [(m.dart, m.reason) for m in result.missing] == [(1, ff.BEFORE_START), (4, ff.BEFORE_START)]
    assert [f.dart for f in result.frames] == [2, 3, 5, 6]
    # entries of a volley are 800, 3300, 5800 ms: the frame is the one 1 s before, at 10 fps
    assert [f.position_ms for f in result.frames] == [2300, 4800, 2300, 4800]
    for frame in result.frames:
        data = (tmp_path / "session" / frame.file).read_bytes()
        assert frame.sha256 == hashlib.sha256(data).hexdigest() and frame.file == f"video-{frame.dart:03d}.jpg"
        assert PIL_Image.open(tmp_path / "session" / frame.file).size == (frame.width, frame.height) == (640, 360)
    assert sorted(p.name for p in (tmp_path / "session").iterdir()) == [f.file for f in result.frames]


def test_stale_files_are_removed_and_only_listed_files_stay(captures, tmp_path):
    folder = tmp_path / "session"
    folder.mkdir()
    (folder / "video-009.jpg").write_bytes(b"old")
    (folder / ".video-002.jpg.x.tmp").write_bytes(b"old")
    result = _extract(captures / NAME, tmp_path)
    assert sorted(p.name for p in folder.iterdir()) == [f.file for f in result.frames]


def test_same_input_gives_the_same_files(captures, tmp_path):
    first = _extract(captures / NAME, tmp_path / "a")
    second = _extract(captures / NAME, tmp_path / "b")
    assert first == second


def test_too_close_when_the_target_precedes_the_previous_photo_stamp_cv27(captures, tmp_path):
    result = _extract(captures / NAME, tmp_path, numbers=(2, 3), before_ms=2500)
    # dart 2: entry 3300 - 2500 = 800 < 842 (photo of dart 1); dart 3: 5800 - 2500 = 3300 < 3342 (photo of dart 2)
    assert [(m.dart, m.reason) for m in result.missing] == [(2, ff.TOO_CLOSE), (3, ff.TOO_CLOSE)]
    assert result.frames == ()
    assert not (tmp_path / "session").exists()


def test_no_position_video_missing_and_orientation_mismatch_cv27(captures, tmp_path):
    root = _copy(captures, tmp_path)
    folder = root / NAME
    data = json.loads((folder / "truth.json").read_text(encoding="utf-8"))
    data["volleys"][0]["darts"][1]["entry"]["videoPositionMs"] = None  # dart 2
    (folder / "truth.json").write_text(json.dumps(data), encoding="utf-8")
    (folder / "volley-002.mp4").unlink()
    result = _extract(folder, tmp_path / "out", numbers=(2, 3, 5, 6))
    assert [(m.dart, m.reason) for m in result.missing] == [(2, ff.NO_POSITION), (5, ff.VIDEO_MISSING),
                                                            (6, ff.VIDEO_MISSING)]
    assert [f.dart for f in result.frames] == [3]
    portrait = lambda truth: dataclasses.replace(truth, photo_width=480, photo_height=640)  # noqa: E731
    mismatch = _extract(captures / NAME, tmp_path / "out2", numbers=(3,), mutate=portrait)
    assert [(m.dart, m.reason) for m in mismatch.missing] == [(3, ff.ORIENTATION_MISMATCH)]
    assert not (tmp_path / "out2").exists()  # never rotated, never written


@pytest.mark.parametrize("damage", ["noise", "truncated"])
def test_a_corrupt_video_does_not_stop_the_next_session_s13(captures, tmp_path, damage):
    root = _copy(captures, tmp_path, "20261101-100000")
    shutil.copytree(captures / NAME, root / OTHER)
    bad = root / NAME / "volley-002.mp4"
    bad.write_bytes(os.urandom(4096) if damage == "noise" else bad.read_bytes()[:3000])
    split = sp.Split("session", (sp.Unit(NAME, None, None, "dev"), sp.Unit(OTHER, None, None, "dev")), {})
    result = vf.run(root, captures / "annotations", split, tmp_path / "out")
    first, second = result.sessions
    assert (first.session, second.session) == (NAME, OTHER)
    assert ff.Missing(6, ff.VIDEO_UNREADABLE) in first.missing and first.frames == ()
    assert [f.dart for f in second.frames] == [6]
    assert (tmp_path / "out" / OTHER / "video-006.jpg").is_file()


def test_session_where_every_frame_is_missing_is_listed_with_only_missing(captures, tmp_path):
    root = _copy(captures, tmp_path)
    (root / NAME / "volley-002.mp4").unlink()
    split = sp.Split("session", (sp.Unit(NAME, None, None, "dev"),), {})
    result = vf.run(root, captures / "annotations", split, tmp_path / "out")
    assert [(s.session, s.frames) for s in result.sessions] == [(NAME, ())]
    assert [m.dart for m in result.sessions[0].missing] == [1, 4, 6]  # dev sample: ranks 1, segment 4, last 6
    assert not (tmp_path / "out" / NAME).exists()


# ---- command line -------------------------------------------------------------------------------------------------

def _split(units):
    counts = {s: {"units": sum(1 for u in units if u.set == s), "darts": 0} for s in sp.SETS}
    return sp.Split("session", units, counts | {"ignoredSessions": 0})


def _split_files(tmp_path, units):
    data = sp.encode(_split(units))
    (tmp_path / "split.json").write_bytes(data)
    (tmp_path / "lock.json").write_bytes(sp.make_lock(data))


def _run(captures, out, tmp_path):
    return subprocess.run(
        [sys.executable, "-m", "kosmo_camera.video_frames", "--captures", str(captures),
         "--annotations", str(captures / "annotations"), "--split", str(tmp_path / "split.json"),
         "--lock", str(tmp_path / "lock.json"), "--out", str(out)],
        capture_output=True, text=True, cwd=ROOT)


def test_main_writes_files_and_manifest_and_prints_counts_only_cv15_cv27(captures, tmp_path):
    _split_files(tmp_path, (sp.Unit(NAME, None, None, "dev"),))
    result = _run(captures, tmp_path / "out", tmp_path)
    assert result.returncode == 0, result.stderr
    assert result.stdout.splitlines() == ["Images vidéo écrites : 1", "Images vidéo absentes : 2",
                                          "  before_start : 2"]
    assert NAME not in result.stdout + result.stderr and str(tmp_path) not in result.stdout + result.stderr
    manifest = ff.read(tmp_path / "out" / "frames.json")
    assert manifest.before_entry_ms == 1000
    (session,) = manifest.sessions
    assert session.session == NAME and [f.dart for f in session.frames] == [6]
    assert sorted(p.name for p in (tmp_path / "out").iterdir()) == [NAME, "frames.json"]
    assert [p.name for p in (tmp_path / "out" / NAME).iterdir()] == ["video-006.jpg"]
    data = (tmp_path / "out" / NAME / "video-006.jpg").read_bytes()
    assert session.frames[0].sha256 == hashlib.sha256(data).hexdigest()


def test_main_prints_nothing_about_absent_frames_when_none_is_missing(captures, tmp_path):
    # an eval session of 6 darts has no dart 55 to 60: nothing to extract, an empty manifest
    _split_files(tmp_path, (sp.Unit(NAME, None, None, "eval"),))
    result = _run(captures, tmp_path / "out", tmp_path)
    assert result.returncode == 0, result.stderr
    assert result.stdout.splitlines() == ["Images vidéo écrites : 0"]
    assert ff.read(tmp_path / "out" / "frames.json").sessions == ()


def test_main_refuses_a_split_that_does_not_match_the_lock(captures, tmp_path):
    _split_files(tmp_path, (sp.Unit(NAME, None, None, "dev"),))
    (tmp_path / "split.json").write_bytes(sp.encode(_split((sp.Unit(NAME, None, None, "eval"),))))
    result = _run(captures, tmp_path / "out", tmp_path)
    assert result.returncode == 2 and "lock" in result.stderr
    assert not (tmp_path / "out").exists()


def test_main_refuses_a_bad_argument(captures, tmp_path):
    result = subprocess.run([sys.executable, "-m", "kosmo_camera.video_frames", "--captures", str(tmp_path / "none")],
                            capture_output=True, text=True, cwd=ROOT)
    assert result.returncode == 2
