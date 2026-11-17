# T9b: Public class map and datasets list from Kosmo's message (D23)

HELD by D30: Kosmo's priority only, it does not depend on labels; revised at P2.

## Goal
Commit the real class map and the six `datasets.md` rows exactly as below, copied from Kosmo's message (root D23).
No mapping or status is chosen here: every value is given.

## Files
- Changed `tools/camera/config/public-class-maps.json`, `sets` (licence `"CC BY 4.0"` for each):
  `dart-detection3-v1i-coco` `{"dart_tip": "tip"}`; `mat24-dartsv2-v2i-coco` `{"Dart-Tip": "tip", "Dart":
  "whole-dart-checked"}`; `dart-tip-detection-v17i-coco` `{"dart_tip": "pseudo-tip-box"}`. Rejected sets: no entry.
- Changed `docs/camera/datasets.md`: six rows in the T9p table `| slug (dossier) | jeu | lien | licence |
  attribution | statut | motif |`. Licence cell of the five Roboflow rows: `CC BY 4.0 (page vue par Kosmo le
  2026-11-15)`. Attribution cell (D28 completed by D35, Kosmo's Q6 answer; author and link as Kosmo gave them):
  the two accepted rows `<jeu> par <auteur>, <lien>, CC BY 4.0 ; modifié : boîtes reconstruites autour de la
  pointe, points de calibration ajoutés par pseudo-étiquetage`; the conditional v17 row `<jeu> par <auteur>,
  <lien>, CC BY 4.0 ; modifié : pointes obtenues par pseudo-étiquetage, boîtes reconstruites autour de la pointe,
  points de calibration ajoutés par pseudo-étiquetage`; rejected rows: `non utilisé`.
  - `dart-detection3-v1i-coco` | dart_detection3 (DartsKEY01) | https://universe.roboflow.com/dartskey01/dart_detection3
    | accepted | motif: `boîte centrée sur la pointe (médiane 1,0 % de la largeur) ; récupéré via un fork Roboflow de
    Kosmo (aucune version publiée), version brute sans prétraitement ni augmentation ; dart_tip -> tip`.
  - `mat24-dartsv2-v2i-coco` | mat24_dartsv2 (school) | https://universe.roboflow.com/school-asc0d/mat24_dartsv2 |
    accepted | `Dart-Tip -> tip (médiane 2,1 %) ; Dart (fléchette entière) ignorée`.
  - `dart-tip-detection-v17i-coco` | dart-tip-detection-6d3mw (DartsDetector) |
    https://universe.roboflow.com/dartsdetector/dart-tip-detection-6d3mw | conditional | `boîtes autour de la
    fléchette entière (14,9 %), coin brut non accepté comme pointe ; utilisable uniquement par pseudo-étiquetage
    contraint (version B, CV23)`.
  - `dart-tip-detection-v1i-coco` | dart tip detection (PJATK) | https://universe.roboflow.com/pjatk-ni0m7/dart-tip-detection
    | rejected | `boîtes à 11,2 %, ne localisent pas la pointe`.
  - `dartsdetectfulldart-v1i-coco` | dartsdetectfulldart (SchoolPXL) | https://universe.roboflow.com/schoolpxl/dartsdetectfulldart
    | rejected | `dart = fléchette entière (24 %), db/sb hors de notre convention, 41 images`.
  - `bullseyebot` | bullseyebot (Hugging Face) | non fourni | licence `aucune` | rejected | `aucune licence`.
- Changed `tools/camera/tests/test_public_import.py`: the committed map test.
- Implementation notes list every mapping (slug, source category, target) for the PR description (T2).

## Depends on
T9p (map reader, `datasets.md` header, `test_datasets_doc.py`).

## Rules
CV21, D20, D23, D24, D28, S11 (attribution built only from Kosmo's words, never invented). No session name in
either file. If the T9p `test_datasets_doc.py` rules reject a row as written here, stop and report to main (no rule
change in this task).

## Tests
- Committed map passes the T9p strict reader and equals the three entries above exactly (expected value in the
  test as data).
- `test_datasets_doc.py` (T9p) passes with the six rows: v17 `conditional` (its `pseudo-tip-box`), the two others
  `accepted`, the three rejected rows unmapped; the three used rows carry the D35 `modifié :` text exactly,
  « points de calibration ajoutés par pseudo-étiquetage » included.

## Done when
Python checks of root "Test plan" green (including the CI command `python -m pytest -q -m "not slow"` from
`tools/camera`), `kosmo-detekt` exit 0.

## Model
haiku. Why: values copied from this file, no design choice left (D23 gives every class and status, D28 the
attribution).

## Implementation notes
