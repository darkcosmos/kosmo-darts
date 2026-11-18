"""Control mode inputs of the annotation tool (CV16, CV26, CV27): the locked split (`--split`, `--lock`) and the frame
manifest (`--frames`), read afresh at every request like the drift file, never written. Stdlib only.

A split that does not match its lock, or an unreadable manifest, gives one warning and switches control (or the video
items) off; the rest of the tool is unchanged. `ControlSnapshot.info` is what one session needs: its role, its
`flow.ControlPlan`, the frames it can serve and the reasons its frames are missing (S13).
"""
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

from kosmo_camera import control, frames_file
from kosmo_camera.annotate import flow
from kosmo_camera.split import SplitError

SPLIT_REFUSED = "Contrôle : indisponible (split refusé)"
NOT_IN_SPLIT = "Contrôle : indisponible (session absente du split)"
FRAMES_UNREADABLE = "Images vidéo illisibles"
SPLIT_ERRORS = (  # (start of the SplitError message, French reason): the known refusals of `control.read_split`
    ("split does not match the lock", "il ne correspond plus au verrou (split-lock.json)"),
    ("cannot read the split or its lock", "le split ou son verrou est illisible"),
    ("split is too large", "le split est trop gros"),
    ("lock", "le verrou (split-lock.json) est invalide"),
)
SPLIT_GENERIC = "le split est invalide ou ne correspond pas au verrou (split-lock.json)"
DEFAULT_LOCK = Path(__file__).resolve().parents[2] / "config" / "split-lock.json"  # the committed lock


@dataclass(frozen=True)
class ControlInfo:
    """Control mode for one session: `plan` is None when it is off, and `reason` then says why (in French)."""
    plan: Optional[flow.ControlPlan] = None
    role: Optional[str] = None  # `dev 1`, `eval 2`... (CV26), never the session name
    reason: Optional[str] = None
    frames_present: bool = False  # the session is listed in the frame manifest
    frame_reasons: dict = field(default_factory=dict)  # reason of `frames_file.REASONS` -> number of darts
    frames: dict = field(default_factory=dict)  # video-NNN.jpg -> (path, (width, height)), the listed frames only


OFF = ControlInfo(reason=flow.CONTROL_OFF)


@dataclass(frozen=True)
class ControlSnapshot:
    configured: bool = False  # `--split` given
    warning: Optional[str] = None  # the split was refused (list warning)
    roles: dict = field(default_factory=dict)  # session name -> control.Role
    frames_dir: Optional[Path] = None
    frames: dict = field(default_factory=dict)  # session name -> frames_file.SessionFrames
    frames_warning: Optional[str] = None

    @property
    def warnings(self) -> list:
        return [text for text in (self.warning, self.frames_warning) if text]

    def info(self, name, truth, annotations) -> ControlInfo:
        if not self.configured:
            return OFF
        if self.warning is not None:
            return ControlInfo(reason=SPLIT_REFUSED)
        role = self.roles.get(name)
        if role is None:
            return ControlInfo(reason=NOT_IN_SPLIT)
        sample = control.sample(truth, annotations, role.set_name)
        session_frames = self.frames.get(name)
        listed = {} if session_frames is None else {f.dart: f for f in session_frames.frames}
        video = tuple((dart, listed[dart].file, listed[dart].position_ms) for dart in sample.video_darts
                      if dart in listed)
        files = {listed[dart].file: (self.frames_dir / name / listed[dart].file,
                                     (listed[dart].width, listed[dart].height)) for dart, _, _ in video}
        reasons = {} if session_frames is None else dict(Counter(m.reason for m in session_frames.missing))
        return ControlInfo(flow.ControlPlan(sample.photo_darts, video), role.label, None, session_frames is not None,
                           reasons, files)


def split_reason(error) -> str:
    """The French reason of a refused split: the known messages are mapped, any other one gets the generic text."""
    text = str(error)
    for start, reason in SPLIT_ERRORS:
        if text.startswith(start):
            return reason
    return SPLIT_GENERIC


class ControlSources:
    """Where the control inputs live: the split file with its lock, and the frames folder; each one optional."""

    def __init__(self, split=None, lock=None, frames=None):
        self.split = None if split is None else Path(split)
        self.lock = DEFAULT_LOCK if lock is None else Path(lock)
        self.frames = None if frames is None else Path(frames)

    def read(self) -> ControlSnapshot:
        warning, roles = None, {}
        if self.split is not None:
            try:
                roles = control.roles(control.read_split(self.split, self.lock))
            except SplitError as error:
                warning = f"Split refusé : {split_reason(error)}"
        frames, frames_warning = {}, None
        if self.frames is not None:
            try:
                manifest = frames_file.read(self.frames / "frames.json")
                frames = {s.session: s for s in manifest.sessions}
            except frames_file.FramesFileError as error:
                frames_warning = f"{FRAMES_UNREADABLE} : {error}"
        return ControlSnapshot(self.split is not None, warning, roles, self.frames, frames, frames_warning)
