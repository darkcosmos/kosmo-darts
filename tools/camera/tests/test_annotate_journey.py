"""Journey state (CV16, CV26, CV30): rows per session, the single instruction, the focus session, roles; pure state, no
server. The texts below are written out in full on purpose: they are the table "Instructions" of the plan."""
from dataclasses import replace
from pathlib import Path

import pytest

from annotate_support import NAME, SIZE, FakeSnapping, make_session
from kosmo_camera import truth as truth_module
from kosmo_camera.annotate import flow, journey
from kosmo_camera.annotate.control_state import OFF, ControlInfo
from kosmo_camera.annotate.views import IGNORED, NO_DRIFT, OK, REJECTED, DriftState, Loaded
from kosmo_camera.annotations import (SNAPPED, Annotations, Calibration, Control, DartAnnotation, Status)
from kosmo_camera import drift_file

POINTS = ((1000.0, 1000.0), (2000.0, 1000.0), (2000.0, 2000.0), (1000.0, 2000.0))
FRAME = (1920, 1080)

CALIBRATE_1 = "Cliquer les 4 points de calibration de la photo de la fléchette 1"
TIPS_3 = "Pointer les fléchettes : prochaine fléchette 3"
PHOTO_1 = "Contrôle de calibration : photo de la fléchette 1"
VIDEO_1 = "Contrôle de calibration : image vidéo de la fléchette 1"
NO_SNAP = "Recalage indisponible : relancer l'outil avec ~/kosmo-tool-venv/bin/python (K7.1b)"
NO_SPLIT = "Contrôle indisponible : relancer l'outil avec --split (K7.3)"
NO_FRAMES = "Images vidéo absentes : lancer l'extraction (K7.2)"
SESSION_DONE = "Session terminée : passer à la session suivante"
ALL_DONE = "Contrôle terminé : fermer l'outil et lancer le diagnostic (K7.4)"


def truth_of(tmp_path, name=NAME):
    folder, _ = make_session(tmp_path, name=name)
    return folder, truth_module.read_truth(folder / "truth.json")


def calibration(truth):
    return Calibration(1, "dart-001.jpg", *SIZE, POINTS)


def tip(number):
    return DartAnnotation(number, Status.VISIBLE, (10.0 * number, 20.0))


def photo_control(number):
    return Control(number, "photo", None, *SIZE, POINTS, (SNAPPED,) * 4)


def video_control(number):
    return Control(number, "video", 1000 + number, *FRAME, POINTS, (SNAPPED,) * 4)


def loaded_of(truth, folder, calibrated=True, darts=(), controls=(), name=NAME):
    annotations = Annotations(name, truth.sha256, (calibration(truth),) if calibrated else (), tuple(darts))
    annotations = replace(annotations, controls=tuple(controls))
    return Loaded(name, folder, OK, truth=truth, plan=flow.plan_of(truth), annotations=annotations)


def control_info(photo=(1, 3), video=(1,), frames=True, role="dev 1", reasons=None, planned=None):
    plan = flow.ControlPlan(tuple(photo), tuple((d, f"video-{d:03d}.jpg", 1000 + d) for d in video))
    return ControlInfo(plan, role, None, frames, dict(reasons or {}), {}, planned)


def state_of(loaded, info=None, drift=NO_DRIFT, snap=True):
    return journey.session_state(loaded, OFF if info is None else info, drift, snap)


def all_tips():
    return [tip(1), tip(3), tip(4)]


@pytest.fixture
def session(tmp_path):
    return truth_of(tmp_path)


def test_every_row_of_the_instruction_table_is_reached_once_in_order_cv16(session):
    folder, truth = session
    info = control_info()
    steps = [
        (loaded_of(truth, folder, calibrated=False), journey.CALIBRATE, CALIBRATE_1, None),
        (loaded_of(truth, folder, darts=[tip(1)]), journey.GOTO, TIPS_3, None),
        (loaded_of(truth, folder, darts=all_tips()), journey.CONTROL, PHOTO_1, "(touche K)"),
        (loaded_of(truth, folder, darts=all_tips(), controls=[photo_control(1)]), journey.CONTROL,
         "Contrôle de calibration : photo de la fléchette 3", "(touche K)"),
        (loaded_of(truth, folder, darts=all_tips(), controls=[photo_control(1), photo_control(3)]), journey.CONTROL,
         VIDEO_1, "(touche K)"),
    ]
    for loaded, action, text, hint in steps:
        step = state_of(loaded, info).next_step
        assert (step.action, step.text, step.hint) == (action, text, hint)
    done = loaded_of(truth, folder, darts=all_tips(), controls=[photo_control(1), photo_control(3), video_control(1)])
    finished = state_of(done, info)
    assert (finished.next_step, finished.state) == (None, "Terminée")
    assert journey.global_instruction([finished]).text == ALL_DONE


def test_the_list_hint_names_the_button_and_never_a_key_u15(session):
    folder, truth = session
    info = control_info()
    for loaded in (loaded_of(truth, folder, calibrated=False), loaded_of(truth, folder, darts=[tip(1)]),
                   loaded_of(truth, folder, darts=all_tips())):
        text = journey.global_instruction([state_of(loaded, info)]).text
        assert text.endswith(" (bouton Continuer)") and "touche" not in text


def test_the_unavailable_rows_replace_the_final_row_u17(session):
    folder, truth = session
    controls = [photo_control(1), photo_control(3)]
    no_split = state_of(loaded_of(truth, folder, darts=all_tips()))
    assert no_split.next_step.text == NO_SPLIT and no_split.next_step.action is None
    assert [s.status for s in no_split.steps] == ["done", "done", "unavailable", "unavailable"]
    assert journey.global_instruction([no_split]).text == NO_SPLIT
    assert "Contrôle : indisponible (option --split absente)" in no_split.info
    no_frames = state_of(loaded_of(truth, folder, darts=all_tips(), controls=controls),
                         control_info(frames=False))
    assert no_frames.next_step.text == NO_FRAMES and no_frames.next_step.action is None
    assert no_frames.steps[3].status == "unavailable" and no_frames.state == "En cours"
    assert journey.global_instruction([no_frames]).text == NO_FRAMES
    assert "Contrôle vidéo : indisponible (images vidéo absentes)" in no_frames.info


def test_a_session_done_with_others_left_says_to_go_on_and_the_final_row_needs_every_session_done(tmp_path):
    folder, truth = truth_of(tmp_path)
    folder_b, truth_b = truth_of(tmp_path, "20261101-090000")
    done = state_of(loaded_of(truth, folder, darts=all_tips(), controls=[photo_control(1), photo_control(3),
                                                                         video_control(1)]), control_info())
    left = state_of(loaded_of(truth_b, folder_b, calibrated=False, name="20261101-090000"), control_info())
    instruction = journey.global_instruction([done, left])
    assert (instruction.focus, instruction.text) == ("20261101-090000", CALIBRATE_1 + " (bouton Continuer)")
    assert journey.session_instruction(done, instruction.focus) == SESSION_DONE
    assert journey.session_instruction(done, None) == ALL_DONE
    final = journey.global_instruction([done, state_of(Loaded("x", Path("."), IGNORED, "Ignorée"))])
    assert (final.text, final.focus) == (ALL_DONE, None)


def test_the_focus_is_the_first_session_in_list_order_with_a_step_left_and_ignored_ones_are_skipped(tmp_path):
    names = ["20261101-090000", "20261102-090000", "20261103-090000"]
    states = []
    for name, kind in zip(names, ("ignored", "left", "left")):
        folder, truth = truth_of(tmp_path, name)
        states.append(state_of(Loaded(name, folder, IGNORED, "Ignorée") if kind == "ignored"
                               else loaded_of(truth, folder, calibrated=False, name=name), control_info()))
    assert states[0].state == "Ignorée" and states[0].steps == ()
    assert journey.global_instruction(states).focus == names[1]


def test_a_blocked_session_never_gives_the_final_row_and_is_never_the_focus(session):
    folder, truth = session
    blocked = state_of(Loaded(NAME, folder, REJECTED, "Fichier d'annotations refusé"))
    assert blocked.blocked and blocked.info == ("Fichier d'annotations refusé",)
    assert journey.session_instruction(blocked, None) is None
    left = state_of(loaded_of(truth, folder, calibrated=False), control_info())
    assert journey.global_instruction([blocked, left]).focus == NAME


BLOCKED = "Session illisible ou refusée : voir l'information de la carte"


def test_only_a_blocked_session_left_gives_the_blocked_row_without_focus_u35(session):
    folder, truth = session
    blocked = state_of(Loaded(NAME, folder, REJECTED, "Fichier d'annotations refusé"))
    only = journey.global_instruction([blocked])
    assert (only.text, only.focus, only.action, only.number) == (BLOCKED, None, None, None)
    done = state_of(loaded_of(truth, folder, darts=all_tips(), controls=[photo_control(1), photo_control(3),
                                                                         video_control(1)]), control_info())
    both = journey.global_instruction([done, blocked])
    assert (both.text, both.focus) == (BLOCKED, None)  # the final row is never shown while a session is blocked (U17)
    assert both.text != ALL_DONE


def test_a_blocked_session_and_an_actionable_row_give_the_actionable_row_u35(session):
    folder, truth = session
    blocked = state_of(Loaded(NAME, folder, REJECTED, "Fichier d'annotations refusé"))
    left = state_of(loaded_of(truth, folder, calibrated=False, name="20261101-090000"), control_info())
    both = journey.global_instruction([blocked, left])
    assert (both.text, both.focus) == (CALIBRATE_1 + " (bouton Continuer)", "20261101-090000")


def test_without_snapping_the_calibration_and_control_rows_are_replaced_and_tips_are_not_d43(session):
    folder, truth = session
    info = control_info()
    for loaded, key in ((loaded_of(truth, folder, calibrated=False), "calibration"),
                        (loaded_of(truth, folder, darts=all_tips()), "control_photo"),
                        (loaded_of(truth, folder, darts=all_tips(), controls=[photo_control(1), photo_control(3)]),
                         "control_video")):
        step = state_of(loaded, info, snap=False).next_step
        assert (step.key, step.text, step.action, step.hint) == (key, NO_SNAP, None, None)
        assert journey.global_instruction([state_of(loaded, info, snap=False)]).text == NO_SNAP  # no button hint
        assert state_of(loaded, info, snap=True).next_step.text != NO_SNAP
    tips = state_of(loaded_of(truth, folder, darts=[tip(1)]), info, snap=False).next_step
    assert (tips.text, tips.action, tips.number) == (TIPS_3, "goto", 3)
    no_split = state_of(loaded_of(truth, folder, darts=all_tips()), None, snap=False).next_step
    assert no_split.text == NO_SPLIT  # the row is unavailable for another cause: its own text, not the snap one
    done = loaded_of(truth, folder, darts=all_tips(), controls=[photo_control(1), photo_control(3), video_control(1)])
    assert state_of(done, info, snap=False).state == "Terminée"  # nothing left to snap: the final row is true


def test_the_role_comes_from_the_split_and_is_absent_without_it_q9(session):
    folder, truth = session
    loaded = loaded_of(truth, folder)
    assert state_of(loaded, control_info(role="eval 2")).role == "eval 2"
    assert state_of(loaded).role is None and "role" not in journey.session_fields(state_of(loaded))
    assert journey.session_fields(state_of(loaded, control_info(role="dev 1")))["role"] == "dev 1"
    assert state_of(Loaded(NAME, folder, IGNORED, "Ignorée")).role is None


def test_a_session_with_every_frame_missing_has_its_video_row_done_with_an_info_line_s13(session):
    folder, truth = session
    info = control_info(video=(), reasons={"decode": 2, "absent": 1})
    state = state_of(loaded_of(truth, folder, darts=all_tips(), controls=[photo_control(1), photo_control(3)]), info)
    assert (state.steps[3].done, state.steps[3].total, state.steps[3].status) == (0, 0, "done")
    assert state.info == ("Aucune image vidéo utilisable pour cette session (3 manquantes)",)
    assert state.next_step is None and state.state == "Terminée"


def test_the_progress_rows_report_done_and_total_per_row(session):
    folder, truth = session
    state = state_of(loaded_of(truth, folder, darts=[tip(1)], controls=[photo_control(1)]), control_info())
    assert [(s.key, s.done, s.total, s.status) for s in state.steps] == [
        ("calibration", 1, 1, "done"), ("tips", 1, 3, "in_progress"), ("control_photo", 1, 2, "in_progress"),
        ("control_video", 0, 1, "todo")]
    assert state.state == "En cours"
    assert state_of(loaded_of(truth, folder, calibrated=False)).state == "À faire"


DRIFT_FLAGS = drift_file.SessionFlags(NAME, "S01", "0" * 64, 3.2, (3, 4), (3,), (1,))


def drift_inputs():
    return {"none": NO_DRIFT, "valid with beyond darts": DriftState({NAME: DRIFT_FLAGS}, None, True),
            "not measured": DriftState({}, None, True),
            "invalid or oversized": DriftState({}, "Mesure de dérive illisible : trop gros", True)}


@pytest.mark.parametrize("kind", ["none", "valid with beyond darts", "not measured", "invalid or oversized"])
def test_drift_never_instructs_in_any_journey_state_a30(session, kind):
    folder, truth = session
    states = [loaded_of(truth, folder, calibrated=False), loaded_of(truth, folder, darts=[tip(1)]),
              loaded_of(truth, folder, darts=all_tips()),
              loaded_of(truth, folder, darts=all_tips(), controls=[photo_control(1), photo_control(3)]),
              loaded_of(truth, folder, darts=all_tips(), controls=[photo_control(1), photo_control(3),
                                                                   video_control(1)])]
    for loaded in states:
        for info in (control_info(), None, control_info(frames=False)):
            plain, drifted = state_of(loaded, info), state_of(loaded, info, drift_inputs()[kind])
            assert (drifted.next_step, drifted.state, drifted.steps) == (plain.next_step, plain.state, plain.steps)
            assert (journey.global_instruction([drifted]) == journey.global_instruction([plain]))
            assert journey.session_instruction(drifted, None) == journey.session_instruction(plain, None)
            assert "Dérive" not in " ".join(filter(None, [journey.global_instruction([drifted]).text]))
            if kind == "valid with beyond darts":
                assert journey.DRIFT_INFO in drifted.info
                assert any(line.startswith("À recalibrer") for line in drifted.info)
            else:
                assert set(drifted.info) - set(plain.info) <= {journey.DRIFT_INFO, "Dérive non mesurée"}


def test_a_stale_drift_label_stays_in_the_info_lines_only_cv30(session):
    folder, truth = session
    state = state_of(loaded_of(truth, folder), drift=DriftState({NAME: DRIFT_FLAGS}, None, True))
    assert state.info[0] == "Mesure de dérive non validée : information seulement"
    assert "(mesure périmée : relancer la mesure)" in state.info[1]
    assert "Dérive" not in state.next_step.text


@pytest.mark.parametrize("annotations_kwargs, expected", [
    (dict(calibrated=False), journey.CALIBRATE),
    (dict(darts=[tip(1)]), journey.GOTO),
    (dict(darts=[tip(1), tip(3), tip(4)]), journey.CONTROL),
])
def test_the_action_of_each_row_opens_the_screen_of_its_title_u15(session, annotations_kwargs, expected):
    folder, truth = session
    loaded = loaded_of(truth, folder, **annotations_kwargs)
    info = control_info()
    step = state_of(loaded, info).next_step
    assert step.action == expected
    cursor = flow.resume(loaded.annotations, truth)
    if step.action == journey.CALIBRATE:
        assert cursor.kind == flow.CALIBRATION and cursor.photo == loaded.plan.first_photo
        assert journey.session_instruction(state_of(loaded, info), None, cursor) == "Calibration : point 1 sur 4"
        return
    if step.action == journey.GOTO:
        opened = flow.apply(loaded.annotations, truth, flow.Action("goto", number=step.number), cursor,
                            FakeSnapping(), info.plan)[1]
        assert (opened.kind, opened.number) == (flow.DART, step.number)
        return
    opened = flow.apply(loaded.annotations, truth, flow.Action("control"), cursor, FakeSnapping(), info.plan)[1]
    assert (opened.kind, opened.source, opened.number) == (flow.CONTROL, "photo", step.number)
    assert journey.session_instruction(state_of(loaded, info), None, opened) == step.text  # the screen's title


def test_the_control_key_is_accepted_from_the_complete_cursor_and_names_the_hint_u15(session):
    folder, truth = session
    loaded = loaded_of(truth, folder, darts=all_tips())
    info = control_info()
    cursor = flow.resume(loaded.annotations, truth)
    assert cursor.kind == flow.COMPLETE
    assert journey.session_instruction(state_of(loaded, info), None, cursor) == PHOTO_1 + " (touche K)"
    opened = flow.apply(loaded.annotations, truth, flow.Action("control"), cursor, FakeSnapping(), info.plan)[1]
    assert opened.kind == flow.CONTROL
    video = loaded_of(truth, folder, darts=all_tips(), controls=[photo_control(1), photo_control(3)])
    video_cursor = flow.apply(video.annotations, truth, flow.Action("control"), cursor, FakeSnapping(), info.plan)[1]
    assert (video_cursor.source, video_cursor.number) == ("video", 1)
    assert journey.session_instruction(state_of(video, info), None, video_cursor) == VIDEO_1


def test_the_recalibration_screen_keeps_its_own_title(session):
    folder, truth = session
    cursor = flow.Cursor(flow.RECALIBRATION, 3, "dart-003.jpg", (flow.Slot((1.0, 2.0), SNAPPED, None),))
    state = state_of(loaded_of(truth, folder, darts=all_tips()), control_info())
    assert journey.session_instruction(state, None, cursor) == "Recalibration à partir de la fléchette 3 : point 2 sur 4"


def test_the_continue_payload_follows_the_action_and_the_recalibration_text_is_defined_here_u15(session):
    payloads = {(None, None): None, ("calibrate", 1): {}, ("goto", 3): {"action": "goto", "number": 3},
                ("control", 2): {"action": "control"}}
    for (action, number), expected in payloads.items():
        assert journey.Instruction("x", "s", action, number).continue_payload() == expected
    assert journey.RECALIBRATION_EXPLANATION.startswith("Recalibrer veut dire : la cible ou le téléphone a bougé")
    assert journey.RECALIBRATION_EXPLANATION.endswith("Ne recalibrer que si le déplacement se voit à l'œil.")


NO_VIDEO_PLANNED = "Contrôle vidéo : aucune image prévue pour cette session"


def test_a_session_with_no_video_frame_planned_is_done_not_missing_cv26(session):
    """An eval session shorter than the video range (darts 55 to 60) plans no frame: nothing to do, not an absence."""
    folder, truth = session
    controls = [photo_control(1), photo_control(3)]
    info = control_info(video=(), frames=False, role="eval 1", planned=0)  # not listed in the manifest: nothing planned
    short = state_of(loaded_of(truth, folder, darts=all_tips(), controls=controls), info)
    assert [s.status for s in short.steps] == ["done", "done", "done", "done"]
    assert (short.steps[3].done, short.steps[3].total) == (0, 0)
    assert short.state == "Terminée" and short.next_step is None
    assert NO_VIDEO_PLANNED in short.info and NO_FRAMES not in short.info
    assert "Contrôle vidéo : indisponible (images vidéo absentes)" not in short.info
    assert journey.global_instruction([short]).text == "Contrôle terminé : fermer l'outil et lancer le diagnostic (K7.4)"
    open_photo = state_of(loaded_of(truth, folder, darts=all_tips(), controls=[photo_control(1)]), info)
    assert open_photo.next_step.text == "Contrôle de calibration : photo de la fléchette 3"  # photo control still asked


def test_planned_frames_missing_from_the_manifest_stay_unavailable_cv27(session):
    folder, truth = session
    loaded = loaded_of(truth, folder, darts=all_tips(), controls=[photo_control(1), photo_control(3)])
    for planned in (1, 2):
        absent = state_of(loaded, control_info(video=(), frames=False, role="eval 1", planned=planned))
        assert absent.next_step.text == NO_FRAMES and absent.steps[3].status == "unavailable"
        assert NO_VIDEO_PLANNED not in absent.info and absent.state == "En cours"
