"""Per-dart tip entry: pure state transitions, no I/O (CV5 judged darts, CV16 tool, N24).

`apply(annotations, session, action, cursor)` returns the new annotations and the new cursor. Judged darts come in
number order; voided darts and darts without a photo are skipped (CV5) and listed in `Plan.skipped`. A session opens on
its first dart without a tip, with or without a segment: tips need no segment (N24). The tool enters dart tips only;
the segments and controls of the file are never changed by an action and are kept as they are on write (CV20, CV25).
"""
import math
from dataclasses import dataclass, replace
from typing import Optional

from kosmo_camera.annotations import DartAnnotation, Status

DART = "dart"
COMPLETE = "complete"
KINDS = ("click", "hidden", "off_board", "clear", "next", "previous", "goto")


class FlowError(ValueError):
    """The action is not possible in the current state; the message is in French (shown to Kosmo)."""


@dataclass(frozen=True)
class Plan:
    first_photo: Optional[str]  # photo of the first judged dart; None when no dart is judged (no flow)
    darts: tuple  # (number, photo) of judged darts, ascending
    skipped: tuple  # numbers of darts that are voided or have no photo


@dataclass(frozen=True)
class Cursor:
    kind: str  # DART or COMPLETE
    number: Optional[int] = None  # dart number when kind is DART
    photo: Optional[str] = None  # photo shown for this cursor


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


def resume(annotations, session) -> Cursor:
    """Cursor when a session is opened: the first dart without annotation, else complete (no calibration screen)."""
    return _first_open(annotations, plan_of(session))


def _after(annotations, plan, number):
    numbers = [n for n, _ in plan.darts]
    index = numbers.index(number)
    if index + 1 < len(numbers):
        return _dart_cursor(plan, numbers[index + 1])
    return _first_open(annotations, plan)


def is_coordinate(value) -> bool:
    """A finite number: a bool, a string, nan, an infinity or an integer too large for a float are not."""
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return False
    try:
        return math.isfinite(value)
    except OverflowError:  # an integer beyond the float range
        return False


def _check_point(action):
    for value in (action.x, action.y):
        if not is_coordinate(value):
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
    return annotations, cursor


def _move(plan, cursor, forward):
    numbers = [n for n, _ in plan.darts]
    if cursor.kind == COMPLETE:
        return cursor if forward else _dart_cursor(plan, numbers[-1])
    index = numbers.index(cursor.number) + (1 if forward else -1)
    return _dart_cursor(plan, numbers[index]) if 0 <= index < len(numbers) else cursor


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
        return annotations, _move(plan, cursor, action.kind == "next")
    if action.kind == "goto":
        if action.number not in dict(plan.darts):
            raise FlowError("Fléchette inconnue.")
        return annotations, _dart_cursor(plan, action.number)
    return _dart_mark(annotations, plan, action, cursor)
