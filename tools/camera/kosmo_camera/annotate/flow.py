"""Per-dart annotation flow: pure state transitions, no I/O (CV2 calibration order, CV5 judged darts).

`apply(annotations, session, action, cursor)` returns the new annotations and the new cursor. Order: calibration
points 1 to 4 on the first photo of the session, then judged darts by number. Voided darts and darts without a photo
are skipped (CV5) and listed in `Plan.skipped`. The file format cannot hold a partial calibration, so the points
clicked so far travel in `Cursor.pending` (kept in memory by the server).
"""
import math
from dataclasses import dataclass, replace
from typing import Optional

from kosmo_camera.annotations import CALIBRATION_POINTS, Annotations, Calibration, DartAnnotation, Status

CALIBRATION = "calibration"
DART = "dart"
COMPLETE = "complete"
KINDS = ("click", "hidden", "off_board", "clear", "next", "previous", "goto")


class FlowError(ValueError):
    """The action is not possible in the current state; the message is in French (shown to Kosmo)."""


@dataclass(frozen=True)
class Plan:
    first_photo: Optional[str]  # calibration photo: photo of the first judged dart
    darts: tuple  # (number, photo) of judged darts, ascending
    skipped: tuple  # numbers of darts that are voided or have no photo


@dataclass(frozen=True)
class Cursor:
    kind: str  # CALIBRATION, DART or COMPLETE
    number: Optional[int] = None  # dart number when kind is DART
    photo: Optional[str] = None  # photo shown for this cursor
    pending: tuple = ()  # calibration points clicked so far, when kind is CALIBRATION


@dataclass(frozen=True)
class Action:
    kind: str
    x: Optional[float] = None
    y: Optional[float] = None
    number: Optional[int] = None  # target of "goto"
    size: Optional[tuple] = None  # real JPEG size (width, height) of the cursor photo, for "click"


def plan_of(session) -> Plan:
    judged, skipped = [], []
    for volley in session.volleys:
        for dart in volley.darts:
            if dart.judged:
                judged.append((dart.number, dart.photo.file))
            else:
                skipped.append(dart.number)
    judged.sort()
    return Plan(judged[0][1] if judged else None, tuple(judged), tuple(sorted(skipped)))


def _annotated(annotations):
    return {d.number for d in annotations.darts}


def _dart_cursor(plan, number):
    return Cursor(DART, number, dict(plan.darts)[number])


def _first_open(annotations, plan):
    """First dart not annotated, else the complete state."""
    done = _annotated(annotations)
    for number, _ in plan.darts:
        if number not in done:
            return _dart_cursor(plan, number)
    return Cursor(COMPLETE)


def resume(annotations, session, pending=()) -> Cursor:
    """Cursor when a session is opened: first missing calibration point, else first dart not annotated, else complete."""
    plan = plan_of(session)
    if annotations.calibration is None:
        return Cursor(CALIBRATION, photo=plan.first_photo, pending=tuple(pending)[:CALIBRATION_POINTS - 1])
    return _first_open(annotations, plan)


def _after(annotations, plan, number):
    numbers = [n for n, _ in plan.darts]
    index = numbers.index(number)
    if index + 1 < len(numbers):
        return _dart_cursor(plan, numbers[index + 1])
    return _first_open(annotations, plan)


def _check_point(action):
    for value in (action.x, action.y):
        if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
            raise FlowError("Coordonnées invalides.")
    if action.size is None:
        raise FlowError("Taille de la photo inconnue.")
    width, height = action.size
    if not (0 <= action.x <= width and 0 <= action.y <= height):
        raise FlowError("Le point est en dehors de la photo.")
    return float(action.x), float(action.y)


def _upsert(annotations, annotation):
    others = tuple(d for d in annotations.darts if d.number != annotation.number)
    darts = tuple(sorted(others + (annotation,), key=lambda d: d.number))
    return replace(annotations, darts=darts)


def _calibration_click(annotations, plan, action, cursor):
    if len(cursor.pending) >= CALIBRATION_POINTS:
        return annotations, cursor  # four points already set: "Effacer" first
    points = cursor.pending + (_check_point(action),)
    if len(points) < CALIBRATION_POINTS:
        return annotations, replace(cursor, pending=points)
    width, height = action.size
    calibration = Calibration(cursor.photo, int(width), int(height), points)
    updated = replace(annotations, calibration=calibration)
    return updated, _first_open(updated, plan)


def _dart_mark(annotations, plan, action, cursor):
    if cursor.kind != DART:
        raise FlowError("Aucune fléchette sélectionnée.")
    if action.kind == "click":
        x, y = _check_point(action)
        annotation = DartAnnotation(cursor.number, Status.VISIBLE, (x, y))
    else:
        status = Status.HIDDEN if action.kind == "hidden" else Status.OFF_BOARD
        annotation = DartAnnotation(cursor.number, status, None)
    updated = _upsert(annotations, annotation)
    return updated, _after(updated, plan, cursor.number)


def _clear(annotations, cursor):
    if cursor.kind == DART:
        darts = tuple(d for d in annotations.darts if d.number != cursor.number)
        return replace(annotations, darts=darts), cursor
    if cursor.kind == CALIBRATION and cursor.pending:
        updated = replace(annotations, calibration=None) if annotations.calibration is not None else annotations
        return updated, replace(cursor, pending=cursor.pending[:-1])
    return annotations, cursor


def _move(annotations, plan, cursor, forward):
    numbers = [n for n, _ in plan.darts]
    if cursor.kind == CALIBRATION:
        if forward and annotations.calibration is not None and numbers:
            return _dart_cursor(plan, numbers[0])
        return cursor
    if cursor.kind == COMPLETE:
        return cursor if forward or not numbers else _dart_cursor(plan, numbers[-1])
    index = numbers.index(cursor.number)
    if forward:
        return _dart_cursor(plan, numbers[index + 1]) if index + 1 < len(numbers) else cursor
    if index > 0:
        return _dart_cursor(plan, numbers[index - 1])
    if annotations.calibration is not None:  # back to the calibration, which can then be cleared and redone
        return Cursor(CALIBRATION, photo=plan.first_photo, pending=annotations.calibration.points)
    return cursor


def apply(annotations, session, action, cursor=None) -> tuple:
    """Apply one action; return (annotations, cursor). Raise FlowError when the action is not possible."""
    if action.kind not in KINDS:
        raise FlowError("Action inconnue.")
    plan = plan_of(session)
    if plan.first_photo is None:
        raise FlowError("Aucune fléchette jugée dans cette session.")
    cursor = cursor if cursor is not None else resume(annotations, session)
    if action.kind == "clear":
        return _clear(annotations, cursor)
    if action.kind in ("next", "previous"):
        return annotations, _move(annotations, plan, cursor, action.kind == "next")
    if action.kind == "goto":
        if annotations.calibration is None:
            raise FlowError("Calibrez d'abord le cadran.")
        if action.number not in dict(plan.darts):
            raise FlowError("Fléchette inconnue.")
        return annotations, _dart_cursor(plan, action.number)
    if cursor.kind == CALIBRATION:
        if action.kind != "click":
            raise FlowError("Calibrez d'abord le cadran.")
        return _calibration_click(annotations, plan, action, cursor)
    return _dart_mark(annotations, plan, action, cursor)
