"""Tripod drift: measure, compensation, statuses, guard, report and command (CV18, CV19, CV15, CV5, D8).

Generated data only (kosmo_camera.synth). Thresholds are read through `config.py`; the one test that pins their values
is test_config.py (A16).
"""
import math
import os
import re
import shutil
import subprocess
import sys
from dataclasses import replace
from pathlib import Path

import numpy
import pytest
from PIL import Image

from kosmo_camera import annotations as ann
from kosmo_camera import config, drift, drift_file, geometry, synth

TOOL_ROOT = Path(__file__).resolve().parent.parent
BIG = (2400, 1800)  # 3.7 px per board mm: finer than the synthetic canvas, so the shift is not limited by its aliasing
SMALL = (640, 480)
VIDEO = (64, 36)
SINGLE = synth.DartSpec(20)
PARAMS = drift.DriftParams()


def _luma(image):
    return numpy.asarray(Image.fromarray(image).convert("L"), dtype=numpy.float32)


def _texture(seed=1, shape=(120, 160)):
    rng = numpy.random.default_rng(seed)
    spectrum = numpy.fft.fft2(rng.normal(size=shape))
    f2 = numpy.fft.fftfreq(shape[0])[:, None] ** 2 + numpy.fft.fftfreq(shape[1])[None, :] ** 2
    texture = numpy.fft.ifft2(spectrum * numpy.exp(-f2 / (2 * 0.08 ** 2))).real
    return texture / texture.std() * 40 + 128


def _fourier_shift(image, dx, dy):
    spectrum = numpy.fft.fft2(image)
    fy, fx = numpy.fft.fftfreq(image.shape[0])[:, None], numpy.fft.fftfreq(image.shape[1])[None, :]
    return numpy.fft.ifft2(spectrum * numpy.exp(-2j * numpy.pi * (fx * dx + fy * dy))).real


def _pair(view, size=SMALL, darts=(), seed=2):
    """Reference luma, drifted luma, the segment clicked on the reference, and the true image of the drifted view."""
    reference, truth = synth.render_board(synth.View(), [], size, synth.Lighting(), 1)
    photo, photo_truth = synth.render_board(view, darts, size, synth.Lighting(), seed)
    segment = ann.Calibration(1, "dart-001.jpg", size[0], size[1], truth.calibration)
    return _luma(reference), _luma(photo), segment, photo_truth


def _true_shifts(segment, photo_truth):
    return numpy.array(photo_truth.calibration) - numpy.array(segment.points)


# ---- phase correlation --------------------------------------------------------------------------------------------

def test_phase_shift_recovers_a_subpixel_translation_and_ignores_brightness():
    texture = _texture()
    shifted = _fourier_shift(texture, 2.5, -7.25)
    for scale in (1.0, 1.3):
        dx, dy, peak = drift.phase_shift(texture, shifted * scale)
        assert abs(dx - 2.5) < 0.1 and abs(dy + 7.25) < 0.1
        assert peak > config.DRIFT_MIN_PEAK


def test_phase_shift_sign_is_photo_position_minus_reference_position():
    texture = _texture()
    dx, dy, _ = drift.phase_shift(texture, numpy.roll(texture, (3, 5), axis=(0, 1)))
    assert (round(dx), round(dy)) == (5, 3)


def test_phase_shift_of_unrelated_images_has_a_low_peak():
    _, _, peak = drift.phase_shift(_texture(1), _texture(2))
    assert peak < config.DRIFT_MIN_PEAK


# ---- planned drifts on a synthetic session ------------------------------------------------------------------------

@pytest.fixture(scope="module")
def drifting_session(tmp_path_factory):
    """12 darts: none, a 3 px translation from dart 4, a 0.5 degree roll from dart 7, a later net move from dart 10."""
    spec = synth.SessionSpec(
        (SINGLE,) * 12, photo_size=BIG, video_size=VIDEO,
        drifts=(synth.Drift(4, dx=3.0), synth.Drift(7, roll=0.5), synth.Drift(10, dx=6.0, dy=-3.0, roll=0.5)))
    session = synth.make_session(tmp_path_factory.mktemp("drifting"), spec, seed=3)
    annotations = ann.read(session.annotations_path)
    return session, drift.measure_session(session.folder, annotations)


def test_each_point_shift_is_within_a_quarter_pixel_of_the_true_view(drifting_session):
    session, result = drifting_session
    for number, photo in result.photos.items():
        truth = numpy.array(session.calibration[number]) - numpy.array(session.calibration[1])
        assert photo.status in (drift.OK, drift.BEYOND), number
        assert numpy.abs(numpy.array(photo.shifts_px) - truth).max() < 0.25, number


def test_drift_mm_is_the_cv9_error_of_the_true_views(drifting_session):
    session, result = drifting_session
    reference = geometry.board_from_image(session.calibration[1])
    for number, photo in result.photos.items():
        expected = geometry.calibration_error_mm(session.homographies[number], reference)
        assert abs(photo.drift_mm - expected) < 0.05, number
        assert abs(photo.drift_px - max(math.hypot(*p) for p in photo.shifts_px)) < 1e-9


def test_reference_photo_has_exactly_no_drift(drifting_session):
    session, result = drifting_session
    first = result.photos[1]
    assert (first.status, first.drift_px, first.drift_mm) == (drift.OK, 0.0, 0.0)
    assert first.shifts_px == ((0.0, 0.0),) * 4
    assert first.reference_points == tuple(ann.read(session.annotations_path).calibrations[0].points)


def test_steady_photos_are_ok_and_clear_moves_are_beyond(drifting_session):
    _, result = drifting_session
    assert {n: result.photos[n].status for n in (2, 3)} == {2: drift.OK, 3: drift.OK}
    assert {result.photos[n].status for n in (7, 8, 9, 10, 11, 12)} == {drift.BEYOND}
    assert [n for n, p in result.photos.items() if p.status == drift.BEYOND][-6:] == [7, 8, 9, 10, 11, 12]


def test_result_names_the_segment_list_it_measured(drifting_session):
    session, result = drifting_session
    annotations = ann.read(session.annotations_path)
    assert result.session == annotations.session
    assert result.segments_sha256 == ann.segments_sha256(annotations)
    assert result.segments == 1
    assert sorted(result.photos) == [dart.number for dart in annotations.darts]


# ---- compensation (CV19) ------------------------------------------------------------------------------------------

def test_reference_points_are_the_segment_points_plus_the_shifts():
    reference, photo, segment, truth = _pair(synth.View(drift_dx=0.4, drift_dy=0.3), BIG)
    result = drift.measure_photo(reference, photo, segment, PARAMS)
    assert result.status == drift.OK and result.reason is None
    expected = numpy.array(segment.points) + numpy.array(result.shifts_px)
    assert numpy.allclose(result.reference_points, expected, atol=1e-9)
    assert numpy.abs(numpy.array(result.reference_points) - numpy.array(truth.calibration)).max() < 0.25


def test_no_reference_points_when_beyond():
    reference, photo, segment, _ = _pair(synth.View(drift_dx=6.0, drift_dy=-3.0, drift_roll=0.5))
    result = drift.measure_photo(reference, photo, segment, PARAMS)
    assert result.status == drift.BEYOND and result.reference_points is None
    assert result.shifts_px is not None and result.drift_mm > PARAMS.max_mm


def test_translation_is_measured_whatever_the_darts_on_the_board():
    reference, photo, segment, truth = _pair(synth.View(drift_dx=2.0, drift_dy=1.0), darts=[(0.0, 60.0), (40.0, 100.0)])
    result = drift.measure_photo(reference, photo, segment, PARAMS)
    assert numpy.abs(numpy.array(result.shifts_px) - _true_shifts(segment, truth)).max() < 0.5


def test_threshold_boundary_is_inclusive_and_read_from_the_parameters():
    reference, photo, segment, _ = _pair(synth.View(drift_dx=0.4, drift_dy=0.3))
    measured = drift.measure_photo(reference, photo, segment, PARAMS).drift_mm
    at = drift.measure_photo(reference, photo, segment, replace(PARAMS, max_mm=measured))
    below = drift.measure_photo(reference, photo, segment, replace(PARAMS, max_mm=math.nextafter(measured, 0.0)))
    assert at.status == drift.OK and at.reference_points is not None
    assert below.status == drift.BEYOND and below.reference_points is None


def test_default_parameters_come_from_config():
    assert PARAMS == drift.DriftParams(config.DRIFT_MAX_MM, config.DRIFT_CANDIDATES_MM, config.DRIFT_COARSE_PX,
                                       config.DRIFT_PATCH_FRACTION, config.DRIFT_MIN_PEAK)


# ---- unmeasurable photos ------------------------------------------------------------------------------------------

def test_photo_of_another_size_is_size_mismatch():
    reference, photo, segment, _ = _pair(synth.View())
    result = drift.measure_photo(reference, photo[:-10], segment, PARAMS)
    assert (result.status, result.reason) == (drift.UNMEASURABLE, drift.SIZE_MISMATCH)
    assert result.shifts_px is result.drift_px is result.drift_mm is result.reference_points is None
    other = replace(segment, width=segment.width + 1)
    assert drift.measure_photo(reference, photo, other, PARAMS).reason == drift.SIZE_MISMATCH


def test_a_patch_past_the_border_is_border():
    reference, photo, segment, _ = _pair(synth.View())
    corner = ((3.0, 3.0), (SMALL[0] - 3.0, 3.0), (SMALL[0] - 3.0, SMALL[1] - 3.0), (3.0, SMALL[1] - 3.0))
    result = drift.measure_photo(reference, photo, replace(segment, points=corner), PARAMS)
    assert (result.status, result.reason) == (drift.UNMEASURABLE, drift.BORDER)


def test_an_occluder_on_one_corner_is_low_peak():
    reference, photo, segment, _ = _pair(synth.View())
    x, y = (int(v) for v in segment.points[2])
    photo = photo.copy()
    photo[y - 30:y + 30, x - 30:x + 30] = 40.0
    result = drift.measure_photo(reference, photo, segment, PARAMS)
    assert (result.status, result.reason) == (drift.UNMEASURABLE, drift.LOW_PEAK)


def test_a_flat_photo_is_low_peak():
    reference, _, segment, _ = _pair(synth.View())
    result = drift.measure_photo(reference, numpy.full(reference.shape, 90.0, dtype=numpy.float32), segment, PARAMS)
    assert (result.status, result.reason) == (drift.UNMEASURABLE, drift.LOW_PEAK)


def test_collinear_points_are_uncalibrated():
    reference, photo, segment, _ = _pair(synth.View())
    line = tuple((100.0 + 50 * i, 100.0) for i in range(4))
    result = drift.measure_photo(reference, photo, replace(segment, points=line), PARAMS)
    assert (result.status, result.reason) == (drift.UNMEASURABLE, drift.UNCALIBRATED)


@pytest.fixture(scope="module")
def small_session(tmp_path_factory):
    spec = synth.SessionSpec((SINGLE, synth.DartSpec(19), synth.DartSpec(5), synth.DartSpec(1), synth.DartSpec(18),
                              synth.DartSpec(4)), photo_size=SMALL, video_size=VIDEO, drifts=(synth.Drift(4, dx=2.0),))
    return synth.make_session(tmp_path_factory.mktemp("small"), spec, seed=5)


def _copy(session, tmp_path):
    """A writable copy: (captures folder, session folder, annotations)."""
    shutil.copytree(session.folder, tmp_path / session.spec.name)
    return tmp_path / session.spec.name, ann.read(session.annotations_path)


def test_a_session_without_calibration_is_uncalibrated_for_every_dart(small_session, tmp_path):
    folder, annotations = _copy(small_session, tmp_path)
    result = drift.measure_session(folder, replace(annotations, calibrations=()))
    assert {p.reason for p in result.photos.values()} == {drift.UNCALIBRATED}
    assert {p.status for p in result.photos.values()} == {drift.UNMEASURABLE}
    assert result.segments == 0


def test_truncated_jpeg_is_unreadable_and_the_run_continues(small_session, tmp_path):
    folder, annotations = _copy(small_session, tmp_path)
    broken = folder / "dart-003.jpg"
    broken.write_bytes(broken.read_bytes()[:400])
    result = drift.measure_session(folder, annotations)
    assert (result.photos[3].status, result.photos[3].reason) == (drift.UNMEASURABLE, drift.UNREADABLE)
    assert result.photos[2].status in (drift.OK, drift.BEYOND)
    assert result.photos[4].status in (drift.OK, drift.BEYOND)


def test_missing_and_garbage_photos_are_unreadable(small_session, tmp_path):
    folder, annotations = _copy(small_session, tmp_path)
    (folder / "dart-002.jpg").unlink()
    (folder / "dart-004.jpg").write_bytes(b"not a jpeg")
    result = drift.measure_session(folder, annotations)
    assert result.photos[2].reason == result.photos[4].reason == drift.UNREADABLE


def test_unreadable_reference_makes_the_other_photos_of_its_segment_unreadable(small_session, tmp_path):
    folder, annotations = _copy(small_session, tmp_path)
    (folder / annotations.calibrations[0].photo).write_bytes(b"")
    result = drift.measure_session(folder, annotations)
    reference_number = int(re.search(r"\d+", annotations.calibrations[0].photo).group())
    assert result.photos[reference_number].status == drift.OK  # drift 0 without computation
    assert {p.reason for n, p in result.photos.items() if n != reference_number} == {drift.UNREADABLE}


def test_session_photo_of_another_size_is_size_mismatch(small_session, tmp_path):
    folder, annotations = _copy(small_session, tmp_path)
    Image.fromarray(numpy.zeros((100, 120, 3), dtype=numpy.uint8)).save(folder / "dart-003.jpg")
    result = drift.measure_session(folder, annotations)
    assert result.photos[3].reason == drift.SIZE_MISMATCH


@pytest.mark.parametrize("limit", [100, SMALL[0] * SMALL[1] - 1])  # above twice the limit (error), above it (warning)
def test_decompression_bomb_is_unreadable_never_an_exception(small_session, tmp_path, monkeypatch, limit):
    folder, annotations = _copy(small_session, tmp_path)
    default = Image.MAX_IMAGE_PIXELS
    monkeypatch.setattr(Image, "MAX_IMAGE_PIXELS", limit)  # lowered in this test only; monkeypatch restores it
    result = drift.measure_session(folder, annotations)
    assert {p.reason for n, p in result.photos.items() if n != 1} == {drift.UNREADABLE}
    monkeypatch.undo()
    assert Image.MAX_IMAGE_PIXELS == default


def test_the_decoder_never_changes_the_pillow_pixel_limit():
    source = (TOOL_ROOT / "kosmo_camera" / "drift.py").read_text(encoding="utf-8")
    assert not re.search(r"MAX_IMAGE_PIXELS\s*=", source) and "LOAD_TRUNCATED_IMAGES" not in source


def test_exif_orientation_is_ignored(small_session, tmp_path):
    folder, annotations = _copy(small_session, tmp_path)
    before = drift.measure_session(folder, annotations)
    path = folder / "dart-003.jpg"
    with Image.open(path) as image:
        exif = Image.Exif()
        exif[0x0112] = 6  # rotate 90 degrees: honoured by viewers, ignored here (D8)
        image.save(path, exif=exif, quality=90)
    after = drift.measure_session(folder, annotations)
    assert after.photos[3].status == before.photos[3].status


# ---- segments (A15) -----------------------------------------------------------------------------------------------

def test_segment_of_each_dart_comes_from_annotations_segment_for(small_session, monkeypatch):
    annotations = ann.read(small_session.annotations_path)
    calls = []
    original = ann.segment_for

    def spy(source, number):
        calls.append(number)
        return original(source, number)
    monkeypatch.setattr(ann, "segment_for", spy)
    drift.measure_session(small_session.folder, annotations)
    assert calls == [dart.number for dart in annotations.darts]


def test_two_segments_each_measure_against_their_own_reference(synthetic_session):
    annotations = ann.read(synthetic_session.annotations_path)
    assert len(annotations.calibrations) == 2
    result = drift.measure_session(synthetic_session.folder, annotations)
    assert sorted(result.photos) == [1, 3, 4, 6]
    assert result.segments == 2
    second = annotations.calibrations[1]
    assert second.from_dart == 4 and second.photo == "dart-004.jpg"
    assert (result.photos[4].drift_px, result.photos[4].drift_mm) == (0.0, 0.0)  # reference of segment 2
    assert result.photos[1].drift_mm == 0.0
    assert result.photos[3].status != drift.UNMEASURABLE and result.photos[6].status != drift.UNMEASURABLE
    # photo 6 shares the drift of the segment-2 reference, so it stays near it; against segment 1 it would be 7 px away
    assert result.photos[6].drift_px < 2.0


# ---- suggested starts and guard -----------------------------------------------------------------------------------

def _photo(status, mm=None, reason=None):
    shifts = None if mm is None else ((mm, 0.0),) * 4
    points = tuple((float(i), float(i)) for i in range(4)) if status == drift.OK else None
    return drift.PhotoDrift(status, reason, shifts, mm, mm, points)


def _session(name, kinds):
    """`kinds`: dart number -> 'ok' | 'beyond' | 'unmeasurable' | a drift in mm."""
    photos = {}
    for number, kind in kinds.items():
        if kind == "unmeasurable":
            photos[number] = _photo(drift.UNMEASURABLE, reason=drift.UNREADABLE)
        elif kind == "beyond":
            photos[number] = _photo(drift.BEYOND, 9.0)
        elif kind == "ok":
            photos[number] = _photo(drift.OK, 0.1)
        else:
            mm = float(kind)
            photos[number] = _photo(drift.OK if mm <= PARAMS.max_mm else drift.BEYOND, mm)
    return drift.SessionDrift(name, "0" * 64, 1, photos)


def _run(first, last, kind="beyond"):
    return {n: kind for n in range(first, last + 1)}


def test_suggested_starts_are_the_first_dart_of_each_run():
    kinds = {**_run(1, 11, "ok"), **_run(12, 14), **_run(15, 19, "ok"), 20: "beyond"}
    assert drift.suggested_starts(_session("20261101-100000", kinds)) == (12, 20)


def test_an_unmeasurable_dart_does_not_split_a_run_nor_start_one():
    kinds = {11: "ok", 12: "beyond", 13: "unmeasurable", 14: "beyond", 15: "ok", 16: "unmeasurable", 17: "ok"}
    assert drift.suggested_starts(_session("20261101-100000", kinds)) == (12,)
    assert drift.suggested_starts(_session("20261101-100000", {5: "unmeasurable", 6: "beyond"})) == (6,)


def test_no_beyond_dart_means_no_suggestion():
    assert drift.suggested_starts(_session("20261101-100000", _run(1, 5, "ok"))) == ()
    assert drift.suggested_starts(_session("20261101-100000", {})) == ()


def test_numbers_are_taken_in_dart_order_whatever_the_dictionary_order():
    session = _session("20261101-100000", {20: "beyond", 12: "beyond", 13: "beyond", 15: "ok"})
    assert drift.suggested_starts(session) == (12, 20)


def test_require_clean_raises_in_french_when_a_photo_is_beyond():
    drifts = [_session("20261101-100000", {1: "ok", 2: "beyond", 3: "beyond"}), _session("20261102-090000", {1: "ok"})]
    with pytest.raises(drift.DriftError, match="drift") as raised:
        drift.require_clean(drifts, accept_drift=False)
    assert "2 photo" in str(raised.value) and "recalibrer" in str(raised.value)


def test_require_clean_counts_the_excluded_photos_when_drift_is_accepted():
    drifts = [_session("20261101-100000", {1: "ok", 2: "beyond", 3: "beyond"}), _session("20261102-090000", {1: "beyond"})]
    assert drift.require_clean(drifts, accept_drift=True) == 3


def test_require_clean_returns_zero_on_a_clean_set_and_ignores_unmeasurable_photos():
    drifts = [_session("20261101-100000", {1: "ok", 2: "unmeasurable"})]
    assert drift.require_clean(drifts, accept_drift=False) == 0
    assert drift.require_clean([], accept_drift=False) == 0


# ---- report and flag file -----------------------------------------------------------------------------------------

CANDIDATES = replace(PARAMS, candidates_mm=(0.5, 1.0, 2.0))


def _table(report, title):
    lines = report.split(f"## {title}")[1].splitlines()
    rows = [line for line in lines if line.startswith("|")][2:]
    return [[cell.strip() for cell in row.strip("|").split("|")] for row in rows]


def _known_drifts():
    first = _session("20261101-100000", {1: 0.2, 2: 0.7, 3: 1.5})
    second = _session("20261102-090000", {1: 0.1, 2: 2.5, 3: "unmeasurable"})
    return [second, first]  # not in name order on purpose


def test_candidate_table_counts_sessions_and_darts_beyond_each_value():
    report = drift.build_report(_known_drifts(), CANDIDATES)
    assert _table(report, "Seuils candidats") == [["0.5", "2", "3"], ["1", "2", "2"], ["2", "1", "1"]]


def test_a_drift_exactly_at_a_candidate_is_not_beyond_it():
    session = _session("20261101-100000", {1: 0.5, 2: 1.0, 3: 2.0})
    assert _table(drift.build_report([session], CANDIDATES), "Seuils candidats") == [
        ["0.5", "1", "2"], ["1", "1", "1"], ["2", "0", "0"]]


def test_report_rows_are_labelled_in_session_name_order_with_the_counts():
    report = drift.build_report(_known_drifts(), CANDIDATES)
    rows = [line for line in report.split("## Seuils candidats")[0].splitlines() if re.match(r"\| S\d", line)]
    assert [row.split("|")[1].strip() for row in rows] == ["S01", "S02"]
    first = [cell.strip() for cell in rows[0].strip("|").split("|")]
    assert first[1:4] == ["3", "1", "3"]  # judged photos, segments, measured
    assert first[7:] == ["1", "3"]  # darts beyond (1.5 only: 0.7 is under the threshold), suggested start dart
    second = [cell.strip() for cell in rows[1].strip("|").split("|")]
    assert second[1:4] == ["3", "1", "2"] and "unreadable 1" in second[4]


def test_report_states_the_threshold_its_reason_and_the_verdict():
    report = drift.build_report(_known_drifts(), CANDIDATES)
    assert f"Seuil : {CANDIDATES.max_mm:g} mm" in report and "CV19" in report
    assert "Total : 6 photos jugées, 5 mesurées," in report
    assert report.rstrip().endswith("Tout sous le seuil : non")
    clean = drift.build_report([_session("20261101-100000", {1: "ok", 2: 0.3})], CANDIDATES)
    assert clean.rstrip().endswith("Tout sous le seuil : oui")
    assert drift.build_report([], CANDIDATES).rstrip().endswith("Tout sous le seuil : oui")


def test_report_has_no_session_name_file_name_or_path_cv15(small_session, tmp_path):
    annotations = ann.read(small_session.annotations_path)
    result = drift.measure_session(small_session.folder, annotations)
    report = drift.build_report([result], PARAMS)
    for forbidden in (annotations.session, small_session.folder.name, "dart-", ".jpg", str(tmp_path), str(small_session.folder),
                      "annotations", "captures"):
        assert forbidden not in report
    assert "S01" in report


def test_write_outputs_writes_both_files_and_overwrites_them(tmp_path):
    runs = tmp_path / "runs"
    runs.mkdir()
    report = drift.write_outputs(_known_drifts(), runs, CANDIDATES)
    assert report == runs / "drift" / "drift-report.md"
    flags = drift_file.read(runs / "drift" / "drift.json")
    assert flags.threshold_mm == CANDIDATES.max_mm
    assert [(s.session, s.label) for s in flags.sessions] == [("20261101-100000", "S01"), ("20261102-090000", "S02")]
    first, second = flags.sessions
    assert (first.beyond, first.suggested, first.unmeasurable) == ((3,), (3,), ())
    assert (second.beyond, second.suggested, second.unmeasurable, second.max_drift_mm) == ((2,), (2,), (3,), 2.5)
    assert first.max_drift_mm == 1.5 and first.segments_sha256 == "0" * 64
    drift.write_outputs([_session("20261103-080000", {1: "ok"})], runs, CANDIDATES)
    assert [s.session for s in drift_file.read(runs / "drift" / "drift.json").sessions] == ["20261103-080000"]
    assert sorted(p.name for p in (runs / "drift").iterdir()) == ["drift-report.md", "drift.json"]


def test_session_without_measure_has_a_null_maximum(tmp_path):
    session = _session("20261101-100000", {1: "unmeasurable"})
    drift.write_outputs([session], tmp_path, CANDIDATES)
    only = drift_file.read(tmp_path / "drift" / "drift.json").sessions[0]
    assert only.max_drift_mm is None and only.unmeasurable == (1,)


# ---- the command as Kosmo runs it (K5.2) --------------------------------------------------------------------------

def _command(captures, annotations, runs, cwd):
    env = {"PATH": os.environ.get("PATH", ""), "PYTHONPATH": str(TOOL_ROOT)}
    return subprocess.run([sys.executable, "-m", "kosmo_camera.drift", "--captures", str(captures),
                           "--annotations", str(annotations), "--runs", str(runs)],
                          cwd=cwd, env=env, capture_output=True, text=True, check=False)


def test_command_writes_both_outputs_and_prints_the_two_lines(small_session, tmp_path):
    captures = small_session.folder.parent
    runs, elsewhere = tmp_path / "runs", tmp_path / "elsewhere"
    runs.mkdir()
    elsewhere.mkdir()
    done = _command(captures, captures / "annotations", runs, elsewhere)
    assert done.returncode == 0, done.stderr
    lines = done.stdout.splitlines()
    assert lines[0] == str(runs / "drift" / "drift-report.md") and len(lines) == 2
    assert re.fullmatch(r"Tout sous le seuil : (oui|non)", lines[1])
    flags = drift_file.read(runs / "drift" / "drift.json")
    assert [s.session for s in flags.sessions] == [small_session.spec.name]
    assert flags.sessions[0].segments_sha256 == ann.segments_sha256(ann.read(small_session.annotations_path))
    report = (runs / "drift" / "drift-report.md").read_text(encoding="utf-8")
    assert small_session.spec.name not in report and lines[1] in report


def test_command_leaves_the_sources_untouched(small_session, tmp_path):
    captures = small_session.folder.parent
    before = sorted((p.relative_to(captures), p.stat().st_mtime_ns, p.stat().st_size) for p in captures.rglob("*"))
    runs = tmp_path / "runs"
    runs.mkdir()
    assert _command(captures, captures / "annotations", runs, tmp_path).returncode == 0
    assert before == sorted((p.relative_to(captures), p.stat().st_mtime_ns, p.stat().st_size) for p in captures.rglob("*"))


def test_command_exit_2_on_a_missing_folder(small_session, tmp_path):
    captures = small_session.folder.parent
    done = _command(captures, tmp_path / "absent", tmp_path, tmp_path)
    assert done.returncode == 2 and done.stdout == ""


def test_command_exit_2_on_an_annotation_file_the_strict_reader_rejects(tmp_path):
    (tmp_path / "captures").mkdir()
    (tmp_path / "annotations").mkdir()
    (tmp_path / "runs").mkdir()
    (tmp_path / "annotations" / "20261101-100000.json").write_text("{}", encoding="utf-8")
    done = _command(tmp_path / "captures", tmp_path / "annotations", tmp_path / "runs", tmp_path)
    assert done.returncode == 2 and "20261101-100000.json" in done.stderr
    assert not (tmp_path / "runs" / "drift").exists()


def test_command_exit_2_when_the_session_name_differs_from_the_file_name(small_session, tmp_path):
    captures = small_session.folder.parent
    folder = tmp_path / "annotations"
    folder.mkdir()
    shutil.copy(small_session.annotations_path, folder / "20261231-235959.json")
    runs = tmp_path / "runs"
    runs.mkdir()
    done = _command(captures, folder, runs, tmp_path)
    assert done.returncode == 2 and "differs from the file name" in done.stderr


def test_command_with_no_session_reports_clean(tmp_path):
    for name in ("captures", "annotations", "runs"):
        (tmp_path / name).mkdir()
    done = _command(tmp_path / "captures", tmp_path / "annotations", tmp_path / "runs", tmp_path)
    assert done.returncode == 0 and done.stdout.splitlines()[1] == "Tout sous le seuil : oui"
