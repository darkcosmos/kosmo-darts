# T6d: Tool page, recalibration button, screen and flags

## Goal
Show the T6c recalibration action and drift flags in the French page, with the same layout, keys and accessibility
rules as T6b.

## Files
- Changed `tools/camera/kosmo_camera/annotate/static/app.js`: `KEY_ACTIONS` gains `r: "recalibrate"`; darts screen
  button "Recalibrer à partir de cette fléchette (R)"; recalibration screen title "Recalibration à partir de la
  fléchette {n} : point {k} sur 4" with the same 4-point diagram and next-point highlight as the calibration screen;
  hint "Effacer (U) retire le dernier point ; sans point, retour à la fléchette"; drift labels of T6c in the session
  list; on a dart whose `drift` is `suggested` a banner "Dérive au-delà du seuil : recalibrer à partir d'ici (R)",
  `beyond` a small marker "Dérive", `unmeasurable` "Dérive non mesurable"; calibration status "Calibrée
  ({count} segments)" when more than one; "(mesure périmée)" appended when `driftStale` (T6c).
- Dart picker: a `suggested` dart's label gets a text marker ("59 !") and the French `aria-label` "Fléchette 59, à
  recalibrer" (not colour only, U8).
- Recalibration screen: the points of the segment in force before dart `n` drawn as hollow crosses labelled
  "ancien 1" to "ancien 4" (label text, not colour only), so Kosmo sees the drift while clicking (U9).
- The recalibration button sits on its own row below the per-dart buttons (H, O, U, N, P and zoom), full panel
  width, at least 48 px high, label wrapping allowed (U10).
- Changed `index.html` and `style.css` only if the new button or banner needs it (colours as `:root` variables).
- Changed tests `tools/camera/tests/test_annotate_static.py` (T6b's static test file).
- Changed `docs/camera/training.md` (last task of batch 1, root D19): new French section "Dérive et recalibration"
  with steps K5.0 to K5.4 of root "How Kosmo verifies" (switch to the branch, rebuild, the exact `docker run ...
  python -m kosmo_camera.drift` line, the tool with `--drift`, R and its undo, "(mesure périmée)", repeat until
  "Tout sous le seuil : oui", paste the last report). T14 later switches it to the `drift` subcommand.
- Added test `tools/camera/tests/test_training_doc.py`: the `drift` and `annotate` commands of that section parse
  with the modules' argument parsers; the `docker run` line has the decision 10 flags and `/data` read-only.

## Depends on
T6c.

## Rules
CV16 (no external resource, no `innerHTML`, `eval`, `document.write`), D5 (French only), D8.

## Tests
- String table has every label above, each action button label ends with its key letter (R included).
- `KEY_ACTIONS` keys match the T6c action names; no key letter used twice.
- String table has the picker marker and its `aria-label`, "ancien 1" to "ancien 4" and "(mesure périmée)"; the
  stylesheet gives the recalibration button row `min-height` >= 48px (static check).
- Stale flags: for a session view with `driftStale` true and a `suggested` dart, the picker label building code
  keeps the "59 !" marker and its `aria-label` and appends "(mesure périmée)" (string and rendering-function check).
- Static scan of T6b still passes; contrast of the banner colours against the background >= 4.5:1.

## Done when
Python checks of root "Test plan" green (including the CI command `python -m pytest -q -m "not slow"` from
`tools/camera`), `kosmo-detekt` exit 0; the branch is then ready to push (main decides). Notes record a manual run: golden-like session
with a fake `drift.json`, server started, `curl` of `/api/sessions` shows the flags and `/static/app.js` is served
(no browser or `node` in the sandbox: say so in the notes; Kosmo checks the page in K5.3).

## Model
sonnet. Why: page changes from a specified flow and French strings, same patterns as T6b.

## Implementation notes
