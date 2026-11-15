# T6b: Annotation page

## Goal
The single French page of the annotation tool: it renders the state that T6a returns and sends Kosmo's actions.
No flow logic in JavaScript beyond display and input.

## Files
- Added `tools/camera/kosmo_camera/annotate/static/index.html`, `app.js`, `style.css`.
- Added tests `tools/camera/tests/test_annotate_static.py`.

## Depends on
T6a.

## Rules
CV16, D5, D8. Flow and states: T6a (do not repeat them here). Page rules:
- Coordinates: clicks converted to natural pixels of the image (independent of zoom, pan and window size); the image
  is shown with `image-orientation: none` (D8). On load, `naturalWidth` and `naturalHeight` are compared with the
  photo size of the truth file and the calibration; a mismatch shows an error and blocks every action.
- Screens: session list (progress, calibration status, error states); calibration screen with a board diagram that
  highlights the next point; darts screen with the true value large ("T20"), entry number and volley.
- Marks not relying on colour: calibration points drawn as crosses labelled "1" to "4"; earlier tips of the volley as
  hollow circles labelled with their dart number; the current tip as a crosshair. Magnifier (x4) beside the cursor,
  the point under the cursor at its centre and visible.
- Keys: H, O, U, N, P (T6a actions), `+` and `-` zoom, arrow keys pan, `0` resets the view, `Esc` back to the list.
  Keys are ignored while focus is in a text field or a button press is in progress. Buttons repeat the key letter.
- Look: dark neutral theme, text contrast at least 4.5:1, controls at least 48 px high and wide, system fonts only.
- Security: text inserted with `textContent` only; no `innerHTML`, `outerHTML`, `insertAdjacentHTML`, `eval`, `new
  Function`, `document.write`; no external URL; only `fetch` to the same origin.

## Tests
- Static scan of the three files: no `http://` or `https://`, no `@import`, no inline event handler, none of the
  forbidden calls above, no `<script src>` outside `/static/`.
- Every visible string in `index.html` and `app.js` (string table at the top of `app.js`) is French and non-empty.
- Contrast: the text and background colours declared in `style.css` meet 4.5:1 (computed in the test); button rules
  set `min-height` and `min-width` of 48px.

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0. Notes record a manual check in a browser if one is
available, otherwise say so; Kosmo's visual check is step K2 of root "How Kosmo verifies".

## Model
sonnet. Why: page from a specified flow and layout.

## Implementation notes
