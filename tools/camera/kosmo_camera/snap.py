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
`no_convergence`, `too_far` (more than `SNAP_RADIUS_PX` from the start), `border` (window or result outside the image).
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
REASONS = (SUPPORT, CONTRAST, RESIDUAL, ANGLE, NO_CONVERGENCE, TOO_FAR, BORDER)

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
_MIN_BLUR_PX = 0.3
_PARALLEL_STRONG = 0.5  # a line is chosen among the parallel offset peaks of at least this share of the highest
_WIRE_TO_STEP = 0.5  # a bar weaker than this share of the step it sits on is the blur of a bare edge
_EXTENT_MARGIN_PX = 2.0  # the profile of a line stops this far beyond its last detected point
_STEM_REACH_PX = 14.0  # the second line is searched within this distance beyond the first line band
_PARALLEL_SEEN = 0.2  # parallel peaks of at least this share are kept out of the other line's profile
_REFINE_SHIFT_PX = 4.0  # a refinement farther than this from the detected crossing is a wrong model


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


class _NoCrossing(Exception):
    def __init__(self, reason):
        super().__init__(reason)
        self.reason = reason


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


def _fit_curve(points, weights, origin, angle):
    """Weighted least squares of the across offset as a quadratic of the along position (b folded into the angle)."""
    for _ in range(2):
        curve = _Curve(origin, angle, 0.0, 0.0)
        along, across = curve.frame(points[:, 0], points[:, 1])
        design = numpy.column_stack([numpy.ones_like(along), along, along * along])
        root = numpy.sqrt(weights)
        solution = numpy.linalg.lstsq(design * root[:, None], across * root, rcond=None)[0]
        angle = angle + math.degrees(math.atan(solution[1]))
    return _Curve(origin, angle, float(solution[0]), float(solution[2]))


def _nearest_parallel(points, weights, selected, origin, angle):
    """Among parallel lines of one orientation (the two wires of a ring), the strong one nearest the window centre: a
    histogram of the offsets, its peaks of at least half the highest one, the peak of smallest offset."""
    offsets = _Curve(origin, angle, 0.0, 0.0).frame(points[:, 0], points[:, 1])[1]
    reach = int(numpy.ceil(numpy.max(numpy.abs(offsets[selected])))) + 2
    histogram = numpy.bincount(numpy.rint(offsets[selected]).astype(int) + reach, weights=weights[selected],
                               minlength=2 * reach + 1)
    smoothed = numpy.convolve(histogram, [0.5, 1.0, 0.5], mode="same")
    peaks = [i - reach for i in range(1, len(smoothed) - 1)
             if smoothed[i] >= smoothed[i - 1] and smoothed[i] > smoothed[i + 1]
             and smoothed[i] >= _PARALLEL_SEEN * smoothed.max()]
    strong = [i for i in peaks if smoothed[i + reach] >= _PARALLEL_STRONG * smoothed.max()]
    best = min(strong, key=abs)
    return numpy.abs(offsets - best) <= _INLIER_PX + 1.0, tuple(float(i - best) for i in peaks if i != best)


def _hough_angle(points, directions, weights, origin):
    """Orientation of the strongest straight alignment of points: for each angle of a 2-degree grid, the heaviest
    1-px offset bin of the points whose own direction agrees within the gate (per-point directions of a thin wire are
    noisy, positions are not)."""
    best, best_angle = -1.0, 0.0
    for angle in numpy.arange(0.0, 180.0, _HOUGH_STEP_DEG):
        gate = _angle_between(directions, angle) <= _HOUGH_GATE_DEG
        if not gate.any():
            continue
        offsets = _Curve(origin, float(angle), 0.0, 0.0).frame(points[gate, 0], points[gate, 1])[1]
        bins = numpy.rint(offsets).astype(int)
        bins -= bins.min()
        histogram = numpy.bincount(bins, weights=weights[gate])
        score = float(numpy.max(numpy.convolve(histogram, [0.5, 1.0, 0.5])))
        if score > best:
            best, best_angle = score, float(angle)
    return best_angle


def _detect_line(points, normals, weights, origin, params):
    """Strongest orientation, then the curve through the inliers of its nearest strong offset: (curve, inlier mask)."""
    directions = _direction_degrees(normals)
    angle = _hough_angle(points, directions, weights, origin)
    selected = _angle_between(directions, angle) <= _HOUGH_GATE_DEG
    if selected.sum() < params.min_support_px:
        raise _NoCrossing(SUPPORT)
    nearest, parallels = _nearest_parallel(points, weights, selected, origin, angle)
    selected &= nearest
    if selected.sum() < params.min_support_px:
        raise _NoCrossing(SUPPORT)
    mean = numpy.average(points[selected], axis=0, weights=weights[selected])
    centred = points[selected] - mean
    covariance = (centred * weights[selected, None]).T @ centred
    eigenvalues, eigenvectors = numpy.linalg.eigh(covariance)
    direction = eigenvectors[:, int(numpy.argmax(eigenvalues))]
    angle = math.degrees(math.atan2(direction[1], direction[0]))
    curve = _Curve(origin, angle, 0.0, 0.0)
    curve = _Curve(origin, angle, float(curve.frame(mean[0], mean[1])[1]), 0.0)
    for _ in range(_FIT_ROUNDS):
        inliers = selected & (numpy.abs(curve.distance(points[:, 0], points[:, 1])) <= _INLIER_PX)
        if inliers.sum() < params.min_support_px:
            raise _NoCrossing(SUPPORT)
        curve = _fit_curve(points[inliers], weights[inliers], origin, curve.angle)
    inliers = selected & (numpy.abs(curve.distance(points[:, 0], points[:, 1])) <= _INLIER_PX)
    if inliers.sum() < params.min_support_px:
        raise _NoCrossing(SUPPORT)
    residual = float(numpy.sqrt(numpy.mean(curve.distance(points[inliers, 0], points[inliers, 1]) ** 2)))
    if residual > params.max_residual_px:
        raise _NoCrossing(RESIDUAL)
    return curve, inliers, tuple(_Curve(origin, curve.angle, curve.c + d, curve.k) for d in parallels)


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
        left = _cdf((-abs(width) / 2 - u) / blur)
        right = _cdf((u - abs(width) / 2) / blur)
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


def _profile_pass(window, grid, origin, q, allowed, side, bar, straight, noise):
    """Two fits on the band of the curve (the second on a band sized from the first, without outlier pixels)."""
    xs, ys = grid
    keep = numpy.ones_like(allowed)
    band = 2.0 * max(q[3], 1.0) + 3.0
    for _ in range(2):
        mask = allowed & keep & _band(q, origin, xs, ys, band)
        if mask.sum() < 16:
            raise _NoCrossing(SUPPORT)
        profile = _Profile(xs[mask], ys[mask], window[mask], side[mask], bar, straight)
        q, levels, cost = _fit_profile(profile, origin, q)
        if not numpy.all(numpy.isfinite(q)) or not numpy.all(numpy.isfinite(levels)):
            raise _NoCrossing(RESIDUAL)
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
    q, levels, _, band = _profile_pass(window, grid, origin, start, allowed, side, True, straight, noise)
    wire = levels[count] - levels[:count]
    excess = float(numpy.min(numpy.abs(wire))) * min(1.0, abs(q[3]))  # a thin wire only fixes width times level
    step = float(numpy.mean(numpy.abs(levels[1:count:2] - levels[0:count:2])))
    if (numpy.all(wire > 0) or numpy.all(wire < 0)) and excess >= max(floor, _WIRE_TO_STEP * step):
        contrast = excess
    else:
        q, levels, _, band = _profile_pass(window, grid, origin, numpy.array([q[0], q[1], q[2], 0.0, q[4]]),
                                           allowed, side, False, straight, noise)
        contrast = float(numpy.mean(numpy.abs(levels[1:count:2] - levels[0:count:2])))
    return _Fitted(_Curve(origin, float(q[0]), float(q[1]), float(q[2])), contrast, band, abs(float(q[3])),
                   float(q[4]))


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
        raise _NoCrossing(BORDER)
    top, left = row - half - margin, column - half - margin
    bottom, right = row + half + margin + 1, column + half + margin + 1
    pad = ((max(0, -top), max(0, bottom - rows)), (max(0, -left), max(0, right - columns)))
    region = luma[max(0, top):min(rows, bottom), max(0, left):min(columns, right)].astype(numpy.float64)
    if any(p for pair in pad for p in pair):
        region = numpy.pad(region, pad, mode="reflect")
    return region, (row - half, column - half)


def _clear(grid, curves, band):
    clear = numpy.ones(grid[0].shape, dtype=bool)
    for curve in curves:
        clear &= numpy.abs(curve.distance(grid[0], grid[1])) > band
    return clear


def _away(points, normals, curve, band, params):
    return ((_angle_between(_direction_degrees(normals), curve.angle) >= params.min_angle_deg)
            & (numpy.abs(curve.distance(points[:, 0], points[:, 1])) > band)
            & (numpy.abs(curve.distance(points[:, 0], points[:, 1])) <= band + _STEM_REACH_PX))


def _locate(luma, estimate, params):
    """One iteration: the crossing seen in the window around `estimate`, or _NoCrossing."""
    half = params.window_px
    margin = int(math.ceil(3 * max(params.sigmas_px)))
    region, (top, left) = _region(luma, estimate, half, margin)
    window = region[margin:margin + 2 * half + 1, margin:margin + 2 * half + 1]
    noise = _noise(window)
    scales = [_Scale(region, margin, sigma) for sigma in params.sigmas_px]
    rows, columns = numpy.mgrid[0:2 * half + 1, 0:2 * half + 1]
    grid = (columns + left + 0.5, rows + top + 0.5)
    scale = scales[int(numpy.argmax([float(numpy.max(numpy.abs(s.ridge))) for s in scales]))]
    strongest = numpy.unravel_index(int(numpy.argmax(numpy.abs(scale.ridge))), scale.ridge.shape)
    polarity = 1.0 if scale.ridge[strongest] > 0 else -1.0
    ridge_threshold, edge_threshold = scale.thresholds(noise, params.min_contrast)
    points, normals, weights = _ridge_points(scale, polarity, ridge_threshold, grid)
    if len(points) == 0:
        raise _NoCrossing(CONTRAST)
    first, first_inliers, first_parallels = _detect_line(points, normals, weights, estimate, params)
    band = 2.0 * scale.sigma + 2.0
    e_points, e_normals, e_weights = _edge_points(scale, edge_threshold, grid)
    away = _away(points, normals, first, band, params)
    e_away = _away(e_points, e_normals, first, band, params)
    if away.sum() == 0 and e_away.sum() == 0:
        raise _NoCrossing(CONTRAST)
    if away.sum() < params.min_support_px:  # no second wire: a bare colour edge
        candidates = (e_points[e_away], e_normals[e_away], e_weights[e_away])
    else:
        candidates = (points[away], normals[away], weights[away])
    second, second_inliers, second_parallels = _detect_line(*candidates, estimate, params)
    if _angle_between(first.angle, second.angle) < params.min_angle_deg:
        raise _NoCrossing(ANGLE)
    first_points, second_points = points[first_inliers], candidates[0][second_inliers]
    detected = _crossing(first, second)
    first_clear = _clear(grid, second_parallels, scale.sigma + 2.0)  # a profile never covers a parallel line
    second_clear = _clear(grid, first_parallels, scale.sigma + 2.0)
    floor = params.min_contrast * noise
    refined_first = _refine(window, grid, first_clear, estimate, first, second, band + 2.0, first_points, scale.sigma,
                            noise, floor)
    refined_second = _refine(window, grid, second_clear, estimate, second, refined_first.curve, refined_first.band,
                             second_points, scale.sigma, noise, floor)
    refined_first = _refine(window, grid, first_clear, estimate, refined_first.curve, refined_second.curve,
                            refined_second.band, first_points, scale.sigma, noise, floor, refined_first)
    if min(refined_first.contrast, refined_second.contrast) < params.min_contrast * noise:
        raise _NoCrossing(CONTRAST)
    if _angle_between(refined_first.curve.angle, refined_second.curve.angle) < params.min_angle_deg:
        raise _NoCrossing(ANGLE)
    refined = _crossing(refined_first.curve, refined_second.curve)
    if math.hypot(refined[0] - detected[0], refined[1] - detected[1]) > _REFINE_SHIFT_PX:
        raise _NoCrossing(RESIDUAL)
    return refined


# ---- public -------------------------------------------------------------------------------------------------------

def snap_point(luma, start, params=SnapParams()) -> SnapResult:
    """The sub-pixel crossing near `start` (CV31), or the start itself with status `unsnapped` and its reason."""
    start = (float(start[0]), float(start[1]))
    rows, columns = luma.shape
    estimate = start
    iterations = 0
    try:
        if not all(math.isfinite(v) for v in start):
            raise _NoCrossing(BORDER)
        while iterations < params.max_iter:
            iterations += 1
            found = _locate(luma, estimate, params)
            if not (0.0 <= found[0] < columns and 0.0 <= found[1] < rows):
                raise _NoCrossing(BORDER)
            if math.hypot(found[0] - start[0], found[1] - start[1]) > params.radius_px:
                raise _NoCrossing(TOO_FAR)
            moved = math.hypot(found[0] - estimate[0], found[1] - estimate[1])
            estimate = found
            if moved < params.converge_px:
                return SnapResult(found, SNAPPED, None, iterations)
        raise _NoCrossing(NO_CONVERGENCE)
    except _NoCrossing as failure:
        return SnapResult(start, UNSNAPPED, failure.reason, iterations)
    except numpy.linalg.LinAlgError:
        return SnapResult(start, UNSNAPPED, RESIDUAL, iterations)


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
