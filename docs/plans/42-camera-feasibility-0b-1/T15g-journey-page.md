# T15g: Tool page, journey: session states, one instruction, recalibration explained

## Goal
Rebuild the session list and the screen headers around Kosmo's journey (D30 point 5): what is done and what is
left per session, one active instruction at a time, information kept apart, recalibration explained on screen.

## Files
- Changed `annotate/static/app.js`, `index.html`, `style.css`, per root plan "Screens and states", items "Session
  list", "Instruction banner" and "Recalibration screen":
  - Session list: one card per session with its state badge (`À faire`, `En cours`, `Terminée`, `Ignorée`) and
    the step rows from `steps` ("Calibration : faite", "Pointes : 120 / 185", "Contrôle photo : 12 / 37",
    "Contrôle vidéo : indisponible (images vidéo absentes)"); `info` lines in a grey "Information" box below.
  - Instruction banner: one line at the top of every screen, the `instruction` of the server, large text; nothing
    else styled as an instruction anywhere.
  - Recalibration screen: the explanation paragraph of root "Screens and states" above the image, always shown.
  - The drift "!" marks and the "Dérive au-delà du seuil" dart text are removed (T15e no longer sends them).
- Changed `tools/camera/tests/test_annotate_static.py`: strings, banner element unique, no drift instruction
  string left in `app.js`.

## Depends on
T15e, T15f.

## Rules
CV16, D30 point 5, contrast and 48 px targets as T6b.

## Tests
- Static: the banner is the only element with the instruction style (one id, one CSS class used once); the
  recalibration explanation constant present; removed strings absent ("Dérive au-delà du seuil", "À recalibrer à
  partir de"); T6b scan.
- What only Kosmo can check: root plan "How Kosmo verifies", checklist K7.C.

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: page from specified screens and strings, same patterns as T6b and T6d.

## Implementation notes
