# T17a: Snapping diagnosis on Kosmo's stored clicks (offline, numbers only, no click)

## Goal
Find why the snap (CV31) almost never worked on Kosmo's real photos (D49): rerun it from every stored control point
of photos and video frames and count the refusals by cause and by image type, with numeric descriptors that let the
squad build realistic synthetic cases (root "Approach, Snapping on real data").

## Files
- Changed `tools/camera/kosmo_camera/snap.py`: `snap_detail(luma, start, params)` -> `SnapResult` plus `stage` (the
  exact check that stopped it: coarse no ridge, coarse no line pair, pair not reaching the crossing, fine support,
  fine contrast, fine blur, stands-out, refine shift, too far, border, ambiguous, no convergence) and the
  descriptors of root "Approach, Snapping on real data"; `snap_point` returns the same values as before (T15m tests
  unchanged).
- Added `tools/camera/kosmo_camera/snap_report.py` (numpy, Pillow through `luma.decode_luma`, stdlib; no PyAV, runs
  in the WSL tool venv, D44): `python -m kosmo_camera.snap_report --captures DIR --annotations DIR --split FILE
  --frames DIR --out DIR`; reads controls and segments (v3, read-only), roles from `control.roles`; starts = the
  stored points (the click for `unsnapped`, the stored point for `snapped`, D49); radius sweep at 1x, 2x, 4x
  `SNAP_RADIUS_PX` (diagnostic only); proposal distance between consecutive controls of a session and source;
  writes `<out>/snap-report.md` and prints its path, the run time, the median time per snap and the line
  « Durée prévue de K7.6 : environ N min » (CV32 planned duration, D54: stored points x starts per point x median
  time per snap of this run), also written in « Durées ».
- Report sections « Échantillon », « Causes de refus » (Kosmo's categories: pas de croisement, forme, ambiguïté,
  hors rayon, autre; each with its detailed `stage`), per source (photo, vidéo) and role, « Descripteurs » (median
  and p10/p90 per category), « Rayon », « Propositions », « Durées »; every section split by split (dev, eval, from
  the split lock) and by source (CV32 independence, D54); numbers and roles only (CV15).
- Changed `docs/rules/camera.md`: CV32 (diagnosis part) of root "Rules".
- Added `tests/test_snap_report.py`; changed `tests/test_snap.py`; `TOOL_TESTS` gains `test_snap_report.py`.

## Depends on
T15m, T15c (`control.roles`), T15b.

## Rules
CV32, CV31, CV15, CV16 (images never leave the PC), CV4 (nothing tuned here), D48 point 5.

## Tests
- `snap_detail` agrees with `snap_point` on every T15m case; each `stage` reached by a scripted board (no ring
  wire, crossing beyond the radius, two crossings, blurred wire, dart-shaft crossing).
- Stage-to-category mapping is total: every `stage` value (and `unreadable`) maps to exactly one category, checked
  over the enum.
- No frames directory content (empty `--frames`) and a session with no controls: no crash, counted in
  « Échantillon ».
- T-junction descriptor: a radial wire ending at the ring wire versus one crossing it (synthetic, `synth.py`).
- Sweep: a start 15 px from a clean crossing is `too_far` at 1x and snapped at 2x.
- Split: a dev and an eval session written by the test: every section shows dev and eval apart, and dev plus eval
  equals the total of each count.
- Planned K7.6 duration: with an injected median time per snap, the printed and written line equals the CV32
  formula for the test's counts (points, `SNAP_REPEAT_DIRECTIONS`, 4 lattice starts per photo control point).
- Report on a T7 session with controls written by the test: counts add up per source and role; hidden (null)
  points counted apart; unreadable image counted `unreadable`; privacy scan (no session or file name, no path).
- `main()` in a subprocess with numpy and Pillow only (no `av` importable).

## Done when
Python checks of root "Test plan" green (CI command too), the Python 3.14 `TOOL_TESTS` run of T15n green,
`kosmo-detekt` exit 0.

## Model
opus. Why: case 3, the refusal causes on real images are unknown and the cause taxonomy must map onto the internal
checks of T15m's algorithm; a wrong diagnosis wastes one of Kosmo's runs within the 10-minute budget (D48).

## Implementation notes
- Where: `snap.snap_detail` (stages `snap.STAGES`, `SnapDescriptors`, `SnapDetail`; `snap_point` is its `.result`);
  `snap_report.py` (`collect`, `run_snaps`, `render`, `report`, `main`, `CATEGORY_OF_STAGE`, `planned_minutes`).
- Deviations: three stages beyond the task list, each mapped once: `coarse_too_far` (every coarse crossing beyond the
  radius, « hors rayon »), `fine_angle` (« forme »), `fit_failure` (non-finite profile or singular system, « autre »).
  `stem_px` = shorter of the 4 arms of the two lines beyond the crossing, sampled on the line flanks (0 at a T);
  `halo` = largest RMS of the profile-model residual over the noise; `bright_wires` = polarity.
- Config: `SNAP_SWEEP_FACTORS` [1, 2, 4] and `SNAP_LATTICE_STARTS` 4 added (defaults.json, config.py, test_config.py).
- Planned K7.6 duration counts every stored point (unreadable included, hidden excluded): (sweep factors +
  `SNAP_REPEAT_DIRECTIONS`) per point plus `SNAP_LATTICE_STARTS` per photo control point, times the median snap time
  of the run, rounded up to the minute; « inconnue » when no snap ran.
- A video control point is snapped only when `frames.json` lists a frame for its dart at its `positionMs`; otherwise
  it is counted « sans image vidéo listée » and `unreadable`. Images are decoded once per file (control and segment
  on the same photo share it).
- For T17b: the 4x sweep often turns a recovered point ambiguous (inner crossings come in range); read 2x. The
  scripted boards of `test_cv32_each_stage_is_reached_by_a_scripted_board` are starting points for the synthetic
  cases. For T17c: `report()` returns the outcomes; groups are `GROUPS` (split, source, kind).
