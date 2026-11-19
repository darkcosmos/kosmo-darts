# C1b: Tool page for tip entry and read-only display of segments and controls

## Goal

Rebuild the annotation tool page on the view contract fixed by C1 (see `C1-remove-stopped-code.md`, `views.py`
bullet): the page enters tips as today and shows the rest read-only (root plan, "Screens and states", N24).

## Files

- `kosmo_camera/annotate/static/app.js`, `index.html`, `style.css`.
- `tests/test_annotate_static.py`.

## Depends on

C1.

## Screens

- List: one card per session with name, tip progress, calibration label, message and warning; the frames warning on
  top. No journey, badge, focus card or « Continuer » button.
- Session: the dart photo with its tip; tip keys and buttons as today (click, H caché, O hors cible, U annuler, N/P,
  picker); zoom, pan and magnifier kept. Read-only overlays: the 4 points of a segment when the photo is that
  segment's photo (as today), and the photo control of the dart, drawn differently with a legend. Key V (with its
  button) toggles the dart's listed video frame from `/api/frame/...` with its video control; no click is sent on a
  frame. The page sends only the actions of `flow.KINDS`.
- Gone: assisted panel, slots, insets, diagram, Entrée save, keys R, K, 1 to 4, recalibration help, the
  « recalé » and « Recalage indisponible » texts, the step rows and the « Contrôle de calibration (K) » button.

## Rules

CV16 (stdlib page, no external resource, same origin only), D5 (French only), D8 (stored pixel order).

## Tests

- Kept: files, no external resource, no inline handler or style, same origin, button labels end with their key, every
  key action is in `flow.KINDS` and no letter is used twice, French strings, contrast >= 4.5, 48 px buttons, system fonts, double-click
  guard, textContent only, one message place.
- New: V toggles the frame route only for a dart whose view has `frame`; overlays read `segments`, `control` and
  `frame.control` from the view and the page counts nothing; no text « recalé », « Recalage », « Points prêts »,
  « Continuer », « Contrôle de calibration », « dérive » remains; no key R, K or 1 to 4 is bound.
- Removed: the tests of the assisted screen, journey and list focus (lines 174-188, 219, 259-339, 355-499 on `0005f0c`
  except the kept ones above).

## Done when

`python -m pytest -q -m "not slow"` in `tools/camera` green; `kosmo-detekt` exit 0; `git diff --check` clean.

## Model and Why this model

sonnet: a screen rebuilt from a fixed JSON contract with its static tests; no stored format, no cross-module change.

## Implementation notes
