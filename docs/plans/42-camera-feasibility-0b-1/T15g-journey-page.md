# T15g: Tool page, journey: session states, one instruction, recalibration explained

## Goal
Rebuild the session list and the screen headers around Kosmo's journey (D30 point 5): what is done and what is
left per session, one active instruction at a time, information kept apart, recalibration explained on screen.

## Files
- Changed `annotate/static/app.js`, `index.html`, `style.css`, per root plan "Screens and states", items "Session
  list", "Instruction banner" and "Recalibration screen":
  - Session list: one card per session with its role (`dev 1`, `eval 2`, from T15e; Kosmo, Q9, D38) and its
    state badge (`À faire`, `En cours`, `Terminée`, `Ignorée`) and
    the step rows from `steps` with the templates of root "Screens and states" ("Calibration : faite|à faire",
    "Pointes : D / T", "Contrôle photo : D / T", "Contrôle vidéo : D / T" or "Contrôle vidéo : indisponible
    (<raison>)"); `info` lines in a grey "Information" box below.
  - Focus card (U15): the card of the server's `focus` session carries the mark "Prochaine étape" and the button
    "Continuer" (also Enter on the list, only when no other button or link has the focus: Enter on a focused
    button keeps its native action; U23, D42), which opens that session and sends its `next_step` action, so it lands on
    the exact screen the instruction names.
  - Three distinct elements (U19): the journey instruction (new `#journey`, one line at the top of every screen,
    the server's `instruction`, large text, the only element with the instruction style); the help text (existing
    `#instruction` panel hint, renamed `#help`, plain small text); the alert (existing `#banner`, errors and
    warnings only). Nothing else styled as an instruction anywhere.
  - Recalibration screen: the explanation paragraph of root "Screens and states" in the side panel (not above the
    image, so the photo stays fully visible), always shown, next to the T15f assisted point panel ("Points prêts :
    n / 4", proposals = the segment in force, Entrée saves; U16).
  - The drift "!" marks and the "Dérive au-delà du seuil" dart text are removed (T15e no longer sends them).
- Changed `tools/camera/tests/test_annotate_static.py`: strings, banner element unique, no drift instruction
  string left in `app.js`.

## Depends on
T15e, T15f.

## Rules
CV16, D30 point 5, D38, D42 (U23), contrast and 48 px targets as T6b.

## Tests
- Static: `#journey` is the only element with the instruction style (one id, one CSS class used once), `#help`
  and `#banner` use other classes; "Prochaine étape" and "Continuer" present once, the Continuer handler sends the
  `next_step` action; the Enter handler of the list checks that `document.activeElement` is not a button or a
  link before running "Continuer" (source test, U23); the role is rendered on each card; the recalibration
  explanation constant present and placed in the side panel; "Points posés" absent; removed strings absent ("Dérive au-delà du seuil", "À recalibrer à partir de"); no
  drift field rendered outside the "Information" box; T6b scan.
- What only Kosmo can check: root plan "How Kosmo verifies", checklist K7.C.

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: page from specified screens and strings, same patterns as T6b and T6d.

## Implementation notes
- Server side (commits before the page): the "Instructions" table has the blocked-session row (U35,
  `journey.BLOCKED_TEXT`, no focus); `views.summary` and `views.view` no longer carry `state`, `driftLabel`,
  `driftStale` nor the per-dart `drift` (A7); the cursor view gains `canSave` (`flow.can_save`) and
  `previousPoints` (recalibration only; A8, A9); the list answer gains `continuePayload` (None: no button, `{}`: open
  only, else the flow action; `Instruction.continue_payload`); `journey.RECALIBRATION_EXPLANATION` is the one text of
  the recalibration explanation.
- Page: `#journey` (server `instruction`, list and session screens), `#help` (was `#instruction`), `#banner` (errors
  only, drift banner and picker "!" removed). Cards: `sessionLine` (head with role and badge, step rows from `steps`,
  grey "Information" box, focus mark "Prochaine étape" and "Continuer" on the focus card). A blocked session shows
  "Lecture seule" instead of a badge. Entrée on the list: `onListKey`, only without a focused button or link.
- A step row already said by an `info` line is not repeated in the box (display only).
- The `lastSaveAt` stamp lives in the save branch of `perform`, so its test is scoped there (and `onEnter` reads it).
