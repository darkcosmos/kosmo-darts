"""Assisted click (CV31; Kosmo, Q10, D39; design D43, D45): the snap against the `board.py` truth, never against itself.

Domain (Kosmo's decision after T15m, option a): photos of 12 Mpx (4080 x 3072) and video of 1080p, so wires of at least
2 px (1600 x 1200 and larger), blur at most 0.5 px, noise at most 3 grey levels. On the domain: every snapped point
within `MAX_ERROR_PX` of the true crossing (criterion 1), at least `MIN_SNAPPED_SHARE` of the starts snapped (criterion
3), and never a wrong crossing, noise 5 included (criterion 2: a start with two crossings of the lattice within
`SNAP_RADIUS_PX` is "unsnapped" with reason `ambiguous`). Cases outside the domain are run and printed (`-s`) with no
bound on accuracy or rate; only their structure (statuses, reasons, determinism) is asserted.
"""
import math
from dataclasses import dataclass

import pytest

numpy = pytest.importorskip("numpy")
pytest.importorskip("PIL")
from PIL import Image  # noqa: E402

from kosmo_camera import board, config, geometry, snap, synth  # noqa: E402
from snap_support import inner_crossings as _inner_crossings  # noqa: E402

MAX_ERROR_PX = 0.25  # criterion 1
MIN_SNAPPED_SHARE = 0.90  # criterion 3
SEARCH = config.SNAP_RADIUS_PX
CRITERION_2_BOUND_PX = 1.0  # a converged wrong crossing is metres of wire away: any bound well above criterion 1


@dataclass(frozen=True)
class Case:
    view: synth.View
    size: tuple
    seed: int
    noise: float = 2.0
    blur: float = 0.0
    style: synth.BoardStyle = synth.BoardStyle()
    ring_wires: bool = True


DOMAIN = {
    "photo-1600": Case(synth.View(), (1600, 1200), 0),
    "photo-1600-wide-wire": Case(synth.View(yaw=5, pitch=-15, roll=-5), (1600, 1200), 8, style=synth.BoardStyle(wire_mm=2.0)),
    "photo-1600-blur-half": Case(synth.View(yaw=30, pitch=5), (1600, 1200), 9, noise=3.0, blur=0.5),
    "video-1080p": Case(synth.View(yaw=10, pitch=15), (1920, 1080), 21),
    "video-1080p-noise-3": Case(synth.View(yaw=-10, pitch=15), (1920, 1080), 22, noise=3.0),
    "noise-5": Case(synth.View(yaw=-20, pitch=-25), (1600, 1200), 10, noise=5.0),
    # Another seed, perspective, wire width and colours than any board used to tune snap.py (T1).
    "unseen-style": Case(synth.View(yaw=22, pitch=-18, roll=-12), (1600, 1200), 101,
                         style=synth.BoardStyle(wire_mm=1.1, wire=(215, 205, 170), rings=((190, 50, 40), (40, 130, 100)),
                                                singles=((25, 25, 30), (225, 205, 165)))),
}
OUTSIDE = {
    "low-1200": Case(synth.View(yaw=-15, pitch=20), (1200, 900), 6),
    "low-1200-noise-3": Case(synth.View(yaw=-15, pitch=20), (1200, 900), 6, noise=3.0),
    "blur-0.8": Case(synth.View(yaw=30, pitch=5), (1600, 1200), 9, blur=0.8),
    "blur-1.2": Case(synth.View(yaw=30, pitch=5), (1600, 1200), 9, blur=1.2),
}
SLOW = {"photo-4000": Case(synth.View(yaw=25, pitch=10), (4000, 3000), 4)}

_CACHE = {}


def _gaussian_blur(luma, sigma):
    radius = int(math.ceil(3 * sigma))
    kernel = numpy.exp(-numpy.arange(-radius, radius + 1) ** 2 / (2 * sigma * sigma))
    kernel /= kernel.sum()
    out = luma.astype(float)
    for axis in (0, 1):
        out = numpy.apply_along_axis(
            lambda v: numpy.convolve(numpy.pad(v, radius, mode="edge"), kernel, "valid"), axis, out)
    return numpy.clip(numpy.rint(out), 0, 255).astype(numpy.uint8)


def _render(case):
    """(luma, truth) of a case, cached for the session: the luma as the tool reads it (`convert("L")`)."""
    if case not in _CACHE:
        pixels, truth = synth.render_board(case.view, [], case.size, synth.Lighting(noise=case.noise), case.seed,
                                           ring_wires=case.ring_wires, style=case.style)
        luma = numpy.asarray(Image.fromarray(pixels).convert("L"))
        _CACHE[case] = (_gaussian_blur(luma, case.blur) if case.blur else luma, truth)
    return _CACHE[case]


def _starts(point):
    """Centre, then 4 starts at 4 px, 4 at 7 px and 4 at 9.5 px (the search radius is 10), each ring turned."""
    yield point
    for radius, turn in ((4.0, 0.0), (7.0, 0.25), (9.5, 0.5)):
        for k in range(4):
            angle = 2 * math.pi * (k + turn) / 4
            yield (point[0] + radius * math.cos(angle), point[1] + radius * math.sin(angle))


def _run(case):
    """(errors in px of the snapped starts, results by start) over every start of the 4 calibration points."""
    if ("run", case) not in _CACHE:
        luma, truth = _render(case)
        results = []
        for point in truth.calibration:
            for start in _starts(point):
                results.append((point, start, snap.snap_point(luma, start)))
        errors = [math.dist(result.point, point) for point, _, result in results if result.status == snap.SNAPPED]
        _CACHE[("run", case)] = (errors, results)
    return _CACHE[("run", case)]


def _report(name, case, errors, results):
    scale = _pixels_per_mm(_render(case)[1])
    median, worst = (float(numpy.median(errors)), max(errors)) if errors else (math.nan, math.nan)
    print(f"{name}: snapped {len(errors)}/{len(results)} median {median:.3f} px max {worst:.3f} px "
          f"({median / scale:.3f} / {worst / scale:.3f} mm at {scale:.2f} px/mm)")


def _pixels_per_mm(truth):
    centre, edge = synth.project(truth.homography, [(0.0, 0.0), (board.DOUBLE_OUTER_MM, 0.0)])
    return math.dist(centre, edge) / board.DOUBLE_OUTER_MM


# ---- criteria 1 and 3, on the domain ------------------------------------------------------------------------------

@pytest.mark.parametrize("name", [n for n in DOMAIN if n != "noise-5"])
def test_cv31_snapped_points_are_within_a_quarter_pixel_of_the_truth(name):
    errors, results = _run(DOMAIN[name])
    _report(name, DOMAIN[name], errors, results)
    assert errors
    assert max(errors) <= MAX_ERROR_PX


@pytest.mark.parametrize("name", [n for n in DOMAIN if n != "noise-5"])
def test_cv31_at_least_nine_starts_in_ten_snap(name):
    errors, results = _run(DOMAIN[name])
    assert len(errors) >= MIN_SNAPPED_SHARE * len(results)


def test_cv31_unsnapped_starts_stay_at_the_click_with_a_known_reason():
    _, results = _run(DOMAIN["photo-1600"])
    for _, start, result in results:
        if result.status == snap.UNSNAPPED:
            assert result.point == start
            assert result.reason in snap.REASONS
        else:
            assert result.status == snap.SNAPPED and result.reason is None


# ---- criterion 2: never a wrong crossing, noise 5 included --------------------------------------------------------

@pytest.mark.parametrize("name", list(DOMAIN))
def test_cv31_no_start_ends_on_a_wrong_crossing_on_the_domain(name):
    errors, results = _run(DOMAIN[name])
    _report(name, DOMAIN[name], errors, results)
    assert all(error <= CRITERION_2_BOUND_PX for error in errors)
    if name == "noise-5":  # the share of criterion 3 is not asked at noise 5, only that no wrong crossing is accepted
        assert errors


def test_cv31_regression_noise_5_start_that_ended_17_px_away_is_refused():
    luma, truth = _render(DOMAIN["noise-5"])
    point = truth.calibration[1]
    start = (point[0] - 6.7, point[1] + 6.7)  # 9.5 px from the crossing; it used to converge 17.07 px away
    result = snap.snap_point(luma, start)
    assert (result.status, result.reason, result.point) == (snap.UNSNAPPED, snap.AMBIGUOUS, start)


def test_cv31_a_start_between_two_crossings_is_ambiguous_and_stays_at_the_click():
    luma, truth = _render(DOMAIN["photo-1600"])
    tested = 0
    for outer, inner in zip(truth.calibration, _inner_crossings(truth)):
        middle = ((outer[0] + inner[0]) / 2, (outer[1] + inner[1]) / 2)
        if math.dist(middle, outer) >= SEARCH - 1.0:  # foreshortened less: the midpoint is out of range of both
            continue
        tested += 1
        result = snap.snap_point(luma, middle)
        assert (result.status, result.reason, result.point) == (snap.UNSNAPPED, snap.AMBIGUOUS, middle)
    assert tested >= 2


def test_cv31_a_start_with_only_one_crossing_in_range_snaps_to_it_even_beside_a_neighbour():
    luma, truth = _render(DOMAIN["photo-1600"])
    for outer, inner in zip(truth.calibration, _inner_crossings(truth)):
        gap = math.dist(outer, inner)
        # 9.5 px from `outer` away from `inner`: `inner` is gap + 9.5 px away, out of range
        start = (outer[0] + (outer[0] - inner[0]) / gap * 9.5, outer[1] + (outer[1] - inner[1]) / gap * 9.5)
        assert math.dist(start, inner) > SEARCH
        result = snap.snap_point(luma, start)
        assert result.status == snap.SNAPPED
        assert math.dist(result.point, outer) <= MAX_ERROR_PX
        # and the other way: close to `inner` (outer out of range) it snaps to the inner crossing, the nearest
        start = (inner[0] + (inner[0] - outer[0]) / gap * 4.0, inner[1] + (inner[1] - outer[1]) / gap * 4.0)
        result = snap.snap_point(luma, start)
        assert result.status == snap.SNAPPED
        assert math.dist(result.point, inner) <= MAX_ERROR_PX


@pytest.mark.parametrize("name", list(OUTSIDE))
def test_cv31_outside_the_domain_the_structure_holds_and_the_numbers_are_reported(name):
    errors, results = _run(OUTSIDE[name])
    _report(name, OUTSIDE[name], errors, results)
    for _, start, result in results:
        assert result.status in (snap.SNAPPED, snap.UNSNAPPED)
        if result.status == snap.UNSNAPPED:
            assert result.point == start and result.reason in snap.REASONS


# ---- reasons -------------------------------------------------------------------------------------------------------

def _plain_sector_point(truth):
    """A point in the middle of a plain single sector, away from every wire."""
    middle_angle = math.radians(board.SECTOR_ANGLE_DEGREES * 4)  # between two radial wires, rotated off the first wire
    radius = (board.TRIPLE_OUTER_MM + board.DOUBLE_INNER_MM) / 2
    return synth.project(truth.homography, [(radius * math.cos(middle_angle), radius * math.sin(middle_angle))])[0]


def test_cv31_a_plain_sector_ends_unsnapped_with_reason_contrast():
    luma, truth = _render(DOMAIN["photo-1600"])
    point = _plain_sector_point(truth)
    result = snap.snap_point(luma, point)
    assert (result.status, result.point, result.reason) == (snap.UNSNAPPED, point, snap.CONTRAST)


def test_cv31_a_flat_dark_area_is_unsnapped_with_contrast():
    luma = numpy.full((200, 200), 40, dtype=numpy.uint8)
    result = snap.snap_point(luma, (100.0, 100.0))
    assert (result.status, result.reason, result.point) == (snap.UNSNAPPED, snap.CONTRAST, (100.0, 100.0))


def test_cv31_a_single_wire_is_not_a_crossing():
    luma = numpy.full((200, 200), 40, dtype=numpy.uint8)
    luma[:, 99:101] = 220  # one vertical wire, no second line
    result = snap.snap_point(luma, (100.0, 100.0))
    assert (result.status, result.reason, result.point) == (snap.UNSNAPPED, snap.CONTRAST, (100.0, 100.0))


def test_cv31_a_start_near_the_image_border_is_unsnapped_with_border():
    luma, truth = _render(DOMAIN["photo-1600"])
    for start in ((5.0, 600.0), (800.0, 3.0), (1597.0, 600.0), (800.0, 1197.0)):
        result = snap.snap_point(luma, start)
        assert (result.status, result.reason, result.point) == (snap.UNSNAPPED, snap.BORDER, start)


def test_cv31_a_non_finite_start_is_unsnapped_with_border():
    luma, _ = _render(DOMAIN["photo-1600"])
    result = snap.snap_point(luma, (math.nan, 3.0))
    assert (result.status, result.reason) == (snap.UNSNAPPED, snap.BORDER)


def test_cv31_a_start_farther_than_the_radius_is_never_snapped_wrongly():
    luma, truth = _render(DOMAIN["photo-1600"])
    point = truth.calibration[0]
    for offset in (12.0, 15.0, 30.0):
        start = (point[0] + offset, point[1])
        result = snap.snap_point(luma, start)
        if result.status == snap.SNAPPED:
            assert math.dist(result.point, start) <= SEARCH
        else:
            assert result.point == start and result.reason in snap.REASONS


def test_cv31_too_far_when_the_crossing_lies_beyond_the_search_radius():
    luma, truth = _render(DOMAIN["photo-1600"])
    point = truth.calibration[0]
    start = (point[0] + SEARCH + 1.0, point[1])  # the crossing is 11 px away: seen by the coarse stage, then refused
    result = snap.snap_point(luma, start)
    assert (result.status, result.reason, result.point) == (snap.UNSNAPPED, snap.TOO_FAR, start)


def test_cv31_no_convergence_when_the_estimate_keeps_moving_within_the_iteration_cap():
    luma, truth = _render(DOMAIN["photo-1600"])
    point = truth.calibration[0]
    start = (point[0] + 4.0, point[1] + 1.0)
    strict = snap.SnapParams(max_iter=1, converge_px=1e-12)  # one window and a step that can never be small enough
    result = snap.snap_point(luma, start, strict)
    assert (result.status, result.reason, result.point, result.iterations) == (snap.UNSNAPPED, snap.NO_CONVERGENCE,
                                                                               start, 1)


def test_cv31_residual_when_the_profile_fit_is_not_finite(monkeypatch):
    luma, truth = _render(DOMAIN["photo-1600"])
    point = truth.calibration[0]
    real = snap._fit_profile

    def broken(*args, **kwargs):  # a degenerate fit: a NaN parameter, as a singular design matrix would give
        q, levels, cost = real(*args, **kwargs)
        q = q.copy()
        q[0] = math.nan
        return q, levels, cost

    monkeypatch.setattr(snap, "_fit_profile", broken)
    start = (point[0] + 3.0, point[1] + 1.0)
    result = snap.snap_point(luma, start)
    assert (result.status, result.reason, result.point) == (snap.UNSNAPPED, snap.RESIDUAL, start)


def test_cv31_the_reasons_are_the_documented_ones():
    assert set(snap.REASONS) == {"support", "contrast", "residual", "angle", "no_convergence", "too_far", "border",
                                 "ambiguous"}


# ---- repeatability D -----------------------------------------------------------------------------------------------

def _board_from_image(truth):
    return geometry.board_from_image(truth.calibration)


def test_cv31_restarts_on_a_clean_crossing_give_a_dispersion_under_five_hundredths_of_a_mm():
    luma, truth = _render(DOMAIN["photo-1600"])
    homography = _board_from_image(truth)
    for point in truth.calibration:
        snapped = snap.snap_point(luma, (point[0] + 2.0, point[1] - 1.0))
        assert snapped.status == snap.SNAPPED
        assert snap.restart_dispersion(luma, snapped.point, homography) < 0.05


def test_cv31_a_point_between_two_crossings_has_a_dispersion_above_the_target():
    luma, truth = _render(DOMAIN["photo-1600"])
    homography = _board_from_image(truth)
    outer, inner = truth.calibration[0], _inner_crossings(truth)[0]
    middle = ((outer[0] + inner[0]) / 2, (outer[1] + inner[1]) / 2)
    assert snap.restart_dispersion(luma, middle, homography) > config.SNAP_REPEAT_MAX_MM


def test_cv31_a_failed_restart_makes_the_dispersion_infinite():
    luma, truth = _render(DOMAIN["photo-1600"])
    homography = _board_from_image(truth)
    assert snap.restart_dispersion(luma, _plain_sector_point(truth), homography) == math.inf
    assert snap.restart_dispersion(luma, (5.0, 600.0), homography) == math.inf


# ---- diagnosis: stages and descriptors (CV32, T17a) ----------------------------------------------------------------

@pytest.mark.parametrize("name", list(DOMAIN) + list(OUTSIDE))
def test_cv32_snap_detail_gives_the_result_of_snap_point_and_a_stage_exactly_when_unsnapped(name):
    luma, truth = _render((DOMAIN | OUTSIDE)[name])
    _, results = _run((DOMAIN | OUTSIDE)[name])
    for point, start, result in results[:13]:  # every start of the first calibration point
        detail = snap.snap_detail(luma, start)
        assert detail.result == result
        assert (detail.stage is None) == (result.status == snap.SNAPPED)
        assert detail.stage is None or detail.stage in snap.STAGES


def test_cv32_the_stages_are_the_documented_ones():
    assert set(snap.STAGES) == {"coarse_no_ridge", "coarse_no_pair", "coarse_not_reaching", "coarse_too_far",
                                "fine_support", "fine_angle", "fine_contrast", "fine_blur", "stands_out",
                                "refine_shift", "fit_failure", "too_far", "border", "ambiguous", "no_convergence"}
    assert len(snap.STAGES) == len(set(snap.STAGES))


def _bar(luma, centre, angle_deg, width, level=215, half_length=60):
    """A bright straight bar (a dart shaft) of `width` px through `centre`, drawn over the board."""
    x0, y0 = int(centre[0]) - 80, int(centre[1]) - 80
    out = luma.astype(float)
    yy, xx = numpy.mgrid[y0:y0 + 160, x0:x0 + 160] + 0.5
    t = math.radians(angle_deg)
    across = -(xx - centre[0]) * math.sin(t) + (yy - centre[1]) * math.cos(t)
    along = (xx - centre[0]) * math.cos(t) + (yy - centre[1]) * math.sin(t)
    mask = numpy.clip(width / 2 + 0.5 - numpy.abs(across), 0, 1) * (numpy.abs(along) <= half_length)
    out[y0:y0 + 160, x0:x0 + 160] = out[y0:y0 + 160, x0:x0 + 160] * (1 - mask) + level * mask
    return numpy.clip(numpy.rint(out), 0, 255).astype(numpy.uint8)


def _mid_ring_point(truth):
    """A point of the outer double wire away from the radial wires (no true crossing there)."""
    angle = math.radians(board.SECTOR_ANGLE_DEGREES / 4)
    return synth.project(truth.homography, [(board.DOUBLE_OUTER_MM * math.cos(angle),
                                             board.DOUBLE_OUTER_MM * math.sin(angle))])[0]


def _stage_cases():
    """(stage, luma, start, params) of scripted boards, one per stage that a board can reach (fit_failure aside)."""
    luma, truth = _render(DOMAIN["photo-1600"])
    p, q = truth.calibration[0], truth.calibration[1]
    flat = numpy.full((200, 200), 40, dtype=numpy.uint8)
    single = flat.copy()
    single[:, 99:101] = 220  # one wire, no ring wire
    oblique, oblique_truth = _render(Case(synth.View(yaw=75, pitch=10), (1600, 1200), 0))
    dull, dull_truth = _render(Case(synth.View(), (1600, 1200), 0, style=synth.BoardStyle(wire=(70, 70, 70))))
    outer, inner = p, _inner_crossings(truth)[0]
    return [
        ("coarse_no_ridge", flat, (100.0, 100.0), snap.SnapParams()),
        ("coarse_no_ridge", single, (100.0, 100.0), snap.SnapParams()),
        ("coarse_no_pair", _bar(luma, _mid_ring_point(truth), 60, 6), _mid_ring_point(truth), snap.SnapParams()),
        ("coarse_not_reaching", oblique, oblique_truth.calibration[3], snap.SnapParams()),
        ("coarse_too_far", luma, (p[0] + 15.0, p[1]), snap.SnapParams()),  # crossing beyond the radius
        ("fine_support", _bar(luma, p, 45, 3), p, snap.SnapParams()),  # a dart shaft over the crossing
        ("fine_angle", _bar(luma, p, 20, 3), (p[0] + 5.0, p[1] + 5.0), snap.SnapParams()),
        ("fine_contrast", dull, list(_starts(dull_truth.calibration[1]))[9], snap.SnapParams()),
        ("fine_blur", _gaussian_blur(luma, 2.0), (p[0] - 4.0, p[1]), snap.SnapParams()),  # blurred wire
        ("stands_out", _bar(luma, q, 20, 4), list(_starts(q))[6], snap.SnapParams()),
        ("refine_shift", _bar(luma, p, 45, 6), (p[0], p[1] - 7.0), snap.SnapParams()),
        ("too_far", luma, (p[0] + SEARCH + 1.0, p[1]), snap.SnapParams()),
        ("border", luma, (5.0, 600.0), snap.SnapParams()),
        ("ambiguous", luma, ((outer[0] + inner[0]) / 2, (outer[1] + inner[1]) / 2), snap.SnapParams()),  # two crossings
        ("no_convergence", luma, (p[0] + 4.0, p[1] + 1.0), snap.SnapParams(max_iter=1, converge_px=1e-12)),
    ]


def test_cv32_each_stage_is_reached_by_a_scripted_board(monkeypatch):
    reached = set()
    for stage, luma, start, params in _stage_cases():
        detail = snap.snap_detail(luma, start, params)
        assert detail.stage == stage, (stage, detail)
        assert detail.result == snap.snap_point(luma, start, params)
        assert detail.result.point == start and detail.result.status == snap.UNSNAPPED
        reached.add(stage)
    luma, truth = _render(DOMAIN["photo-1600"])
    real = snap._fit_profile

    def broken(*args, **kwargs):  # a NaN parameter, as a singular design matrix would give
        q, levels, cost = real(*args, **kwargs)
        q = q.copy()
        q[0] = math.nan
        return q, levels, cost

    monkeypatch.setattr(snap, "_fit_profile", broken)
    point = truth.calibration[0]
    assert snap.snap_detail(luma, (point[0] + 3.0, point[1] + 1.0)).stage == "fit_failure"
    assert reached | {"fit_failure"} == set(snap.STAGES)


def test_cv32_t_junction_descriptor_tells_a_radial_wire_ending_at_the_ring_from_one_crossing_it():
    luma, truth = _render(DOMAIN["photo-1600"])  # the radial wires stop at the outer double wire (synth.py)
    for outer in truth.calibration:
        detail = snap.snap_detail(luma, (outer[0] + 2.0, outer[1] - 1.0))
        assert detail.stage is None and detail.descriptors.stem_px == 0.0
    for inner in _inner_crossings(truth):
        detail = snap.snap_detail(luma, (inner[0] + 2.0, inner[1] - 1.0))
        assert detail.stage is None and detail.descriptors.stem_px > 5.0
        descriptors = detail.descriptors
        assert descriptors.bright_wires is True and descriptors.contrast_to_noise > config.SNAP_MIN_CONTRAST
        assert descriptors.width_px > 0 and descriptors.blur_px >= 0 and descriptors.halo >= 0
        assert descriptors.noise > 0


# ---- determinism ---------------------------------------------------------------------------------------------------

def test_cv31_same_input_gives_the_same_result_bit_for_bit():
    luma, truth = _render(DOMAIN["noise-5"])
    for point in truth.calibration[:2]:
        for start in list(_starts(point))[:5]:
            assert snap.snap_point(luma, start) == snap.snap_point(luma.copy(), start)


# ---- slow: 12 Mpx --------------------------------------------------------------------------------------------------

@pytest.mark.slow
def test_cv31_twelve_megapixel_photo_meets_the_domain_criteria_and_one_snap_stays_below_a_second():
    import time
    case = SLOW["photo-4000"]
    luma, truth = _render(case)
    started = time.perf_counter()
    snap.snap_point(luma, truth.calibration[0])
    assert time.perf_counter() - started < 1.0  # generous: the measured time is about 0.1 s, it never depends on size
    errors, results = _run(case)
    _report("photo-4000", case, errors, results)
    assert max(errors) <= MAX_ERROR_PX and len(errors) >= MIN_SNAPPED_SHARE * len(results)
