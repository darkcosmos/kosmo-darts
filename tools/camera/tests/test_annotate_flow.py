"""Per-dart flow (T6a): CV2 calibration order, CV5 judged darts, resume cursor, replace, clear, skips, autosave;
segment 0 of the calibration list (T5b, CV20)."""
import pytest

from annotate_support import NAME, SIZE, FakeSnapping, make_session, truth_dict
from kosmo_camera import annotations as an
from kosmo_camera import config
from kosmo_camera import truth as truth_module
from kosmo_camera.annotate import flow
from kosmo_camera.annotate.flow import Action, Cursor, FlowConflict, FlowError, Slot
from kosmo_camera.annotations import Annotations, Calibration, DartAnnotation, Status

POINTS = ((1000.0, 1000.0), (2000.0, 1000.0), (2000.0, 2000.0), (1000.0, 2000.0))  # far apart: U27 click rule


def apply(annotations, session, action, cursor=None, snapping=None):
    """`flow.apply` with a fake snapper that snaps every click onto itself, unless the test gives its own."""
    return flow.apply(annotations, session, action, cursor, FakeSnapping() if snapping is None else snapping)


def points_of(cursor):
    """The positioned points of the open item, in slot order."""
    return tuple(slot.point for slot in cursor.pending if slot.point is not None)


def slots_of(points, status=an.SNAPPED):
    return tuple(Slot(point, status) for point in points) + (flow.EMPTY,) * (4 - len(points))


def save(annotations, session, cursor, number=1, size=SIZE):
    return apply(annotations, session, Action("save", number=number, size=size), cursor)


def place(annotations, session, cursor, points, size=SIZE):
    """Each point on its own slot: the key 1 to 4 chooses the slot (U27), then the click."""
    for number, (x, y) in enumerate(points, start=1):
        _, cursor = apply(annotations, session, Action("select", number=number), cursor)
        _, cursor = apply(annotations, session, Action("click", x, y, size=size), cursor)
    return cursor


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
        annotations, cursor = apply(annotations, session, click(x, y), cursor)
    return save(annotations, session, cursor)


def test_plan_lists_judged_darts_and_skipped_ones_cv5(tmp_path):
    plan = flow.plan_of(session_of(tmp_path))
    assert plan.darts == ((1, "dart-001.jpg"), (3, "dart-003.jpg"), (4, "dart-004.jpg"))
    assert plan.skipped == (2, 5)  # 2 is voided, 5 has no photo
    assert plan.first_photo == "dart-001.jpg"


def test_resume_starts_with_calibration_cv2(tmp_path):
    session = session_of(tmp_path)
    assert flow.resume(empty(session), session) == Cursor(flow.CALIBRATION, photo="dart-001.jpg", pending=flow.BLANK)


def test_calibration_points_one_to_four_then_save_then_first_dart_cv2(tmp_path):
    session = session_of(tmp_path)
    annotations, cursor = empty(session), None
    for count, (x, y) in enumerate(POINTS, start=1):
        annotations, cursor = apply(annotations, session, click(x, y), cursor)
        assert annotations.calibrations == () and cursor.kind == flow.CALIBRATION  # nothing is written before the save
        assert points_of(cursor) == POINTS[:count] and flow.ready_count(cursor.pending) == count
    annotations, cursor = save(annotations, session, cursor)
    expected = Calibration(1, "dart-001.jpg", SIZE[0], SIZE[1], POINTS, (an.SNAPPED,) * 4)
    assert annotations.calibrations == (expected,)  # segment 0 (CV20)
    assert cursor == Cursor(flow.DART, 1, "dart-001.jpg")


def test_calibration_takes_the_real_jpeg_size_given_with_the_click(tmp_path):
    session = session_of(tmp_path)
    annotations, cursor = empty(session), None
    for x, y in POINTS:
        annotations, cursor = apply(annotations, session, Action("click", x, y, size=(4000, 3000)), cursor)
    annotations, cursor = save(annotations, session, cursor, size=(4000, 3000))
    assert (annotations.calibrations[0].width, annotations.calibrations[0].height) == (4000, 3000)


def test_click_on_a_dart_sets_the_tip_visible_and_moves_on(tmp_path):
    session = session_of(tmp_path)
    annotations, cursor = calibrate(session)
    annotations, cursor = apply(annotations, session, click(5.5, 6.5), cursor)
    assert annotations.darts == (DartAnnotation(1, Status.VISIBLE, (5.5, 6.5)),)
    assert cursor == Cursor(flow.DART, 3, "dart-003.jpg")  # dart 2 is voided: skipped


@pytest.mark.parametrize("kind,status", [("hidden", Status.HIDDEN), ("off_board", Status.OFF_BOARD)])
def test_hidden_and_off_board_set_the_status_and_move_on(tmp_path, kind, status):
    session = session_of(tmp_path)
    annotations, cursor = calibrate(session)
    annotations, cursor = apply(annotations, session, Action(kind), cursor)
    assert annotations.darts == (DartAnnotation(1, status, None),)
    assert cursor.number == 3


def test_replace_a_tip_on_an_annotated_dart_and_move_on(tmp_path):
    session = session_of(tmp_path)
    annotations = calibrated(session, [DartAnnotation(1, Status.VISIBLE, (1.0, 2.0)),
                                       DartAnnotation(3, Status.HIDDEN, None)])
    cursor = Cursor(flow.DART, 1, "dart-001.jpg")
    annotations, cursor = apply(annotations, session, click(9.0, 8.0), cursor)
    assert annotations.darts[0] == DartAnnotation(1, Status.VISIBLE, (9.0, 8.0))
    assert len(annotations.darts) == 2
    assert cursor.number == 3  # the next dart in order, annotated or not


def test_replace_a_status_by_a_tip_and_a_tip_by_a_status(tmp_path):
    session = session_of(tmp_path)
    annotations = calibrated(session, [DartAnnotation(3, Status.HIDDEN, None)])
    cursor = Cursor(flow.DART, 3, "dart-003.jpg")
    annotations, _ = apply(annotations, session, click(1.0, 1.0), cursor)
    assert DartAnnotation(3, Status.VISIBLE, (1.0, 1.0)) in annotations.darts
    annotations, _ = apply(annotations, session, Action("off_board"), cursor)
    assert DartAnnotation(3, Status.OFF_BOARD, None) in annotations.darts


def test_after_the_last_dart_the_cursor_goes_to_the_first_open_dart_then_complete(tmp_path):
    session = session_of(tmp_path)
    annotations = calibrated(session, [DartAnnotation(3, Status.HIDDEN, None)])
    annotations, cursor = apply(annotations, session, Action("hidden"), Cursor(flow.DART, 4, "dart-004.jpg"))
    assert cursor.number == 1  # dart 1 is still open
    annotations, cursor = apply(annotations, session, Action("hidden"), cursor)
    assert cursor.number == 3  # next in order, even if annotated
    annotations, cursor = apply(annotations, session, Action("hidden"), Cursor(flow.DART, 4, "dart-004.jpg"))
    assert cursor == Cursor(flow.COMPLETE)
    assert [d.number for d in annotations.darts] == [1, 3, 4]  # ascending, as the strict writer needs


def test_clear_removes_the_annotation_of_the_current_dart_and_the_cursor_stays(tmp_path):
    session = session_of(tmp_path)
    annotations = calibrated(session, [DartAnnotation(1, Status.VISIBLE, (1.0, 2.0)),
                                       DartAnnotation(3, Status.HIDDEN, None)])
    cursor = Cursor(flow.DART, 3, "dart-003.jpg")
    annotations, new_cursor = apply(annotations, session, Action("clear"), cursor)
    assert [d.number for d in annotations.darts] == [1]
    assert new_cursor == cursor
    same, _ = apply(annotations, session, Action("clear"), cursor)  # nothing to clear: no change
    assert same == annotations


def test_clear_in_calibration_removes_the_last_point_and_the_cursor_stays(tmp_path):
    session = session_of(tmp_path)
    annotations, cursor = empty(session), None
    for x, y in POINTS[:3]:
        annotations, cursor = apply(annotations, session, click(x, y), cursor)
    annotations, cursor = apply(annotations, session, Action("clear"), cursor)
    assert cursor.kind == flow.CALIBRATION and points_of(cursor) == POINTS[:2]
    _, cursor = apply(annotations, session, click(*POINTS[2]), cursor)
    assert points_of(cursor) == POINTS[:3]


def test_clear_on_empty_calibration_changes_nothing(tmp_path):
    session = session_of(tmp_path)
    annotations = empty(session)
    cursor = flow.resume(annotations, session)
    assert apply(annotations, session, Action("clear"), cursor) == (annotations, cursor)
    assert cursor.pending == flow.BLANK


def test_previous_from_the_first_dart_reopens_the_calibration_which_clear_then_redoes(tmp_path):
    session = session_of(tmp_path)
    annotations, cursor = calibrate(session)
    _, cursor = apply(annotations, session, Action("previous"), cursor)
    assert cursor.kind == flow.CALIBRATION and points_of(cursor) == POINTS
    annotations, cursor = apply(annotations, session, Action("clear"), cursor)
    assert annotations.calibrations == () and points_of(cursor) == POINTS[:3]
    annotations, cursor = apply(annotations, session, click(1500.0, 2500.0), cursor)  # far from the others: the empty slot
    annotations, _ = save(annotations, session, cursor)
    assert annotations.calibrations[0].points[3] == (1500.0, 2500.0)


def test_click_far_from_four_set_points_is_refused_in_calibration_u27(tmp_path):
    session = session_of(tmp_path)
    annotations, cursor = calibrate(session)
    _, cursor = apply(annotations, session, Action("previous"), cursor)
    with pytest.raises(FlowConflict, match="Clic loin des 4 points"):
        apply(annotations, session, click(), cursor)


def test_next_and_previous_only_move(tmp_path):
    session = session_of(tmp_path)
    annotations = calibrated(session, [DartAnnotation(1, Status.HIDDEN, None)])
    cursor = Cursor(flow.DART, 3, "dart-003.jpg")
    same, cursor = apply(annotations, session, Action("next"), cursor)
    assert same == annotations and cursor.number == 4
    same, cursor = apply(annotations, session, Action("next"), cursor)
    assert cursor.number == 4  # stays on the last dart
    _, cursor = apply(annotations, session, Action("previous"), cursor)
    assert cursor.number == 3


def test_next_is_refused_to_leave_calibration_before_it_is_complete(tmp_path):
    session = session_of(tmp_path)
    annotations, cursor = empty(session), None
    annotations, cursor = apply(annotations, session, click(), cursor)
    assert apply(annotations, session, Action("next"), cursor) == (annotations, cursor)
    with pytest.raises(FlowError):
        apply(annotations, session, Action("hidden"), cursor)


def test_previous_from_complete_goes_to_the_last_dart(tmp_path):
    session = session_of(tmp_path)
    _, cursor = apply(calibrated(session), session, Action("previous"), Cursor(flow.COMPLETE))
    assert cursor.number == 4


def test_goto_selects_a_judged_dart_only_after_calibration(tmp_path):
    session = session_of(tmp_path)
    annotations = calibrated(session)
    _, cursor = apply(annotations, session, Action("goto", number=4), None)
    assert cursor == Cursor(flow.DART, 4, "dart-004.jpg")
    for number in (2, 5, 99):  # voided, no photo, unknown
        with pytest.raises(FlowError):
            apply(annotations, session, Action("goto", number=number), None)
    with pytest.raises(FlowError):
        apply(empty(session), session, Action("goto", number=1), None)


@pytest.mark.parametrize("x,y", [(-0.1, 5), (5, -1), (SIZE[0] + 0.5, 5), (5, SIZE[1] + 1),
                                 (float("nan"), 5), (float("inf"), 5), (True, 5), ("5", 5)])
def test_click_outside_the_photo_or_not_a_number_is_refused(tmp_path, x, y):
    session = session_of(tmp_path)
    annotations, cursor = calibrate(session)
    with pytest.raises(FlowError):
        apply(annotations, session, Action("click", x, y, size=SIZE), cursor)


def test_click_on_the_photo_border_is_accepted(tmp_path):
    session = session_of(tmp_path)
    annotations, cursor = calibrate(session)
    updated, _ = apply(annotations, session, Action("click", SIZE[0], 0, size=SIZE), cursor)
    assert updated.darts[0].tip == (float(SIZE[0]), 0.0)


def test_click_without_a_known_photo_size_is_refused(tmp_path):
    session = session_of(tmp_path)
    with pytest.raises(FlowError):
        apply(empty(session), session, Action("click", 1, 1), None)


def test_unknown_action_is_refused(tmp_path):
    session = session_of(tmp_path)
    with pytest.raises(FlowError):
        apply(empty(session), session, Action("delete"), None)


def test_resume_cursor_on_the_first_dart_not_annotated_then_complete(tmp_path):
    session = session_of(tmp_path)
    annotations = calibrated(session, [DartAnnotation(1, Status.HIDDEN, None), DartAnnotation(4, Status.HIDDEN, None)])
    assert flow.resume(annotations, session) == Cursor(flow.DART, 3, "dart-003.jpg")
    full = calibrated(session, [DartAnnotation(n, Status.HIDDEN, None) for n in (1, 3, 4)])
    assert flow.resume(full, session) == Cursor(flow.COMPLETE)


def test_resume_keeps_the_points_clicked_so_far(tmp_path):
    session = session_of(tmp_path)
    cursor = flow.resume(empty(session), session, slots_of(POINTS[:2]))
    assert points_of(cursor) == POINTS[:2]


def test_session_without_judged_dart_has_no_flow(tmp_path):
    session = session_of(tmp_path, judged=(), voided=(1, 2), no_photo=(3,))
    assert flow.plan_of(session).first_photo is None
    with pytest.raises(FlowError):
        apply(empty(session), session, click(), None)


def test_autosave_round_trip_apply_write_reload_gives_the_same_file_and_cursor(tmp_path):
    session = session_of(tmp_path / "captures")
    annotations, cursor = calibrate(session)
    annotations, cursor = apply(annotations, session, click(11.5, 12.5), cursor)
    annotations, cursor = apply(annotations, session, Action("hidden"), cursor)
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
    _, cursor = apply(annotations, session, Action("previous"), Cursor(flow.DART, 1, "dart-001.jpg"))
    assert cursor.kind == flow.CALIBRATION and cursor.pending == slots_of(POINTS, an.MANUAL)  # segment 0 points
    assert flow.resume(annotations, session) == Cursor(flow.DART, 1, "dart-001.jpg")


def test_clear_of_segment_0_with_later_segments_is_refused_and_changes_nothing_cv20(tmp_path):
    session = session_of(tmp_path)
    annotations = _two_segments(session)
    cursor = Cursor(flow.CALIBRATION, photo="dart-001.jpg", pending=slots_of(POINTS, an.MANUAL))
    with pytest.raises(FlowError):
        apply(annotations, session, Action("clear"), cursor)


def test_flow_saves_version_3_with_one_snapped_segment_from_dart_1_cv20_cv25(tmp_path):
    session = session_of(tmp_path / "captures")
    annotations, cursor = calibrate(session)
    annotations, cursor = apply(annotations, session, click(11.5, 12.5), cursor)
    path = tmp_path / "annotations.json"
    an.write_atomic(path, annotations)
    assert an.read(path).calibrations == (Calibration(1, "dart-001.jpg", *SIZE, POINTS, (an.SNAPPED,) * 4),)
    assert '"version": 3' in path.read_text(encoding="utf-8")


def test_truth_dict_helper_gives_a_readable_truth(tmp_path):
    assert truth_dict()["session"] == NAME


# ---- recalibration from a judged dart (T6c, CV20) --------------------------------------------------------------------

NEW = ((1100.0, 1200.0), (1300.0, 1400.0), (1500.0, 1600.0), (1700.0, 1800.0))


def _at_dart(annotations, session, number):
    return apply(annotations, session, Action("goto", number=number), None)[1]


def _recalibrate(annotations, session, number):
    cursor = _at_dart(annotations, session, number)
    return apply(annotations, session, Action("recalibrate"), cursor)


def _tips(annotations):
    return [(d.number, d.tip) for d in annotations.darts]


def test_recalibrate_proposes_the_segment_in_force_snapped_on_the_new_photo_cv31(tmp_path):
    session = session_of(tmp_path)
    snapping = FakeSnapping(shift=(0.5, -0.5))
    _, cursor = apply(calibrated(session), session, Action("recalibrate"), _at_dart(calibrated(session), session, 3),
                      snapping)
    assert cursor.kind == flow.RECALIBRATION and cursor.photo == "dart-003.jpg"
    assert cursor.pending == tuple(Slot((x + 0.5, y - 0.5), an.SNAPPED) for x, y in POINTS)
    assert [photo for photo, _ in snapping.calls] == ["dart-003.jpg"] * 4  # the new photo, from the plan
    assert [start for _, start in snapping.calls] == list(POINTS)  # each point snapped from its old position


def test_a_proposal_that_does_not_snap_stays_to_be_clicked_cv31(tmp_path):
    session = session_of(tmp_path)
    snapping = FakeSnapping(failing={POINTS[1]: "contrast", POINTS[3]: "border"})
    annotations = calibrated(session)
    _, cursor = apply(annotations, session, Action("recalibrate"), _at_dart(annotations, session, 3), snapping)
    assert [slot.point is None for slot in cursor.pending] == [False, True, False, True]
    assert flow.ready_count(cursor.pending) == 2
    with pytest.raises(FlowConflict, match=r"Points prêts : 2 / 4"):
        save(annotations, session, cursor, number=3)


def test_recalibrate_from_a_dart_saves_the_segment_with_that_photo_and_its_snap_cv20_cv31(tmp_path):
    session = session_of(tmp_path)
    tips = [DartAnnotation(1, Status.VISIBLE, (1.0, 2.0)), DartAnnotation(4, Status.VISIBLE, (3.0, 4.0))]
    annotations = calibrated(session, tips)
    _, cursor = _recalibrate(annotations, session, 3)
    cursor = place(annotations, session, cursor, NEW, size=(4000, 3000))
    updated, cursor = save(annotations, session, cursor, number=3, size=(4000, 3000))
    assert updated.calibrations[1] == Calibration(3, "dart-003.jpg", 4000, 3000, NEW, (an.SNAPPED,) * 4)
    assert updated.calibrations[0] == annotations.calibrations[0]
    assert cursor == Cursor(flow.DART, 3, "dart-003.jpg")
    assert _tips(updated) == _tips(annotations)  # tips never move


def test_a_click_that_does_not_snap_is_stored_unsnapped_with_its_reason_at_the_click_cv31(tmp_path):
    session = session_of(tmp_path)
    snapping = FakeSnapping(failing={(5.0, 6.0): "unreadable"})
    annotations, cursor = empty(session), None
    for x, y in POINTS[:3] + ((5.0, 6.0),):
        annotations, cursor = apply(annotations, session, click(x, y), cursor, snapping)
    assert cursor.pending[3] == Slot((5.0, 6.0), an.UNSNAPPED, "unreadable")
    annotations, _ = apply(annotations, session, Action("save", number=1, size=SIZE), cursor, snapping)
    assert annotations.calibrations[0].snap == (an.SNAPPED,) * 3 + (an.UNSNAPPED,)
    assert annotations.calibrations[0].points[3] == (5.0, 6.0)


def test_click_on_a_full_recalibration_far_from_the_points_is_refused_u27(tmp_path):
    session = session_of(tmp_path)
    annotations = _two_segments(session)
    annotations, cursor = _recalibrate(annotations, session, 3)
    assert points_of(cursor) == _two_segments(session).calibrations[1].points
    with pytest.raises(FlowConflict, match="Clic loin des 4 points : choisir le point avec les touches 1 à 4"):
        apply(annotations, session, click(3000.0, 3000.0), cursor)


def test_recalibrate_on_an_existing_start_shows_its_points_and_clear_removes_the_saved_segment_cv20(tmp_path):
    session = session_of(tmp_path)
    annotations = _two_segments(session, [DartAnnotation(4, Status.VISIBLE, (3.0, 4.0))])
    points = annotations.calibrations[1].points
    snapping = FakeSnapping()
    annotations, cursor = _recalibrate(annotations, session, 3)
    assert cursor.kind == flow.RECALIBRATION and cursor.pending == slots_of(points, an.MANUAL) and not snapping.calls
    annotations, cursor = apply(annotations, session, Action("clear"), cursor)
    assert annotations.calibrations == _two_segments(session).calibrations[:1]  # removed at once (saved)
    assert points_of(cursor) == points[:3]
    unchanged, cursor = apply(annotations, session, Action("clear"), cursor)  # nothing left to undo: leave the item
    assert unchanged == annotations and cursor == Cursor(flow.DART, 3, "dart-003.jpg")
    assert _tips(annotations) == [(4, (3.0, 4.0))]


def test_saving_again_replaces_the_segment_at_the_same_dart_cv20(tmp_path):
    session = session_of(tmp_path)
    annotations, cursor = _recalibrate(_two_segments(session), session, 3)
    cursor = place(annotations, session, cursor, NEW)
    annotations, cursor = save(annotations, session, cursor, number=3)
    assert [c.from_dart for c in annotations.calibrations] == [1, 3] and annotations.calibrations[1].points == NEW


@pytest.mark.parametrize("leave", [Action("next"), Action("previous"), Action("goto", number=4)])
def test_leaving_with_unsaved_points_drops_them_and_changes_nothing(tmp_path, leave):
    session = session_of(tmp_path)
    annotations = calibrated(session)
    cursor = _at_dart(annotations, session, 3)
    _, cursor = apply(annotations, session, Action("recalibrate"), cursor)
    cursor = place(annotations, session, cursor, NEW[:2])
    assert points_of(cursor)[:2] == NEW[:2]
    same, left = apply(annotations, session, leave, cursor)
    assert same == annotations and left.kind == flow.DART and left.pending == () and left.history == ()


def test_recalibrate_needs_a_dart_cursor_and_the_first_dart_opens_segment_0(tmp_path):
    session = session_of(tmp_path)
    annotations = calibrated(session)
    for cursor in (Cursor(flow.CALIBRATION, photo="dart-001.jpg", pending=slots_of(POINTS)), Cursor(flow.COMPLETE)):
        with pytest.raises(FlowError):
            apply(annotations, session, Action("recalibrate"), cursor)
    with pytest.raises(FlowError):  # a voided entry is never a cursor: goto refuses it
        apply(annotations, session, Action("goto", number=2), None)
    _, cursor = _recalibrate(annotations, session, 1)
    assert cursor.kind == flow.CALIBRATION and points_of(cursor) == POINTS
    with pytest.raises(FlowError):
        apply(empty(session), session, Action("recalibrate"), Cursor(flow.DART, 3, "dart-003.jpg"))


@pytest.mark.parametrize("kind", ["hidden", "off_board"])
def test_marks_are_refused_with_a_conflict_on_the_calibration_screens_cv20_cv31(tmp_path, kind):
    session = session_of(tmp_path)
    annotations, cursor = _recalibrate(calibrated(session), session, 3)
    with pytest.raises(FlowConflict, match="4 points"):
        apply(annotations, session, Action(kind), cursor)
    with pytest.raises(FlowConflict, match="4 points"):
        apply(empty(session), session, Action(kind), flow.resume(empty(session), session))


def test_a_new_segment_goes_in_order_between_existing_ones(tmp_path):
    session = session_of(tmp_path, judged=(1, 3, 4, 6), voided=(2,), no_photo=(5,))
    annotations = _two_segments(session)  # segments from 1 and 3
    annotations, cursor = _recalibrate(annotations, session, 4)
    cursor = place(annotations, session, cursor, NEW)
    annotations, cursor = save(annotations, session, cursor, number=4)
    assert [c.from_dart for c in annotations.calibrations] == [1, 3, 4]


def test_the_save_at_the_segment_cap_is_a_conflict_and_changes_nothing_cv20(tmp_path):
    session = session_of(tmp_path)
    base = calibrated(session)
    filler = tuple(Calibration(100 + i, "dart-003.jpg", *SIZE, POINTS) for i in range(an.MAX_SEGMENTS - 1))
    full = Annotations(NAME, session.sha256, base.calibrations + filler, ())
    _, cursor = apply(full, session, Action("recalibrate"), _at_dart(full, session, 3))
    with pytest.raises(flow.FlowConflict, match=r"Nombre maximal de recalibrations atteint \(64\)"):
        save(full, session, cursor, number=3)


# ---- the click rule (U27, CV31): a pure function ---------------------------------------------------------------------

RADIUS = config.CLICK_REPLACE_RADIUS_PX
FOUR = slots_of(((1000.0, 1000.0), (2000.0, 1000.0), (2000.0, 2000.0), (1000.0, 2000.0)))


def test_the_selected_point_takes_the_click_wherever_it_is_u27():
    assert flow.click_target(FOUR, (1000.0, 1000.0), selected=3) == 2  # right on point 1, but point 3 is selected


def test_the_nearest_positioned_point_within_the_radius_is_replaced_u27():
    assert flow.click_target(FOUR, (2010.0, 2010.0)) == 2
    assert flow.click_target(FOUR, (1000.0 + RADIUS, 1000.0)) == 0  # at the radius: replaced
    with pytest.raises(FlowConflict):  # radius + 1: not
        flow.click_target(FOUR, (1000.0 + RADIUS + 1, 1000.0))


def test_of_two_points_within_the_radius_the_nearer_wins_u27():
    slots = slots_of(((1000.0, 1000.0), (1080.0, 1000.0)))
    assert flow.click_target(slots, (1050.0, 1000.0)) == 1
    assert flow.click_target(slots, (1030.0, 1000.0)) == 0


def test_two_points_at_the_same_distance_give_the_lowest_index_u27():
    slots = slots_of(((1000.0, 1000.0), (1100.0, 1000.0)))
    assert flow.click_target(slots, (1050.0, 1000.0)) == 0
    swapped = (slots[1], slots[0]) + slots[2:]  # same geometry, the tie now sits the other way round
    assert flow.click_target(swapped, (1050.0, 1000.0)) == 0


def test_none_within_the_radius_fills_the_first_empty_slot_in_order_u27():
    assert flow.click_target(flow.BLANK, (5.0, 5.0)) == 0
    slots = (Slot((1000.0, 1000.0), an.SNAPPED), flow.EMPTY, Slot((2000.0, 2000.0), an.SNAPPED), flow.EMPTY)
    assert flow.click_target(slots, (3000.0, 500.0)) == 1  # not within the radius of anything: first empty, 2
    assert flow.click_target(slots, (1010.0, 1010.0)) == 0  # within the radius of point 1: replaced, not filled


def test_every_slot_positioned_and_none_within_the_radius_is_refused_with_its_text_u27():
    with pytest.raises(FlowConflict, match="^Clic loin des 4 points : choisir le point avec les touches 1 à 4, puis "
                                           "cliquer$"):
        flow.click_target(FOUR, (3000.0, 500.0))


def test_an_accepted_click_consumes_the_selection_like_hide_u27(tmp_path):
    session = session_of(tmp_path)
    annotations, cursor = calibrate(session)
    _, cursor = apply(annotations, session, Action("previous"), cursor)
    _, cursor = apply(annotations, session, Action("select", number=2), cursor)
    assert cursor.selected == 2
    _, cursor = apply(annotations, session, click(3500.0, 500.0), cursor)  # far from every point: only the key lets it in
    assert cursor.selected is None and cursor.pending[1].point == (3500.0, 500.0)
    with pytest.raises(FlowConflict):  # the next click follows the rule again
        apply(annotations, session, click(3600.0, 600.0), cursor)


def test_a_refused_click_keeps_the_selection_and_the_snapper_is_not_asked(tmp_path):
    session = session_of(tmp_path)
    snapping = FakeSnapping()
    annotations, cursor = calibrate(session)
    _, cursor = apply(annotations, session, Action("previous"), cursor)
    with pytest.raises(FlowConflict):
        apply(annotations, session, click(3500.0, 500.0), cursor, snapping)
    assert snapping.calls == []


@pytest.mark.parametrize("number", [0, 5, -1, None, True, 2.0, "2"])
def test_select_refuses_a_point_that_is_not_1_to_4(tmp_path, number):
    session = session_of(tmp_path)
    with pytest.raises(FlowError):
        apply(empty(session), session, Action("select", number=number), flow.resume(empty(session), session))


def test_select_is_refused_outside_the_calibration_screens(tmp_path):
    session = session_of(tmp_path)
    with pytest.raises(FlowError):
        apply(calibrated(session), session, Action("select", number=1), Cursor(flow.DART, 1, "dart-001.jpg"))


# ---- undo, save rule (U29) and availability (A32) --------------------------------------------------------------------

def test_clear_undoes_the_last_change_of_the_item_selection_included_u27(tmp_path):
    session = session_of(tmp_path)
    annotations, cursor = empty(session), None
    for x, y in POINTS[:2]:
        annotations, cursor = apply(annotations, session, click(x, y), cursor)
    _, selected = apply(annotations, session, Action("select", number=1), cursor)
    _, moved = apply(annotations, session, click(1050.0, 1000.0), selected)  # replaces point 1
    assert moved.pending[0].point == (1050.0, 1000.0)
    _, undone = apply(annotations, session, Action("clear"), moved)
    assert undone.pending == cursor.pending and undone.selected == 1 and undone.history == cursor.history


@pytest.mark.parametrize("count", [0, 1, 3])
def test_save_below_four_ready_points_is_a_conflict_and_the_file_is_unchanged_u29(tmp_path, count):
    session = session_of(tmp_path)
    annotations, cursor = empty(session), None
    for x, y in POINTS[:count]:
        annotations, cursor = apply(annotations, session, click(x, y), cursor)
    cursor = cursor or flow.resume(annotations, session)
    with pytest.raises(FlowConflict, match=rf"^Points prêts : {count} / 4$"):
        save(annotations, session, cursor)


def test_save_needs_the_size_of_the_photo(tmp_path):
    session = session_of(tmp_path)
    annotations, cursor = empty(session), None
    for x, y in POINTS:
        annotations, cursor = apply(annotations, session, click(x, y), cursor)
    with pytest.raises(FlowError, match="Taille"):
        apply(annotations, session, Action("save", number=1), cursor)


def test_a_repeated_save_of_the_same_item_is_a_conflict_and_writes_one_segment_u29(tmp_path):
    session = session_of(tmp_path)
    annotations = _two_segments(session)
    annotations, cursor = _recalibrate(annotations, session, 3)
    cursor = place(annotations, session, cursor, NEW)
    saved, after = save(annotations, session, cursor, number=3)
    assert [c.from_dart for c in saved.calibrations] == [1, 3] and after == Cursor(flow.DART, 3, "dart-003.jpg")
    with pytest.raises(FlowConflict, match="^Photo déjà enregistrée$"):
        save(saved, session, after, number=3)  # the same save again, the cursor is now on the dart
    with pytest.raises(FlowConflict, match="^Photo déjà enregistrée$"):
        save(saved, session, cursor, number=4)  # a save that names another item than the open one


def test_a_save_that_names_the_open_calibration_item_writes_segment_0_once_u29(tmp_path):
    session = session_of(tmp_path)
    annotations, cursor = empty(session), None
    for x, y in POINTS:
        annotations, cursor = apply(annotations, session, click(x, y), cursor)
    with pytest.raises(FlowConflict, match="^Photo déjà enregistrée$"):
        save(annotations, session, cursor, number=3)
    annotations, cursor = save(annotations, session, cursor)
    assert len(annotations.calibrations) == 1
    with pytest.raises(FlowConflict, match="^Photo déjà enregistrée$"):
        save(annotations, session, cursor)


def test_without_a_snapper_clicks_saves_and_recalibration_are_refused_and_nothing_changes_a32(tmp_path):
    session = session_of(tmp_path)
    off = FakeSnapping(available=False)
    annotations, cursor = empty(session), flow.resume(empty(session), session)
    for action in (click(), Action("save", number=1, size=SIZE)):
        with pytest.raises(FlowConflict, match="Recalage indisponible"):
            apply(annotations, session, action, cursor, off)
    done = calibrated(session, [DartAnnotation(1, Status.VISIBLE, (1.0, 2.0))])
    with pytest.raises(FlowConflict, match="Recalage indisponible"):
        apply(done, session, Action("recalibrate"), _at_dart(done, session, 3), off)
    updated, cursor = apply(done, session, click(7.0, 8.0), Cursor(flow.DART, 3, "dart-003.jpg"), off)
    assert (3, (7.0, 8.0)) in _tips(updated)  # tips do not need the snapper
    with pytest.raises(FlowConflict, match="Recalage indisponible"):
        flow.apply(annotations, session, click(), cursor=None, snapping=None)  # no snapper object at all
