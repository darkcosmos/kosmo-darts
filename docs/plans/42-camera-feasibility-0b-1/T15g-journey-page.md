# T15g: Tool page, journey: session states, one instruction, recalibration explained

## Goal
Rebuild the session list and the screen headers around Kosmo's journey (D30 point 5): what is done and what is
left per session, one active instruction at a time, information kept apart, recalibration explained on screen.

## Files
- Changed `annotate/static/app.js`, `index.html`, `style.css`, per root plan "Screens and states", items "Session
  list", "Instruction banner" and "Recalibration screen":
  - Session list: one card per session with its state badge (`À faire`, `En cours`, `Terminée`, `Ignorée`) and
    the step rows from `steps` with the templates of root "Screens and states" ("Calibration : faite|à faire",
    "Pointes : D / T", "Contrôle photo : D / T", "Contrôle vidéo : D / T" or "Contrôle vidéo : indisponible
    (<raison>)"); `info` lines in a grey "Information" box below.
  - Focus card (U15): the card of the server's `focus` session carries the mark "Prochaine étape" and the button
    "Continuer" (also Enter on the list), which opens that session and sends its `next_step` action, so it lands on
    the exact screen the instruction names.
  - Three distinct elements (U19): the journey instruction (new `#journey`, one line at the top of every screen,
    the server's `instruction`, large text, the only element with the instruction style); the help text (existing
    `#instruction` panel hint, renamed `#help`, plain small text); the alert (existing `#banner`, errors and
    warnings only). Nothing else styled as an instruction anywhere.
  - Recalibration screen: the explanation paragraph of root "Screens and states" in the side panel (not above the
    image, so the photo stays fully visible), always shown; the line "Points posés : n / 4 (enregistrés au 4e
    point)" (U16; the T6c recalibration also writes only at the 4th point).
  - The drift "!" marks and the "Dérive au-delà du seuil" dart text are removed (T15e no longer sends them).
- Changed `tools/camera/tests/test_annotate_static.py`: strings, banner element unique, no drift instruction
  string left in `app.js`.

## Depends on
T15e, T15f.

## Rules
CV16, D30 point 5, contrast and 48 px targets as T6b.

## Tests
- Static: `#journey` is the only element with the instruction style (one id, one CSS class used once), `#help`
  and `#banner` use other classes; "Prochaine étape" and "Continuer" present once, the Continuer handler sends the
  `next_step` action; the recalibration explanation constant present and placed in the side panel; "Points posés"
  template present once; removed strings absent ("Dérive au-delà du seuil", "À recalibrer à partir de"); no
  drift field rendered outside the "Information" box; T6b scan.
- What only Kosmo can check: root plan "How Kosmo verifies", checklist K7.C.

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: page from specified screens and strings, same patterns as T6b and T6d.

## Implementation notes
