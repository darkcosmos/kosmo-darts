"""Journey of the annotation tool (CV16, CV26, CV30): what is done and what is left per session, and the one active
instruction, computed from the annotation file and the presence of frames only (never from `drift.json`, which is
information; never cached: recomputed at every request). Pure functions, stdlib only, all texts in French.

Rows, in order: `calibration`, `tips`, `control_photo`, `control_video`. The next step of a session is its first row not
done (an `unavailable` row is not done, so completion is never claimed over it, U17). Texts are the table
"Instructions" of the plan, each defined once here; the page only displays them.
"""
from dataclasses import dataclass
from typing import Optional

from kosmo_camera.annotate import flow, views
from kosmo_camera.annotations import PHOTO, VIDEO, control_for

CALIBRATION, TIPS, CONTROL_PHOTO, CONTROL_VIDEO = "calibration", "tips", "control_photo", "control_video"
ROWS = (CALIBRATION, TIPS, CONTROL_PHOTO, CONTROL_VIDEO)

TODO, IN_PROGRESS, DONE, UNAVAILABLE = "todo", "in_progress", "done", "unavailable"
STATE_TODO, STATE_IN_PROGRESS, STATE_DONE, STATE_IGNORED = "À faire", "En cours", "Terminée", "Ignorée"

CALIBRATE, GOTO, CONTROL = "calibrate", "goto", "control"  # the `action` of a next step, applied through the flow

CALIBRATION_TEXT = "Cliquer les 4 points de calibration de la photo de la fléchette {n}"
TIPS_TEXT = "Pointer les fléchettes : prochaine fléchette {n}"
CONTROL_PHOTO_TEXT = "Contrôle de calibration : photo de la fléchette {n}"
CONTROL_VIDEO_TEXT = "Contrôle de calibration : image vidéo de la fléchette {n}"
SNAP_UNAVAILABLE_TEXT = flow.UNAVAILABLE  # "Recalage indisponible : ..." (D43), the same sentence as the flow's
NO_SPLIT_TEXT = "Contrôle indisponible : relancer l'outil avec --split (K7.3)"
NO_FRAMES_TEXT = "Images vidéo absentes : lancer l'extraction (K7.2)"
SESSION_DONE_TEXT = "Session terminée : passer à la session suivante"
BLOCKED_TEXT = "Session illisible ou refusée : voir l'information de la carte"  # only a blocked session is left (U35)
ALL_DONE_TEXT = "Contrôle terminé : fermer l'outil et lancer le diagnostic (K7.4)"
LIST_HINT = "(bouton Continuer)"
CONTROL_KEY_HINT = "(touche K)"
CALIBRATION_TITLE = "Calibration : point {n} sur 4"
RECALIBRATION_TITLE = "Recalibration à partir de la fléchette {number} : point {n} sur 4"
NO_FRAMES_REASON = "Contrôle vidéo : indisponible (images vidéo absentes)"
DRIFT_INFO = "Mesure de dérive non validée : information seulement"
NO_USABLE_FRAMES = "Aucune image vidéo utilisable pour cette session ({n} manquantes)"


@dataclass(frozen=True)
class Step:
    key: str
    done: int
    total: int
    status: str
    reason: Optional[str] = None  # why the row is `unavailable`, in French


@dataclass(frozen=True)
class NextStep:
    key: Optional[str]  # the row, None for the "session done" step
    text: str
    action: Optional[str] = None  # CALIBRATE, GOTO, CONTROL or None
    number: Optional[int] = None  # the dart of the row, or the focus session for a goto
    hint: Optional[str] = None  # the hint of the session screen: the key, for the control rows only

    def for_list(self) -> str:
        """The text followed by the hint of the list: the button that applies the action."""
        return f"{self.text} {LIST_HINT}" if self.action else self.text

    def for_session(self) -> str:
        return f"{self.text} {self.hint}" if self.hint else self.text


@dataclass(frozen=True)
class SessionState:
    name: str
    state: str  # STATE_*
    role: Optional[str] = None
    steps: tuple = ()
    next_step: Optional[NextStep] = None
    info: tuple = ()  # information lines only: never an instruction
    blocked: bool = False  # unreadable files: nothing to do, but not done either


@dataclass(frozen=True)
class Instruction:
    text: str  # BLOCKED_TEXT while a session is blocked and nothing else is left
    focus: Optional[str]  # the focus session name
    action: Optional[str] = None
    number: Optional[int] = None


def _status(done, total):
    if done >= total:
        return DONE
    return IN_PROGRESS if done else TODO


def _tip_steps(loaded):
    numbers = [n for n, _ in loaded.plan.darts]
    annotated = {d.number for d in loaded.annotations.darts}
    first_open = next((n for n in numbers if n not in annotated), None)
    done = len(annotated & set(numbers))
    return Step(TIPS, done, len(numbers), _status(done, len(numbers))), first_open


def _control_steps(loaded, control_info):
    """The two control rows and the first open dart of each source (None when nothing is left or available)."""
    if control_info.plan is None:
        off = control_info.reason
        return Step(CONTROL_PHOTO, 0, 0, UNAVAILABLE, off), Step(CONTROL_VIDEO, 0, 0, UNAVAILABLE, off), {}
    progress = flow.control_progress(loaded.annotations, control_info.plan)
    first_open = {}
    for source, number in control_info.plan.items:
        if source not in first_open and control_for(loaded.annotations, number, source) is None:
            first_open[source] = number
    photo = Step(CONTROL_PHOTO, progress[PHOTO]["done"], progress[PHOTO]["total"],
                 _status(progress[PHOTO]["done"], progress[PHOTO]["total"]))
    if not control_info.frames_present:
        return photo, Step(CONTROL_VIDEO, 0, 0, UNAVAILABLE, NO_FRAMES_REASON), first_open
    video = Step(CONTROL_VIDEO, progress[VIDEO]["done"], progress[VIDEO]["total"],
                 _status(progress[VIDEO]["done"], progress[VIDEO]["total"]))
    return photo, video, first_open


def _info_lines(loaded, control_info, drift, steps):
    lines = []
    if drift.configured and drift.warning is None:
        lines += [DRIFT_INFO, views.drift_view(loaded, drift)[0]]
    lines += [step.reason for step in steps if step.status == UNAVAILABLE]
    missing = sum(control_info.frame_reasons.values())
    video = next(step for step in steps if step.key == CONTROL_VIDEO)
    if video.status == DONE and video.total == 0 and missing:
        lines.append(NO_USABLE_FRAMES.format(n=missing))
    return tuple(dict.fromkeys(lines))  # both control rows may share the same reason: said once


def _next_step(steps, first_tip, first_open, first_dart, snap_available):
    step = next((s for s in steps if s.status != DONE), None)
    if step is None:
        return None
    unavailable = step.status == UNAVAILABLE
    if step.key == CALIBRATION:
        if not snap_available:
            return NextStep(CALIBRATION, SNAP_UNAVAILABLE_TEXT)
        return NextStep(CALIBRATION, CALIBRATION_TEXT.format(n=first_dart), CALIBRATE, first_dart)
    if step.key == TIPS:
        return NextStep(TIPS, TIPS_TEXT.format(n=first_tip), GOTO, first_tip)
    if unavailable:
        return NextStep(step.key, NO_FRAMES_TEXT if step.reason == NO_FRAMES_REASON else NO_SPLIT_TEXT)
    if not snap_available:
        return NextStep(step.key, SNAP_UNAVAILABLE_TEXT)
    if step.key == CONTROL_PHOTO:
        number = first_open[PHOTO]
        return NextStep(CONTROL_PHOTO, CONTROL_PHOTO_TEXT.format(n=number), CONTROL, number, CONTROL_KEY_HINT)
    number = first_open[VIDEO]
    return NextStep(CONTROL_VIDEO, CONTROL_VIDEO_TEXT.format(n=number), CONTROL, number, CONTROL_KEY_HINT)


def session_state(loaded, control_info, drift, snap_available) -> SessionState:
    """The state of one loaded session: rows, next step and information lines (`control_info`: `ControlInfo`)."""
    role = None if control_info is None else control_info.role
    if loaded.state == views.IGNORED:
        return SessionState(loaded.name, STATE_IGNORED, role)
    if loaded.state != views.OK:
        return SessionState(loaded.name, STATE_TODO, role, info=(loaded.message,), blocked=True)
    calibrated = bool(loaded.annotations.calibrations)
    tips, first_tip = _tip_steps(loaded)
    photo, video, first_open = _control_steps(loaded, control_info)
    steps = (Step(CALIBRATION, int(calibrated), 1, DONE if calibrated else TODO), tips, photo, video)
    next_step = _next_step(steps, first_tip, first_open, loaded.plan.darts[0][0], snap_available)
    if next_step is None:
        state = STATE_DONE
    else:
        state = STATE_IN_PROGRESS if any(s.done for s in steps) else STATE_TODO
    return SessionState(loaded.name, state, role, steps, next_step,
                        _info_lines(loaded, control_info, drift, steps))


def global_instruction(states) -> Instruction:
    """The focus session (first in list order with a step left) and its instruction; the final row only when nothing
    is left, unavailable or blocked in any session (U17)."""
    focus = next((s for s in states if s.next_step is not None), None)
    if focus is not None:
        step = focus.next_step
        return Instruction(step.for_list(), focus.name, step.action, step.number)
    if any(s.blocked for s in states):
        return Instruction(BLOCKED_TEXT, None)
    return Instruction(ALL_DONE_TEXT, None)


def session_instruction(state, focus_name, cursor=None) -> Optional[str]:
    """The instruction of one session screen: the title of a calibration, recalibration or control screen, else the
    next step of the session with its key hint, else "session done" (when `focus_name` is another session left) or the final row."""
    if state.blocked or state.state == STATE_IGNORED:
        return None
    if cursor is not None:
        title = _screen_title(cursor)
        if title is not None:
            return title
    if state.next_step is not None:
        return state.next_step.for_session()
    return SESSION_DONE_TEXT if focus_name is not None else ALL_DONE_TEXT


def _screen_title(cursor):
    if cursor.kind == flow.CONTROL:
        text = CONTROL_PHOTO_TEXT if cursor.source == PHOTO else CONTROL_VIDEO_TEXT
        return text.format(n=cursor.number)
    next_point = min(flow.ready_count(cursor.pending) + 1, 4)
    if cursor.kind == flow.CALIBRATION:
        return CALIBRATION_TITLE.format(n=next_point)
    if cursor.kind == flow.RECALIBRATION:
        return RECALIBRATION_TITLE.format(number=cursor.number, n=next_point)
    return None


def session_fields(state) -> dict:
    """The journey fields of a session in the JSON answers: `badge` (`state` keeps the load state the page reads until
    T15g), `steps`, `info`, and `role` when there is one."""
    fields = {"badge": state.state, "info": list(state.info),
              "steps": [{"key": s.key, "done": s.done, "total": s.total, "status": s.status, "reason": s.reason}
                        for s in state.steps]}
    if state.role is not None:
        fields["role"] = state.role
    return fields


def next_step_fields(state) -> Optional[dict]:
    step = state.next_step
    if step is None:
        return None
    return {"key": step.key, "text": step.text, "action": step.action, "number": step.number,
            "hint": step.hint}
