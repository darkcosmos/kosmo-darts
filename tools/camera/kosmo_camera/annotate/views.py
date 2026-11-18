"""What the annotation tool shows (CV16): the loaded state of a session and the JSON views built from it, pure
functions with no I/O. `server.py` loads and writes; this module only describes. All messages are in French."""
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from kosmo_camera import annotations as annotation_file
from kosmo_camera.annotate import flow
from kosmo_camera.annotate.snapping import UNREADABLE

IGNORED_LABEL = "Ignorée : aucune fléchette jugée"
OK = "ok"
IGNORED = "ignored"
TRUTH_INVALID = "truth_invalid"
TOO_LARGE = "annotation_too_large"
REJECTED = "annotation_rejected"
STALE_LABEL = "(mesure périmée : relancer la mesure)"
NOT_MEASURED_LABEL = "Dérive non mesurée"
NOT_CALIBRATED_LABEL = "Non calibrée"
CALIBRATED_LABEL = "Calibrée"
SEGMENTS_LABEL = "Calibrée ({count} segments)"


@dataclass(frozen=True)
class DriftState:
    """The drift flag file as read for one request (CV16: read-only, never written by the tool)."""
    sessions: dict  # session name -> drift_file.SessionFlags
    warning: Optional[str] = None
    configured: bool = False


NO_DRIFT = DriftState({})


@dataclass(frozen=True)
class Loaded:
    name: str
    folder: Path
    state: str
    message: Optional[str] = None
    warning: Optional[str] = None
    truth: object = None
    annotations: object = None
    plan: object = None


def _count_label(count, singular, plural):
    return f"{count} {singular if count == 1 else plural}"


def _numbers_text(numbers):
    return ", ".join(str(n) for n in numbers)


def _flag_labels(flags):
    """List labels of one session (CV16): where to recalibrate, or the maximum drift, plus unmeasurable photos."""
    if flags.beyond:
        start = ("de la fléchette" if len(flags.suggested) == 1 else "des fléchettes")
        labels = [f"À recalibrer à partir {start} {_numbers_text(flags.suggested)}"]
    elif flags.max_drift_mm is None:
        labels = ["Dérive OK"]
    else:
        labels = [f"Dérive OK (max {flags.max_drift_mm:.1f} mm)".replace(".", ",")]
    if flags.unmeasurable:
        labels.append(_count_label(len(flags.unmeasurable), "photo non mesurable", "photos non mesurables"))
    return labels


def drift_view(loaded, drift):
    """(label, stale, flags) of a session: stale when the segments changed since the measure (U7)."""
    if not drift.configured or drift.warning is not None:
        return None, False, None
    flags = drift.sessions.get(loaded.name)
    if flags is None:
        return NOT_MEASURED_LABEL, False, None
    stale = (loaded.annotations is not None
             and flags.segments_sha256 != annotation_file.segments_sha256(loaded.annotations))
    label = " ; ".join(_flag_labels(flags))
    return (f"{label} {STALE_LABEL}" if stale else label), stale, flags


def _progress(loaded):
    if loaded.state != OK:
        return None
    numbers = {n for n, _ in loaded.plan.darts}
    return {"done": len({d.number for d in loaded.annotations.darts} & numbers), "total": len(numbers)}


def calibration_label(loaded):
    """What the page shows of the calibration: none, one segment, or the number of segments (CV20); None if unread."""
    if loaded.state != OK:
        return None
    count = len(loaded.annotations.calibrations)
    if count == 0:
        return NOT_CALIBRATED_LABEL
    return SEGMENTS_LABEL.format(count=count) if count > 1 else CALIBRATED_LABEL


def control_view(loaded, info) -> dict:
    """Control mode of an open session (CV26): counts per source, the role, the frame reasons counted (S13)."""
    if info.plan is None:
        return {"available": False, "reason": info.reason, "role": info.role}
    progress = flow.control_progress(loaded.annotations, info.plan)
    return {"available": True, "reason": None, "role": info.role, "photo": progress["photo"],
            "video": progress["video"], "framesPresent": info.frames_present, "frameMissing": dict(info.frame_reasons)}


def summary(loaded, drift=NO_DRIFT, control_info=None) -> dict:
    label, stale, _ = drift_view(loaded, drift)
    return {"name": loaded.name, "state": loaded.state, "message": loaded.message, "warning": loaded.warning,
            "label": IGNORED_LABEL if loaded.state == IGNORED else None, "progress": _progress(loaded),
            "calibrated": bool(loaded.annotations.calibrations) if loaded.state == OK else None,
            "calibrationLabel": calibration_label(loaded),
            "driftLabel": label, "driftStale": stale,
            "control": control_view(loaded, control_info) if loaded.state == OK and control_info is not None else None}


def view(loaded, cursor, drift, snap_available, control_info=None) -> dict:
    """The full session view: the summary, the darts with their drift flags, the segments and the open item."""
    result = summary(loaded, drift, control_info)
    result["readOnly"] = loaded.state != OK
    if loaded.state != OK:  # read-only states show their message, with no photo flow
        return result
    by_number = {d.number: d for d in loaded.annotations.darts}
    result["photoSize"] = [loaded.truth.photo_width, loaded.truth.photo_height]
    flags = drift_view(loaded, drift)[2]
    by_dart = {d.number: (_value_label(d), v.index) for v in loaded.truth.volleys for d in v.darts}
    result["darts"] = [{"number": number, "photo": photo, "value": by_dart[number][0], "volley": by_dart[number][1],
                        "status": by_number[number].status.value if number in by_number else None,
                        "tip": list(by_number[number].tip) if number in by_number and by_number[number].tip
                        else None, "drift": _dart_drift(flags, number)} for number, photo in loaded.plan.darts]
    result["skipped"] = list(loaded.plan.skipped)
    result["calibrationPhoto"] = loaded.plan.first_photo
    segments = loaded.annotations.calibrations  # the page shows segment 0, from dart 1 (CV20)
    calibration = segments[0] if segments else None
    result["calibration"] = None if calibration is None else {
        "photo": calibration.photo, "width": calibration.width, "height": calibration.height,
        "points": [list(p) for p in calibration.points]}
    result["segments"] = [{"fromDart": c.from_dart, "photo": c.photo, "points": [list(p) for p in c.points]}
                          for c in segments]
    result["snapAvailable"] = snap_available
    result["cursor"] = {"kind": cursor.kind, "number": cursor.number, "photo": cursor.photo,
                        "pending": [list(slot.point) for slot in cursor.pending if slot.point is not None],
                        "slots": [{"point": None if slot.point is None else list(slot.point), "status": slot.status,
                                   "reason": slot.reason} for slot in cursor.pending],
                        "source": cursor.source, "selected": cursor.selected, "ready": flow.ready_count(cursor.pending),
                        "nextSlot": flow.next_slot(cursor.pending, cursor.selected),
                        "diagram": flow.shows_diagram(cursor),
                        "unreadable": any(slot.reason == UNREADABLE for slot in cursor.pending)}
    return result


def _dart_drift(flags, number):
    """"suggested", "beyond", "unmeasurable" or None for one dart (the strongest flag wins)."""
    if flags is None:
        return None
    for name, numbers in (("suggested", flags.suggested), ("beyond", flags.beyond),
                          ("unmeasurable", flags.unmeasurable)):
        if number in numbers:
            return name
    return None


def _value_label(dart) -> str:
    """The true value as Kosmo reads it on the board: "T20", "D16", "20", "25", "50" or "0" (miss)."""
    prefix = {"DOUBLE": "D", "TRIPLE": "T"}.get(dart.multiplier, "")
    return f"{prefix}{dart.segment}"
