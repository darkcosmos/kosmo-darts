"""Per-dart annotation flow: pure state transitions, no I/O (CV2 calibration order, CV5 judged darts).

`apply(annotations, session, action, cursor)` returns the new annotations and the new cursor. Order: calibration
points 1 to 4 on the first photo of the session, then judged darts by number. Voided darts and darts without a photo
are skipped (CV5) and listed in `Plan.skipped`. The file format cannot hold a partial calibration, so the 4 slots of
the open calibration or recalibration item travel in `Cursor.pending` (kept in memory by the server). The calibration
screen edits segment 0, from dart 1, of the segment list (CV20); later segments are kept as they are.

Control mode (CV25, CV26, CV31): `control` opens the first sampled photo or video frame without a control (`ControlPlan`
from the server: the sample and the frames of `frames.json`); the 4 slots, proposals, hide rule and save rule are the
assisted ones, and `save` writes a `Control` instead of a segment. Nothing is written before `save`.

Assisted click (CV31, D39, D43): a click on these screens goes to the slot named by `click_target` (U27) and is snapped
at once through the `snapping` object given to `apply` (`available` and `snap(photo, start)`; the server binds the
session folder, so the flow does no I/O). Nothing is written before `save`, which needs 4 ready points (U29).
"""
import math
from dataclasses import dataclass, replace
from typing import Optional

from kosmo_camera import config
from kosmo_camera.annotate.snapping import UNREADABLE
from kosmo_camera.annotations import (CALIBRATION_POINTS, MAX_CONTROLS, MAX_SEGMENTS, PHOTO, SNAPPED, SOURCES, VIDEO,
                                      Annotations, Calibration, Control, DartAnnotation, Status, control_for,
                                      segment_for)

CALIBRATION = "calibration"
DART = "dart"
RECALIBRATION = "recalibration"  # new segment from dart `number` (CV20), points in `pending`
COMPLETE = "complete"
CONTROL = "control"  # a control item (CV25): `number` is the dart, `source` PHOTO or VIDEO, `photo` the image file
CONTROL_DONE = "control_done"  # "Contrôle terminé": no sampled item without a control is left
HIDDEN = "hidden"  # status of a hidden slot (control only, CV31 hide rule): no point, ready to save as `null`
KINDS = ("click", "hidden", "off_board", "clear", "next", "previous", "goto", "recalibrate", "select", "save",
         "control", "hidden_point")
CONTROL_OFF = "Contrôle : indisponible (option --split absente)"
NO_HIDDEN_SEGMENT = "Un segment demande 4 points : cet écran n'accepte pas « caché »."
CONTROL_NO_STATUS = "Sur cet écran, H cache un point : une fléchette n'a pas de statut ici."
HIDE_REFUSED = "Choisir le point à cacher : touche 1 à 4, puis H"
UNAVAILABLE = "Recalage indisponible : relancer l'outil avec ~/kosmo-tool-venv/bin/python (K7.1b)"
ALREADY_SAVED = "Photo déjà enregistrée"
FAR_CLICK = "Clic loin des 4 points : choisir le point avec les touches 1 à 4, puis cliquer"


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
class Slot:
    """One calibration point of the open item: empty (`à cliquer`) or positioned with its snap status (CV31)."""
    point: Optional[tuple] = None  # (x, y) in D8 pixels, or None
    status: Optional[str] = None  # SNAPPED, UNSNAPPED or MANUAL (a saved v2 point); None when empty
    reason: Optional[str] = None  # why the snap failed, when status is UNSNAPPED; `unreadable` on an empty proposal


EMPTY = Slot()
BLANK = (EMPTY,) * CALIBRATION_POINTS


@dataclass(frozen=True)
class Cursor:
    kind: str  # CALIBRATION, DART or COMPLETE
    number: Optional[int] = None  # dart number when kind is DART or RECALIBRATION
    photo: Optional[str] = None  # photo shown for this cursor
    pending: tuple = ()  # the 4 `Slot`s of the open item, when kind is CALIBRATION or RECALIBRATION
    source: Optional[str] = None  # PHOTO or VIDEO when kind is CONTROL
    selected: Optional[int] = None  # point 1 to 4 chosen with the keys, taken by the next click
    history: tuple = ()  # earlier (pending, selected) of this item, for undo


@dataclass(frozen=True)
class Action:
    kind: str
    x: Optional[float] = None
    y: Optional[float] = None
    number: Optional[int] = None  # target of "goto"
    size: Optional[tuple] = None  # real JPEG size (width, height) of the cursor photo, for "click" and "save"
    source: Optional[str] = None  # PHOTO or VIDEO, with `number` the dart, for a control "save"


@dataclass(frozen=True)
class ControlPlan:
    """The control items of one session (CV26), built by the server: photo darts, then video frames in dart order."""
    photo_darts: tuple  # ascending dart numbers
    video_frames: tuple = ()  # (dart, file, position_ms), ascending dart: only darts with a frame in frames.json

    @property
    def items(self) -> tuple:
        return (tuple((PHOTO, dart) for dart in self.photo_darts)
                + tuple((VIDEO, dart) for dart, _, _ in self.video_frames))

    def file_of(self, plan, source, number) -> str:
        if source == PHOTO:
            return dict(plan.darts)[number]
        return next(file for dart, file, _ in self.video_frames if dart == number)

    def position_of(self, number) -> int:
        return next(position for dart, _, position in self.video_frames if dart == number)


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
    """Cursor when a session is opened: the calibration screen, else first dart not annotated, else complete."""
    plan = plan_of(session)
    if not _calibrated(annotations):
        slots = tuple(pending) if len(pending) == CALIBRATION_POINTS else BLANK
        return Cursor(CALIBRATION, photo=plan.first_photo, pending=slots)
    return _first_open(annotations, plan)


def _saved_slots(calibration):
    return tuple(Slot(point, status, None) for point, status in zip(calibration.points, calibration.snap))


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


def ready_count(slots) -> int:
    """Points ready to save: positioned (snapped or clicked) or hidden (a proposal that did not snap is empty)."""
    return sum(1 for slot in slots if slot.point is not None or slot.status == HIDDEN)


def click_target(slots, click, selected=None) -> int:
    """Index (0 to 3) of the slot a click goes to (CV31 click rule, U27); FlowConflict when none can take it.

    The selected point takes it; else the positioned point nearest to the click within `CLICK_REPLACE_RADIUS_PX`
    (equal distances: the lowest index); else the first empty slot (a hidden slot is not empty); else refused."""
    if selected is not None:
        return selected - 1
    nearest, best = None, None
    for index, slot in enumerate(slots):
        if slot.point is None:
            continue
        distance = math.hypot(slot.point[0] - click[0], slot.point[1] - click[1])
        if distance <= config.CLICK_REPLACE_RADIUS_PX and (best is None or distance < best):
            nearest, best = index, distance
    if nearest is not None:
        return nearest
    for index, slot in enumerate(slots):
        if slot.point is None and slot.status != HIDDEN:
            return index
    raise FlowConflict(FAR_CLICK)


def _require_snapping(snapping):
    if snapping is None or not snapping.available:
        raise FlowConflict(UNAVAILABLE)


def _assist_click(annotations, plan, action, cursor, snapping):
    _require_snapping(snapping)
    click = _check_point(action)
    index = click_target(cursor.pending, click, cursor.selected)
    outcome = snapping.snap(cursor.photo, click)
    slot = Slot((float(outcome.point[0]), float(outcome.point[1])), outcome.status, outcome.reason)
    slots = cursor.pending[:index] + (slot,) + cursor.pending[index + 1:]
    history = cursor.history + ((cursor.pending, cursor.selected),)
    return annotations, replace(cursor, pending=slots, selected=None, history=history)


def _select(cursor, action):
    if cursor.kind not in (CALIBRATION, RECALIBRATION, CONTROL):
        raise FlowError("Aucun point à choisir sur cet écran.")
    number = action.number
    if isinstance(number, bool) or not isinstance(number, int) or not 1 <= number <= CALIBRATION_POINTS:
        raise FlowError("Point inconnu : touche 1 à 4.")
    return replace(cursor, selected=number)


def _open_item(cursor):
    """Dart number of the open assisted item: the calibration screen is the segment from dart 1."""
    return 1 if cursor.kind == CALIBRATION else cursor.number


def _save(annotations, plan, action, cursor, snapping):
    if cursor.kind not in (CALIBRATION, RECALIBRATION) or action.number != _open_item(cursor):
        raise FlowConflict(ALREADY_SAVED)  # a repeated save never writes the next item (U29)
    _require_snapping(snapping)
    ready = ready_count(cursor.pending)
    if ready < CALIBRATION_POINTS:
        raise FlowConflict(f"Points prêts : {ready} / {CALIBRATION_POINTS}")
    if action.size is None:
        raise FlowError("Taille de la photo inconnue.")
    width, height = action.size
    points = tuple(slot.point for slot in cursor.pending)
    snap = tuple(slot.status for slot in cursor.pending)
    if cursor.kind == CALIBRATION:
        segment = Calibration(1, cursor.photo, int(width), int(height), points, snap)
        updated = replace(annotations, calibrations=(segment,) + annotations.calibrations[1:])
        return updated, _first_open(updated, plan)
    if _segment_at(annotations, cursor.number) is None and len(annotations.calibrations) >= MAX_SEGMENTS:
        raise FlowConflict(f"Nombre maximal de recalibrations atteint ({MAX_SEGMENTS})")
    segment = Calibration(cursor.number, cursor.photo, int(width), int(height), points, snap)
    others = tuple(c for c in annotations.calibrations if c.from_dart != cursor.number)
    segments = tuple(sorted(others + (segment,), key=lambda c: c.from_dart))
    return replace(annotations, calibrations=segments), _dart_cursor(plan, cursor.number)


def _segment_at(annotations, number):
    """Index of the segment that starts exactly at dart `number`, else None (lookup through `segment_for`, A15)."""
    found = segment_for(annotations, number)
    if found is not None and found[1].from_dart == number:
        return found[0]
    return None


def _propose(snapping, photo, point):
    """One proposal snapped on the new image (CV31); a point that does not snap stays to be clicked, a hidden or absent
    one is empty, and an unreadable image is remembered in the empty slot's reason (S14)."""
    if point is None:
        return EMPTY
    outcome = snapping.snap(photo, point)
    if outcome.status == SNAPPED:
        return Slot(tuple(outcome.point), outcome.status, None)
    return Slot(None, None, outcome.reason) if outcome.reason == UNREADABLE else EMPTY


def _proposals(annotations, cursor, snapping):
    """The segment in force, snapped on the new photo (CV31)."""
    found = segment_for(annotations, cursor.number)
    return tuple(_propose(snapping, cursor.photo, point) for point in found[1].points)


def _recalibrate(annotations, plan, cursor, snapping):
    """R on a judged dart: the first judged dart is the calibration screen, else a recalibration from that dart."""
    if cursor.kind != DART:
        raise FlowError("Aucune fléchette sélectionnée.")
    if not _calibrated(annotations):
        raise FlowError("Calibrez d'abord le cadran.")
    if cursor.number == plan.darts[0][0]:
        return annotations, Cursor(CALIBRATION, photo=plan.first_photo, pending=_saved_slots(annotations.calibrations[0]))
    _require_snapping(snapping)
    index = _segment_at(annotations, cursor.number)
    slots = _saved_slots(annotations.calibrations[index]) if index is not None \
        else _proposals(annotations, cursor, snapping)
    return annotations, Cursor(RECALIBRATION, cursor.number, cursor.photo, slots)


def _assist_clear(annotations, plan, cursor):
    """Undo the last change of the open item; with none, drop a saved segment (CV20) or leave the item."""
    if cursor.history:
        pending, selected = cursor.history[-1]
        return annotations, replace(cursor, pending=pending, selected=selected, history=cursor.history[:-1])
    index = _segment_at(annotations, _open_item(cursor))
    if index is not None:  # saved segment: removed from the file at once, its other points stay on screen
        if cursor.kind == CALIBRATION and len(annotations.calibrations) > 1:
            raise FlowError("Supprimez d'abord les recalibrations suivantes.")
        segments = annotations.calibrations[:index] + annotations.calibrations[index + 1:]
        return replace(annotations, calibrations=segments), replace(cursor, pending=cursor.pending[:-1] + (EMPTY,),
                                                                     selected=None)
    if cursor.kind == RECALIBRATION:
        return annotations, _dart_cursor(plan, cursor.number)
    return annotations, replace(cursor, selected=None)


# ---- control items (CV25, CV26, CV31) -----------------------------------------------------------------------------

def _require_control(control):
    if control is None:
        raise FlowConflict(CONTROL_OFF)


def control_progress(annotations, control) -> dict:
    """Per source, the sampled items with a saved control and the sampled total (a tip change never moves them)."""
    result = {}
    for source in SOURCES:
        items = [item for item in control.items if item[0] == source]
        done = sum(1 for _, number in items if control_for(annotations, number, source) is not None)
        result[source] = {"done": done, "total": len(items)}
    return result


def _first_open_item(annotations, control):
    """First sampled item without a control, photos before video; None when none is left."""
    for item in control.items:
        if control_for(annotations, item[1], item[0]) is None:
            return item
    return None


def _proposal_points(annotations, control, item):
    """Points proposed for a new item: the nearest earlier control of the same source (CV31), else the segment of
    that dart for a photo, else none (the first video item)."""
    source, number = item
    earlier = [c for c in annotations.controls if c.source == source and c.dart < number]
    if earlier:
        return max(earlier, key=lambda c: c.dart).points
    found = segment_for(annotations, number) if source == PHOTO else None
    return None if found is None else found[1].points


def _open_control(annotations, plan, control, item, snapping, points=None):
    """The cursor of an item with its 4 slots: `points` (or the rule's proposals) snapped on that image."""
    source, number = item
    photo = control.file_of(plan, source, number)
    points = _proposal_points(annotations, control, item) if points is None else points
    slots = BLANK if points is None else tuple(_propose(snapping, photo, point) for point in points)
    return Cursor(CONTROL, number, photo, slots, source)


def _control_open(annotations, plan, cursor, snapping, control):
    """K: from any cursor; the item of the current dart when it is sampled and open, else the first open item."""
    _require_control(control)
    _require_snapping(snapping)
    if cursor.kind == CONTROL:
        return annotations, cursor  # the open item stays, with its unsaved points
    item = (PHOTO, cursor.number) if cursor.kind == DART else None
    if item not in control.items or control_for(annotations, item[1], PHOTO) is not None:
        item = _first_open_item(annotations, control)
    if item is None:
        return annotations, Cursor(CONTROL_DONE)
    return annotations, _open_control(annotations, plan, control, item, snapping)


def _hide_point(cursor):
    if cursor.kind in (CALIBRATION, RECALIBRATION):
        raise FlowConflict(NO_HIDDEN_SEGMENT)
    if cursor.kind != CONTROL:
        raise FlowError("Aucun point à cacher sur cet écran.")
    if cursor.selected is not None:
        index = cursor.selected - 1
    else:
        index = next((i for i, slot in enumerate(cursor.pending) if slot.point is None and slot.status is None), None)
        if index is None:
            raise FlowConflict(HIDE_REFUSED)
    slots = cursor.pending[:index] + (Slot(None, HIDDEN),) + cursor.pending[index + 1:]
    history = cursor.history + ((cursor.pending, cursor.selected),)
    return replace(cursor, pending=slots, selected=None, history=history)


def _save_control(annotations, plan, action, cursor, snapping, control):
    if (cursor.kind != CONTROL or action.number != cursor.number or action.source != cursor.source):
        raise FlowConflict(ALREADY_SAVED)  # a repeated save never writes the next item (U29)
    _require_control(control)
    _require_snapping(snapping)
    ready = ready_count(cursor.pending)
    if ready < CALIBRATION_POINTS:
        raise FlowConflict(f"Points prêts : {ready} / {CALIBRATION_POINTS}")
    if action.size is None:
        raise FlowError("Taille de la photo inconnue.")
    if control_for(annotations, cursor.number, cursor.source) is None and len(annotations.controls) >= MAX_CONTROLS:
        raise FlowConflict(f"Nombre maximal de contrôles atteint ({MAX_CONTROLS})")
    position = control.position_of(cursor.number) if cursor.source == VIDEO else None
    saved = Control(cursor.number, cursor.source, position, int(action.size[0]), int(action.size[1]),
                    tuple(slot.point for slot in cursor.pending),
                    tuple(None if slot.point is None else slot.status for slot in cursor.pending))
    updated = replace(annotations, controls=_with_control(annotations.controls, saved))
    item = _first_open_item(updated, control)
    if item is None:
        return updated, Cursor(CONTROL_DONE)
    return updated, _open_control(updated, plan, control, item, snapping)


def _with_control(controls, saved):
    others = tuple(c for c in controls if (c.dart, c.source) != (saved.dart, saved.source))
    return tuple(sorted(others + (saved,), key=lambda c: (c.dart, SOURCES.index(c.source))))


def _control_clear(annotations, plan, cursor, snapping, control):
    """U: the last slot change; with none, back to the previous item, whose control is removed and shown again as
    proposals (U28); on the first item, nothing."""
    if cursor.kind == CONTROL and cursor.history:
        pending, selected = cursor.history[-1]
        return annotations, replace(cursor, pending=pending, selected=selected, history=cursor.history[:-1])
    _require_control(control)
    _require_snapping(snapping)
    items = control.items
    if cursor.kind == CONTROL_DONE:
        previous = items[-1] if items else None
    else:
        index = items.index((cursor.source, cursor.number)) if (cursor.source, cursor.number) in items else 0
        previous = items[index - 1] if index > 0 else None
    if previous is None:
        return annotations, replace(cursor, selected=None) if cursor.kind == CONTROL else cursor
    saved = control_for(annotations, previous[1], previous[0])
    updated = annotations
    if saved is not None:
        updated = replace(annotations, controls=tuple(c for c in annotations.controls if c is not saved))
    points = None if saved is None else saved.points
    return updated, _open_control(updated, plan, control, previous, snapping, points)


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


def _clear(annotations, plan, cursor, snapping, control):
    if cursor.kind in (CONTROL, CONTROL_DONE):
        return _control_clear(annotations, plan, cursor, snapping, control)
    if cursor.kind in (CALIBRATION, RECALIBRATION):
        return _assist_clear(annotations, plan, cursor)
    if cursor.kind == DART:
        darts = tuple(d for d in annotations.darts if d.number != cursor.number)
        return replace(annotations, darts=darts), cursor
    return annotations, cursor


def _move(annotations, plan, cursor, forward):
    numbers = [n for n, _ in plan.darts]
    if cursor.kind == CONTROL:  # leaving drops the unsaved points; the file is unchanged (U16)
        return _dart_cursor(plan, cursor.number)
    if cursor.kind == CONTROL_DONE:
        return _first_open(annotations, plan)
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
        return Cursor(CALIBRATION, photo=plan.first_photo, pending=_saved_slots(annotations.calibrations[0]))
    return cursor


def apply(annotations, session, action, cursor=None, snapping=None, control=None) -> tuple:
    """Apply one action; return (annotations, cursor). Raise FlowError when the action is not possible.

    `snapping` has `available` and `snap(photo, start)`; without it the calibration and control screens refuse (CV16).
    `control` is the `ControlPlan` of the session, None when control mode is off."""
    if action.kind not in KINDS:
        raise FlowError("Action inconnue.")
    plan = plan_of(session)
    if plan.first_photo is None:
        raise FlowError("Aucune fléchette jugée dans cette session.")
    cursor = cursor if cursor is not None else resume(annotations, session)
    if action.kind == "clear":
        return _clear(annotations, plan, cursor, snapping, control)
    if action.kind in ("next", "previous"):
        return annotations, _move(annotations, plan, cursor, action.kind == "next")
    if action.kind == "goto":
        if not _calibrated(annotations):
            raise FlowError("Calibrez d'abord le cadran.")
        if action.number not in dict(plan.darts):
            raise FlowError("Fléchette inconnue.")
        return annotations, _dart_cursor(plan, action.number)
    if action.kind == "recalibrate":
        return _recalibrate(annotations, plan, cursor, snapping)
    if action.kind == "select":
        return annotations, _select(cursor, action)
    if action.kind == "control":
        return _control_open(annotations, plan, cursor, snapping, control)
    if action.kind == "hidden_point":
        return annotations, _hide_point(cursor)
    if action.kind == "save":
        if cursor.kind in (CONTROL, CONTROL_DONE):
            return _save_control(annotations, plan, action, cursor, snapping, control)
        return _save(annotations, plan, action, cursor, snapping)
    if cursor.kind in (CALIBRATION, RECALIBRATION, CONTROL):
        if action.kind == "click":
            return _assist_click(annotations, plan, action, cursor, snapping)
        # a segment needs 4 points (CV20); a control hides a point with `hidden_point`: no dart status here
        raise FlowConflict(CONTROL_NO_STATUS if cursor.kind == CONTROL else NO_HIDDEN_SEGMENT)
    return _dart_mark(annotations, plan, action, cursor)
