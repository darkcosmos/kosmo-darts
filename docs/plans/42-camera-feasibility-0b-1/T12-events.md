# T12: Arrival and empty-board events on video

CANCELLED (2026-11-18): events are rewritten in phase 2 (D1, difference of registered images) after its prototype (root plan N18).

## Goal
Detect dart arrivals and the empty board on each volley video, as the phone pipeline will (motion first, model only
on stable frames), and score them against the entry times.

## Files
- Added `tools/camera/kosmo_camera/video.py`: `frames(path, fps=10, width=960)` yields `(position_ms, gray uint8,
  rgb)` with PyAV, positions from frame timestamps.
- Added `tools/camera/kosmo_camera/events.py`: `EventParams` (`stable_diff`, `stable_ms=300`, blob area bounds in
  mm², `occluder_fraction`; defaults added to `config/defaults.json` and read through `config.py`, A1); `detect_events(frames, detector, thresholds, params) -> list[Event]` with `Event(kind:
  "arrival" | "empty", position_ms)`; board region from `decide.calibrate` on the first stable frame (no region: no
  event, counted); `score_arrivals(volley, events)` and `score_empty(volley, events)` per CV10 and CV11.
- Changed `tools/camera/config/defaults.json` (event parameters) and its test in `test_config.py`.
- Added tests `tools/camera/tests/test_events.py` on T7 videos.

## Depends on
T11.

## Rules
CV10, CV11, root plan "Approach, Events on video". Dart k window: previous non-voided entry (volley start for the
first) to entry k, using `entry.videoPositionMs`; darts with a null position are not evaluated (counted). Misses
(segment 0) have no arrival to find, but an event in their window is false. Empty board counted only on `captures-eval/` sessions
(CV11, CV17; on `captures/` the report says "not measurable") and on volleys with at least one dart on the board; a volley whose `end` is null is not evaluated.

## Tests
- Three darts appearing at planned frames: three arrivals, each in its window.
- A dart that appears and an unrelated flicker below `stable_diff`: one arrival only.
- A person-sized occluder crossing then leaving: no arrival; a dart added while occluded: one arrival when stable.
- Arrival in the wrong window: missed for dart k and false for the other.
- Empty board after removal: declared between last entry and `end`; a fake detector that sees no tip before the last
  entry produces a false empty.
- Null `videoPositionMs` and null `end` cases skipped and counted.
- A `captures/` session: empty-board result is "not measurable", never a rate; a `captures-eval/` session is scored.

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: algorithm and rules CV10, CV11 specified.

## Implementation notes
