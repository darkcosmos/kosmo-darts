# T15k: Tool page, visual check: reference and measured photo alternating, registration applied

## Goal
Let Kosmo see with his own eyes, on his PC only, whether a photo moved: the reference photo and the measured photo
alternate, registered by the automatic measure or by the control clicks, with the shift shown (D30 point 3).

## Files
- Changed `tools/camera/kosmo_camera/annotate/server.py`: option `--diagnostic FILE` (read with the stdlib strict
  reader of `diagnostic_file.py`, T15i; sessions absent from the file are ignored, a session whose
  `segmentsSha256` or `controlsSha256` differs from the current annotation file is marked "Diagnostic périmé :
  relancer le diagnostic (K7.4)"). Route `GET /api/visual/<session>`: per diagnosed photo `dart`,
  `referencePhoto`, the two 3x3 matrices "Automatique" (from `autoPoints`) and "Clics de contrôle" (from
  `controlPoints`) mapping the measured photo onto the reference photo, computed in Python (`homography.py`,
  stdlib, 8x8 Gaussian elimination with partial pivoting, refuses a near-singular system), and the numbers
  `autoPx`, `autoMm`, `trueMm`, `errorMm`. Same hardening as the existing routes (127.0.0.1 only, Host and token
  checks, served files only from the session's capture folder).
- Added `tools/camera/kosmo_camera/annotate/homography.py` (stdlib only).
- Changed `annotate/static/app.js`, `index.html`, `style.css`: screen "Contrôle visuel" (key V, button "Contrôle
  visuel (V)") of root plan "Screens and states", item "Visual check": the two images stacked, alternating every
  `VISUAL_PERIOD_MS` (700); Space pauses; keys 1, 2, 3 choose "Automatique", "Clics de contrôle", "Aucun" (applied
  with CSS `matrix3d`); arrows go to the previous or next diagnosed photo; the line "Décalage automatique : … px
  (… mm) · d'après les clics : … mm · écart : … mm"; "Non mesurable" when a matrix is missing. Without
  `--diagnostic`: "Contrôle visuel : indisponible (option --diagnostic absente)".
- Changed tests `test_annotate_server.py`, `test_annotate_static.py`; added `test_homography.py`.

## Depends on
T15i, T15g.

## Rules
CV16 (local only, 127.0.0.1, images never leave the PC), CV15 (`diagnostic.json` local), CV28.

## Tests
- Homography: identity; a known translation, rotation, scale and perspective rebuilt from 4 points within 1e-9;
  collinear points refused.
- Server: route with a diagnostic file written by the test; stale hashes give the "périmé" text; session not in
  the file ignored; unknown session 404; Host or token wrong 403; `--diagnostic` with a file over the cap or with
  an unknown key refused at start with a one-line message.
- Static: strings, keys V, Space, 1, 2, 3, arrows bound; `matrix3d` built from the server numbers only; T6b scan.
- What only Kosmo can check: root plan "How Kosmo verifies", checklist K7.C (visual check lines).

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: screen and route from a specified layout; the homography is the textbook 4-point solve with tests.

## Implementation notes
