"""Per-dart tip entry (T6a, N24): CV5 judged darts, resume cursor on the first dart without a tip (no calibration
screen), replace, clear, skips, autosave; segments and controls are never changed by an action (CV20, CV25)."""
import pytest

from annotate_support import NAME, SIZE, make_session, truth_dict
from kosmo_camera import annotations as an
from kosmo_camera import truth as truth_module
from kosmo_camera.annotate import flow
from kosmo_camera.annotate.flow import Action, Cursor, FlowError
from kosmo_camera.annotations import Annotations, Calibration, DartAnnotation, Status

POINTS = ((1000.0, 1000.0), (2000.0, 1000.0), (2000.0, 2000.0), (1000.0, 2000.0))
REMOVED_KINDS = ("recalibrate", "select", "save", "control", "hidden_point")  # stopped by ADR 0020 (Q1 B)


def apply(annotations, session, action, cursor=None):
    return flow.apply(annotations, session, action, cursor)


def session_of(tmp_path, **kwargs):
    folder, _ = make_session(tmp_path, **kwargs)
    return truth_module.read_truth(folder / "truth.json")


def empty(session):
    return Annotations(NAME, session.sha256, (), ())


def click(x=100.0, y=200.0):
    return Action("click", x, y, size=SIZE)


def calibrated(session, darts=()):
    return Annotations(NAME, session.sha256, (Calibration(1, "dart-001.jpg", *SIZE, POINTS),), tuple(darts))


def test_plan_lists_judged_darts_and_skipped_ones_cv5(tmp_path):
    plan = flow.plan_of(session_of(tmp_path))
    assert plan.darts == ((1, "dart-001.jpg"), (3, "dart-003.jpg"), (4, "dart-004.jpg"))
    assert plan.skipped == (2, 5)  # 2 is voided, 5 has no photo
    assert plan.first_photo == "dart-001.jpg"


def test_click_on_a_dart_sets_the_tip_visible_and_moves_on(tmp_path):
    session = session_of(tmp_path)
    annotations, cursor = empty(session), None  # no segment needed (N24)
    annotations, cursor = apply(annotations, session, click(5.5, 6.5), cursor)
    assert annotations.darts == (DartAnnotation(1, Status.VISIBLE, (5.5, 6.5)),)
    assert cursor == Cursor(flow.DART, 3, "dart-003.jpg")  # dart 2 is voided: skipped


@pytest.mark.parametrize("kind,status", [("hidden", Status.HIDDEN), ("off_board", Status.OFF_BOARD)])
def test_hidden_and_off_board_set_the_status_and_move_on(tmp_path, kind, status):
    session = session_of(tmp_path)
    annotations, cursor = empty(session), None  # no segment needed (N24)
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


def test_previous_from_complete_goes_to_the_last_dart(tmp_path):
    session = session_of(tmp_path)
    _, cursor = apply(calibrated(session), session, Action("previous"), Cursor(flow.COMPLETE))
    assert cursor.number == 4


def test_goto_selects_a_judged_dart_with_no_calibration_needed(tmp_path):
    session = session_of(tmp_path)
    annotations = calibrated(session)
    _, cursor = apply(annotations, session, Action("goto", number=4), None)
    assert cursor == Cursor(flow.DART, 4, "dart-004.jpg")
    for number in (2, 5, 99):  # voided, no photo, unknown
        with pytest.raises(FlowError):
            apply(annotations, session, Action("goto", number=number), None)
    _, cursor = apply(empty(session), session, Action("goto", number=3), None)  # no segment needed (N24)
    assert cursor == Cursor(flow.DART, 3, "dart-003.jpg")


@pytest.mark.parametrize("x,y", [(-0.1, 5), (5, -1), (SIZE[0] + 0.5, 5), (5, SIZE[1] + 1),
                                 (float("nan"), 5), (float("inf"), 5), (True, 5), ("5", 5)])
def test_click_outside_the_photo_or_not_a_number_is_refused(tmp_path, x, y):
    session = session_of(tmp_path)
    annotations, cursor = empty(session), None  # no segment needed (N24)
    with pytest.raises(FlowError):
        apply(annotations, session, Action("click", x, y, size=SIZE), cursor)


def test_click_on_the_photo_border_is_accepted(tmp_path):
    session = session_of(tmp_path)
    annotations, cursor = empty(session), None  # no segment needed (N24)
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


def test_session_without_judged_dart_has_no_flow(tmp_path):
    session = session_of(tmp_path, judged=(), voided=(1, 2), no_photo=(3,))
    assert flow.plan_of(session).first_photo is None
    with pytest.raises(FlowError):
        apply(empty(session), session, click(), None)


def test_autosave_round_trip_apply_write_reload_gives_the_same_file_and_cursor(tmp_path):
    session = session_of(tmp_path / "captures")
    annotations, cursor = empty(session), None  # no segment needed (N24)
    annotations, cursor = apply(annotations, session, click(11.5, 12.5), cursor)
    annotations, cursor = apply(annotations, session, Action("hidden"), cursor)
    path = tmp_path / "annotations.json"
    an.write_atomic(path, annotations)
    reloaded = an.read(path)
    assert reloaded == annotations
    assert an.validate_against(reloaded, session) == []
    assert flow.resume(reloaded, session) == cursor
    assert cursor == Cursor(flow.DART, 4, "dart-004.jpg")


def test_truth_dict_helper_gives_a_readable_truth(tmp_path):
    assert truth_dict()["session"] == NAME


# ---- tip entry only (N24, ADR 0020 Q1 B) ------------------------------------------------------------------------------

@pytest.mark.parametrize("kind", REMOVED_KINDS)
def test_the_removed_kinds_are_unknown_actions_and_change_nothing_n24(tmp_path, kind):
    session = session_of(tmp_path)
    annotations = calibrated(session, [DartAnnotation(1, Status.HIDDEN, None)])
    assert kind not in flow.KINDS
    with pytest.raises(FlowError, match="Action inconnue"):
        apply(annotations, session, Action(kind, 10.0, 10.0, number=1, size=SIZE), Cursor(flow.DART, 1, "dart-001.jpg"))


def test_a_new_session_without_segment_opens_on_its_first_dart_and_saves_tips_with_no_segment_n24(tmp_path):
    session = session_of(tmp_path / "captures")
    annotations = empty(session)
    cursor = flow.resume(annotations, session)
    assert cursor == Cursor(flow.DART, 1, "dart-001.jpg")  # no calibration screen
    annotations, cursor = apply(annotations, session, click(11.5, 12.5), cursor)
    annotations, cursor = apply(annotations, session, Action("off_board"), cursor)
    path = tmp_path / "annotations.json"
    an.write_atomic(path, annotations)
    reloaded = an.read(path)
    assert reloaded.calibrations == () and reloaded.controls == ()
    assert reloaded.darts == (DartAnnotation(1, Status.VISIBLE, (11.5, 12.5)), DartAnnotation(3, Status.OFF_BOARD, None))
    assert an.validate_against(reloaded, session) == []
    assert cursor == Cursor(flow.DART, 4, "dart-004.jpg")


def test_every_action_keeps_the_segments_and_controls_of_the_file_cv20_cv25(tmp_path):
    session = session_of(tmp_path)
    control = an.Control(1, an.PHOTO, None, *SIZE, POINTS, (an.SNAPPED,) * 4)
    start = Annotations(NAME, session.sha256, (Calibration(1, "dart-001.jpg", *SIZE, POINTS),), (), (control,))
    annotations, cursor = start, None
    for action in (click(), Action("hidden"), Action("previous"), Action("clear"), Action("next"),
                   Action("goto", number=4), Action("off_board")):
        annotations, cursor = apply(annotations, session, action, cursor)
        assert annotations.calibrations == start.calibrations and annotations.controls == start.controls
