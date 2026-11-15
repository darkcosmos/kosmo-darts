"""Per-dart annotation flow: pure state transitions, no I/O (CV2 calibration order, CV5 judged darts).

`apply(annotations, session, action, cursor)` returns the new annotations and the new cursor. Order: calibration
points 1 to 4 on the first photo of the session, then judged darts by number. Voided darts and darts without a photo
are skipped (CV5) and listed in `Plan.skipped`. The file format cannot hold a partial calibration, so the points
clicked so far travel in `Cursor.pending` (kept in memory by the server). The calibration screen edits segment 0,
from dart 1, of the segment list (CV20); later segments are kept as they are.
"""
import math
from dataclasses import dataclass, replace
from typing import Optional

from kosmo_camera.annotations import (CALIBRATION_POINTS, MAX_SEGMENTS, Annotations, Calibration, DartAnnotation,
                                      Status, segment_for)

CALIBRATION = "calibration"
DART = "dart"
RECALIBRATION = "recalibration"  # new segment from dart `number` (CV20), points in `pending`
COMPLETE = "complete"
KINDS = ("click", "hidden", "off_board", "clear", "next", "previous", "goto", "recalibrate")


class FlowError(ValueError):
    """The action is not possible in the current state; the message is in French (shown to Kosmo)."""


class FlowConflict(FlowError):
    """The action would break a cap of the file (CV20): answered 409 by the server."""


@dataclass(frozen=True)
class Plan:
    first_photo: Optional[str]  # calibration photo: photo of the first judged dart
    darts: tuple  # (number, photo) of judged darts, ascending
    skipped: tuple  # numbers of darts that are voided or have no photo


@dataclass(frozen=True)
class Cursor:
    kind: str  # CALIBRATION, DART or COMPLETE
    number: Optional[int] = None  # dart number when kind is DART or RECALIBRATION
    photo: Optional[str] = None  # photo shown for this cursor
    pending: tuple = ()  # points clicked so far, when kind is CALIBRATION or RECALIBRATION


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


def _calibrated(annotations):
    """Segment 0 exists (CV20: when there is a segment, the first one starts at dart 1)."""
    return bool(annotations.calibrations)


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
    if not _calibrated(annotations):
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
    calibration = Calibration(1, cursor.photo, int(width), int(height), points)
    updated = replace(annotations, calibrations=(calibration,) + annotations.calibrations[1:])
    return updated, _first_open(updated, plan)


def _segment_at(annotations, number):
    """Index of the segment that starts exactly at dart `number`, else None (lookup through `segment_for`, A15)."""
    found = segment_for(annotations, number)
    if found is not None and found[1].from_dart == number:
        return found[0]
    return None


def _recalibrate(annotations, plan, cursor):
    """R on a judged dart: the first judged dart is the calibration screen, else a recalibration from that dart."""
    if cursor.kind != DART:
        raise FlowError("Aucune fléchette sélectionnée.")
    if not _calibrated(annotations):
        raise FlowError("Calibrez d'abord le cadran.")
    if cursor.number == plan.darts[0][0]:
        return annotations, Cursor(CALIBRATION, photo=plan.first_photo, pending=annotations.calibrations[0].points)
    index = _segment_at(annotations, cursor.number)
    pending = () if index is None else annotations.calibrations[index].points
    return annotations, Cursor(RECALIBRATION, cursor.number, cursor.photo, pending)


def _recalibration_click(annotations, plan, action, cursor):
    if len(cursor.pending) >= CALIBRATION_POINTS:
        return annotations, cursor  # four points already set: "Effacer" first
    points = cursor.pending + (_check_point(action),)
    if len(points) < CALIBRATION_POINTS:
        return annotations, replace(cursor, pending=points)
    index = _segment_at(annotations, cursor.number)
    if index is None and len(annotations.calibrations) >= MAX_SEGMENTS:
        raise FlowConflict(f"Nombre maximal de recalibrations atteint ({MAX_SEGMENTS})")
    width, height = action.size
    segment = Calibration(cursor.number, cursor.photo, int(width), int(height), points)
    others = tuple(c for c in annotations.calibrations if c.from_dart != cursor.number)
    segments = tuple(sorted(others + (segment,), key=lambda c: c.from_dart))
    return replace(annotations, calibrations=segments), _dart_cursor(plan, cursor.number)


def _recalibration_clear(annotations, plan, cursor):
    index = _segment_at(annotations, cursor.number)
    if index is not None:  # saved segment: removed from the file, its other points stay pending
        segments = annotations.calibrations[:index] + annotations.calibrations[index + 1:]
        return replace(annotations, calibrations=segments), replace(cursor, pending=cursor.pending[:-1])
    if cursor.pending:
        return annotations, replace(cursor, pending=cursor.pending[:-1])
    return annotations, _dart_cursor(plan, cursor.number)


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


def _clear(annotations, plan, cursor):
    if cursor.kind == RECALIBRATION:
        return _recalibration_clear(annotations, plan, cursor)
    if cursor.kind == DART:
        darts = tuple(d for d in annotations.darts if d.number != cursor.number)
        return replace(annotations, darts=darts), cursor
    if cursor.kind == CALIBRATION and cursor.pending:
        if len(annotations.calibrations) > 1:  # removing segment 0 would leave no segment from dart 1 (CV20)
            raise FlowError("Supprimez d'abord les recalibrations suivantes.")
        updated = replace(annotations, calibrations=()) if _calibrated(annotations) else annotations
        return updated, replace(cursor, pending=cursor.pending[:-1])
    return annotations, cursor


def _move(annotations, plan, cursor, forward):
    numbers = [n for n, _ in plan.darts]
    if cursor.kind == CALIBRATION:
        if forward and _calibrated(annotations) and numbers:
            return _dart_cursor(plan, numbers[0])
        return cursor
    if cursor.kind == COMPLETE:
        return cursor if forward or not numbers else _dart_cursor(plan, numbers[-1])
    index = numbers.index(cursor.number)  # DART or RECALIBRATION: leaving drops the unsaved points
    if forward:
        return _dart_cursor(plan, numbers[index + 1]) if index + 1 < len(numbers) else cursor
    if index > 0:
        return _dart_cursor(plan, numbers[index - 1])
    if _calibrated(annotations):  # back to segment 0, which can then be cleared and redone
        return Cursor(CALIBRATION, photo=plan.first_photo, pending=annotations.calibrations[0].points)
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
        return _clear(annotations, plan, cursor)
    if action.kind in ("next", "previous"):
        return annotations, _move(annotations, plan, cursor, action.kind == "next")
    if action.kind == "goto":
        if not _calibrated(annotations):
            raise FlowError("Calibrez d'abord le cadran.")
        if action.number not in dict(plan.darts):
            raise FlowError("Fléchette inconnue.")
        return annotations, _dart_cursor(plan, action.number)
    if action.kind == "recalibrate":
        return _recalibrate(annotations, plan, cursor)
    if cursor.kind == RECALIBRATION:
        if action.kind != "click":
            raise FlowError("Aucune fléchette sélectionnée.")
        return _recalibration_click(annotations, plan, action, cursor)
    if cursor.kind == CALIBRATION:
        if action.kind != "click":
            raise FlowError("Calibrez d'abord le cadran.")
        return _calibration_click(annotations, plan, action, cursor)
    return _dart_mark(annotations, plan, action, cursor)
