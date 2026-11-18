# T15f: Tool page, assisted point screen (control, calibration, recalibration)

## Goal
Give Kosmo a fast assisted screen: the 4 snapped points shown zoomed with their status, Entrée to confirm, a click
near the right crossing to correct, hidden point and undo by key, progress always visible (D30 point 1, D39).

## Files
- Changed `tools/camera/kosmo_camera/annotate/static/app.js`, `index.html`, `style.css`, per root plan "Screens
  and states", items "Assisted point screen" and "Control screen" (strings exactly as written there):
  - Assisted point screen, shared by the control, first calibration and recalibration screens (each keeps its
    title): 4 insets in a 2 x 2 grid in the side panel, 400 px wide on this screen (x3 zoom of `SNAP_INSET_PX`
    60 source px, so 180 x 180 CSS px; insets, ready line and save button visible together at 1080p, U26), drawn
    from the already loaded image with canvas `drawImage`, no new request, labels `1 : 5|20` to `4 : 8|11`, a thin
    cross and the status text (`recalé`, `non recalé`, `caché`, `à cliquer`) from the server slots, an
    `aria-label` « Point k, statut » (U31); numbered crosses on the full view; the CV2 board diagram kept only where
    points are filled in order (first calibration, item without proposals), highlighting the slot the next click
    fills (U27); line
    "Points prêts : n / 4 (Entrée pour enregistrer)"; buttons "Enregistrer (Entrée)", "Point caché (H)" (control
    only), "Annuler (U)", "Quitter (Échap)"; keys Entrée, 1 to 4, H (control only), U, Escape, 0 and the wheel;
    the hint (calibration screens without its H sentence); "Enregistré à HH:MM:SS" after a save; the line
    "Recalage indisponible : relancer l'outil avec ~/kosmo-tool-venv/bin/python (K7.1b)" when the server says
    unavailable, with clicks ignored on this screen.
  - Entrée rule (U25, D45): one document `keydown` handler, `event.preventDefault()` on Entrée, Entrée always
    means « Enregistrer » whatever has the focus; every button calls `blur()` after its click. Repeat guard (U29):
    Entrée with `event.repeat` ignored, and for `SAVE_GUARD_MS` (300, page constant next to `CLICK_GUARD_MS`) after
    a save; `save` sends the open item. Banners from the server: « Photo précédente rouverte : son enregistrement
    est retiré jusqu'à Entrée » (U28), « Photo illisible : le recalage est impossible pour cette photo » (S14),
    the click and hide refusals of CV31.
  - Control screen: title, progress templates "Photos contrôlées : D / T · Images vidéo : D / T", key K on every
    session screen ("Terminé" included) and the button "Contrôle de calibration (K)".
  - Text built with `textContent` only.
- Changed `tools/camera/tests/test_annotate_static.py`: new strings, keys and buttons; static scan as T6b.

## Depends on
T15d (control slots; T15n serves the calibration and recalibration slots).

## Rules
CV16, CV25, CV31, D45 (U25 to U29, U31), D8 (clicks in stored JPEG pixels through the existing view transform), contrast and 48 px targets as
T6b.

## Tests
- Static: every French string above present in `app.js` exactly once as a constant; keys Enter, 1 to 4, H, U,
  Escape, 0 and the wheel handler bound on the assisted screen; K bound in the handler shared by every session
  screen; the four status texts and the "Recalage indisponible" line present; the strings "Points posés" and
  `zoomCentre` absent; no `innerHTML`, `eval`, `document.write`, external URL (T6b scan); insets drawn without a
  new fetch (no URL built in the inset code, source test); `SNAP_INSET_PX` 60, `SAVE_GUARD_MS` 300; the Entrée
  handler holds `preventDefault`, `event.repeat` and the guard; each assisted-screen button handler calls
  `blur()`; insets set `aria-label` (U25, U26, U29, U31).
- What only Kosmo can check (JavaScript cannot run in the sandbox): root plan "How Kosmo verifies", checklist K7.C.

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: page change from a specified screen and strings, same patterns as T6b and T6d.

## Implementation notes
- Page: the titles of the three assisted screens come from the server (`view.instruction`), so the page holds no
  title template; the old click-placed calibration texts and `pending` use are removed. The server sends no banner text
  for `unreadable` (`cursor.unreadable`) nor for the reopened photo: the page builds both (`bannerMessage`, and
  `reopenedItem` after a `clear` that moves a control cursor to another item). Refusals (409) stay in the header status.
- `renderAssistedPanel`, `drawInsets` (canvas `drawImage` from `#photo`), `assistedButton` (blur), `onEnter`/`saveItem`
  (guards, save payload by kind), `onAssistedKey`; video items load from `/api/frame/...` and skip the truth size check.
- Kosmo-only checks (no JavaScript runtime here): root plan K7.C. The CV2 diagram shows only for an item opened with
  all four slots empty (`state.noProposals`). T15g still owns the list, `#journey`/`#help` and the Continuer card.
