# T9b: Public class maps and datasets list completed from Kosmo's lists

## Goal
Fill the committed class map and the `datasets.md` rows from the category lists and attribution texts Kosmo sends
after downloading the accepted public sets (root "How Kosmo verifies", K4; root D20). Runs only once those lists
are in main's assignment; nothing else depends on it.

## Files
- Changed `tools/camera/config/public-class-maps.json`: one entry per set Kosmo downloaded (slug = last segment of
  its link), `licence` `"CC BY 4.0"`, every category name of his list mapped under CV21 (the Roboflow supercategory
  at id 0 included, usually `ignore`).
- Changed `docs/camera/datasets.md`: attribution cell = Kosmo's exact text (kept "à compléter par Kosmo au
  téléchargement" for a set he did not send); `dart-tip-detection-6d3mw` becomes `accepted` or `rejected` per his
  check; a set he did not download keeps its status and has no map entry.
- Changed `tools/camera/tests/test_public_import.py`: the committed map loads strictly.
- Licence cell: date updated if Kosmo gives the day he checked the page at download; attribution copied from his
  message (from the page), never invented (S11).
- Implementation notes list every `tip` and `calN` mapping (slug, source category, target) for the PR description,
  so main and Kosmo confirm them once (T2).

## Depends on
T9.

## Rules
CV21, D20. Mapping: `tip` or `calN` only when the category name says the box marks exactly that point (dart tip;
one of the CV2 calibration points 5|20, 13|6, 17|3, 8|11 at the outer double edge, in that order); a whole-dart,
flight, barrel or ambiguous category is `drop-image`; a category with no dart and no point (board, supercategory)
is `ignore`. Never guess: a category whose meaning is unclear is `drop-image` and listed in the notes, so main can
ask Kosmo. No session name in either file.

## Tests
- Committed map passes the T9 strict reader; each mapped set's category names equal Kosmo's list exactly (the list
  copied into the test as data, from main's assignment).
- `test_datasets_doc.py` (T9) still passes: every mapped slug is an `accepted` row.

## Done when
Python checks of root "Test plan" green (including the CI command `python -m pytest -q -m "not slow"` from
`tools/camera`), `kosmo-detekt` exit 0.

## Model
sonnet. Why: each source category is mapped under CV21 by its meaning; not haiku because a wrong `tip` mapping
silently corrupts training.

## Implementation notes
