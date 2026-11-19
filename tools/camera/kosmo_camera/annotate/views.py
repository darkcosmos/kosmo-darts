"""What the annotation tool shows (CV16): the loaded state of a session and the JSON views built from it, pure
functions with no I/O. `server.py` loads and writes; this module only describes. All messages are in French."""
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from kosmo_camera.annotations import PHOTO, VIDEO, control_for

IGNORED_LABEL = "Ignorée : aucune fléchette jugée"
OK = "ok"
IGNORED = "ignored"
TRUTH_INVALID = "truth_invalid"
TOO_LARGE = "annotation_too_large"
REJECTED = "annotation_rejected"
NOT_CALIBRATED_LABEL = "Non calibrée"
CALIBRATED_LABEL = "Calibrée"
SEGMENTS_LABEL = "Calibrée ({count} segments)"
PROGRESS_LABEL = "{done} / {total} pointes saisies"


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


def progress_label(loaded):
    """« 12 / 24 pointes saisies »: judged darts with an annotation over judged darts (T5); None if unread."""
    if loaded.state != OK:
        return None
    numbers = {n for n, _ in loaded.plan.darts}
    done = len({d.number for d in loaded.annotations.darts} & numbers)
    return PROGRESS_LABEL.format(done=done, total=len(numbers))


def calibration_label(loaded):
    """What the page shows of the calibration: none, one segment, or the number of segments (CV20); None if unread."""
    if loaded.state != OK:
        return None
    count = len(loaded.annotations.calibrations)
    if count == 0:
        return NOT_CALIBRATED_LABEL
    return SEGMENTS_LABEL.format(count=count) if count > 1 else CALIBRATED_LABEL


def summary(loaded) -> dict:
    return {"name": loaded.name, "message": loaded.message, "warning": loaded.warning,
            "label": IGNORED_LABEL if loaded.state == IGNORED else None, "progressLabel": progress_label(loaded),
            "calibrationLabel": calibration_label(loaded)}


def _points(points):
    """A point list for the page; a hidden control point stays null."""
    return [None if p is None else list(p) for p in points]


def _control_points(annotations, number, source):
    found = control_for(annotations, number, source)
    return None if found is None else _points(found.points)


def view(loaded, cursor, frames=None) -> dict:
    """The full session view: the summary, the darts with their tip, photo control and listed frame, the segments
    (shown read-only, CV20, CV25) and the cursor. `frames` maps a dart number to its `frames_file.Frame` (CV27)."""
    result = summary(loaded)
    result["readOnly"] = loaded.state != OK
    if loaded.state != OK:  # read-only states show their message, with no photo flow
        return result
    frames = {} if frames is None else frames
    annotations = loaded.annotations
    by_number = {d.number: d for d in annotations.darts}
    result["photoSize"] = [loaded.truth.photo_width, loaded.truth.photo_height]
    result["skipped"] = list(loaded.plan.skipped)
    result["segments"] = [{"fromDart": c.from_dart, "photo": c.photo, "points": _points(c.points)}
                          for c in annotations.calibrations]
    result["cursor"] = {"kind": cursor.kind, "number": cursor.number, "photo": cursor.photo}
    by_dart = {d.number: (_value_label(d), v.index) for v in loaded.truth.volleys for d in v.darts}
    result["darts"] = [{"number": number, "photo": photo, "value": by_dart[number][0], "volley": by_dart[number][1],
                        "status": by_number[number].status.value if number in by_number else None,
                        "tip": list(by_number[number].tip) if number in by_number and by_number[number].tip
                        else None,
                        "control": _control_points(annotations, number, PHOTO),
                        "frame": None if number not in frames else {
                            "file": frames[number].file, "control": _control_points(annotations, number, VIDEO)}}
                       for number, photo in loaded.plan.darts]
    return result


def _value_label(dart) -> str:
    """The true value as Kosmo reads it on the board: "T20", "D16", "20", "25", "50" or "0" (miss)."""
    prefix = {"DOUBLE": "D", "TRIPLE": "T"}.get(dart.multiplier, "")
    return f"{prefix}{dart.segment}"
