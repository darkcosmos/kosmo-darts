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
  ({count} segments)" when more than one.
- Changed `index.html` and `style.css` only if the new button or banner needs it (colours as `:root` variables).
- Changed tests `tools/camera/tests/test_annotate_static.py` (T6b's static test file).

## Depends on
T6c.

## Rules
CV16 (no external resource, no `innerHTML`, `eval`, `document.write`), D5 (French only), D8.

## Tests
- String table has every label above, each action button label ends with its key letter (R included).
- `KEY_ACTIONS` keys match the T6c action names; no key letter used twice.
- Static scan of T6b still passes; contrast of the banner colours against the background >= 4.5:1.

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0. Notes record a manual run: golden-like session
with a fake `drift.json`, server started, `curl` of `/api/sessions` shows the flags and `/static/app.js` is served
(no browser or `node` in the sandbox: say so in the notes; Kosmo checks the page in K5.3).

## Model
sonnet. Why: page changes from a specified flow and French strings, same patterns as T6b.

## Implementation notes
