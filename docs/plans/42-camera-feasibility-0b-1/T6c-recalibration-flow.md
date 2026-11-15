# T6c: Tool, recalibration action and drift flags (flow and server)

## Goal
Let Kosmo start a new calibration segment from any judged dart, undo it, and see from the drift flag file which
sessions and darts need it (D15), in the Python flow and server so every rule is tested without a browser.

## Files
- Changed `tools/camera/kosmo_camera/annotate/flow.py`: `KINDS` gains `"recalibrate"`; cursor kind
  `RECALIBRATION` with `number` (start dart), `photo` (that dart's photo) and `pending`.
  - `recalibrate` on a dart cursor `n`: if a segment starts at `n`, cursor `RECALIBRATION(n)` with its 4 points as
    `pending` (as T6a reopens the calibration); else with no point. On the first segment's start dart it is the
    existing calibration screen.
  - `click`: adds a pending point; the 4th inserts the segment `{fromDart n, photo of n, real JPEG size, points}`
    (or replaces the one at `n`), saves, cursor back to dart `n`. A click with 4 points is ignored.
  - `clear` (U): removes the last point; if the segment was saved, it is removed from the file (saved) and its other
    points stay pending; with no point left, leaves to dart `n`, file unchanged.
  - `next`, `previous`, `goto`: leave; pending points of an unsaved segment are dropped (no file change).
- Changed `tools/camera/kosmo_camera/annotate/server.py` and `__main__.py`: option `--drift FILE` (optional);
  the file is read through `drift_file.decode` on every `GET /api/sessions` and session view, never written;
  missing, invalid or over 1 MiB gives one list warning "Mesure de dérive illisible : <cause>" and no flags.
  Per session (absent from the file: "Dérive non mesurée"; `segmentsSha256` differs from the current file: "Mesure
  de dérive périmée : relancer la mesure"; `beyond` not empty: "À recalibrer à partir de la fléchette 59" or "... des
  fléchettes 12, 59" from `suggested`; else "Dérive OK (max 0,4 mm)"; plus "N photos non mesurables" when N > 0). The
  session view gives each dart `drift: "suggested" | "beyond" | "unmeasurable" | null` (null when stale or absent).
- Changed `docs/rules/camera.md`: CV16 drift file sentence (root plan "Rules").
- Changed tests `test_annotate_flow.py`, `test_annotate_server.py`.

## Depends on
T5b (segments), T8b (`drift_file.py`).

## Rules
CV20, CV16, CV5, D8. Every change saved at once through the single write route ("Enregistré à HH:MM:SS"); the
segment photo size is the real JPEG size (D8). The tool never reads photos for drift (stdlib only).

## Tests
- Recalibrate from dart 59: 4 clicks give a second segment from 59 with dart 59's photo, saved; cursor on dart 59.
- Recalibrate on an existing segment start: points shown; U removes the 4th (segment removed, saved), 3 pending;
  U x3 then U again leaves to the dart; file has no segment from 59.
- Leaving with 2 pending points: file unchanged, no segment. A 5th click ignored.
- Recalibrate on a voided entry is impossible (not a dart cursor); on the first dart it opens segment 0.
- Tips of darts after 59 unchanged by any recalibration step.
- Flags: each list label above; stale `segmentsSha256`; invalid, oversized and missing file give the warning and the
  tool still serves; the flag file is unchanged after a session of actions (hash); unlisted session in the file
  ignored.

## Done when
Python checks of root "Test plan" green, `python3 -I -S -m kosmo_camera.annotate --help` works, `kosmo-detekt`
exit 0.

## Model
sonnet. Why: flow, undo and flag rules specified, Python tests, same patterns as T6a.

## Implementation notes
