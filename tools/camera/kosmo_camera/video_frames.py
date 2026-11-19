"""Video frame of each sampled dart, about 1 s before its entry (CV27), and the manifest `frames.json`.

`python -m kosmo_camera.video_frames --captures DIR --annotations DIR --split FILE --out DIR [--lock FILE]` writes
`<out>/<session>/video-NNN.jpg` for the video darts of the control sample (CV26) and `<out>/frames.json`. Videos are
Kosmo's data (S13): a file above `VIDEO_MAX_BYTES` or needing more than `VIDEO_MAX_DECODED_FRAMES` decoded frames, and
any decode error, give `video_unreadable` for that dart; the run goes on and ends with exit 0. The output counts frames
and reasons only: no session name, no path.
"""
import argparse
import hashlib
import io
import math
import os
import stat
import sys
from dataclasses import dataclass
from fractions import Fraction
from pathlib import Path

import av
from PIL import Image

from kosmo_camera import annotations as ann, config, control, frames_file as ff, split as split_module, strict_json
from kosmo_camera.truth import TruthError, read_truth, session_folders

JPEG_QUALITY = 95
DEFAULT_LOCK = Path(__file__).resolve().parent.parent / "config" / "split-lock.json"
# Errors of PyAV and Pillow on a file that is not a good video; `FFmpegError` is a ValueError, listed for clarity.
_DECODE_ERRORS = (av.error.FFmpegError, OSError, ValueError, MemoryError, IndexError, TypeError)


class FrameError(Exception):
    """No frame for a dart; `reason` is one of `frames_file.REASONS`."""

    def __init__(self, reason):
        super().__init__(reason)
        self.reason = reason


@dataclass(frozen=True)
class VideoFrame:
    position_ms: int  # exact position in the video, floored to the millisecond
    pixels: object  # numpy array height x width x 3 (RGB), stored order, rotation metadata ignored (D8)


def _check_file(path):
    try:
        info = os.lstat(path)
    except OSError as error:
        raise FrameError(ff.VIDEO_MISSING) from error
    if not stat.S_ISREG(info.st_mode):
        raise FrameError(ff.VIDEO_MISSING)
    if info.st_size > config.VIDEO_MAX_BYTES:
        raise FrameError(ff.VIDEO_UNREADABLE)


def _position_ms(frame, stream):
    if frame.pts is None:
        raise ValueError("frame without a presentation time")
    return math.floor(Fraction(frame.pts) * Fraction(stream.time_base) * 1000)


def _last_frame_before(path, target_ms):
    """(frame, position_ms) of the last decoded frame at or before `target_ms`; FrameError for the CV27 reasons."""
    with av.open(str(path)) as container:
        stream = container.streams.video[0]
        rate = stream.average_rate
        period = 1000 / float(rate) if rate else 0.0
        found, found_ms = None, None
        for count, frame in enumerate(container.decode(stream), start=1):
            if count > config.VIDEO_MAX_DECODED_FRAMES:
                raise FrameError(ff.VIDEO_UNREADABLE)
            milliseconds = _position_ms(frame, stream)
            if milliseconds > target_ms:
                if found is None:
                    raise FrameError(ff.BEFORE_START)
                return found.to_ndarray(format="rgb24"), found_ms
            found, found_ms = frame, milliseconds
        if found is None:
            raise FrameError(ff.VIDEO_UNREADABLE)  # a video stream without any frame
        if target_ms >= found_ms + period:
            raise FrameError(ff.PAST_END)
        return found.to_ndarray(format="rgb24"), found_ms


def frame_at(video_path, target_ms) -> VideoFrame:
    """The last decoded frame whose time is at or before `target_ms` (CV27); raise FrameError with the reason when
    there is none: `video_missing`, `video_unreadable` (oversized, too long or any decode error), `before_start`,
    `past_end` (the target is a frame period or more after the last frame)."""
    _check_file(video_path)
    try:
        pixels, position = _last_frame_before(video_path, target_ms)
    except FrameError:
        raise
    except _DECODE_ERRORS as error:
        raise FrameError(ff.VIDEO_UNREADABLE) from error
    return VideoFrame(position, pixels)


def _orientation(width, height):
    return width >= height


def _previous_photo_ms(volley, dart):
    """Video position of the photo stamp of the nearest earlier dart of the volley that has one, else None."""
    previous = None
    for other in volley.darts:
        if other.number == dart.number:
            return previous
        if other.photo is not None and other.photo.video_position_ms is not None:
            previous = other.photo.video_position_ms
    return previous


def _jpeg(pixels):
    buffer = io.BytesIO()
    Image.fromarray(pixels).save(buffer, format="JPEG", quality=JPEG_QUALITY)
    return buffer.getvalue()


def _write_bytes(path, data):
    """Atomic write shared with the other camera files (`strict_json.replace_atomic`); an OS failure stays an OSError."""
    try:
        strict_json.replace_atomic(path, data)
    except strict_json.AtomicWriteError as error:
        raise OSError(str(error)) from error


def _one(dart, volley, folder, truth, before_ms):
    """(position_ms, width, height, jpeg bytes) for one dart; FrameError when it has no frame."""
    entry = dart.entry.video_position_ms
    if entry is None:
        raise FrameError(ff.NO_POSITION)
    target = entry - before_ms
    previous = _previous_photo_ms(volley, dart)
    if previous is not None and target < previous:
        raise FrameError(ff.TOO_CLOSE)  # the phone may still be moving from the previous tap
    result = frame_at(folder / volley.video, target)
    height, width = result.pixels.shape[:2]
    if _orientation(width, height) != _orientation(truth.photo_width, truth.photo_height):
        raise FrameError(ff.ORIENTATION_MISMATCH)
    try:
        return result.position_ms, width, height, _jpeg(result.pixels)
    except _DECODE_ERRORS as error:
        raise FrameError(ff.VIDEO_UNREADABLE) from error


def extract_session(name, truth, folder, dart_numbers, out_dir, before_ms=None) -> ff.SessionFrames:
    """Write the frames of `dart_numbers` into `<out_dir>/<name>/` and return what the manifest lists; the folder then
    holds only the listed files. Never raises for a bad video (reason `video_unreadable`)."""
    before_ms = config.VIDEO_BEFORE_ENTRY_MS if before_ms is None else before_ms
    index = {d.number: (d, v) for v in truth.volleys for d in v.darts}
    target_dir = Path(out_dir) / name
    frames, missing = [], []
    for number in sorted(dart_numbers):
        dart, volley = index[number]
        try:
            position, width, height, data = _one(dart, volley, Path(folder), truth, before_ms)
        except FrameError as error:
            missing.append(ff.Missing(number, error.reason))
            continue
        target_dir.mkdir(parents=True, exist_ok=True)
        file = ff.video_file_name(number)
        _write_bytes(target_dir / file, data)
        frames.append(ff.Frame(number, position, width, height, file, hashlib.sha256(data).hexdigest()))
    _remove_unlisted(target_dir, {f.file for f in frames})
    return ff.SessionFrames(name, tuple(frames), tuple(missing))


def _remove_unlisted(target_dir, listed):
    if not target_dir.is_dir():
        return
    for path in target_dir.iterdir():
        stale = path.name.startswith("video-") or path.name.endswith(".tmp")
        if path.is_file() and stale and path.name not in listed:  # a folder or a link target is never touched
            path.unlink()


def _annotations_of(folder, name):
    try:
        return ann.read(Path(folder) / f"{name}.json")
    except (ann.AnnotationError, OSError):
        return None


def _set_of(name, split):
    try:
        return split_module.set_of(name, 0, split)
    except split_module.SplitError:
        return None  # a session that is not in the split (ignored) has no control sample


def run(captures, annotations, split, out) -> ff.FramesFile:
    """Extract the video frames of every session of the split; sessions in session list order (CV26)."""
    sessions = []
    for name, folder in session_folders(captures).items():
        set_name = _set_of(name, split)
        if set_name is None:
            continue
        try:
            truth = read_truth(folder / "truth.json")
        except (TruthError, OSError):
            continue
        darts = control.sample(truth, _annotations_of(annotations, name), set_name).video_darts
        if darts:
            sessions.append(extract_session(name, truth, folder, darts, out))
    return ff.FramesFile(config.VIDEO_BEFORE_ENTRY_MS, tuple(sessions))


def summary(frames_file) -> str:
    """Counts only: frames written, then the missing ones per reason; no session name."""
    written = sum(len(s.frames) for s in frames_file.sessions)
    lines = [f"Images vidéo écrites : {written}"]
    absent = [m.reason for s in frames_file.sessions for m in s.missing]
    if absent:
        lines.append(f"Images vidéo absentes : {len(absent)}")
        lines.extend(f"  {reason} : {absent.count(reason)}" for reason in ff.REASONS if reason in absent)
    return "\n".join(lines)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(prog="python -m kosmo_camera.video_frames", description=__doc__.splitlines()[0])
    for name in ("captures", "annotations", "out"):
        parser.add_argument(f"--{name}", required=True, type=Path)
    parser.add_argument("--split", required=True, type=Path)
    parser.add_argument("--lock", type=Path, default=DEFAULT_LOCK, help="split lock (default: the committed one)")
    args = parser.parse_args(argv)
    for folder in (args.captures, args.annotations):
        if not folder.is_dir():
            print("not a folder", file=sys.stderr)
            return 2
    try:
        split = control.read_split(args.split, args.lock)
    except split_module.SplitError as error:
        print(f"split or lock invalid: {error}", file=sys.stderr)
        return 2
    try:
        args.out.mkdir(parents=True, exist_ok=True)
        result = run(args.captures, args.annotations, split, args.out)
        ff.write_atomic(args.out / "frames.json", result)
    except (OSError, ff.FramesFileError) as error:
        print(f"cannot write the frames: {error.strerror if isinstance(error, OSError) else error}", file=sys.stderr)
        return 2
    print(summary(result))
    return 0


if __name__ == "__main__":
    sys.exit(main())
