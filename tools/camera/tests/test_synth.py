"""Synthetic boards and sessions (T7). Rules CV1, CV2, CV5, CV10, CV11, CV20."""
import random

import av
import numpy
import pytest

from kosmo_camera import annotations, synth
from kosmo_camera.board import CALIBRATION_POINTS_MM, SECTOR_ORDER, zone_at
from kosmo_camera.truth import read_truth

ALL_ZONES = [(n, m) for n in SECTOR_ORDER for m in ("SINGLE", "DOUBLE", "TRIPLE")] + [(25, "SINGLE"), (50, "SINGLE")]


def _files(folder):
    return {p.name: p.read_bytes() for p in sorted(folder.rglob("*")) if p.is_file()}


def _small_spec(**kwargs):
    darts = (synth.DartSpec(20, "TRIPLE"), synth.DartSpec(3, "DOUBLE"))
    return synth.SessionSpec(kwargs.pop("darts", darts), **kwargs)


def test_same_seed_gives_identical_bytes_and_different_seeds_differ(tmp_path):
    first = synth.make_session(tmp_path / "a", _small_spec(), seed=3)
    again = synth.make_session(tmp_path / "b", _small_spec(), seed=3)
    other = synth.make_session(tmp_path / "c", _small_spec(), seed=4)
    assert _files(first.folder) == _files(again.folder)
    assert first.annotations_path.read_bytes() == again.annotations_path.read_bytes()
    assert _files(first.folder)["dart-001.jpg"] != _files(other.folder)["dart-001.jpg"]
    assert _files(first.folder)["volley-001.mp4"] != _files(other.folder)["volley-001.mp4"]


def test_render_board_same_seed_same_pixels(synthetic_board):
    assert numpy.array_equal(synthetic_board([(10, 20)], seed=1)[0], synthetic_board([(10, 20)], seed=1)[0])
    assert not numpy.array_equal(synthetic_board([(10, 20)], seed=1)[0], synthetic_board([(10, 20)], seed=2)[0])


def test_generated_session_reads_and_its_annotations_validate(synthetic_session):
    session = read_truth(synthetic_session.truth_path)
    result = annotations.read(synthetic_session.annotations_path)
    assert annotations.validate_against(result, session) == []  # no warning: the hash matches the truth file
    assert [d.number for d in result.darts] == [1, 3, 4, 6]  # CV5: dart 2 voided, dart 5 without photo
    assert [c.from_dart for c in result.calibrations] == [1, 4]  # CV20
    assert result.calibrations[1].photo == "dart-004.jpg"
    assert [v.empty for v in session.volleys] == [False, False]
    dart5 = session.volleys[1].darts[1]
    assert dart5.photo is None and not dart5.judged
    assert session.volleys[0].darts[1].voided


def test_dart_at_the_centre_of_a_zone_scores_that_zone(synthetic_board):
    view = synth.View(yaw=25.0, pitch=-12.0, distance=650.0, roll=7.0)
    for segment, multiplier in random.Random(5).sample(ALL_ZONES, 20):
        _, truth = synthetic_board([synth.zone_centre_mm(segment, multiplier)], view)
        back = synth.unproject(truth.homography, truth.tips[0])
        assert zone_at(*back) == (segment, multiplier)


def test_miss_is_outside_the_board():
    assert zone_at(*synth.zone_centre_mm(0, "SINGLE")) == (0, "SINGLE")


def test_calibration_pixels_are_the_homography_of_the_cv2_points(synthetic_board):
    _, truth = synthetic_board([(0.0, 0.0)], synth.View(yaw=-30.0, pitch=20.0, roll=-5.0))
    assert len(truth.calibration) == 4
    expected = synth.project(truth.homography, CALIBRATION_POINTS_MM)
    assert truth.calibration == expected
    assert all(0 <= x <= 640 and 0 <= y <= 480 for x, y in truth.calibration)


def test_dart_is_drawn_at_its_tip(synthetic_board):
    empty, _ = synthetic_board([])
    image, truth = synthetic_board([synth.zone_centre_mm(20, "TRIPLE")])
    x, y = (round(v) for v in truth.tips[0])
    changed = numpy.abs(image.astype(int) - empty.astype(int)).sum(axis=2) > 60
    assert changed[y - 20:y + 5, x - 5:x + 20].any()
    assert not changed[:, :max(0, x - 120)].any()


def test_video_frames_duration_and_arrival(synthetic_session):
    session = read_truth(synthetic_session.truth_path)
    for volley in session.volleys:
        with av.open(str(synthetic_session.folder / volley.video)) as container:
            stream = container.streams.video[0]
            frames = [f.to_ndarray(format="rgb24") for f in container.decode(stream)]
            assert (stream.width, stream.height) == (640, 360)
            assert float(stream.average_rate) == 10.0
        assert len(frames) == synthetic_session.video_frames[volley.index]
        assert len(frames) == volley.end.video_position_ms // 100 + 1  # CV11 end of the volley, in video time
        for dart in volley.darts:
            frame = synthetic_session.arrival_frames[dart.number]
            assert frame * 100 < dart.entry.video_position_ms  # CV10: the arrival precedes the entry
            previous = volley.darts.index(dart) - 1
            assert frame * 100 > (volley.darts[previous].entry.video_position_ms if previous >= 0 else 0)
            before, after = (frames[i].astype(int) for i in (frame - 1, frame))
            assert _box_difference(before, after, dart, synthetic_session) > 40  # appears at the planned frame
            earlier = frames[frame - 2].astype(int)
            assert _box_difference(earlier, before, dart, synthetic_session) < 5
        empty_from = synthetic_session.video_empty_from[volley.index]
        assert empty_from * 100 > volley.darts[-1].entry.video_position_ms  # CV11: removal after the last entry
        assert empty_from * 100 < volley.end.video_position_ms  # and before the volley end
        assert numpy.abs(frames[empty_from + 1].astype(int) - frames[0].astype(int)).mean() < 3  # empty board again


def _box_difference(before, after, dart, session):
    x, y = (round(v) for v in synth.project(session.spec.view.homography(session.spec.video_size),
                                            [session.positions_mm[dart.number]])[0])
    box = (slice(max(0, y - 80), y + 5), slice(max(0, x - 5), x + 60))
    return (numpy.abs(before[box] - after[box]).sum(axis=2) > 60).sum()  # changed pixels in the dart's box


def test_video_occluder_covers_the_board_only_in_its_frames(tmp_path):
    spec = _small_spec(video_occluder=(2, 3))
    session = synth.make_session(tmp_path, spec, seed=1)
    with av.open(str(session.folder / "volley-001.mp4")) as container:
        frames = [f.to_ndarray(format="rgb24") for f in container.decode(video=0)]
    middle = frames[2][100:, 250:390].astype(int)
    assert middle.mean() < 60 and frames[0][100:, 250:390].mean() > 60
    assert frames[5][100:, 250:390].mean() > 60


def test_no_drift_every_photo_has_the_same_view(tmp_path):
    session = synth.make_session(tmp_path, _small_spec(), seed=1)
    assert len({tuple(h.flatten()) for h in session.homographies.values()}) == 1
    assert session.calibration[1] == session.calibration[2]


def test_drifted_photos_calibration_is_the_drifted_view(tmp_path):
    drifts = (synth.Drift(2, dx=8.0, dy=-4.0, roll=1.0), synth.Drift(3, dx=-5.0))
    darts = tuple(synth.DartSpec(n) for n in (20, 1, 18))
    session = synth.make_session(tmp_path, _small_spec(darts=darts, drifts=drifts, darts_per_volley=3), seed=1)
    assert session.calibration[1] == synth.project(session.spec.view.homography((640, 480)), CALIBRATION_POINTS_MM)
    for number in (2, 3):
        drifted = synth.project(session.homographies[number], CALIBRATION_POINTS_MM)
        assert session.calibration[number] == drifted
        assert session.views[number] == synth.View(20.0, 15.0, 700.0, 0.0, *(
            (8.0, -4.0, 1.0) if number == 2 else (-5.0, 0.0, 0.0)))
    shift = numpy.array(session.calibration[2]) - numpy.array(session.calibration[1])
    assert numpy.abs(shift.mean(axis=0) - (8.0, -4.0)).max() < 3.0  # a roll of 1 degree moves corners by a few px
    assert session.homographies[1].tolist() != session.homographies[2].tolist()


def test_a_session_with_two_segments_validates(synthetic_session):
    result = annotations.read(synthetic_session.annotations_path)
    segment = annotations.segment_for(result, 6)
    assert segment[0] == 1 and segment[1].points == synthetic_session.calibration[4]
    assert annotations.segment_for(result, 3)[0] == 0


def test_corner_occlusion_covers_one_corner_of_one_photo(tmp_path):
    clean = synth.make_session(tmp_path / "a", _small_spec(), seed=1)
    covered = synth.make_session(tmp_path / "b", _small_spec(corner_occlusion=(2, 1)), seed=1)
    from PIL import Image
    first = numpy.asarray(Image.open(clean.folder / "dart-002.jpg")).astype(int)
    second = numpy.asarray(Image.open(covered.folder / "dart-002.jpg")).astype(int)
    x, y = (round(v) for v in covered.calibration[2][1])
    assert numpy.abs(first - second)[y - 15:y + 15, x - 15:x + 15].mean() > 10
    assert (clean.folder / "dart-001.jpg").read_bytes() == (covered.folder / "dart-001.jpg").read_bytes()
    assert covered.calibration == clean.calibration  # the truth does not change


def test_no_real_image_is_read():
    import kosmo_camera.synth as module
    text = open(module.__file__, encoding="utf-8").read()
    assert "Image.open" not in text and "kosmo-data" not in text
