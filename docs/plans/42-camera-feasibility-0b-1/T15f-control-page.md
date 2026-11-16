# T15f: Tool page, control calibration screen

## Goal
Give Kosmo a fast control screen: one point at a time, already zoomed on the expected area, hidden point and undo
by key, progress always visible (D30 point 1).

## Files
- Changed `tools/camera/kosmo_camera/annotate/static/app.js`, `index.html`, `style.css`: control screen of root plan
  "Screens and states", item "Control screen": title "Contrôle de calibration : photo de la fléchette N, point k sur
  4" (or "image vidéo de la fléchette N"), the CV2 diagram highlighting point k, progress "Photos contrôlées : D /
  T · Images vidéo : D / T" (templates filled from the server counts), the line "Points posés : n / 4 (enregistrés
  au 4e point)" from `pending` (U16), buttons with their keys "Point caché (H)", "Effacer (U)", "Quitter le
  contrôle (Échap)"; key K on every session screen ("Terminé" included) and the button "Contrôle de calibration
  (K)". At each point the view is zoomed (x4, `CONTROL_ZOOM`, a page constant) on `zoomCentre` of point k from
  the server; the full view (key 0) and the wheel zoom of T6b keep working, and the hint "Point hors de la vue :
  touche 0. H seulement si une fléchette le cache" is always shown under the buttons (U18; H four times skips an
  unusable photo, stored as four hidden points, already accepted by T15d); no marker is drawn at
  that centre and no automatic measure is shown (no anchoring, root D31). Clicked points of the current item drawn
  as filled numbered crosses. "Enregistré à HH:MM:SS" after each 4th point. Text built with `textContent` only.
- Changed `tools/camera/tests/test_annotate_static.py`: new strings, keys and buttons; static scan as T6b.

## Depends on
T15d.

## Rules
CV16, CV25, D8 (clicks in stored JPEG pixels through the existing view transform), contrast and 48 px targets as
T6b.

## Tests
- Static: every French string above present in `app.js` exactly once as a constant; keys K, H, U, Escape, 0 and
  the wheel handler bound on the control screen; K bound in the handler shared by every session screen; no
  `innerHTML`, `eval`, `document.write`, external URL (T6b scan); the zoom-centre code path never draws a marker
  (no call of the marker function with `zoomCentre`, checked by a source test).
- What only Kosmo can check (JavaScript cannot run in the sandbox): root plan "How Kosmo verifies", checklist K7.C.

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: page change from a specified screen and strings, same patterns as T6b and T6d.

## Implementation notes
