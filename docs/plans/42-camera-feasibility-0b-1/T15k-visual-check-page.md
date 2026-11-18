# T15k: Tool page, visual check: reference and measured photo alternating, alignment applied

## Goal
Let Kosmo see with his own eyes, on his PC only, whether a photo moved: the reference photo and the measured photo
alternate, aligned by the automatic measure or by the control clicks, with the shift shown (D30 point 3). The
French text says « alignement », never « recalage », which names the snap only (U30, D45).

## Change of lot 3b (D48, D52, Q15, D54)
- DEFERRED (Q15 option 2, D54): not built until a verdict asks for the visual check. Then plan update P2 or P3
  rewrites this task and step K7.8 to fit the 10-minute budget of ADR 0018 (for example a sample of about 2 min);
  otherwise it is never built. The state strings below say « relancer le diagnostic (K7.7) » (lot 3b step
  numbers) and the static test pins that one string.
- `clicks` reads `controlPoints` from `diagnostic.json`, which since T15i lot 3b are the re-snapped points
  (`snapped-points.json`); the label stays « Alignement d'après les clics » (they come from Kosmo's clicks).

## Files
- Changed `tools/camera/kosmo_camera/annotate/server.py`: option `--diagnostic FILE` (read with the stdlib strict
  reader of `diagnostic_file.py`, T15i; sessions absent from the file are ignored, a session whose
  `segmentsSha256` or `controlsSha256` differs from the current annotation file is marked "Diagnostic périmé :
  relancer le diagnostic (K7.7)"). Route `GET /api/visual/<session>`: the session name is resolved through the
  session listing like every T6a route (unlisted name, encoded `..`, `/`, backslash, NUL: 404 as T6a). For a
  listed session it always answers 200 with a `state` the page shows in French (S12): `ok`, "Diagnostic périmé :
  relancer le diagnostic (K7.7)", "Pas de diagnostic pour cette session : relancer le diagnostic (K7.7)"; with
  `ok`, per diagnosed photo (dart order) `dart`, `referencePhoto`, the two 3x3 matrices `auto` (from
  `autoPoints`) and `clicks` (from `controlPoints`), each null with "Non mesurable" when its points are
  missing or the system is near-singular, mapping the measured photo onto the reference photo, computed in Python
  (`homography.py`, stdlib, 8x8 Gaussian elimination with partial pivoting), the numbers `autoPx`, `autoMm`,
  `trueMm`, `errorMm`, `start` = index of the photo with the largest `errorMm`, and `nextAbove[i]` = index of the
  next photo after `i` (wrapping) with `errorMm` > `VALIDATION_MAX_MM`, or null (U20, computed by the server
  function `next_above`). Same hardening as the existing routes (127.0.0.1 only, Host and Origin checks as T6a,
  served files only from the session's capture folder).
- Added `tools/camera/kosmo_camera/annotate/homography.py` (stdlib only).
- Changed `annotate/static/app.js`, `index.html`, `style.css`: screen "Contrôle visuel" (key V, button "Contrôle
  visuel (V)") of root plan "Screens and states", item "Visual check": the two images stacked, alternating every
  `VISUAL_PERIOD_MS` (700); Space pauses; keys 1, 2, 3 choose "Alignement automatique", "Alignement d'après les clics", "Aucun alignement" (applied
  with CSS `matrix3d`); opens on `start` (largest gap); arrows go to the previous or next diagnosed photo in dart
  order (the server's list order, index − 1 and + 1, no wrap past the ends); key N jumps to `nextAbove` ("Aucune
  autre photo au-delà de 0,5 mm" when null); position line "Photo i / n" with `i` = shown index + 1 in that same
  dart order, so the opening jump, N and the arrows keep `i` consistent (U24, D42); the line
  "Décalage automatique : … px (… mm) · d'après les clics : … mm · écart : … mm"; "Non mesurable" when a matrix is
  missing; the route `state` text when not `ok`. Without `--diagnostic`: "Contrôle visuel : indisponible (option
  --diagnostic absente)".
- Changed tests `test_annotate_server.py`, `test_annotate_static.py`; added `test_homography.py`.

## Depends on
T15i, T15g, T15o; Q15.

## Rules
CV16 (local only, 127.0.0.1, images never leave the PC), CV15 (`diagnostic.json` local), CV28. U24 (D42): arrows
and "Photo i / n" follow dart order. U30 (D45): « alignement » wording.

## Tests
- Homography: identity; a known translation, rotation, scale and perspective rebuilt from 4 points within 1e-9;
  collinear points refused.
- Server: route with a diagnostic file written by the test; stale hashes give 200 with the "périmé" state; a
  listed session absent from the file gives 200 with "Pas de diagnostic"; a photo with null `autoPoints` gives
  "Non mesurable" for that matrix; the T6a path attacks (encoded `..`, `/`, backslash, NUL, unlisted name) on
  `GET /api/visual/<session>` each 404 with no file read; Host or Origin wrong 403 (S12); `--diagnostic` with a
  file over the cap or with an unknown key refused at start with a one-line message.
- `next_above` and `start` on a scripted diagnostic (U20): errors [0.1, 0.9, 0.2, 0.7]: start 1, next above from 1
  is 3, from 3 wraps to 1; with a single photo above: null from that photo, that photo from any other; none above:
  null;
  boundary exactly 0.5 is not above.
- Dart order (U24, D42): a diagnostic file whose photos are written out of dart order (darts 31, 7, 19) is served
  sorted by `dart` (7, 19, 31); `start` and every `nextAbove` value index that sorted list, so "Photo i / n" of the
  page (i = index + 1) matches the dart rank; the page's arrow handlers use index − 1 and + 1 on that list only
  (static check: no other ordering in `app.js`), stopping at the ends.
- Static: strings, keys V, N, Space, 1, 2, 3, arrows bound; « recalage » and « recalé » absent from the visual
  check strings (U30); `matrix3d` built from the server numbers only; T6b scan.
- What only Kosmo can check: root plan "How Kosmo verifies", checklist K7.C (visual check lines).

## Done when
Python checks of root "Test plan" green, `kosmo-detekt` exit 0.

## Model
sonnet. Why: screen and route from a specified layout; the homography is the textbook 4-point solve with tests.

## Implementation notes
