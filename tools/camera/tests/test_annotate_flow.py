"""Per-dart flow (T6a): CV2 calibration order, CV5 judged darts, resume cursor, replace, clear, skips, autosave;
segment 0 of the calibration list (T5b, CV20)."""
import pytest

from annotate_support import NAME, SIZE, make_session, truth_dict
from kosmo_camera import annotations as an
from kosmo_camera import truth as truth_module
from kosmo_camera.annotate import flow
from kosmo_camera.annotate.flow import Action, Cursor, FlowError
from kosmo_camera.annotations import Annotations, Calibration, DartAnnotation, Status

POINTS = ((10.0, 20.0), (30.0, 40.0), (50.0, 60.0), (70.0, 80.0))


def session_of(tmp_path, **kwargs):
    folder, _ = make_session(tmp_path, **kwargs)
    return truth_module.read_truth(folder / "truth.json")


def empty(session):
    return Annotations(NAME, session.sha256, (), ())


def click(x=100.0, y=200.0):
    return Action("click", x, y, size=SIZE)


def calibrated(session, darts=()):
    return Annotations(NAME, session.sha256, (Calibration(1, "dart-001.jpg", *SIZE, POINTS),), tuple(darts))


def calibrate(session):
    annotations, cursor = empty(session), None
    for x, y in POINTS:
        annotations, cursor = flow.apply(annotations, session, click(x, y), cursor)
    return annotations, cursor


def test_plan_lists_judged_darts_and_skipped_ones_cv5(tmp_path):
    plan = flow.plan_of(session_of(tmp_path))
    assert plan.darts == ((1, "dart-001.jpg"), (3, "dart-003.jpg"), (4, "dart-004.jpg"))
    assert plan.skipped == (2, 5)  # 2 is voided, 5 has no photo
    assert plan.first_photo == "dart-001.jpg"


def test_resume_starts_with_calibration_cv2(tmp_path):
    session = session_of(tmp_path)
    assert flow.resume(empty(session), session) == Cursor(flow.CALIBRATION, photo="dart-001.jpg")


def test_calibration_points_one_to_four_then_first_dart_cv2(tmp_path):
    session = session_of(tmp_path)
    annotations, cursor = empty(session), None
    for count, (x, y) in enumerate(POINTS, start=1):
        annotations, cursor = flow.apply(annotations, session, click(x, y), cursor)
        if count < 4:
            assert annotations.calibrations == () and cursor.kind == flow.CALIBRATION
            assert cursor.pending == POINTS[:count]
    assert annotations.calibrations == (Calibration(1, "dart-001.jpg", SIZE[0], SIZE[1], POINTS),)  # segment 0 (CV20)
    assert cursor == Cursor(flow.DART, 1, "dart-001.jpg")


def test_calibration_takes_the_real_jpeg_size_given_with_the_click(tmp_path):
    session = session_of(tmp_path)
    annotations, cursor = empty(session), None
    for x, y in POINTS:
        annotations, cursor = flow.apply(annotations, session, Action("click", x, y, size=(2000, 1000)), cursor)
    assert (annotations.calibrations[0].width, annotations.calibrations[0].height) == (2000, 1000)


def test_click_on_a_dart_sets_the_tip_visible_and_moves_on(tmp_path):
    session = session_of(tmp_path)
    annotations, cursor = calibrate(session)
    annotations, cursor = flow.apply(annotations, session, click(5.5, 6.5), cursor)
    assert annotations.darts == (DartAnnotation(1, Status.VISIBLE, (5.5, 6.5)),)
    assert cursor == Cursor(flow.DART, 3, "dart-003.jpg")  # dart 2 is voided: skipped


@pytest.mark.parametrize("kind,status", [("hidden", Status.HIDDEN), ("off_board", Status.OFF_BOARD)])
def test_hidden_and_off_board_set_the_status_and_move_on(tmp_path, kind, status):
    session = session_of(tmp_path)
    annotations, cursor = calibrate(session)
    annotations, cursor = flow.apply(annotations, session, Action(kind), cursor)
    assert annotations.darts == (DartAnnotation(1, status, None),)
    assert cursor.number == 3


def test_replace_a_tip_on_an_annotated_dart_and_move_on(tmp_path):
    session = session_of(tmp_path)
    annotations = calibrated(session, [DartAnnotation(1, Status.VISIBLE, (1.0, 2.0)),
                                       DartAnnotation(3, Status.HIDDEN, None)])
    cursor = Cursor(flow.DART, 1, "dart-001.jpg")
    annotations, cursor = flow.apply(annotations, session, click(9.0, 8.0), cursor)
    assert annotations.darts[0] == DartAnnotation(1, Status.VISIBLE, (9.0, 8.0))
    assert len(annotations.darts) == 2
    assert cursor.number == 3  # the next dart in order, annotated or not


def test_replace_a_status_by_a_tip_and_a_tip_by_a_status(tmp_path):
    session = session_of(tmp_path)
    annotations = calibrated(session, [DartAnnotation(3, Status.HIDDEN, None)])
    cursor = Cursor(flow.DART, 3, "dart-003.jpg")
    annotations, _ = flow.apply(annotations, session, click(1.0, 1.0), cursor)
    assert DartAnnotation(3, Status.VISIBLE, (1.0, 1.0)) in annotations.darts
    annotations, _ = flow.apply(annotations, session, Action("off_board"), cursor)
    assert DartAnnotation(3, Status.OFF_BOARD, None) in annotations.darts


def test_after_the_last_dart_the_cursor_goes_to_the_first_open_dart_then_complete(tmp_path):
    session = session_of(tmp_path)
    annotations = calibrated(session, [DartAnnotation(3, Status.HIDDEN, None)])
    annotations, cursor = flow.apply(annotations, session, Action("hidden"), Cursor(flow.DART, 4, "dart-004.jpg"))
    assert cursor.number == 1  # dart 1 is still open
    annotations, cursor = flow.apply(annotations, session, Action("hidden"), cursor)
    assert cursor.number == 3  # next in order, even if annotated
    annotations, cursor = flow.apply(annotations, session, Action("hidden"), Cursor(flow.DART, 4, "dart-004.jpg"))
    assert cursor == Cursor(flow.COMPLETE)
    assert [d.number for d in annotations.darts] == [1, 3, 4]  # ascending, as the strict writer needs


def test_clear_removes_the_annotation_of_the_current_dart_and_the_cursor_stays(tmp_path):
    session = session_of(tmp_path)
    annotations = calibrated(session, [DartAnnotation(1, Status.VISIBLE, (1.0, 2.0)),
                                       DartAnnotation(3, Status.HIDDEN, None)])
    cursor = Cursor(flow.DART, 3, "dart-003.jpg")
    annotations, new_cursor = flow.apply(annotations, session, Action("clear"), cursor)
    assert [d.number for d in annotations.darts] == [1]
    assert new_cursor == cursor
    same, _ = flow.apply(annotations, session, Action("clear"), cursor)  # nothing to clear: no change
    assert same == annotations


def test_clear_in_calibration_removes_the_last_point_and_the_cursor_stays(tmp_path):
    session = session_of(tmp_path)
    annotations, cursor = empty(session), None
    for x, y in POINTS[:3]:
        annotations, cursor = flow.apply(annotations, session, click(x, y), cursor)
    annotations, cursor = flow.apply(annotations, session, Action("clear"), cursor)
    assert cursor.kind == flow.CALIBRATION and cursor.pending == POINTS[:2]
    _, cursor = flow.apply(annotations, session, click(*POINTS[2]), cursor)
    assert cursor.pending == POINTS[:3]


def test_clear_on_empty_calibration_changes_nothing(tmp_path):
    session = session_of(tmp_path)
    annotations = empty(session)
    cursor = flow.resume(annotations, session)
    assert flow.apply(annotations, session, Action("clear"), cursor) == (annotations, cursor)


def test_previous_from_the_first_dart_reopens_the_calibration_which_clear_then_redoes(tmp_path):
    session = session_of(tmp_path)
    annotations, cursor = calibrate(session)
    _, cursor = flow.apply(annotations, session, Action("previous"), cursor)
    assert cursor.kind == flow.CALIBRATION and cursor.pending == POINTS
    annotations, cursor = flow.apply(annotations, session, Action("clear"), cursor)
    assert annotations.calibrations == () and cursor.pending == POINTS[:3]
    annotations, cursor = flow.apply(annotations, session, click(1.0, 2.0), cursor)
    assert annotations.calibrations[0].points[3] == (1.0, 2.0)


def test_click_with_four_points_already_set_in_calibration_is_ignored(tmp_path):
    session = session_of(tmp_path)
    annotations, cursor = calibrate(session)
    _, cursor = flow.apply(annotations, session, Action("previous"), cursor)
    assert flow.apply(annotations, session, click(), cursor) == (annotations, cursor)


def test_next_and_previous_only_move(tmp_path):
    session = session_of(tmp_path)
    annotations = calibrated(session, [DartAnnotation(1, Status.HIDDEN, None)])
    cursor = Cursor(flow.DART, 3, "dart-003.jpg")
    same, cursor = flow.apply(annotations, session, Action("next"), cursor)
    assert same == annotations and cursor.number == 4
    same, cursor = flow.apply(annotations, session, Action("next"), cursor)
    assert cursor.number == 4  # stays on the last dart
    _, cursor = flow.apply(annotations, session, Action("previous"), cursor)
    assert cursor.number == 3


def test_next_is_refused_to_leave_calibration_before_it_is_complete(tmp_path):
    session = session_of(tmp_path)
    annotations, cursor = empty(session), None
    annotations, cursor = flow.apply(annotations, session, click(), cursor)
    assert flow.apply(annotations, session, Action("next"), cursor) == (annotations, cursor)
    with pytest.raises(FlowError):
        flow.apply(annotations, session, Action("hidden"), cursor)


def test_previous_from_complete_goes_to_the_last_dart(tmp_path):
    session = session_of(tmp_path)
    _, cursor = flow.apply(calibrated(session), session, Action("previous"), Cursor(flow.COMPLETE))
    assert cursor.number == 4


def test_goto_selects_a_judged_dart_only_after_calibration(tmp_path):
    session = session_of(tmp_path)
    annotations = calibrated(session)
    _, cursor = flow.apply(annotations, session, Action("goto", number=4), None)
    assert cursor == Cursor(flow.DART, 4, "dart-004.jpg")
    for number in (2, 5, 99):  # voided, no photo, unknown
        with pytest.raises(FlowError):
            flow.apply(annotations, session, Action("goto", number=number), None)
    with pytest.raises(FlowError):
        flow.apply(empty(session), session, Action("goto", number=1), None)


@pytest.mark.parametrize("x,y", [(-0.1, 5), (5, -1), (SIZE[0] + 0.5, 5), (5, SIZE[1] + 1),
                                 (float("nan"), 5), (float("inf"), 5), (True, 5), ("5", 5)])
def test_click_outside_the_photo_or_not_a_number_is_refused(tmp_path, x, y):
    session = session_of(tmp_path)
    annotations, cursor = calibrate(session)
    with pytest.raises(FlowError):
        flow.apply(annotations, session, Action("click", x, y, size=SIZE), cursor)


def test_click_on_the_photo_border_is_accepted(tmp_path):
    session = session_of(tmp_path)
    annotations, cursor = calibrate(session)
    updated, _ = flow.apply(annotations, session, Action("click", SIZE[0], 0, size=SIZE), cursor)
    assert updated.darts[0].tip == (float(SIZE[0]), 0.0)


def test_click_without_a_known_photo_size_is_refused(tmp_path):
    session = session_of(tmp_path)
    with pytest.raises(FlowError):
        flow.apply(empty(session), session, Action("click", 1, 1), None)


def test_unknown_action_is_refused(tmp_path):
    session = session_of(tmp_path)
    with pytest.raises(FlowError):
        flow.apply(empty(session), session, Action("delete"), None)


def test_resume_cursor_on_the_first_dart_not_annotated_then_complete(tmp_path):
    session = session_of(tmp_path)
    annotations = calibrated(session, [DartAnnotation(1, Status.HIDDEN, None), DartAnnotation(4, Status.HIDDEN, None)])
    assert flow.resume(annotations, session) == Cursor(flow.DART, 3, "dart-003.jpg")
    full = calibrated(session, [DartAnnotation(n, Status.HIDDEN, None) for n in (1, 3, 4)])
    assert flow.resume(full, session) == Cursor(flow.COMPLETE)


def test_resume_keeps_the_points_clicked_so_far(tmp_path):
    session = session_of(tmp_path)
    cursor = flow.resume(empty(session), session, POINTS[:2])
    assert cursor.pending == POINTS[:2]


def test_session_without_judged_dart_has_no_flow(tmp_path):
    session = session_of(tmp_path, judged=(), voided=(1, 2), no_photo=(3,))
    assert flow.plan_of(session).first_photo is None
    with pytest.raises(FlowError):
        flow.apply(empty(session), session, click(), None)


def test_autosave_round_trip_apply_write_reload_gives_the_same_file_and_cursor(tmp_path):
    session = session_of(tmp_path / "captures")
    annotations, cursor = calibrate(session)
    annotations, cursor = flow.apply(annotations, session, click(11.5, 12.5), cursor)
    annotations, cursor = flow.apply(annotations, session, Action("hidden"), cursor)
    path = tmp_path / "annotations.json"
    an.write_atomic(path, annotations)
    reloaded = an.read(path)
    assert reloaded == annotations
    assert an.validate_against(reloaded, session) == []
    assert flow.resume(reloaded, session) == cursor
    assert cursor == Cursor(flow.DART, 4, "dart-004.jpg")


def _two_segments(session, darts=()):
    later = Calibration(3, "dart-003.jpg", *SIZE, ((1.0, 1.0), (2.0, 2.0), (3.0, 3.0), (4.0, 4.0)))
    return Annotations(NAME, session.sha256, (Calibration(1, "dart-001.jpg", *SIZE, POINTS), later), tuple(darts))


def test_calibration_screen_edits_segment_0_and_keeps_later_segments_cv20(tmp_path):
    session = session_of(tmp_path)
    annotations = _two_segments(session)
    _, cursor = flow.apply(annotations, session, Action("previous"), Cursor(flow.DART, 1, "dart-001.jpg"))
    assert cursor.kind == flow.CALIBRATION and cursor.pending == POINTS  # segment 0 points
    assert flow.resume(annotations, session) == Cursor(flow.DART, 1, "dart-001.jpg")


def test_clear_of_segment_0_with_later_segments_is_refused_and_changes_nothing_cv20(tmp_path):
    session = session_of(tmp_path)
    annotations = _two_segments(session)
    cursor = Cursor(flow.CALIBRATION, photo="dart-001.jpg", pending=POINTS)
    with pytest.raises(FlowError):
        flow.apply(annotations, session, Action("clear"), cursor)


def test_flow_saves_version_2_with_one_segment_from_dart_1_cv20(tmp_path):
    session = session_of(tmp_path / "captures")
    annotations, cursor = calibrate(session)
    annotations, cursor = flow.apply(annotations, session, click(11.5, 12.5), cursor)
    path = tmp_path / "annotations.json"
    an.write_atomic(path, annotations)
    assert an.read(path).calibrations == (Calibration(1, "dart-001.jpg", *SIZE, POINTS),)
    assert '"version": 2' in path.read_text(encoding="utf-8")


def test_truth_dict_helper_gives_a_readable_truth(tmp_path):
    assert truth_dict()["session"] == NAME


# ---- recalibration from a judged dart (T6c, CV20) --------------------------------------------------------------------

NEW = ((11.0, 12.0), (13.0, 14.0), (15.0, 16.0), (17.0, 18.0))


def _at_dart(annotations, session, number):
    return flow.apply(annotations, session, Action("goto", number=number), None)[1]


def _recalibrate(annotations, session, number):
    cursor = _at_dart(annotations, session, number)
    return flow.apply(annotations, session, Action("recalibrate"), cursor)


def _tips(annotations):
    return [(d.number, d.tip) for d in annotations.darts]


def test_recalibrate_from_a_dart_takes_four_clicks_and_saves_the_segment_with_that_photo_cv20(tmp_path):
    session = session_of(tmp_path)
    tips = [DartAnnotation(1, Status.VISIBLE, (1.0, 2.0)), DartAnnotation(4, Status.VISIBLE, (3.0, 4.0))]
    annotations = calibrated(session, tips)
    _, cursor = _recalibrate(annotations, session, 3)
    assert cursor == Cursor(flow.RECALIBRATION, 3, "dart-003.jpg")
    for count, (x, y) in enumerate(NEW, start=1):
        updated, cursor = flow.apply(annotations, session, Action("click", x, y, size=(2000, 1000)), cursor)
        if count < 4:
            assert updated == annotations and cursor.pending == NEW[:count]
    assert updated.calibrations[1] == Calibration(3, "dart-003.jpg", 2000, 1000, NEW)
    assert updated.calibrations[0] == annotations.calibrations[0]
    assert cursor == Cursor(flow.DART, 3, "dart-003.jpg")
    assert _tips(updated) == _tips(annotations)  # tips never move


def test_fifth_click_of_a_full_recalibration_is_ignored(tmp_path):
    session = session_of(tmp_path)
    annotations = _two_segments(session)
    annotations, cursor = _recalibrate(annotations, session, 3)
    assert cursor.pending == _two_segments(session).calibrations[1].points
    assert flow.apply(annotations, session, click(5.0, 5.0), cursor) == (annotations, cursor)


def test_recalibrate_on_an_existing_start_shows_its_points_and_clear_removes_them_one_by_one_cv20(tmp_path):
    session = session_of(tmp_path)
    annotations = _two_segments(session, [DartAnnotation(4, Status.VISIBLE, (3.0, 4.0))])
    points = annotations.calibrations[1].points
    annotations, cursor = _recalibrate(annotations, session, 3)
    assert cursor.kind == flow.RECALIBRATION and cursor.pending == points
    annotations, cursor = flow.apply(annotations, session, Action("clear"), cursor)
    assert annotations.calibrations == _two_segments(session).calibrations[:1]  # removed at once (saved)
    assert cursor.pending == points[:3]
    for left in (2, 1, 0):
        _, cursor = flow.apply(annotations, session, Action("clear"), cursor)
        assert cursor.pending == points[:left]
    unchanged, cursor = flow.apply(annotations, session, Action("clear"), cursor)
    assert unchanged == annotations and cursor == Cursor(flow.DART, 3, "dart-003.jpg")
    assert _tips(annotations) == [(4, (3.0, 4.0))]


def test_clicking_again_replaces_the_segment_at_the_same_dart_cv20(tmp_path):
    session = session_of(tmp_path)
    annotations, cursor = _recalibrate(_two_segments(session), session, 3)
    for _ in range(4):
        annotations, cursor = flow.apply(annotations, session, Action("clear"), cursor)
    for x, y in NEW:
        annotations, cursor = flow.apply(annotations, session, Action("click", x, y, size=SIZE), cursor)
    assert [c.from_dart for c in annotations.calibrations] == [1, 3] and annotations.calibrations[1].points == NEW


@pytest.mark.parametrize("leave", [Action("next"), Action("previous"), Action("goto", number=4)])
def test_leaving_with_unsaved_points_drops_them_and_changes_nothing(tmp_path, leave):
    session = session_of(tmp_path)
    annotations = calibrated(session)
    cursor = _at_dart(annotations, session, 3)
    _, cursor = flow.apply(annotations, session, Action("recalibrate"), cursor)
    for x, y in NEW[:2]:
        _, cursor = flow.apply(annotations, session, click(x, y), cursor)
    assert cursor.pending == NEW[:2]
    same, left = flow.apply(annotations, session, leave, cursor)
    assert same == annotations and left.kind == flow.DART and left.pending == ()


def test_recalibrate_needs_a_dart_cursor_and_the_first_dart_opens_segment_0(tmp_path):
    session = session_of(tmp_path)
    annotations = calibrated(session)
    for cursor in (Cursor(flow.CALIBRATION, photo="dart-001.jpg", pending=POINTS), Cursor(flow.COMPLETE)):
        with pytest.raises(FlowError):
            flow.apply(annotations, session, Action("recalibrate"), cursor)
    with pytest.raises(FlowError):  # a voided entry is never a cursor: goto refuses it
        flow.apply(annotations, session, Action("goto", number=2), None)
    _, cursor = _recalibrate(annotations, session, 1)
    assert cursor.kind == flow.CALIBRATION and cursor.pending == POINTS
    with pytest.raises(FlowError):
        flow.apply(empty(session), session, Action("recalibrate"), Cursor(flow.DART, 3, "dart-003.jpg"))


def test_marks_are_refused_on_the_recalibration_screen(tmp_path):
    session = session_of(tmp_path)
    annotations, cursor = _recalibrate(calibrated(session), session, 3)
    for kind in ("hidden", "off_board"):
        with pytest.raises(FlowError):
            flow.apply(annotations, session, Action(kind), cursor)


def test_a_new_segment_goes_in_order_between_existing_ones(tmp_path):
    session = session_of(tmp_path, judged=(1, 3, 4, 6), voided=(2,), no_photo=(5,))
    annotations = _two_segments(session)  # segments from 1 and 3
    annotations, cursor = _recalibrate(annotations, session, 4)
    for x, y in NEW:
        annotations, cursor = flow.apply(annotations, session, Action("click", x, y, size=SIZE), cursor)
    assert [c.from_dart for c in annotations.calibrations] == [1, 3, 4]


def test_the_fourth_click_at_the_segment_cap_is_a_conflict_and_changes_nothing_cv20(tmp_path):
    session = session_of(tmp_path)
    base = calibrated(session)
    filler = tuple(Calibration(100 + i, "dart-003.jpg", *SIZE, POINTS) for i in range(an.MAX_SEGMENTS - 1))
    full = Annotations(NAME, session.sha256, base.calibrations + filler, ())
    cursor = _at_dart(full, session, 3)
    _, cursor = flow.apply(full, session, Action("recalibrate"), cursor)
    for x, y in NEW[:3]:
        _, cursor = flow.apply(full, session, click(x, y), cursor)
    with pytest.raises(flow.FlowConflict, match=r"Nombre maximal de recalibrations atteint \(64\)"):
        flow.apply(full, session, click(*NEW[3]), cursor)
