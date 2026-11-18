"""Snapping diagnosis on Kosmo's stored clicks (CV32, D49, D54; T17a): why the snap refuses real photos and frames.

`python -m kosmo_camera.snap_report --captures DIR --annotations DIR --split FILE --frames DIR --out DIR [--lock FILE]`
reruns `snap.snap_detail` from every stored point of the controls (photos and the video frames listed in
`<frames>/frames.json`) and of the calibration segments, the stored point as start (the click for `unsnapped`, the
snapped point for `snapped`, D39), with the radius at each of `SNAP_SWEEP_FACTORS` times `SNAP_RADIUS_PX`. It writes
`<out>/snap-report.md` and prints its path, the run time, the median time per snap and the planned duration of K7.6.

Read-only on Kosmo's data. The report holds numbers and roles (`dev 1`, `eval 2`) only, never a session or file name
nor a path (CV15), and every section is split by split (dev, eval) and by source (D54). numpy and Pillow are imported
only when the snaps run (through `snap` and `luma`): no PyAV, the WSL tool venv runs it (D44).
"""
import argparse
import math
import sys
import time
from collections import Counter, defaultdict
from dataclasses import dataclass, fields
from pathlib import Path

from kosmo_camera import annotations as ann, config, control, frames_file as ff, split as split_module
from kosmo_camera.truth import session_folders

REPORT_NAME = "snap-report.md"
DEFAULT_LOCK = Path(__file__).resolve().parent.parent / "config" / "split-lock.json"
UNREADABLE = "unreadable"  # stage of a point whose image is missing, not listed, undecodable or of another size

# Kosmo's refusal categories (CV32, root plan "Approach, Snapping on real data"), then the snapped points.
NO_CROSSING = "pas de croisement"
SHAPE = "forme"
AMBIGUITY = "ambiguïté"
OUT_OF_RADIUS = "hors rayon"
OTHER = "autre"
SNAPPED = "recalé"
CATEGORIES = (NO_CROSSING, SHAPE, AMBIGUITY, OUT_OF_RADIUS, OTHER)
# Every `snap.STAGES` value and UNREADABLE, to exactly one category (checked over the enum by the tests).
CATEGORY_OF_STAGE = {
    "coarse_no_ridge": NO_CROSSING,
    "coarse_no_pair": NO_CROSSING,
    "fine_support": NO_CROSSING,
    "fine_contrast": NO_CROSSING,
    "coarse_not_reaching": SHAPE,
    "fine_angle": SHAPE,
    "fine_blur": SHAPE,
    "stands_out": SHAPE,
    "refine_shift": SHAPE,
    "ambiguous": AMBIGUITY,
    "coarse_too_far": OUT_OF_RADIUS,
    "too_far": OUT_OF_RADIUS,
    "border": OTHER,
    "no_convergence": OTHER,
    "fit_failure": OTHER,
    UNREADABLE: OTHER,
}

DEV, EVAL = split_module.SETS
PHOTO_LABEL = "photo"
VIDEO_LABEL = "vidéo"
CONTROL_KIND = "contrôle"
SEGMENT_KIND = "segment"
# Report columns: every split, source and kind that can hold a point (a segment is always on a photo).
GROUPS = tuple((s, source, kind) for s in (DEV, EVAL)
               for source, kind in ((PHOTO_LABEL, CONTROL_KIND), (PHOTO_LABEL, SEGMENT_KIND),
                                    (VIDEO_LABEL, CONTROL_KIND)))
_T_STEM_PX = 1.0  # a stem shorter than this is a T junction: the radial wire stops at the ring
_clock = time.perf_counter  # injectable by the tests


@dataclass(frozen=True)
class _Point:
    set_name: str
    role: str  # `dev 1`...; the session name never leaves `collect`
    source: str  # PHOTO_LABEL or VIDEO_LABEL
    kind: str  # CONTROL_KIND or SEGMENT_KIND
    image: object  # Path of the image, or None when no image is listed for it
    image_key: object  # one value per image: its path, or (session, dart) for an unlisted video frame
    size: tuple  # (width, height) stored with the point
    start: tuple  # the stored point
    stored: str  # stored status: snapped, unsnapped or manual

    @property
    def group(self):
        return (self.set_name, self.source, self.kind)


@dataclass
class _Sample:
    """What `collect` found: the points to snap and the counts of what has none."""
    points: list
    sessions: Counter  # set name to sessions of the split found in the captures
    without_annotations: Counter
    invalid_annotations: Counter
    without_controls: Counter
    hidden: Counter  # group to hidden (null) control points
    not_listed: Counter  # group to video control points without a listed frame
    proposals: dict  # group to the distances between consecutive controls' points
    frames_state: str  # « présent », « absent » or « invalide »
    frames_dir: Path  # where the listed video frames are


@dataclass(frozen=True)
class _Outcome:
    point: _Point
    stage: object  # stage at 1x, None when snapped
    descriptors: object  # snap.SnapDescriptors at 1x, None when unreadable
    snapped_at: tuple  # one bool per sweep factor


def _frames(frames_dir):
    path = Path(frames_dir) / "frames.json"
    if not path.is_file():
        return {}, "absent"
    try:
        manifest = ff.read(path)
    except ff.FramesFileError:
        return {}, "invalide"
    return {s.session: {f.dart: f for f in s.frames} for s in manifest.sessions}, "présent"


def _proposal_distances(controls):
    """Distance between each point of a control and the same point of the previous control of that source."""
    distances = []
    for previous, current in zip(controls, controls[1:]):
        for a, b in zip(previous.points, current.points):
            if a is not None and b is not None:
                distances.append(math.hypot(a[0] - b[0], a[1] - b[1]))
    return distances


def _control_points(name, folder, role, annotations, frames, sample):
    for control_item in annotations.controls:
        photo = control_item.source == ann.PHOTO
        source = PHOTO_LABEL if photo else VIDEO_LABEL
        group = (role.set_name, source, CONTROL_KIND)
        if photo:
            image = folder / ann.photo_name(control_item.dart)
        else:
            frame = frames.get(name, {}).get(control_item.dart)
            listed = frame is not None and frame.position_ms == control_item.position_ms
            image = sample.frames_dir / name / frame.file if listed else None
        key = image if image is not None else (name, control_item.dart)  # an unlisted frame is still one image
        for point, status in zip(control_item.points, control_item.snap):
            if point is None:
                sample.hidden[group] += 1
                continue
            if image is None:
                sample.not_listed[group] += 1
            sample.points.append(_Point(role.set_name, role.label, source, CONTROL_KIND, image, key,
                                        (control_item.width, control_item.height), point, status))
    for source_name, label in ((ann.PHOTO, PHOTO_LABEL), (ann.VIDEO, VIDEO_LABEL)):
        same = [c for c in annotations.controls if c.source == source_name]
        sample.proposals[(role.set_name, label, CONTROL_KIND)].extend(_proposal_distances(same))


def _segment_points(folder, role, annotations, sample):
    for segment in annotations.calibrations:
        for point, status in zip(segment.points, segment.snap):
            image = folder / segment.photo
            sample.points.append(_Point(role.set_name, role.label, PHOTO_LABEL, SEGMENT_KIND, image, image,
                                        (segment.width, segment.height), point, status))


def collect(captures, annotations_dir, split, frames_dir) -> _Sample:
    """Every stored point of the sessions of the split, in session list order; reads each file at most once, capped."""
    roles = control.roles(split)
    frames, frames_state = _frames(frames_dir)
    sample = _Sample([], Counter(), Counter(), Counter(), Counter(), Counter(), Counter(), defaultdict(list),
                     frames_state, Path(frames_dir))
    for name, folder in session_folders(captures).items():
        role = roles.get(name)
        if role is None:
            continue  # not in the split: an ignored session has no control
        sample.sessions[role.set_name] += 1
        path = Path(annotations_dir) / f"{name}.json"
        if not path.is_file():
            sample.without_annotations[role.set_name] += 1
            continue
        try:
            annotations = ann.read(path)
        except ann.AnnotationError:
            sample.invalid_annotations[role.set_name] += 1
            continue
        if not annotations.controls:
            sample.without_controls[role.set_name] += 1
        _control_points(name, folder, role, annotations, frames, sample)
        _segment_points(folder, role, annotations, sample)
    return sample


def _timed(snap, luma, start, params, times):
    begin = _clock()
    detail = snap.snap_detail(luma, start, params)
    times.append(_clock() - begin)
    return detail


def run_snaps(points, times):
    """`_Outcome` of every point, image by image (one decoded image in memory); appends each snap's time to `times`."""
    from kosmo_camera import luma as luma_module, snap  # numpy and Pillow, only when the snaps run

    factors = tuple(config.SNAP_SWEEP_FACTORS)
    params = [snap.SnapParams(radius_px=factor * config.SNAP_RADIUS_PX) for factor in factors]
    main = factors.index(1.0)
    by_image = defaultdict(list)
    for point in points:
        by_image[(point.image_key, point.size)].append(point)
    outcomes = []
    unreadable = Counter()
    for (_, size), group in by_image.items():
        luma = None
        if group[0].image is not None:
            luma, _ = luma_module.decode_luma(group[0].image, size)
        if luma is None:
            unreadable.update({p.group for p in group})
            outcomes.extend(_Outcome(p, UNREADABLE, None, (False,) * len(factors)) for p in group)
            continue
        for point in group:
            details = [_timed(snap, luma, point.start, p, times) for p in params]
            outcomes.append(_Outcome(point, details[main].stage, details[main].descriptors,
                                     tuple(d.stage is None for d in details)))
        del luma
    return outcomes, unreadable


# ---- report ----------------------------------------------------------------------------------------------------------


def _quantile(values, q):
    ordered = sorted(values)
    position = q * (len(ordered) - 1)
    low = math.floor(position)
    high = min(low + 1, len(ordered) - 1)
    return ordered[low] + (ordered[high] - ordered[low]) * (position - low)


def _share(part, whole):
    return "-" if whole == 0 else f"{100.0 * part / whole:.1f} %"


def _group_label(group):
    return " · ".join(group)


def _row(label, by_group, value=str):
    """A table row: one cell per group, then dev, eval and total, the sums of the group cells."""
    cells = [by_group.get(g, 0) for g in GROUPS]
    dev = sum(c for g, c in zip(GROUPS, cells) if g[0] == DEV)
    evaluation = sum(c for g, c in zip(GROUPS, cells) if g[0] == EVAL)
    return "| " + " | ".join([label] + [value(c) for c in cells + [dev, evaluation, dev + evaluation]]) + " |"


def _header(first):
    labels = [first] + [_group_label(g) for g in GROUPS] + [DEV, EVAL, "total"]
    return ["| " + " | ".join(labels) + " |", "|" + "---|" * len(labels)]


def _category(outcome):
    return SNAPPED if outcome.stage is None else CATEGORY_OF_STAGE[outcome.stage]


def _sample_section(sample, outcomes, unreadable_images):
    lines = ["## Échantillon", "", f"Manifeste des images vidéo : {sample.frames_state}", "",
             "| sessions | dev | eval | total |", "|---|---|---|---|"]
    for label, counts in (("sessions du split", sample.sessions), ("sans annotations", sample.without_annotations),
                          ("annotations invalides", sample.invalid_annotations),
                          ("sans contrôle", sample.without_controls)):
        lines.append(f"| {label} | {counts[DEV]} | {counts[EVAL]} | {counts[DEV] + counts[EVAL]} |")
    images = Counter(group for group, _, _ in {(o.point.group, o.point.image_key, o.point.size) for o in outcomes})
    points = Counter(o.point.group for o in outcomes)
    unread_points = Counter(o.point.group for o in outcomes if o.stage == UNREADABLE)
    lines += [""] + _header("")
    lines.append(_row("images", images))
    lines.append(_row("images illisibles ou absentes", unreadable_images))
    lines.append(_row("points enregistrés", points))
    lines.append(_row("points masqués (comptés à part)", sample.hidden))
    lines.append(_row("points sans image vidéo listée", sample.not_listed))
    lines.append(_row("points illisibles", unread_points))
    for status in (ann.SNAPPED, ann.UNSNAPPED, ann.MANUAL):
        lines.append(_row(f"statut enregistré {status}", Counter(o.point.group for o in outcomes
                                                                  if o.point.stored == status)))
    return lines


def _causes_section(outcomes):
    lines = ["## Causes de refus", "", "Rayon 1x, depuis le point enregistré.", ""] + _header("cause")
    lines.append(_row(SNAPPED, Counter(o.point.group for o in outcomes if o.stage is None)))
    stages = sorted(CATEGORY_OF_STAGE)
    for category in CATEGORIES:
        lines.append(_row(f"**{category}**", Counter(o.point.group for o in outcomes if _category(o) == category)))
        for stage in stages:
            if CATEGORY_OF_STAGE[stage] == category:
                lines.append(_row(f"  {stage}", Counter(o.point.group for o in outcomes if o.stage == stage)))
    return lines


def _roles_section(outcomes):
    columns = (SNAPPED,) + CATEGORIES
    lines = ["## Par rôle", "", "| rôle | source | type | points | " + " | ".join(columns) + " |",
             "|" + "---|" * (4 + len(columns))]
    by_role = defaultdict(list)
    for outcome in outcomes:
        point = outcome.point
        by_role[(point.set_name, int(point.role.split()[1]), point.source, point.kind)].append(outcome)
    for key in sorted(by_role):
        group = by_role[key]
        counts = Counter(_category(o) for o in group)
        cells = [f"{key[0]} {key[1]}", key[2], key[3], str(len(group))] + [str(counts[c]) for c in columns]
        lines.append("| " + " | ".join(cells) + " |")
    return lines


def _descriptor_cell(values):
    if not values:
        return "-"
    return f"{_quantile(values, 0.5):.2f} [{_quantile(values, 0.1):.2f}–{_quantile(values, 0.9):.2f}] n={len(values)}"


def _descriptors_section(outcomes, snap_fields):
    lines = ["## Descripteurs", "", "Médiane [p10–p90] et effectif, au rayon 1x ; stem_px = bras le plus court des deux "
             "lignes au-delà du croisement (jonction en T : 0).", "",
             "| groupe | catégorie | " + " | ".join(snap_fields) + " | part en T | part fils clairs |",
             "|" + "---|" * (4 + len(snap_fields))]
    for group in GROUPS:
        for category in (SNAPPED,) + CATEGORIES:
            chosen = [o.descriptors for o in outcomes
                      if o.point.group == group and o.descriptors is not None and _category(o) == category]
            if not chosen:
                continue
            cells = [_descriptor_cell([float(getattr(d, f)) for d in chosen
                                       if getattr(d, f) is not None]) for f in snap_fields]
            stems = [d.stem_px for d in chosen if d.stem_px is not None]
            bright = [d.bright_wires for d in chosen if d.bright_wires is not None]
            cells += [_share(sum(s < _T_STEM_PX for s in stems), len(stems)), _share(sum(bright), len(bright))]
            lines.append("| " + " | ".join([_group_label(group), category] + cells) + " |")
    return lines


def _radius_section(outcomes):
    factors = tuple(config.SNAP_SWEEP_FACTORS)
    readable = [o for o in outcomes if o.stage != UNREADABLE]
    lines = ["## Rayon", "", f"Rayon de base : {config.SNAP_RADIUS_PX:g} px ; points lisibles seulement.", ""]
    lines += _header("")
    totals = Counter(o.point.group for o in readable)
    lines.append(_row("points lisibles", totals))
    for index, factor in enumerate(factors):
        snapped = Counter(o.point.group for o in readable if o.snapped_at[index])
        lines.append(_row(f"recalés à {factor:g}x", snapped))
    lines += ["", "| groupe | " + " | ".join(f"part recalée à {f:g}x" for f in factors) + " |",
              "|" + "---|" * (1 + len(factors))]
    for group in GROUPS:
        group_points = [o for o in readable if o.point.group == group]
        shares = [_share(sum(o.snapped_at[i] for o in group_points), len(group_points)) for i in range(len(factors))]
        lines.append("| " + " | ".join([_group_label(group)] + shares) + " |")
    return lines


def _proposals_section(sample):
    radius = config.SNAP_RADIUS_PX
    lines = ["## Propositions", "", "Distance entre un point d'un contrôle et le même point du contrôle précédent de la "
             "même session et de la même source (px).", "",
             "| groupe | paires | médiane | p90 | au-delà du rayon |", "|---|---|---|---|---|"]
    for group in GROUPS:
        if group[2] != CONTROL_KIND:
            continue
        values = sample.proposals.get(group, [])
        if values:
            cells = [str(len(values)), f"{_quantile(values, 0.5):.1f}", f"{_quantile(values, 0.9):.1f}",
                     _share(sum(v > radius for v in values), len(values))]
        else:
            cells = ["0", "-", "-", "-"]
        lines.append("| " + " | ".join([_group_label(group)] + cells) + " |")
    return lines


def planned_minutes(outcomes, median_s):
    """CV32 planned duration of K7.6 in whole minutes (rounded up): stored points x starts per point x median snap.

    Starts per point: the snaps this run made per point (one per sweep factor), plus `SNAP_REPEAT_DIRECTIONS`, plus
    `SNAP_LATTICE_STARTS` for a photo control point (its share of the lattice corners of its control)."""
    per_point = len(config.SNAP_SWEEP_FACTORS) + config.SNAP_REPEAT_DIRECTIONS
    starts = sum(per_point + (config.SNAP_LATTICE_STARTS if _is_photo_control(o.point) else 0) for o in outcomes)
    return math.ceil(starts * median_s / 60.0 - 1e-9)


def _is_photo_control(point):
    return point.source == PHOTO_LABEL and point.kind == CONTROL_KIND


def planned_line(outcomes, times) -> str:
    if not times:
        return "Durée prévue de K7.6 : inconnue (aucun recalage mesuré)"
    return f"Durée prévue de K7.6 : environ {planned_minutes(outcomes, _quantile(times, 0.5))} min"


def _durations_section(outcomes, times, run_s):
    median = "-" if not times else f"{1000.0 * _quantile(times, 0.5):.1f} ms"
    return ["## Durées", "", f"Durée du calcul : {run_s:.1f} s", f"Recalages exécutés : {len(times)}",
            f"Temps médian par recalage : {median}", planned_line(outcomes, times)]


def render(sample, outcomes, unreadable_images, times, run_s) -> str:
    from kosmo_camera import snap  # the descriptor names

    snap_fields = [f.name for f in fields(snap.SnapDescriptors) if f.name != "bright_wires"]
    sections = [["# Diagnostic du recalage (CV32)", "",
                 "Chiffres et rôles seulement ; dev et eval séparés, par source (D54)."],
                _sample_section(sample, outcomes, unreadable_images), _causes_section(outcomes),
                _roles_section(outcomes), _descriptors_section(outcomes, snap_fields), _radius_section(outcomes),
                _proposals_section(sample), _durations_section(outcomes, times, run_s)]
    return "\n\n".join("\n".join(section) for section in sections) + "\n"


def report(captures, annotations_dir, split, frames_dir) -> tuple:
    """(report text, timings of the snaps, run time in s, the outcomes)."""
    begin = _clock()
    sample = collect(captures, annotations_dir, split, frames_dir)
    times = []
    outcomes, unreadable_images = run_snaps(sample.points, times)
    run_s = _clock() - begin
    return render(sample, outcomes, unreadable_images, times, run_s), times, run_s, outcomes


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(prog="python -m kosmo_camera.snap_report", description=__doc__.splitlines()[0])
    for name in ("captures", "annotations", "frames", "out"):
        parser.add_argument(f"--{name}", required=True, type=Path)
    parser.add_argument("--split", required=True, type=Path)
    parser.add_argument("--lock", type=Path, default=DEFAULT_LOCK, help="split lock (default: the committed one)")
    args = parser.parse_args(argv)
    for option in ("captures", "annotations", "frames"):
        if not getattr(args, option).is_dir():
            print(f"Dossier introuvable : --{option}", file=sys.stderr)
            return 2
    try:
        split = control.read_split(args.split, args.lock)
        text, times, run_s, outcomes = report(args.captures, args.annotations, split, args.frames)
    except split_module.SplitError:  # the detail may name sessions: not shown
        print("Split ou verrou invalide : --split ne correspond pas à --lock.", file=sys.stderr)
        return 2
    path = args.out / REPORT_NAME
    try:
        args.out.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
    except OSError:  # the detail may hold a full path: not shown
        print("Impossible d'écrire le rapport : --out n'est pas inscriptible.", file=sys.stderr)
        return 2
    median = "-" if not times else f"{1000.0 * _quantile(times, 0.5):.1f} ms"
    print(f"Rapport : {path}")
    print(f"Durée du calcul : {run_s:.1f} s")
    print(f"Temps médian par recalage : {median}")
    print(planned_line(outcomes, times))
    return 0


if __name__ == "__main__":
    sys.exit(main())
