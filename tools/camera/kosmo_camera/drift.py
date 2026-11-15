"""Tripod drift of the judged photos against the reference photo of their calibration segment (CV18, CV19).

numpy for the maths, Pillow only to decode a JPEG to luma. The clicked calibration of a segment (CV20) is the reference:
each judged photo gets one sub-pixel shift per calibration point, a drift in px and in mm, and, when the drift is at
most `DRIFT_MAX_MM`, a compensated reference calibration (CV19). Reads every session without the split lock (CV18).

Shift sign: content position in the photo minus content position in the reference. Luma is decoded in stored pixel
order, EXIF orientation ignored (D8). The segment of a dart is `annotations.segment_for` (A15), never recomputed here.
"""
import argparse
import math
import statistics
import sys
import warnings
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

import numpy
from PIL import Image

from kosmo_camera import annotations as ann
from kosmo_camera import config, drift_file, geometry

OK = "ok"
BEYOND = "beyond"
UNMEASURABLE = "unmeasurable"
UNCALIBRATED = "uncalibrated"
SIZE_MISMATCH = "size_mismatch"
BORDER = "border"
LOW_PEAK = "low_peak"
UNREADABLE = "unreadable"
REASONS = (UNCALIBRATED, SIZE_MISMATCH, BORDER, LOW_PEAK, UNREADABLE)

_BOX_MARGIN = 0.15  # CV18 (1): the box of the 4 points is enlarged by 15 %
_MIN_BOX_PX = 16  # a coarse box clipped to fewer pixels than this cannot be correlated
_EPSILON = 1e-12
# Gaussian low-pass on the whitened spectrum, in cycles per pixel: it widens the correlation peak so that the 3-point
# parabola is unbiased (a bare whitened peak is a sinc and pulls the fit towards the integer pixel by about 0.1 px).
_SPECTRAL_SIGMA = 0.08
_NO_DRIFT_SHIFTS = ((0.0, 0.0),) * ann.CALIBRATION_POINTS
_NOT_CLEAN = ("Dérive du trépied au-delà du seuil sur {count} photo(s) : lancer « drift », recalibrer depuis la "
              "fléchette indiquée dans l'outil d'annotation, puis relancer « drift » (ou --accept-drift pour passer outre).")


class DriftError(RuntimeError):
    """A photo is beyond the drift threshold and the run did not accept it (CV19)."""


@dataclass(frozen=True)
class DriftParams:
    """CV18 and CV19 constants, read from `config.py` only (A16)."""
    max_mm: float = config.DRIFT_MAX_MM
    candidates_mm: tuple = tuple(config.DRIFT_CANDIDATES_MM)
    coarse_px: int = config.DRIFT_COARSE_PX
    patch_fraction: float = config.DRIFT_PATCH_FRACTION
    min_peak: float = config.DRIFT_MIN_PEAK


@dataclass(frozen=True)
class PhotoDrift:
    status: str  # OK, BEYOND or UNMEASURABLE
    reason: Optional[str]  # one of REASONS exactly when the status is UNMEASURABLE
    shifts_px: Optional[tuple]  # 4 (dx, dy), one per calibration point, in CV2 order
    drift_px: Optional[float]
    drift_mm: Optional[float]
    reference_points: Optional[tuple]  # CV19: segment points plus shifts, only when the status is OK


@dataclass(frozen=True)
class SessionDrift:
    session: str
    segments_sha256: str
    segments: int
    photos: dict  # dart number -> PhotoDrift, judged darts only (CV5)


def _unmeasurable(reason):
    return PhotoDrift(UNMEASURABLE, reason, None, None, None, None)


# ---- phase correlation --------------------------------------------------------------------------------------------

def _prepare(image):
    """Zero mean, Hann window: the input of the correlation."""
    data = numpy.asarray(image, dtype=numpy.float64)
    data = data - data.mean()
    rows, columns = data.shape
    return data * numpy.outer(numpy.hanning(rows), numpy.hanning(columns))


def _signed(index, size):
    return index - size if index > size // 2 else index


def _parabola(left, centre, right):
    """Offset of the vertex of the parabola through three equally spaced samples, from the centre one."""
    denominator = left - 2.0 * centre + right
    if abs(denominator) < _EPSILON:
        return 0.0
    return max(-0.5, min(0.5, 0.5 * (left - right) / denominator))


def phase_shift(a, b):
    """(dx, dy, peak): position of the content of `b` minus its position in `a`, in pixels (CV18).

    Zero mean, Hann window, normalised cross-power spectrum (`numpy.fft`) with a Gaussian low-pass weight, 3-point parabolic sub-pixel fit per axis.
    `peak` is the height of the correlation peak: about 1 for a clean shift, near 0 when the images share no content.
    """
    spectrum_a = numpy.fft.fft2(_prepare(a))
    spectrum_b = numpy.fft.fft2(_prepare(b))
    cross = spectrum_b * numpy.conj(spectrum_a)
    rows, columns = cross.shape
    frequencies = numpy.fft.fftfreq(rows)[:, None] ** 2 + numpy.fft.fftfreq(columns)[None, :] ** 2
    weights = numpy.exp(-frequencies / (2.0 * _SPECTRAL_SIGMA ** 2))
    correlation = numpy.fft.ifft2(cross / (numpy.abs(cross) + _EPSILON) * weights).real / weights.mean()
    row, column = numpy.unravel_index(int(numpy.argmax(correlation)), correlation.shape)
    peak = float(correlation[row, column])
    dx = _signed(int(column), columns) + _parabola(correlation[row, (column - 1) % columns], peak,
                                                   correlation[row, (column + 1) % columns])
    dy = _signed(int(row), rows) + _parabola(correlation[(row - 1) % rows, column], peak,
                                             correlation[(row + 1) % rows, column])
    return float(dx), float(dy), peak


# ---- one photo ----------------------------------------------------------------------------------------------------

def _coarse_box(points, width, height):
    """The box of the 4 points enlarged by 15 %, clipped to the image: (x0, y0, x1, y1) integers, or None."""
    low, high = points.min(axis=0), points.max(axis=0)
    margin = (high - low) * _BOX_MARGIN
    x0, y0 = (max(0, math.floor(v)) for v in low - margin)
    x1, y1 = min(width, math.ceil(high[0] + margin[0])), min(height, math.ceil(high[1] + margin[1]))
    if x1 - x0 < _MIN_BOX_PX or y1 - y0 < _MIN_BOX_PX:
        return None
    return x0, y0, x1, y1


def _block_mean(crop, factor):
    rows, columns = (side // factor * factor for side in crop.shape)
    trimmed = crop[:rows, :columns].astype(numpy.float64)
    return trimmed.reshape(rows // factor, factor, columns // factor, factor).mean(axis=(1, 3))


def _coarse_shift(reference, photo, points, params):
    """CV18 (1): shift `t0` of the board box, in full-resolution pixels; None when the box is unusable."""
    height, width = reference.shape
    box = _coarse_box(points, width, height)
    if box is None:
        return None
    x0, y0, x1, y1 = box
    factor = max(1, math.ceil(max(x1 - x0, y1 - y0) / params.coarse_px))
    dx, dy, peak = phase_shift(_block_mean(reference[y0:y1, x0:x1], factor), _block_mean(photo[y0:y1, x0:x1], factor))
    return (dx * factor, dy * factor, peak)


def _patch_window(centre, side, width, height):
    """Slices of the square patch of `side` px centred on the nearest pixel of `centre`, or None past the border."""
    x, y = round(float(centre[0])) - side // 2, round(float(centre[1])) - side // 2
    if x < 0 or y < 0 or x + side > width or y + side > height:
        return None
    return slice(y, y + side), slice(x, x + side)


def _error_mm(shifted, points):
    """CV18 (3): the CV9 error with the shifted points as truth and the segment homography as the prediction."""
    h_true = numpy.linalg.inv(geometry.board_from_image(shifted))
    return geometry.calibration_error_mm(h_true, geometry.board_from_image(points))


def measure_photo(ref_luma, photo_luma, segment, params=DriftParams()) -> PhotoDrift:
    """Drift of one photo against the reference photo of its segment (CV18), compensation included (CV19).

    Both arguments are 2D luma arrays in stored pixel order; the segment gives the clicked points and the size."""
    size = (segment.height, segment.width)
    if ref_luma.shape != size or photo_luma.shape != size:
        return _unmeasurable(SIZE_MISMATCH)
    points = numpy.array(segment.points, dtype=float)
    try:
        geometry.board_from_image(points)
    except geometry.DegenerateError:
        return _unmeasurable(UNCALIBRATED)
    coarse = _coarse_shift(ref_luma, photo_luma, points, params)
    if coarse is None:
        return _unmeasurable(BORDER)
    side = max(2, round(params.patch_fraction * max(segment.width, segment.height)))
    windows = []
    for point in points:
        reference = _patch_window(point, side, segment.width, segment.height)
        photo = _patch_window(point + coarse[:2], side, segment.width, segment.height)
        if reference is None or photo is None:
            return _unmeasurable(BORDER)
        windows.append((reference, photo))
    if coarse[2] < params.min_peak:
        return _unmeasurable(LOW_PEAK)
    shifts = []
    for point, (reference, photo) in zip(points, windows):
        dx, dy, peak = phase_shift(ref_luma[reference], photo_luma[photo])
        if peak < params.min_peak:
            return _unmeasurable(LOW_PEAK)
        offset = numpy.array([photo[1].start - reference[1].start, photo[0].start - reference[0].start])
        shifts.append((float(offset[0] + dx), float(offset[1] + dy)))
    return _from_shifts(points, tuple(shifts), params)


def _from_shifts(points, shifts, params):
    shifted = points + numpy.array(shifts)
    try:
        drift_mm = _error_mm(shifted, points)
    except (geometry.DegenerateError, numpy.linalg.LinAlgError):
        return _unmeasurable(UNCALIBRATED)
    drift_px = max(math.hypot(dx, dy) for dx, dy in shifts)
    if drift_mm > params.max_mm:
        return PhotoDrift(BEYOND, None, shifts, drift_px, drift_mm, None)
    reference_points = tuple((float(x), float(y)) for x, y in shifted)
    return PhotoDrift(OK, None, shifts, drift_px, drift_mm, reference_points)


# ---- one session --------------------------------------------------------------------------------------------------

def _decode_luma(path):
    """(luma array, None), or (None, UNREADABLE): a bad, truncated or oversized JPEG is a status, never an error (S8).

    `Image.MAX_IMAGE_PIXELS` stays at its default: Pillow raises above twice the limit and warns above the limit; both
    are refused here."""
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            with Image.open(path) as image:
                return numpy.asarray(image.convert("L"), dtype=numpy.float32), None
    except (OSError, ValueError, SyntaxError, Image.DecompressionBombError, Image.DecompressionBombWarning):
        return None, UNREADABLE


def _reference_drift(segment):
    points = tuple((float(x), float(y)) for x, y in segment.points)
    return PhotoDrift(OK, None, _NO_DRIFT_SHIFTS, 0.0, 0.0, points)


def measure_session(session_dir, annotations, params=DriftParams()) -> SessionDrift:
    """Drift of every judged dart (CV5) of a session; each segment's reference photo is decoded once (CV18)."""
    folder = Path(session_dir)
    references = {}  # segment index -> (luma, reason)
    photos = {}
    for dart in annotations.darts:
        found = ann.segment_for(annotations, dart.number)
        if found is None:
            photos[dart.number] = _unmeasurable(UNCALIBRATED)
            continue
        index, segment = found
        name = ann.photo_name(dart.number)
        if name == segment.photo:
            photos[dart.number] = _reference_drift(segment)
            continue
        if index not in references:
            references[index] = _decode_luma(folder / segment.photo)
        reference, reason = references[index]
        photo, photo_reason = _decode_luma(folder / name) if reference is not None else (None, reason)
        if photo is None:
            photos[dart.number] = _unmeasurable(photo_reason or UNREADABLE)
        else:
            photos[dart.number] = measure_photo(reference, photo, segment, params)
    return SessionDrift(annotations.session, ann.segments_sha256(annotations), len(annotations.calibrations), photos)


def _numbers(session_drift, status):
    return tuple(number for number, photo in sorted(session_drift.photos.items()) if photo.status == status)


def suggested_starts(session_drift) -> tuple:
    """First dart of each run of consecutive beyond darts, in number order; an unmeasurable dart neither starts nor
    splits a run, a dart under the threshold ends it."""
    starts, inside = [], False
    for number, photo in sorted(session_drift.photos.items()):
        if photo.status == UNMEASURABLE:
            continue
        if photo.status == BEYOND and not inside:
            starts.append(number)
        inside = photo.status == BEYOND
    return tuple(starts)


def count_beyond(drifts) -> int:
    """Number of beyond photos (CV19) over all sessions."""
    return sum(len(_numbers(session_drift, BEYOND)) for session_drift in drifts)


def require_clean(drifts, accept_drift) -> int:
    """CV19 guard of `dataset` and `measure`: the number of beyond photos, or DriftError when there are some and the
    run does not accept them."""
    count = count_beyond(drifts)
    if count and not accept_drift:
        raise DriftError(_NOT_CLEAN.format(count=count))
    return count


# ---- outputs ------------------------------------------------------------------------------------------------------

def _measured(session_drift):
    return [photo for photo in session_drift.photos.values() if photo.status != UNMEASURABLE]


def _flags(session_drift, label, params):
    measured = _measured(session_drift)
    return drift_file.SessionFlags(
        session_drift.session, label, session_drift.segments_sha256,
        max((photo.drift_mm for photo in measured), default=None),
        _numbers(session_drift, BEYOND), suggested_starts(session_drift), _numbers(session_drift, UNMEASURABLE))


def _labels(drifts):
    ordered = sorted(drifts, key=lambda session_drift: session_drift.session)
    return [(f"S{index:02d}", session_drift) for index, session_drift in enumerate(ordered, start=1)]


def _reasons(session_drift):
    counts = {}
    for photo in session_drift.photos.values():
        if photo.reason is not None:
            counts[photo.reason] = counts.get(photo.reason, 0) + 1
    return ", ".join(f"{reason} {counts[reason]}" for reason in REASONS if reason in counts) or "0"


def _stat(values, function):
    return f"{function(values):.2f}" if values else "-"


def _row(label, session_drift):
    measured = _measured(session_drift)
    px, mm = [photo.drift_px for photo in measured], [photo.drift_mm for photo in measured]
    beyond = _numbers(session_drift, BEYOND)
    starts = ", ".join(str(n) for n in suggested_starts(session_drift)) or "-"
    return (f"| {label} | {len(session_drift.photos)} | {session_drift.segments} | {len(measured)} | "
            f"{_reasons(session_drift)} | {_stat(px, max)} / {_stat(mm, max)} | "
            f"{_stat(px, statistics.median)} / {_stat(mm, statistics.median)} | {len(beyond)} | {starts} |")


def _candidates(labelled, params):
    lines = ["| Seuil (mm) | Sessions au-delà | Fléchettes au-delà |", "|---|---|---|"]
    for candidate in params.candidates_mm:
        sessions = darts = 0
        for _, session_drift in labelled:
            over = sum(1 for photo in _measured(session_drift) if photo.drift_mm > candidate)
            sessions += 1 if over else 0
            darts += over
        lines.append(f"| {candidate:g} | {sessions} | {darts} |")
    return lines


def build_report(drifts, params=DriftParams()) -> str:
    """The numbers-only report (CV15): sessions are labelled S01, S02... in session-name order."""
    labelled = _labels(drifts)
    beyond = sum(len(_numbers(session_drift, BEYOND)) for _, session_drift in labelled)
    lines = ["# Dérive du trépied", "",
             f"Seuil : {params.max_mm:g} mm, la moitié de l'erreur de calibration admise ({config.CAL_MAX_ERROR_MM:g} mm) "
             "(CV19) : au-delà, recalibrer depuis la fléchette indiquée ; en dessous, la dérive est compensée.",
             f"Paramètres : recherche grossière {params.coarse_px} px, vignette {params.patch_fraction:g} du grand "
             f"côté, pic minimal {params.min_peak:g}.", "",
             "| Session | Photos jugées | Segments | Mesurées | Non mesurables | Dérive max (px / mm) | "
             "Dérive médiane (px / mm) | Fléchettes au-delà | Départs suggérés |",
             "|---|---|---|---|---|---|---|---|---|"]
    lines += [_row(label, session_drift) for label, session_drift in labelled]
    judged = sum(len(session_drift.photos) for _, session_drift in labelled)
    measured = sum(len(_measured(session_drift)) for _, session_drift in labelled)
    lines += ["", f"Total : {judged} photos jugées, {measured} mesurées, {beyond} au-delà du seuil.", "",
              "## Seuils candidats", "", "Pour information : combien de sessions et de fléchettes dépasseraient chaque seuil.",
              ""] + _candidates(labelled, params)
    lines += ["", f"Tout sous le seuil : {'oui' if beyond == 0 else 'non'}", ""]
    return "\n".join(lines)


def write_outputs(drifts, runs_dir, params=DriftParams()) -> Path:
    """Write `<runs>/drift/drift.json` and `<runs>/drift/drift-report.md`, each replaced atomically; returns the report."""
    folder = Path(runs_dir) / "drift"
    folder.mkdir(parents=True, exist_ok=True)
    flags = tuple(_flags(session_drift, label, params) for label, session_drift in _labels(drifts))
    drift_file.write_atomic(folder / "drift.json", drift_file.DriftFile(params.max_mm, flags))
    report = folder / "drift-report.md"
    drift_file.replace_atomic(report, build_report(drifts, params).encode("utf-8"))
    return report


# ---- command ------------------------------------------------------------------------------------------------------

def _sessions(captures, annotations_dir):
    drifts = []
    for path in sorted(annotations_dir.glob("*.json")):
        try:
            annotations = ann.read(path)
        except ann.AnnotationError as error:
            raise ann.AnnotationError(f"{path}: {error}") from error
        if annotations.session != path.stem:
            raise ann.AnnotationError(f"{path}: the session name differs from the file name")
        drifts.append(measure_session(captures / annotations.session, annotations))
    return drifts


def main(argv=None) -> int:
    """`python -m kosmo_camera.drift --captures DIR --annotations DIR --runs DIR` (K5.2): exit 0 whatever the result,
    2 on bad input."""
    parser = argparse.ArgumentParser(prog="python -m kosmo_camera.drift", description=__doc__.splitlines()[0])
    for name in ("captures", "annotations", "runs"):
        parser.add_argument(f"--{name}", required=True, type=Path)
    args = parser.parse_args(argv)
    for folder in (args.captures, args.annotations, args.runs):
        if not folder.is_dir():
            print(f"not a folder: {folder}", file=sys.stderr)
            return 2
    try:
        drifts = _sessions(args.captures, args.annotations)
        report = write_outputs(drifts, args.runs)
    except (ann.AnnotationError, drift_file.DriftFileError, OSError) as error:
        print(str(error), file=sys.stderr)
        return 2
    print(report)
    print(f"Tout sous le seuil : {'oui' if count_beyond(drifts) == 0 else 'non'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
