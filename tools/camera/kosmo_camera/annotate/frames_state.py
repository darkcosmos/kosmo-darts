"""The frame manifest input of the annotation tool (CV16, CV27): `--frames`, read afresh at every request, never
written. Stdlib only. An unreadable manifest gives one fixed warning and no frame; tip entry is unchanged.
"""
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

from kosmo_camera import frames_file

FRAMES_UNREADABLE = "Images vidéo illisibles : frames.json est invalide ou illisible (relancer l'extraction, K7.2)"


@dataclass(frozen=True)
class FramesSnapshot:
    frames_dir: Optional[Path] = None
    frames: dict = field(default_factory=dict)  # session name -> frames_file.SessionFrames
    warning: Optional[str] = None

    def listed(self, name) -> dict:
        """Dart number -> `frames_file.Frame` of the frames listed for session `name` (empty when none)."""
        session_frames = self.frames.get(name)
        return {} if session_frames is None else {f.dart: f for f in session_frames.frames}


class FrameSources:
    """Where the extracted frames live: the frames folder with its `frames.json`, optional."""

    def __init__(self, frames=None):
        self.frames = None if frames is None else Path(frames)

    def read(self) -> FramesSnapshot:
        if self.frames is None:
            return FramesSnapshot()
        try:
            manifest = frames_file.read(self.frames / "frames.json")
        except frames_file.FramesFileError:  # its text can hold a path: the warning is a fixed sentence
            return FramesSnapshot(self.frames, {}, FRAMES_UNREADABLE)
        return FramesSnapshot(self.frames, {s.session: s for s in manifest.sessions})
