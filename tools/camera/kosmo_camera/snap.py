"""Assisted click (CV31; Kosmo, Q10, D39; design D43, D45): the sub-pixel wire crossing near a rough click.

numpy only, on a luma array (decode it with `luma.decode_luma`); pure functions, no file or network access. Points are
continuous image coordinates in stored pixel order (D8), the convention of the annotation tool: pixel (row i, column j)
covers [j, j + 1) x [i, i + 1), so its centre is (j + 0.5, i + 0.5).

One iteration, around the current estimate (root plan "Approach, Assisted click"):
1. Window of half side `SNAP_WINDOW_PX`; scale-normalised Gaussian derivatives at each of `SNAP_SIGMAS_PX`, the scale
   with the strongest ridge response is kept for the window.
2. Detection: ridge points (Hessian, sub-pixel centre line) and step-edge points (gradient maximum, sub-pixel), each
   above `SNAP_MIN_CONTRAST` times the response of the window noise (MAD of the pixel differences). Line 1 = the
   dominant ridge orientation (histogram), fitted by weighted total least squares plus a quadratic term (the ring is a
   conic in the image), inliers only; line 2 = the dominant ridge, else step edge, at least `SNAP_MIN_ANGLE_DEG` away
   from line 1 and outside its band. Either line can be the radial wire or the ring (wire or bare colour edge).
3. Refinement (T15m): each line's position is fitted to the pixels of its band, outside the other line's band, with a
   blurred profile model (left level, wire level and width, right level, blur, levels per side of the other line,
   plus a background plane). Hessian and gradient centres are pulled up to 0.25 px towards the brighter side when the
   two sides of a wire differ (red and green sectors, or double ring and black surround); the model is not.
4. Estimate = crossing of the two fitted curves; repeat until the move is below `SNAP_CONVERGE_PX`, at most
   `SNAP_MAX_ITER` times. Every iteration works on the same fixed window size, whatever the image size.

"No clear crossing" gives status `unsnapped`, the point stays at the start, with one reason: `support` (a line with
fewer than `SNAP_MIN_SUPPORT_PX` detected points), `contrast` (no detected point, or a fitted wire or edge contrast
below `SNAP_MIN_CONTRAST` times the noise), `residual` (detected points farther than `SNAP_MAX_RESIDUAL_PX` RMS from
their fitted curve, or a profile fit that fails), `angle` (fitted lines closer than `SNAP_MIN_ANGLE_DEG`),
`no_convergence`, `too_far` (more than `SNAP_RADIUS_PX` from the start), `border` (window or result outside the image), `ambiguous`
(two or more distinct crossings of the lattice within `SNAP_RADIUS_PX` of the start: the click does not say which one).

Diagnosis (CV32, T17a): `snap_detail` runs the same snap and also returns `stage`, the exact check that stopped it (one
of STAGES, None when snapped), and numeric descriptors of what the checks saw (`SnapDescriptors`). `snap_point` is
`snap_detail(...).result`: the stages and descriptors never change a result.
"""
import math
from dataclasses import dataclass
from typing import Optional

import numpy

from kosmo_camera import config

SNAPPED = "snapped"
UNSNAPPED = "unsnapped"
SUPPORT = "support"
CONTRAST = "contrast"
RESIDUAL = "residual"
ANGLE = "angle"
NO_CONVERGENCE = "no_convergence"
TOO_FAR = "too_far"
BORDER = "border"
AMBIGUOUS = "ambiguous"
REASONS = (SUPPORT, CONTRAST, RESIDUAL, ANGLE, NO_CONVERGENCE, TOO_FAR, BORDER, AMBIGUOUS)

# Stages (CV32): the check that stopped a snap, finer than its reason (a reason can come from several checks).
COARSE_NO_RIDGE = "coarse_no_ridge"  # coarse region: no detected point, or no ridge line that is a wire
COARSE_NO_PAIR = "coarse_no_pair"  # coarse region: no second line at the minimum angle from a wire
COARSE_NOT_REACHING = "coarse_not_reaching"  # coarse region: a pair crosses in range but a line stops short of it
COARSE_TOO_FAR = "coarse_too_far"  # coarse region: every crossing lies beyond the radius (plus the coarse slack)
FINE_SUPPORT = "fine_support"  # fine window: a line with too few points, or too few pixels for its profile
FINE_ANGLE = "fine_angle"  # fine window: the fitted lines closer than the minimum angle
FINE_CONTRAST = "fine_contrast"  # fine window: a fitted wire or edge contrast below the floor
FINE_BLUR = "fine_blur"  # fine window: a fitted wire or edge blurrier than `_SHARP_BLUR_PX`
STANDS_OUT = "stands_out"  # fine window: a line shows on neither flank of the crossing
REFINE_SHIFT = "refine_shift"  # fine window: the profile crossing moved more than `_REFINE_SHIFT_PX` from the detected
FIT_FAILURE = "fit_failure"  # a profile fit that is not finite, or a singular system
STAGE_TOO_FAR = "too_far"  # the refined crossing lies beyond the radius from the start
STAGE_BORDER = "border"  # window or result outside the image, or a non-finite start
STAGE_AMBIGUOUS = "ambiguous"  # two or more distinct crossings within the radius
STAGE_NO_CONVERGENCE = "no_convergence"  # still moving after `max_iter` windows
STAGES = (COARSE_NO_RIDGE, COARSE_NO_PAIR, COARSE_NOT_REACHING, COARSE_TOO_FAR, FINE_SUPPORT, FINE_ANGLE, FINE_CONTRAST,
          FINE_BLUR, STANDS_OUT, REFINE_SHIFT, FIT_FAILURE, STAGE_TOO_FAR, STAGE_BORDER, STAGE_AMBIGUOUS,
          STAGE_NO_CONVERGENCE)

_NOISE_FLOOR = 0.5  # grey levels: 8-bit quantisation and JPEG keep at least this much noise
_MAD_TO_SIGMA = 1.4826
_HALF_NORMAL_Q25 = 0.3186  # first quartiles, in noise sigmas, of |Gaussian| and of a Gaussian gradient magnitude:
_RAYLEIGH_Q25 = 0.7585  # the background quarter of a window that is mostly wires and edges
_HOUGH_STEP_DEG = 2.0
_HOUGH_GATE_DEG = 25.0  # a point's own direction may differ this much from its line (thin wires alias)
_INLIER_PX = 1.0
_FIT_ROUNDS = 3
_LM_ROUNDS = 20
_BAND_MARGIN_PX = 2.5  # flat pixels on each side of a fitted wire profile
_MIN_BLUR_PX = 0.05
_MIN_FOOTPRINT_PX = 0.02  # pixel footprint sides never below this (the trapezoid formula divides by them)
_WIRE_TO_STEP = 0.5  # a bar weaker than this share of the step it sits on is the blur of a bare edge
_EXTENT_MARGIN_PX = 2.0  # the profile of a line stops this far beyond its last detected point
_REFINE_SHIFT_PX = 4.0  # a refinement farther than this from the detected crossing is a wrong model
_RIDGE = "ridge"
_EDGE = "edge"
_LINE_BAND_PX = 1.5  # points this close to a line hypothesis start its fit
_CURVED_EXTENT_PX = 24.0  # a line whose points span less than this is fitted straight
_MAX_LINES = 6  # lines looked for per kind in the coarse region
_GAP_SIGMAS = 2.0  # a line reaches a crossing when its points come within this many scales plus _GAP_PX of it
_GAP_PX = 3.0
_SCALE_SHARE = 0.7  # the smallest scale whose ridge response reaches this share of the strongest
_SHARP_BLUR_PX = 2.0  # a fitted wire or edge blurrier than this (beyond the pixel footprint) is a blob, not a wire
_DISTINCT_PX = 3.0  # coarse crossings closer than this are one crossing of the lattice (a wire and its own edge)
_COARSE_SLACK_PX = 2.0  # coarse crossings this far beyond `radius_px` are still refined (the result is checked)


@dataclass(frozen=True)
class SnapParams:
    """CV31 constants, read from `config.py` only (A16)."""
    radius_px: float = config.SNAP_RADIUS_PX
    window_px: int = config.SNAP_WINDOW_PX
    sigmas_px: tuple = tuple(config.SNAP_SIGMAS_PX)
    min_angle_deg: float = config.SNAP_MIN_ANGLE_DEG
    min_support_px: int = config.SNAP_MIN_SUPPORT_PX
    min_contrast: float = config.SNAP_MIN_CONTRAST
    max_residual_px: float = config.SNAP_MAX_RESIDUAL_PX
    converge_px: float = config.SNAP_CONVERGE_PX
    max_iter: int = config.SNAP_MAX_ITER
    repeat_directions: int = config.SNAP_REPEAT_DIRECTIONS
    repeat_offset_px: float = config.SNAP_REPEAT_OFFSET_PX


@dataclass(frozen=True)
class SnapResult:
    point: tuple  # (x, y): the crossing when snapped, else the start
    status: str  # SNAPPED or UNSNAPPED
    reason: Optional[str]  # one of REASONS exactly when UNSNAPPED
    iterations: int  # windows analysed, at most `max_iter`


@dataclass(frozen=True)
class SnapDescriptors:
    """What the checks of one snap saw (CV32), None where the snap stopped before measuring it. Grey levels are those
    of the luma array."""
    noise: Optional[float] = None  # robust noise of the coarse region, grey levels
    bright_wires: Optional[bool] = None  # polarity: True when the wires are brighter than their sides
    stem_px: Optional[float] = None  # shorter arm of the two lines beyond their crossing (T junction: about 0)
    width_px: Optional[float] = None  # larger fitted wire width of the two lines (0 for two bare edges)
    blur_px: Optional[float] = None  # larger fitted blur of the two lines
    contrast_to_noise: Optional[float] = None  # smaller fitted contrast over the noise of the fine window
    halo: Optional[float] = None  # larger RMS of the profile model residual over the noise (reflections, halos)


@dataclass(frozen=True)
class SnapDetail:
    result: SnapResult  # exactly what `snap_point` returns
    stage: Optional[str]  # one of STAGES exactly when the result is UNSNAPPED
    descriptors: SnapDescriptors


class _Trace:
    """Mutable record of the descriptors, filled while a snap runs."""

    def __init__(self):
        self.values = {}


class _NoCrossing(Exception):
    def __init__(self, reason, stage):
        super().__init__(reason)
        self.reason = reason
        self.stage = stage


# ---- derivatives --------------------------------------------------------------------------------------------------

def _kernels(sigma):
    radius = int(math.ceil(3 * sigma))
    x = numpy.arange(-radius, radius + 1, dtype=numpy.float64)
    g = numpy.exp(-x * x / (2 * sigma * sigma))
    g /= g.sum()
    return radius, g, -x / sigma ** 2 * g, (x * x / sigma ** 4 - 1 / sigma ** 2) * g


def _convolve(region, k_rows, k_columns):
    """Separable convolution, 'valid' part only."""
    radius = len(k_rows) // 2
    rows, columns = region.shape
    tmp = sum(k * region[radius - t:rows - radius - t] for t, k in zip(range(-radius, radius + 1), k_rows))
    return sum(k * tmp[:, radius - t:columns - radius - t] for t, k in zip(range(-radius, radius + 1), k_columns))


class _Scale:
    """Derivatives of the window at one scale, cropped to the window."""

    def __init__(self, region, margin, sigma):
        radius, g, g1, g2 = _kernels(sigma)
        crop = margin - radius

        def derivative(k_rows, k_columns):
            out = _convolve(region, k_rows, k_columns)
            return out[crop:out.shape[0] - crop, crop:out.shape[1] - crop] if crop else out

        self.sigma = sigma
        self.ix, self.iy = derivative(g, g1), derivative(g1, g)
        self.ixx, self.iyy, self.ixy = derivative(g, g2), derivative(g2, g), derivative(g1, g1)
        half = numpy.sqrt(((self.ixx - self.iyy) / 2) ** 2 + self.ixy ** 2)
        mean = (self.ixx + self.iyy) / 2
        large = numpy.where(numpy.abs(mean + half) >= numpy.abs(mean - half), mean + half, mean - half)
        self.lam = large
        self.ridge = sigma * sigma * large  # signed, scale-normalised
        self.edge = sigma * numpy.hypot(self.ix, self.iy)
        norm_g = math.sqrt(float((g * g).sum()))
        self.ridge_noise = sigma * sigma * norm_g * math.sqrt(float((g2 * g2).sum()))  # per grey level of white noise
        self.edge_noise = sigma * norm_g * math.sqrt(float((g1 * g1).sum()))

    def thresholds(self, noise, min_contrast):
        """Ridge and edge thresholds: min_contrast times the noise of each response, measured on the quietest quarter
        of the window (blur and JPEG make the noise correlated, so pixel differences underestimate it at large scales),
        never below the response of white noise of the window's level."""
        ridge = max(float(numpy.percentile(numpy.abs(self.ridge), 25)) / _HALF_NORMAL_Q25, self.ridge_noise * noise)
        edge = max(float(numpy.percentile(self.edge, 25)) / _RAYLEIGH_Q25, self.edge_noise * noise)
        return min_contrast * ridge, min_contrast * edge


# ---- detection ----------------------------------------------------------------------------------------------------

def _noise(window):
    differences = numpy.diff(window, axis=1).ravel()
    mad = numpy.median(numpy.abs(differences - numpy.median(differences)))
    return max(_NOISE_FLOOR, _MAD_TO_SIGMA * float(mad) / math.sqrt(2))


def _ridge_points(scale, polarity, threshold, centres):
    """Sub-pixel centre-line points of the ridges of one polarity: (points, normals, weights)."""
    lam = scale.lam
    a = scale.ixy
    b = lam - scale.ixx
    c = lam - scale.iyy
    use_first = numpy.abs(b) >= numpy.abs(c)
    nx = numpy.where(use_first, a, c)
    ny = numpy.where(use_first, b, a)
    norm = numpy.hypot(nx, ny)
    norm[norm == 0] = 1.0
    nx, ny = nx / norm, ny / norm
    strength = polarity * scale.ridge
    with numpy.errstate(divide="ignore", invalid="ignore"):
        t = -(nx * scale.ix + ny * scale.iy) / lam
    keep = (strength > threshold) & (numpy.abs(t * nx) <= 0.5) & (numpy.abs(t * ny) <= 0.5)
    points = numpy.column_stack([centres[0][keep] + (t * nx)[keep], centres[1][keep] + (t * ny)[keep]])
    return points, numpy.column_stack([nx[keep], ny[keep]]), strength[keep]


def _bilinear(image, x, y):
    rows, columns = image.shape
    x0 = numpy.clip(numpy.floor(x).astype(int), 0, columns - 2)
    y0 = numpy.clip(numpy.floor(y).astype(int), 0, rows - 2)
    fx, fy = x - x0, y - y0
    return ((1 - fy) * ((1 - fx) * image[y0, x0] + fx * image[y0, x0 + 1])
            + fy * ((1 - fx) * image[y0 + 1, x0] + fx * image[y0 + 1, x0 + 1]))


def _edge_points(scale, threshold, centres):
    """Sub-pixel step-edge points (gradient maximum along the gradient): (points, normals, weights)."""
    magnitude = scale.edge
    rows, columns = numpy.nonzero(magnitude > threshold)
    inside = (rows > 0) & (rows < magnitude.shape[0] - 1) & (columns > 0) & (columns < magnitude.shape[1] - 1)
    rows, columns = rows[inside], columns[inside]
    gx, gy = scale.ix[rows, columns], scale.iy[rows, columns]
    norm = numpy.hypot(gx, gy)
    nx, ny = gx / norm, gy / norm
    m0 = magnitude[rows, columns]
    before = _bilinear(magnitude, columns - nx, rows - ny)
    after = _bilinear(magnitude, columns + nx, rows + ny)
    peak = (m0 >= before) & (m0 >= after)
    curvature = before - 2 * m0 + after
    with numpy.errstate(divide="ignore", invalid="ignore"):
        t = numpy.where(curvature < 0, (before - after) / (2 * curvature), 0.0)
    keep = peak & (numpy.abs(t) <= 0.5)
    points = numpy.column_stack([centres[0][rows, columns] + t * nx, centres[1][rows, columns] + t * ny])[keep]
    return points, numpy.column_stack([nx, ny])[keep], m0[keep]


def _direction_degrees(normals):
    """Line direction (perpendicular to the normal), in [0, 180)."""
    return numpy.mod(numpy.degrees(numpy.arctan2(normals[:, 0], -normals[:, 1])), 180.0)


def _angle_between(a, b):
    difference = numpy.abs(numpy.mod(a - b, 180.0))
    return numpy.minimum(difference, 180.0 - difference)


@dataclass(frozen=True)
class _Curve:
    """offset across = c + k * along^2 in the frame (origin, angle in degrees); the normal is direction + 90."""
    origin: tuple
    angle: float
    c: float
    k: float

    def frame(self, x, y):
        theta = math.radians(self.angle)
        dx, dy = x - self.origin[0], y - self.origin[1]
        along = dx * math.cos(theta) + dy * math.sin(theta)
        across = -dx * math.sin(theta) + dy * math.cos(theta)
        return along, across

    def distance(self, x, y):
        along, across = self.frame(x, y)
        return across - self.c - self.k * along * along


def _fit_curve(points, weights, origin, angle, curved=True):
    """Weighted least squares of the across offset as a line, or a quadratic, of the along position (the slope is
    folded into the angle)."""
    for _ in range(2):
        curve = _Curve(origin, angle, 0.0, 0.0)
        along, across = curve.frame(points[:, 0], points[:, 1])
        terms = [numpy.ones_like(along), along] + ([along * along] if curved else [])
        design = numpy.column_stack(terms)
        root = numpy.sqrt(weights)
        solution = numpy.linalg.lstsq(design * root[:, None], across * root, rcond=None)[0]
        angle = angle + math.degrees(math.atan(solution[1]))
    return _Curve(origin, angle, float(solution[0]), float(solution[2]) if curved else 0.0)


@dataclass(frozen=True)
class _Line:
    """A detected wire (ridge) or bare colour edge: its curve and the along extent of its points on that curve."""
    curve: _Curve
    kind: str  # _RIDGE or _EDGE
    extent: tuple  # (first, last) along position of its inlier points
    points: numpy.ndarray = None  # its inlier points (coarse stage)


def _peak(points, directions, weights, origin):
    """(angle, offset) of the heaviest straight alignment of points: for each angle of a 2-degree grid, the heaviest
    1-px offset bin of the points whose own direction agrees within the gate (per-point directions of a thin wire are
    noisy, positions are not)."""
    best, best_angle, best_offset = -1.0, 0.0, 0.0
    for angle in numpy.arange(0.0, 180.0, _HOUGH_STEP_DEG):
        gate = _angle_between(directions, angle) <= _HOUGH_GATE_DEG
        if not gate.any():
            continue
        offsets = _Curve(origin, float(angle), 0.0, 0.0).frame(points[gate, 0], points[gate, 1])[1]
        bins = numpy.rint(offsets).astype(int)
        low = int(bins.min())
        smoothed = numpy.convolve(numpy.bincount(bins - low, weights=weights[gate]), [0.5, 1.0, 0.5], mode="same")
        index = int(numpy.argmax(smoothed))
        if smoothed[index] > best:
            best, best_angle, best_offset = float(smoothed[index]), float(angle), float(index + low)
    return best_angle, best_offset


def _fit_line(points, weights, selected, curve, origin, params, band=_LINE_BAND_PX):
    """The curve (frame at `origin`) through the points of `selected` near `curve`, refitted on its inliers:
    (curve, inlier mask), or None when fewer than `min_support_px` points or an RMS residual above `max_residual_px`.
    Straight while the inliers span less than `_CURVED_EXTENT_PX`, else with the quadratic term of a ring."""
    inliers = selected & (numpy.abs(curve.distance(points[:, 0], points[:, 1])) <= band)
    angle = curve.angle
    for _ in range(_FIT_ROUNDS):
        if inliers.sum() < params.min_support_px:
            return None
        along = _Curve(origin, angle, 0.0, 0.0).frame(points[inliers, 0], points[inliers, 1])[0]
        curve = _fit_curve(points[inliers], weights[inliers], origin, angle,
                           float(numpy.ptp(along)) >= _CURVED_EXTENT_PX)
        angle = curve.angle
        inliers = selected & (numpy.abs(curve.distance(points[:, 0], points[:, 1])) <= _INLIER_PX)
    if inliers.sum() < params.min_support_px:
        return None
    residual = float(numpy.sqrt(numpy.mean(curve.distance(points[inliers, 0], points[inliers, 1]) ** 2)))
    if residual > params.max_residual_px:
        return None
    return curve, inliers


def _lines(points, normals, weights, origin, kind, exclusion, params, known=()):
    """Up to `_MAX_LINES` lines of one kind, strongest first. A point near a line already found (this kind, or one of
    `known`, within `exclusion` px) is not used again: the flanks of a wire are not taken for colour edges."""
    directions = _direction_degrees(normals)
    free = numpy.ones(len(points), dtype=bool)
    for line in known:
        free &= numpy.abs(line.curve.distance(points[:, 0], points[:, 1])) > exclusion
    found = []
    while len(found) < _MAX_LINES and free.sum() >= params.min_support_px:
        angle, offset = _peak(points[free], directions[free], weights[free], origin)
        guess = _Curve(origin, angle, offset, 0.0)
        selected = free & (_angle_between(directions, angle) <= _HOUGH_GATE_DEG)
        fitted = _fit_line(points, weights, selected, guess, origin, params)
        if fitted is None:
            used = selected & (numpy.abs(guess.distance(points[:, 0], points[:, 1])) <= _LINE_BAND_PX)
            if not used.any():
                break
            free &= ~used
            continue
        curve, inliers = fitted
        along = curve.frame(points[inliers, 0], points[inliers, 1])[0]
        found.append(_Line(curve, kind, (float(along.min()), float(along.max())), points[inliers]))
        free &= numpy.abs(curve.distance(points[:, 0], points[:, 1])) > max(_LINE_BAND_PX, exclusion / 2)
    return found


# ---- profile refinement -------------------------------------------------------------------------------------------

def _erf(x):
    """Abramowitz and Stegun 7.1.26 (absolute error below 1.5e-7), vectorised."""
    sign = numpy.sign(x)
    x = numpy.abs(x)
    t = 1.0 / (1.0 + 0.3275911 * x)
    poly = t * (0.254829592 + t * (-0.284496736 + t * (1.421413741 + t * (-1.453152027 + t * 1.061405429))))
    return sign * (1.0 - poly * numpy.exp(-x * x))


def _cdf(z):
    return 0.5 * (1.0 + _erf(z / math.sqrt(2.0)))


def _cdf_second_integral(z):
    """Second antiderivative of the standard normal CDF."""
    return 0.5 * ((z * z + 1.0) * _cdf(z) + z * numpy.exp(-0.5 * z * z) / math.sqrt(2.0 * math.pi))


def _pixel_step(x, blur, angle):
    """Share of a pixel beyond a blurred straight edge, its centre at signed distance `x` past the edge: the unit
    square projected on the edge normal (a trapezoid of sides |cos| and |sin| of the angle) convolved with the
    Gaussian blur. Sampling a blurred step at the pixel centre instead biases sharp edges by up to 0.1 px."""
    theta = math.radians(angle)
    a = max(abs(math.cos(theta)), _MIN_FOOTPRINT_PX)
    c = max(abs(math.sin(theta)), _MIN_FOOTPRINT_PX)
    total = 0.0
    for sign_a, sign_c, weight in ((1, 1, 1.0), (1, -1, -1.0), (-1, 1, -1.0), (-1, -1, 1.0)):
        total = total + weight * _cdf_second_integral((x + sign_a * a / 2 + sign_c * c / 2) / blur)
    return total * blur * blur / (a * c)


class _Profile:
    """Blurred profile across one curve on a fixed pixel set; levels solved linearly (variable projection)."""

    def __init__(self, xs, ys, values, halves, bar, straight):
        self.xs, self.ys, self.values, self.halves, self.bar = xs, ys, values, halves, bar
        self.straight = straight

    def design(self, origin, q):
        angle, c, k, width, blur = q
        curve = _Curve(origin, angle, c, k)
        along, across = curve.frame(self.xs, self.ys)
        u = across - c - k * along * along
        blur = max(blur, _MIN_BLUR_PX)
        left = _pixel_step(-abs(width) / 2 - u, blur, angle)
        right = _pixel_step(u - abs(width) / 2, blur, angle)
        columns = []
        for half in numpy.unique(self.halves):
            mask = self.halves == half
            columns += [left * mask, right * mask]
        if self.bar:
            columns.append(1.0 - left - right)
        columns += [along, across]  # background plane: shading across the window
        return numpy.column_stack(columns)

    def solve(self, origin, q):
        design = self.design(origin, q)
        levels = numpy.linalg.lstsq(design, self.values, rcond=None)[0]
        return self.values - design @ levels, levels


def _fit_profile(profile, origin, q):
    """Levenberg-Marquardt on (angle, c, k, width, blur); the width stays 0 for a bare edge.

    Returns (q, levels, cost)."""
    free = [i for i in range(5) if not (i == 3 and not profile.bar) and not (i == 2 and profile.straight)]
    steps = numpy.array([1e-3, 1e-3, 1e-5, 1e-3, 1e-3])
    q = numpy.array(q, dtype=numpy.float64)
    residual, _ = profile.solve(origin, q)
    cost = float(residual @ residual)
    damping = 1e-3
    for _ in range(_LM_ROUNDS):
        jacobian = numpy.empty((residual.size, len(free)))
        for column, index in enumerate(free):
            moved = q.copy()
            moved[index] += steps[index]
            jacobian[:, column] = (profile.solve(origin, moved)[0] - residual) / steps[index]
        normal = jacobian.T @ jacobian
        gradient = jacobian.T @ residual
        while True:
            delta = numpy.linalg.solve(normal + damping * numpy.diag(numpy.diag(normal) + 1e-12), -gradient)
            candidate = q.copy()
            candidate[free] += delta
            candidate[3] = abs(candidate[3])
            candidate[4] = max(candidate[4], _MIN_BLUR_PX)
            new_residual, _ = profile.solve(origin, candidate)
            new_cost = float(new_residual @ new_residual)
            if new_cost <= cost or damping > 1e6:
                break
            damping *= 10
        if new_cost > cost:
            break
        converged = abs(delta[1]) < 1e-4 and abs(delta[0]) < 1e-3
        q, residual, cost, damping = candidate, new_residual, new_cost, max(damping / 10, 1e-6)
        if converged:
            break
    return q, profile.solve(origin, q)[1], cost


def _band(q, origin, xs, ys, half_width):
    return numpy.abs(_Curve(origin, q[0], q[1], q[2]).distance(xs, ys)) <= half_width


@dataclass(frozen=True)
class _Fitted:
    curve: _Curve
    contrast: float  # grey levels: wire against its nearer side, or the step of a bare edge
    band: float  # half width of the pixels it disturbs
    width: float
    blur: float
    rms: float = 0.0  # grey levels: RMS of the profile model residual on its band (diagnosis only, CV32)


def _profile_pass(window, grid, origin, q, allowed, side, bar, straight, noise):
    """Two fits on the band of the curve (the second on a band sized from the first, without outlier pixels)."""
    xs, ys = grid
    keep = numpy.ones_like(allowed)
    band = 2.0 * max(q[3], 1.0) + 3.0
    for _ in range(2):
        mask = allowed & keep & _band(q, origin, xs, ys, band)
        if mask.sum() < 16:
            raise _NoCrossing(SUPPORT, FINE_SUPPORT)
        profile = _Profile(xs[mask], ys[mask], window[mask], side[mask], bar, straight)
        q, levels, cost = _fit_profile(profile, origin, q)
        if not numpy.all(numpy.isfinite(q)) or not numpy.all(numpy.isfinite(levels)):
            raise _NoCrossing(RESIDUAL, FIT_FAILURE)
        residual = numpy.zeros_like(window)
        residual[mask] = profile.solve(origin, q)[0]
        spread = _MAD_TO_SIGMA * float(numpy.median(numpy.abs(residual[mask])))
        keep = numpy.abs(residual) <= 3.0 * max(spread, noise)
        band = abs(q[3]) / 2 + 2.5 * q[4] + _BAND_MARGIN_PX
    return q, levels, cost / mask.sum(), band


def _refine(window, grid, clear, origin, curve, other, other_band, inlier_points, sigma, noise, floor, previous=None):
    """Position of one curve by the profile model on its band, outside the other curve's band, on the sides of the
    other curve where its detected points lie, along the extent of those points. A wire (a level beyond both sides,
    at least `floor` grey levels once spread over one pixel) is fitted as a bar, else as a bare step edge."""
    xs, ys = grid
    side = numpy.sign(other.distance(xs, ys))
    point_side = numpy.sign(other.distance(inlier_points[:, 0], inlier_points[:, 1]))
    counts = {h: int((point_side == h).sum()) for h in (-1.0, 1.0)}
    halves = [h for h, n in counts.items() if n >= max(3, 0.2 * len(point_side))] or [max(counts, key=counts.get)]
    allowed = clear & numpy.isin(side, halves) & ~_band((other.angle, other.c, other.k), origin, xs, ys, other_band)
    along = curve.frame(xs, ys)[0]
    point_along = curve.frame(inlier_points[:, 0], inlier_points[:, 1])[0]
    allowed &= (along >= point_along.min() - _EXTENT_MARGIN_PX) & (along <= point_along.max() + _EXTENT_MARGIN_PX)
    count = 2 * len(halves)
    straight = len(halves) == 1  # the stem of the T (radial wire or sector edge) is straight in any perspective
    width, blur = (max(previous.width, 1.0), previous.blur) if previous else (max(1.0, sigma), 1.0)
    start = numpy.array([curve.angle, curve.c, 0.0 if straight else curve.k, width, blur])
    q, levels, mean_square, band = _profile_pass(window, grid, origin, start, allowed, side, True, straight, noise)
    wire = levels[count] - levels[:count]
    excess = float(numpy.min(numpy.abs(wire))) * min(1.0, abs(q[3]))  # a thin wire only fixes width times level
    step = float(numpy.mean(numpy.abs(levels[1:count:2] - levels[0:count:2])))
    if (numpy.all(wire > 0) or numpy.all(wire < 0)) and excess >= max(floor, _WIRE_TO_STEP * step):
        contrast = excess
    else:
        q, levels, mean_square, band = _profile_pass(window, grid, origin, numpy.array([q[0], q[1], q[2], 0.0, q[4]]),
                                                     allowed, side, False, straight, noise)
        contrast = float(numpy.mean(numpy.abs(levels[1:count:2] - levels[0:count:2])))
    return _Fitted(_Curve(origin, float(q[0]), float(q[1]), float(q[2])), contrast, band, abs(float(q[3])),
                   float(q[4]), math.sqrt(max(float(mean_square), 0.0)))


def _stands_out(scan, curve, point, floor):
    """A wire or step edge shows on at least one flank of `point` along `curve`, `floor` grey levels above or beside
    its sides; the flanks lie beyond the other line's band (the other line crosses at `point` at least
    `min_angle_deg` away, and the scales of the scan give the band)."""
    theta = math.radians(curve.angle)
    direction = numpy.array([math.cos(theta), math.sin(theta)])
    normal = numpy.array([-direction[1], direction[0]])
    side = 2.0 * scan.sigma + _LINE_BAND_PX
    reach = min(4.0 * scan.sigma + 4.0, scan.window.shape[0] / 2.0 - side - 1.0)
    corner = numpy.array([scan.grid[0][0, 0], scan.grid[1][0, 0]])
    brighter = -scan.polarity

    def sample(shift, along):
        at = numpy.array(point) + along * direction + shift * normal - corner
        return float(_bilinear(scan.window, numpy.array([at[0]]), numpy.array([at[1]]))[0])

    for along in (reach, -reach):
        centre, low, high = sample(0.0, along), sample(-side, along), sample(side, along)
        wire = min(brighter * (centre - low), brighter * (centre - high))
        if max(wire, abs(high - low)) >= floor:
            return True
    return False


def _crossing(first, second):
    """Intersection of two curves near their common origin (Newton from the origin)."""
    x, y = first.origin
    for _ in range(8):
        f = numpy.array([first.distance(x, y), second.distance(x, y)])
        jacobian = numpy.empty((2, 2))
        for row, curve in enumerate((first, second)):
            jacobian[row] = [(curve.distance(x + 1e-4, y) - f[row]) / 1e-4,
                             (curve.distance(x, y + 1e-4) - f[row]) / 1e-4]
        step = numpy.linalg.solve(jacobian, -f)
        x, y = x + step[0], y + step[1]
        if math.hypot(*step) < 1e-6:
            break
    return float(x), float(y)


# ---- one window ---------------------------------------------------------------------------------------------------

def _region(luma, centre, half, margin):
    """Window plus margin around the pixel holding `centre` (reflected outside the image), and its pixel origin."""
    rows, columns = luma.shape
    row, column = int(math.floor(centre[1])), int(math.floor(centre[0]))
    if row - half < 0 or column - half < 0 or row + half >= rows or column + half >= columns:
        raise _NoCrossing(BORDER, STAGE_BORDER)
    top, left = row - half - margin, column - half - margin
    bottom, right = row + half + margin + 1, column + half + margin + 1
    pad = ((max(0, -top), max(0, bottom - rows)), (max(0, -left), max(0, right - columns)))
    region = luma[max(0, top):min(rows, bottom), max(0, left):min(columns, right)].astype(numpy.float64)
    if any(p for pair in pad for p in pair):
        region = numpy.pad(region, pad, mode="reflect")
    return region, (row - half, column - half)


def _cell(grid, curves, point, band):
    """Pixels on the side of `point` of every curve, farther than `band` from it: the cell of the crossing."""
    cell = numpy.ones(grid[0].shape, dtype=bool)
    for curve in curves:
        offsets = curve.distance(grid[0], grid[1])
        cell &= (numpy.abs(offsets) > band) & (numpy.sign(offsets) == numpy.sign(curve.distance(*point)))
    return cell


@dataclass(frozen=True)
class _Scan:
    """One window at its best scale: pixel grid, noise, detected ridge and step-edge points."""
    window: numpy.ndarray
    grid: tuple
    noise: float
    sigma: float
    polarity: float  # sign of the ridge response of the wires: -1 for bright wires
    ridge: tuple  # (points, normals, weights)
    edge: tuple

    def points(self, kind):
        return self.ridge if kind == _RIDGE else self.edge


def _scan(luma, centre, half, params):
    margin = int(math.ceil(3 * max(params.sigmas_px)))
    region, (top, left) = _region(luma, centre, half, margin)
    window = region[margin:margin + 2 * half + 1, margin:margin + 2 * half + 1]
    noise = _noise(window)
    scales = [_Scale(region, margin, sigma) for sigma in params.sigmas_px]
    rows, columns = numpy.mgrid[0:2 * half + 1, 0:2 * half + 1]
    grid = (columns + left + 0.5, rows + top + 0.5)
    responses = [float(numpy.max(numpy.abs(s.ridge))) for s in scales]
    scale = next(s for s, r in zip(scales, responses) if r >= _SCALE_SHARE * max(responses))
    strongest = numpy.unravel_index(int(numpy.argmax(numpy.abs(scale.ridge))), scale.ridge.shape)
    polarity = 1.0 if scale.ridge[strongest] > 0 else -1.0
    ridge_threshold, edge_threshold = scale.thresholds(noise, params.min_contrast)
    return _Scan(window, grid, noise, scale.sigma, polarity, _ridge_points(scale, polarity, ridge_threshold, grid),
                 _edge_points(scale, edge_threshold, grid))


def _is_wire(line, scan, offset, floor):
    """A wire stands out from both of its sides; the ridge seen on the bright side of a bare colour edge does not (one
    side is as bright as its centre). Median over the line's points of the smaller of the two differences."""
    points = line.points
    theta = math.radians(line.curve.angle)
    normal = numpy.array([-math.sin(theta), math.cos(theta)])
    corner = numpy.array([scan.grid[0][0, 0], scan.grid[1][0, 0]])

    def sample(shift):
        at = points + shift * normal - corner
        return _bilinear(scan.window, at[:, 0], at[:, 1])

    centre = sample(0.0)
    brighter = -scan.polarity  # a bright wire has a negative ridge response
    excess = numpy.minimum(brighter * (centre - sample(offset)), brighter * (centre - sample(-offset)))
    return float(numpy.median(excess)) >= floor


def _in_cell(line, point, partner, others, exclusion, params, origin):
    """The line refitted on its points of the cell of `point` (on the side of `point` of every other line, farther
    than `exclusion` from them and from `partner`): beyond the next ring a radial wire runs between a cream and a
    black single and its ridge points are offset; near a junction they bend."""
    cell = numpy.abs(partner.curve.distance(line.points[:, 0], line.points[:, 1])) > exclusion
    for other in others:
        offsets = other.curve.distance(line.points[:, 0], line.points[:, 1])
        cell &= (numpy.abs(offsets) > exclusion) & (numpy.sign(offsets) == numpy.sign(other.curve.distance(*point)))
    if cell.sum() < params.min_support_px or cell.all():
        return line
    points = line.points[cell]
    along = line.curve.frame(points[:, 0], points[:, 1])[0]
    curve = _fit_curve(points, numpy.ones(len(points)), origin, line.curve.angle,
                       float(numpy.ptp(along)) >= _CURVED_EXTENT_PX)
    return _Line(curve, line.kind, (float(along.min()), float(along.max())), points)


def _reaches(line, point, gap):
    """The line's points come within `gap` px (along the line) of `point`: it passes through it or ends at it."""
    along = line.curve.frame(point[0], point[1])[0]
    return line.extent[0] - gap <= along <= line.extent[1] + gap


def _stem(scan, first, second, point, floor):
    """Shortest arm of the two lines beyond their crossing (CV32 T-junction indicator, diagnosis only): for each line
    and each side of `point`, the distance along the line up to which it keeps standing out (a wire above both of its
    sides, or a step between them, by `floor` grey levels), from where the other line's band ends; 0 when it does not
    stand out there (a radial wire that ends at the ring wire), capped by the scanned region."""
    side = 2.0 * scan.sigma + _LINE_BAND_PX
    sine = max(math.sin(math.radians(float(_angle_between(first.angle, second.angle)))), 0.5)
    begin = 2.0 * side / sine  # the side samples clear the other line's band from here
    corner = numpy.array([scan.grid[0][0, 0], scan.grid[1][0, 0]])
    size = scan.window.shape[0]
    brighter = -scan.polarity
    arms = []
    for curve in (first, second):
        theta = math.radians(curve.angle)
        direction = numpy.array([math.cos(theta), math.sin(theta)])
        normal = numpy.array([-direction[1], direction[0]])
        for sign in (1.0, -1.0):
            arm, along = 0.0, begin
            while True:
                at = numpy.array(point) + sign * along * direction - corner
                samples = numpy.array([at, at - side * normal, at + side * normal])
                if samples.min() < 0.0 or samples.max() > size - 1.0:
                    break
                centre, low, high = _bilinear(scan.window, samples[:, 0], samples[:, 1])
                if max(min(brighter * (centre - low), brighter * (centre - high)), abs(high - low)) < floor:
                    break
                arm, along = along, along + 1.0
            arms.append(arm)
    return float(min(arms))


def _coarse(luma, start, params, trace):
    """Coarse stage: the lines of a region of half side `window_px + radius_px` around the start, and the crossing of
    two of them (angle at least `min_angle_deg`, both lines reaching it) nearest the start within `radius_px`.

    Returns (estimate, first, second, others) or raises _NoCrossing."""
    scan = _scan(luma, start, params.window_px + int(math.ceil(params.radius_px)), params)
    trace.values["noise"] = scan.noise
    trace.values["bright_wires"] = scan.polarity < 0
    if len(scan.ridge[0]) == 0 and len(scan.edge[0]) == 0:
        raise _NoCrossing(CONTRAST, COARSE_NO_RIDGE)
    exclusion = 2.0 * scan.sigma + _LINE_BAND_PX
    ridges = [line for line in _lines(*scan.ridge, start, _RIDGE, exclusion, params)
              if _is_wire(line, scan, exclusion, params.min_contrast * scan.noise)]
    edges = _lines(*scan.edge, start, _EDGE, exclusion, params, ridges)
    if not ridges:
        raise _NoCrossing(SUPPORT if len(scan.ridge[0]) else CONTRAST, COARSE_NO_RIDGE)
    gap = _GAP_SIGMAS * scan.sigma + _GAP_PX
    seen, best, found = set(), None, []
    for index, first in enumerate(ridges):
        for second in ridges[index + 1:] + edges:
            if _angle_between(first.curve.angle, second.curve.angle) < params.min_angle_deg:
                seen.add(ANGLE)
                continue
            point = _crossing(first.curve, second.curve)
            distance = math.hypot(point[0] - start[0], point[1] - start[1])
            if not math.isfinite(distance) or distance > params.radius_px + _COARSE_SLACK_PX:
                seen.add(TOO_FAR)
            elif not (_reaches(first, point, gap) and _reaches(second, point, gap)):
                seen.add(SUPPORT)
            else:
                if distance <= params.radius_px and all(math.dist(point, other) > _DISTINCT_PX for other in found):
                    found.append(point)
                if best is None or distance < best[0]:
                    best = (distance, point, first, second)
    if best is not None:
        trace.values["stem_px"] = _stem(scan, best[2].curve, best[3].curve, best[1], params.min_contrast * scan.noise)
    if len(found) > 1:
        raise _NoCrossing(AMBIGUOUS, STAGE_AMBIGUOUS)
    if best is None:
        stages = ((TOO_FAR, COARSE_TOO_FAR), (SUPPORT, COARSE_NOT_REACHING), (ANGLE, COARSE_NO_PAIR))
        raise _NoCrossing(*next((pair for pair in stages if pair[0] in seen), (SUPPORT, COARSE_NO_PAIR)))
    _, point, first, second = best
    keep = 2.0 * scan.sigma + _GAP_PX
    others = tuple(line for line in ridges + edges if line is not first and line is not second and not any(
        _angle_between(line.curve.angle, chosen.curve.angle) < params.min_angle_deg
        and abs(line.curve.distance(*point) - chosen.curve.distance(*point)) < keep for chosen in (first, second)))
    first, second = (_in_cell(first, point, second, others, exclusion, params, start),
                     _in_cell(second, point, first, others, exclusion, params, start))
    return _crossing(first.curve, second.curve), first, second, others


def _fine(luma, estimate, first, second, others, params, trace):
    """Fine stage, one iteration: both lines refitted on the points of the window around `estimate`, then refined
    by their profiles. Returns (crossing, first, second) or raises _NoCrossing."""
    scan = _scan(luma, estimate, params.window_px, params)
    sigma = scan.sigma
    fitted = []
    for line, other in ((first, second), (second, first)):
        result = None
        for kind in (line.kind, _EDGE if line.kind == _RIDGE else _RIDGE):  # a bare edge also has a bright-side ridge
            points, normals, weights = scan.points(kind)
            if len(points) == 0:
                continue
            selected = ((_angle_between(_direction_degrees(normals), line.curve.angle) <= _HOUGH_GATE_DEG)
                        & (numpy.abs(other.curve.distance(points[:, 0], points[:, 1])) > sigma + _LINE_BAND_PX))
            result = _fit_line(points, weights, selected, line.curve, estimate, params, 2.0 * sigma + _LINE_BAND_PX)
            if result is not None:
                break
        if result is None:
            raise _NoCrossing(SUPPORT, FINE_SUPPORT)
        fitted.append((result[0], points[result[1]]))
    (first_curve, first_points), (second_curve, second_points) = fitted
    if _angle_between(first_curve.angle, second_curve.angle) < params.min_angle_deg:
        raise _NoCrossing(ANGLE, FINE_ANGLE)
    detected = _crossing(first_curve, second_curve)
    clear = _cell(scan.grid, [line.curve for line in others], estimate, sigma + 2.0)  # profiles stop at other lines
    band = 2.0 * sigma + 2.0
    floor = params.min_contrast * scan.noise
    common = (scan.window, scan.grid, clear, estimate)
    refined_first = _refine(*common, first_curve, second_curve, band + 2.0, first_points, sigma, scan.noise, floor)
    refined_second = _refine(*common, second_curve, refined_first.curve, refined_first.band, second_points, sigma,
                             scan.noise, floor, refined_first)  # both wires of a crossing have about the same width
    refined_first = _refine(*common, refined_first.curve, refined_second.curve, refined_second.band, first_points,
                            sigma, scan.noise, floor, refined_first)
    pair = (refined_first, refined_second)
    trace.values.update(width_px=max(f.width for f in pair), blur_px=max(f.blur for f in pair),
                        contrast_to_noise=min(f.contrast for f in pair) / scan.noise,
                        halo=max(f.rms for f in pair) / scan.noise)
    if min(refined_first.contrast, refined_second.contrast) < floor:
        raise _NoCrossing(CONTRAST, FINE_CONTRAST)
    if _angle_between(refined_first.curve.angle, refined_second.curve.angle) < params.min_angle_deg:
        raise _NoCrossing(ANGLE, FINE_ANGLE)
    refined = _crossing(refined_first.curve, refined_second.curve)
    if math.hypot(refined[0] - detected[0], refined[1] - detected[1]) > _REFINE_SHIFT_PX:
        raise _NoCrossing(RESIDUAL, REFINE_SHIFT)
    if max(refined_first.blur, refined_second.blur) > _SHARP_BLUR_PX:
        raise _NoCrossing(RESIDUAL, FINE_BLUR)
    if not all(_stands_out(scan, fitted.curve, refined, floor) for fitted in (refined_first, refined_second)):
        raise _NoCrossing(CONTRAST, STANDS_OUT)
    return refined, _Line(refined_first.curve, first.kind, ()), _Line(refined_second.curve, second.kind, ())


# ---- public -------------------------------------------------------------------------------------------------------

def snap_detail(luma, start, params=SnapParams()) -> SnapDetail:
    """The snap of `snap_point` with the stage that stopped it and the descriptors its checks measured (CV32)."""
    start = (float(start[0]), float(start[1]))
    rows, columns = luma.shape
    estimate = start
    iterations = 0
    trace = _Trace()
    try:
        if not all(math.isfinite(v) for v in start):
            raise _NoCrossing(BORDER, STAGE_BORDER)
        estimate, first, second, others = _coarse(luma, start, params, trace)
        while iterations < params.max_iter:
            iterations += 1
            found, first, second = _fine(luma, estimate, first, second, others, params, trace)
            if not (0.0 <= found[0] < columns and 0.0 <= found[1] < rows):
                raise _NoCrossing(BORDER, STAGE_BORDER)
            if math.hypot(found[0] - start[0], found[1] - start[1]) > params.radius_px:
                raise _NoCrossing(TOO_FAR, STAGE_TOO_FAR)
            moved = math.hypot(found[0] - estimate[0], found[1] - estimate[1])
            estimate = found
            if moved < params.converge_px:
                return SnapDetail(SnapResult(found, SNAPPED, None, iterations), None, SnapDescriptors(**trace.values))
        raise _NoCrossing(NO_CONVERGENCE, STAGE_NO_CONVERGENCE)
    except _NoCrossing as failure:
        result, stage = SnapResult(start, UNSNAPPED, failure.reason, iterations), failure.stage
    except numpy.linalg.LinAlgError:
        result, stage = SnapResult(start, UNSNAPPED, RESIDUAL, iterations), FIT_FAILURE
    return SnapDetail(result, stage, SnapDescriptors(**trace.values))


def snap_point(luma, start, params=SnapParams()) -> SnapResult:
    """The sub-pixel crossing near `start` (CV31), or the start itself with status `unsnapped` and its reason."""
    return snap_detail(luma, start, params).result


def _board_mm(image_to_board, point):
    u, v, w = numpy.asarray(image_to_board, dtype=numpy.float64) @ numpy.array([point[0], point[1], 1.0])
    return u / w, v / w


def restart_dispersion(luma, point, homography, params=SnapParams()) -> float:
    """Repeatability `D` of a snapped point (CV28, CV31): largest board-plane distance in mm between `point` and the
    snap restarted from `repeat_directions` starts at `repeat_offset_px` around it; `inf` when a restart fails.
    `homography` maps image pixels to board mm (`geometry.board_from_image`). It measures repeatability only: restarts
    that all converge on the same wrong crossing give 0 (A35, T2)."""
    reference = _board_mm(homography, point)
    largest = 0.0
    for index in range(params.repeat_directions):
        angle = 2 * math.pi * index / params.repeat_directions
        offset = params.repeat_offset_px
        start = (point[0] + offset * math.cos(angle), point[1] + offset * math.sin(angle))
        result = snap_point(luma, start, params)
        if result.status != SNAPPED:
            return math.inf
        x, y = _board_mm(homography, result.point)
        largest = max(largest, math.hypot(x - reference[0], y - reference[1]))
    return largest
